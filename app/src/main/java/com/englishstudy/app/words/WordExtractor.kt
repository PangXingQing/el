package com.englishstudy.app.words

/**
 * 一个单词在原文里的出现情况
 *
 * @param count 出现次数
 * @param sentence 第一次出现所在的句子，显示在生词下方当上下文
 */
data class WordOccurrence(
    val count: Int,
    val sentence: String
)

/**
 * 从文本里抽取英文单词：统计出现次数，并记下它在原文中的位置句子。
 *
 * 只做字面归一化（小写 + 去所有格），**不做词形还原**（那一步在 [WordMorphology]）：
 * books / running 这类变形不会折算成 book / run，
 * 所以只要词库里没有它们本身，就会被当成生词。
 */
object WordExtractor {

    /**
     * 拉丁字母，含重音字符（cliché / résumé）。
     * 刻意不扩大到「所有 Unicode 字母」——那样中文也会被当成单词抽出来。
     */
    private const val LETTER = "A-Za-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u00FF"

    /** 英文单词：字母开头，允许中间的连字符与撇号（don't / well-known） */
    private val TOKEN = Regex("[$LETTER]+(?:['’-][$LETTER]+)*")

    /** 找句子时向两侧最多各扫描这么多字符（防止一句话没标点时把整段吞进来） */
    private const val MAX_SENTENCE_SCAN = 240

    /** 上下文最多显示这么多字符，超出截断加省略号 */
    private const val MAX_SENTENCE_CHARS = 140

    /**
     * 统计每个单词的出现次数与首次出现的句子。
     *
     * @param texts 原文（文件解析后的每段文本）
     */
    fun analyze(texts: Sequence<String>): LinkedHashMap<String, WordOccurrence> {
        val counts = LinkedHashMap<String, Int>()
        val sentences = HashMap<String, String>()

        for (text in texts) {
            if (text.isEmpty()) continue
            for (match in TOKEN.findAll(text)) {
                val word = normalize(match.value)
                if (word.isEmpty()) continue

                counts[word] = (counts[word] ?: 0) + 1
                if (word !in sentences) {
                    sentences[word] = sentenceAt(text, match.range.first)
                }
            }
        }

        val result = LinkedHashMap<String, WordOccurrence>(counts.size)
        for ((word, count) in counts) {
            result[word] = WordOccurrence(count, sentences[word].orEmpty())
        }
        return result
    }

    /**
     * 取 offset 所在的句子，用于给生词显示上下文。
     *
     * 句末标点一起带上；跨行内容合并成一行；过长时截断加省略号。
     */
    fun sentenceAt(text: String, offset: Int): String {
        if (text.isEmpty()) return ""
        val pos = offset.coerceIn(0, text.lastIndex)

        var start = pos
        while (start > 0 && pos - start < MAX_SENTENCE_SCAN && !isSentenceBoundary(text[start - 1])) start--

        var end = pos
        while (end < text.length && end - pos < MAX_SENTENCE_SCAN && !isSentenceBoundary(text[end])) end++
        // 句末标点（不是换行）也一起显示
        if (end < text.length && text[end] != '\n') end++

        val sentence = text.substring(start, end).trim().replace('\n', ' ')
        return if (sentence.length > MAX_SENTENCE_CHARS) {
            sentence.take(MAX_SENTENCE_CHARS).trimEnd() + "…"
        } else {
            sentence
        }
    }

    private fun isSentenceBoundary(c: Char): Boolean =
        c == '.' || c == '!' || c == '?' || c == ';' || c == '\n' ||
        c == '。' || c == '！' || c == '？' || c == '；'

    /**
     * 归一化：小写 + 去掉所有格。
     *
     * student's → student，students' → students，
     * 词首尾残留的连字符 / 撇号（引号、破折号造成）一并去掉。
     */
    fun normalize(raw: String): String {
        var word = raw.lowercase().trim { !it.isLetter() }
        if (word.length > 2 && (word.endsWith("'s") || word.endsWith("’s"))) {
            word = word.dropLast(2)
        }
        return word.trim { !it.isLetter() }
    }

    /**
     * 取一行文本里的第一个英文单词，用于读取词表文件。
     *
     * 「一行一词」与「单词 + TAB + 释义」两种写法都能读，后者只取行首单词。
     */
    fun firstWord(line: String): String {
        val match = TOKEN.find(line) ?: return ""
        return normalize(match.value)
    }
}
