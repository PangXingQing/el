package com.englishstudy.app.words

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

/** 生词：文件里出现过、但不在词库中的单词 */
data class UnknownWord(
    val word: String,
    /** 在文中出现的次数 */
    val count: Int,
    /** 第一次出现所在的句子，显示在单词下方当上下文 */
    val sentence: String = ""
)

/**
 * 词库仓库。
 *
 * 词库由两部分组成，都常驻内存（HashSet），判定是 O(1)：
 * - **默认词表**：APK 里的一份词表文件 `assets/wordlist/known_words.txt`（约 8600 词），
 *   启动时整份读进内存，不落库；
 * - **用户词条**：用户点「加入词库」加进来的词，存 SQLite（通常只有几十条）。
 *
 * 默认词表之所以不放进数据库：它是只读数据，进库要走「建库 + 8600 多次 INSERT + 再查回来」，
 * 实测首次启动约 1.1s（在后台线程，但首次分析生词要等它）；
 * 直接读一个词表文件进内存只要几十毫秒，而且词表文件本身也更好维护。
 *
 * 注意：[warmUp] / [filterUnknown] 会读 assets 与数据库，请在后台线程调用；
 * 应用启动时已经 warmUp 过一次，界面里的 [isKnown] 读的是内存缓存。
 */
object WordRepository {

    /** 默认词表：一行一词；也兼容「单词 + TAB + 释义」的词典导出格式 */
    private const val ASSET_DEFAULT_WORDS = "wordlist/known_words.txt"

    private var appContext: Context? = null
    private var database: KnownWordDatabase? = null

    /** 默认词表缓存，null 表示尚未加载 */
    @Volatile
    private var defaultWords: HashSet<String>? = null

    /** 用户词条缓存，null 表示尚未加载 */
    @Volatile
    private var userWords: HashSet<String>? = null

    private val lock = Any()

    /** 在 Application.onCreate 里调用一次 */
    fun init(context: Context) {
        if (database == null) {
            appContext = context.applicationContext
            database = KnownWordDatabase(context)
        }
    }

    /** 预热：默认词表、用户词条与不规则变形表都读进内存（后台线程调用） */
    fun warmUp() {
        defaults()
        users()
        WordMorphology.warmUp(requireContext().assets)
    }

    /**
     * 该词是否已经认识（词库中的词不会出现在阅读区）。
     *
     * 变形也算认识：books / walking / went / children 只要能还原成词库里的词就返回 true。
     */
    fun isKnown(rawWord: String): Boolean {
        val word = WordExtractor.normalize(rawWord)
        if (word.isEmpty()) return false

        val defaults = defaults()
        val users = users()
        return matchesKnownWord(word) { it in defaults || it in users }
    }

    /**
     * 过滤出生词，按出现次数降序（次数相同按字母序）。
     *
     * @param words [WordExtractor.analyze] 的结果
     */
    fun filterUnknown(words: Map<String, WordOccurrence>): List<UnknownWord> {
        val defaults = defaults()
        val users = users()
        return words.entries
            .asSequence()
            .filterNot { matchesKnownWord(it.key) { word -> word in defaults || word in users } }
            .map { UnknownWord(it.key, it.value.count, it.value.sentence) }
            .sortedWith(compareByDescending<UnknownWord> { it.count }.thenBy { it.word })
            .toList()
    }

    /**
     * 新增词条。
     *
     * @return true 表示确实新增；false 表示归一化后为空、或词库里已经有它
     */
    fun add(rawWord: String): Boolean {
        val word = WordExtractor.normalize(rawWord)
        if (word.isEmpty() || isKnown(word)) return false

        val values = ContentValues().apply {
            put(KnownWordDatabase.COL_WORD, word)
            put(KnownWordDatabase.COL_SOURCE, KnownWordDatabase.SOURCE_USER)
            put(KnownWordDatabase.COL_ADDED_AT, System.currentTimeMillis())
        }
        val rowId = requireDatabase().writableDatabase.insertWithOnConflict(
            KnownWordDatabase.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        if (rowId == -1L) return false   // 并发场景下已被别的调用插入

        synchronized(lock) { userWords?.add(word) }
        return true
    }

    /** 词库条目总数（默认词表 + 用户词条） */
    fun count(): Int = defaults().size + users().size

    // ===== 内部 =====

    private fun requireDatabase(): KnownWordDatabase = database
        ?: error("WordRepository.init() 未被调用")

    private fun requireContext(): Context = appContext
        ?: error("WordRepository.init() 未被调用")

    /**
     * token 能否通过"原形"命中词库。
     *
     * 先直接查，再用不规则表 / 词尾规则还原出候选原形逐个查；
     * 缩写还原后可能还带词形变化（doesn't → does → do），所以再做一跳。
     */
    private fun matchesKnownWord(token: String, known: (String) -> Boolean): Boolean {
        if (known(token)) return true

        val candidates = ArrayList<String>(8)
        WordMorphology.irregularLemma(token)?.let { candidates.add(it) }
        candidates.addAll(WordMorphology.candidates(token))

        for (candidate in candidates) {
            if (known(candidate)) return true

            WordMorphology.irregularLemma(candidate)?.let { if (known(it)) return true }
            for (second in WordMorphology.candidates(candidate)) {
                if (known(second)) return true
            }
        }
        return false
    }

    /** 默认词表：首次访问时从 assets 读进来 */
    private fun defaults(): HashSet<String> {
        defaultWords?.let { return it }
        synchronized(lock) {
            defaultWords?.let { return it }

            val set = HashSet<String>(16_384)
            val assets = requireContext().assets
            runCatching {
                assets.open(ASSET_DEFAULT_WORDS).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val word = parseWordLine(line)
                        if (word.isNotEmpty()) set.add(word)
                    }
                }
            }
            defaultWords = set
            return set
        }
    }

    /**
     * 解析词表里的一行。
     *
     * 打包的词表是「一行一词」，直接走快速路径（不做正则匹配）；
     * 如果换成「单词 + TAB + 释义」的词典导出格式，再退回正则取行首单词。
     */
    private fun parseWordLine(line: String): String {
        val trimmed = line.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return ""

        val first = trimmed[0]
        val looksLikeSingleWord = trimmed.length <= 64 &&
            trimmed.none { it.isWhitespace() } &&
            first.isLetter() && first.code < 0x2E80    // 排除中日韩文字

        return if (looksLikeSingleWord) {
            WordExtractor.normalize(trimmed)
        } else {
            WordExtractor.firstWord(trimmed)
        }
    }

    /** 用户词条：首次访问时从数据库读进来 */
    private fun users(): HashSet<String> {
        userWords?.let { return it }
        synchronized(lock) {
            userWords?.let { return it }

            val set = HashSet<String>(256)
            val sql = "SELECT ${KnownWordDatabase.COL_WORD} FROM ${KnownWordDatabase.TABLE}"
            requireDatabase().readableDatabase.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    set.add(cursor.getString(0).lowercase())
                }
            }
            userWords = set
            return set
        }
    }
}
