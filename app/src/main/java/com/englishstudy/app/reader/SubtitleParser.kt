package com.englishstudy.app.reader

import java.io.BufferedReader
import java.io.StringReader

/**
 * 字幕解析器接口
 */
interface SubtitleParser {
    fun parse(content: String): List<SubtitleEntry>
    fun supports(fileName: String): Boolean
}

// ===== 字幕文本清洗 =====

/** ASS/SSA 样式覆盖标签，例如 {\fs16\an2\b0} （注意 \} 必须转义，ICU 正则不接受未转义的 }） */
private val ASS_STYLE_TAG = Regex("""\{[^{}]*\}""")

/** 常见 HTML 标签，例如 <i> </i> <font color="#fff"> */
private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")

/** ASS 的换行转义 \N \n */
private val ASS_LINE_BREAK = Regex("""\\[Nn]""")

/** 中日韩文字（用于识别双语字幕里的译文行） */
private val CJK_CHAR = Regex("""[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\u3040-\u30FF\uAC00-\uD7AF]""")

/**
 * 去掉字幕行里的样式标签等非正文内容。
 * 双语字幕常见形如 `{\fs16\an2\b0}我们说到哪了?`，这些标签不该显示给读者。
 */
internal fun cleanSubtitleLine(raw: String): String =
    raw.replace(ASS_STYLE_TAG, "")
        .replace(HTML_TAG, "")
        .replace(ASS_LINE_BREAK, " ")
        .replace("\uFEFF", "")
        .trim()

/**
 * 拆分双语字幕。
 *
 * 同一时间轴上的多行里，含中日韩文字的行视为译文，其余视为原文；
 * 若各行属于同一种文字（单语字幕），则全部作为原文，译文留空。
 */
internal fun splitBilingual(lines: List<String>): Pair<String, String> {
    val cjkLines = lines.filter { CJK_CHAR.containsMatchIn(it) }
    val otherLines = lines.filterNot { CJK_CHAR.containsMatchIn(it) }

    return if (cjkLines.isNotEmpty() && otherLines.isNotEmpty()) {
        otherLines.joinToString("\n") to cjkLines.joinToString("\n")
    } else {
        lines.joinToString("\n") to ""
    }
}

/**
 * TXT 文本解析器
 * - 按空行分段，每段作为一个条目
 * - 段内保留原始换行，并自动拆分双语
 */
class TxtParser : SubtitleParser {
    override fun parse(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val reader = BufferedReader(StringReader(content.removePrefix("\uFEFF")))
        val buffer = mutableListOf<String>()
        var index = 0

        fun flush() {
            val lines = buffer.map { cleanSubtitleLine(it) }.filter { it.isNotBlank() }
            buffer.clear()
            if (lines.isEmpty()) return

            val (text, translation) = splitBilingual(lines)
            entries.add(SubtitleEntry(text = text, translation = translation, index = index++))
        }

        reader.forEachLine { line ->
            if (line.isBlank()) flush() else buffer.add(line)
        }
        flush()

        // 如果没有任何分段，则将所有非空行作为一个条目
        if (entries.isEmpty()) {
            val lines = content.lines().map { cleanSubtitleLine(it) }.filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                val (text, translation) = splitBilingual(lines)
                entries.add(SubtitleEntry(text = text, translation = translation, index = 0))
            }
        }

        return entries
    }

    override fun supports(fileName: String): Boolean =
        fileName.endsWith(".txt", ignoreCase = true) ||
        fileName.endsWith(".text", ignoreCase = true)
}

/**
 * SRT 字幕解析器
 * 格式：
 * 1
 * 00:00:20,000 --> 00:00:24,400
 * Some text here
 *
 * 2
 * 00:00:25,000 --> 00:00:29,000
 * Another subtitle
 *
 * 双语字幕同一时间轴会有两行（原文 + 译文），会被自动拆分。
 */
class SrtParser : SubtitleParser {

    private val timePattern = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

    override fun parse(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val lines = content.removePrefix("\uFEFF").lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                // 序号行
                line.isNotEmpty() && line.all { it.isDigit() } -> {
                    val index = line.toIntOrNull() ?: 0
                    i++

                    // 时间行
                    if (i < lines.size) {
                        val timeLine = lines[i].trim()
                        val timeMatch = timePattern.find(timeLine)
                        if (timeMatch != null) {
                            val startTime = parseTimeMs(
                                timeMatch.groupValues[1].toInt(),
                                timeMatch.groupValues[2].toInt(),
                                timeMatch.groupValues[3].toInt(),
                                timeMatch.groupValues[4].toInt()
                            )
                            val endTime = parseTimeMs(
                                timeMatch.groupValues[5].toInt(),
                                timeMatch.groupValues[6].toInt(),
                                timeMatch.groupValues[7].toInt(),
                                timeMatch.groupValues[8].toInt()
                            )
                            i++

                            // 文本行（可能有多行，双语字幕通常是两行）
                            val textLines = mutableListOf<String>()
                            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                                textLines.add(cleanSubtitleLine(lines[i]))
                                i++
                            }

                            val cleaned = textLines.filter { it.isNotBlank() }
                            if (cleaned.isNotEmpty()) {
                                val (text, translation) = splitBilingual(cleaned)
                                entries.add(
                                    SubtitleEntry(
                                        text = text,
                                        translation = translation,
                                        startTime = startTime,
                                        endTime = endTime,
                                        index = index
                                    )
                                )
                            }
                        }
                    }
                }
                line.isEmpty() -> i++
                else -> i++  // 跳过非标准行
            }
        }

        return entries
    }

    private fun parseTimeMs(h: Int, m: Int, s: Int, ms: Int): Long =
        (h * 3600000L + m * 60000L + s * 1000L + ms)

    override fun supports(fileName: String): Boolean =
        fileName.endsWith(".srt", ignoreCase = true)
}

/**
 * 文件解析器工厂
 *
 * 只有 SRT 需要专用解析；其余一律按纯文本分段处理，
 * 所以**任意文本文件都能打开**（.md / .log / .csv / 无扩展名都可以当素材）。
 */
object SubtitleParserFactory {
    private val parsers = listOf(TxtParser(), SrtParser())

    /** 没匹配到专用解析器时的兜底：按纯文本分段 */
    private val fallback: SubtitleParser = TxtParser()

    fun getParser(fileName: String): SubtitleParser =
        parsers.firstOrNull { it.supports(fileName) } ?: fallback

    /** 界面提示用：优先按格式解析的扩展名 */
    fun preferredExtensions(): List<String> = listOf("srt", "txt")
}
