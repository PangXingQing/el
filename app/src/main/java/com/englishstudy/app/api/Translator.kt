package com.englishstudy.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 翻译结果
 */
data class TranslationResult(
    val original: String,          // 原文
    val translation: String,       // 翻译
    val phoneticUk: String = "",   // 英式音标（仅单个单词才有）
    val phoneticUs: String = "",   // 美式音标（仅单个单词才有）
    val from: String = "en",       // 源语言
    val to: String = "zh"          // 目标语言
) {
    /** 是否拿到了音标 */
    val hasPhonetic: Boolean get() = phoneticUk.isNotBlank() || phoneticUs.isNotBlank()
}

/**
 * 翻译服务接口 - 使用国内 API
 */
interface Translator {
    /** 翻译单词/短语 */
    suspend fun translate(text: String): TranslationResult

    /** 是否支持该服务 */
    val name: String
}

/**
 * 在线翻译实现（使用免 API Key 的公开接口）
 *
 * - 释义：百度 /sug 接口（注意：它**不返回音标**）
 * - 音标：有道 jsonapi（仅对单个单词查询，返回英美两套 IPA）
 * - 整句：MyMemory 机器翻译
 */
class BaiduTranslator : Translator {

    override val name: String = "英语学习翻译"

    /** 音标缓存：同一个单词重复查询时不再请求网络 */
    private val phoneticCache = ConcurrentHashMap<String, Pair<String, String>>()

    override suspend fun translate(text: String): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext TranslationResult(trimmed, "")
        }

        // 单词：音标与释义并行请求，避免多等一轮网络
        if (SINGLE_WORD.matches(trimmed)) {
            val (meaning, phonetics) = coroutineScope {
                val meaningJob = async {
                    runCatching { querySugApi(trimmed) }.getOrNull()
                }
                val phoneticJob = async { queryPhonetics(trimmed) }
                meaningJob.await() to phoneticJob.await()
            }
            val (uk, us) = phonetics

            if (meaning != null) {
                return@withContext meaning.copy(phoneticUk = uk, phoneticUs = us)
            }

            // 百度查不到的生僻词，退回机器翻译，但音标照样带上
            val fallback = runCatching { queryMyMemoryApi(trimmed) }.getOrNull()
            if (fallback != null) {
                return@withContext fallback.copy(phoneticUk = uk, phoneticUs = us)
            }
            return@withContext TranslationResult(
                original = trimmed,
                translation = "翻译失败，请检查网络后重试",
                phoneticUk = uk,
                phoneticUs = us
            )
        }

        // 短语 / 整句：不带音标
        val sugResult = runCatching { querySugApi(trimmed) }.getOrNull()
        if (sugResult != null) {
            return@withContext sugResult
        }

        // 百度 sug 对整句返回的是空数据（errno=0 但 data 为空），
        // 百度 transapi 现在必须有 token/sign、有道 sug 已下线，
        // 所以改走可用的免密钥机器翻译接口。
        val sentenceResult = runCatching { queryMyMemoryApi(trimmed) }.getOrNull()
        if (sentenceResult != null) {
            return@withContext sentenceResult
        }

        TranslationResult(trimmed, "翻译失败，请检查网络后重试")
    }

    /**
     * 百度翻译 /sug 接口 - 适合单词和短句
     * POST https://fanyi.baidu.com/sug
     */
    private fun querySugApi(text: String): TranslationResult? {
        val url = URL("https://fanyi.baidu.com/sug")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://fanyi.baidu.com/")
        }

        val postData = "kw=${URLEncoder.encode(text, "UTF-8")}"
        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(postData) }

        val responseCode = conn.responseCode
        if (responseCode != 200) return null

        val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            .readText()

        conn.disconnect()

        return parseSugResponse(response, text)
    }

    private fun parseSugResponse(json: String, original: String): TranslationResult? {
        try {
            val root = org.json.JSONObject(json)
            val errno = root.optInt("errno", -1)
            if (errno != 0) return null

            val data: JSONArray = root.optJSONArray("data") ?: return null
            if (data.length() == 0) return null

            // 找到最匹配的条目
            val bestMatch = (0 until data.length())
                .map { data.getJSONObject(it) }
                .firstOrNull {
                    it.optString("k", "").lowercase() == original.lowercase()
                } ?: data.getJSONObject(0)

            val value = bestMatch.optString("v", "")

            // 注意：百度 /sug 的释义文本里并没有音标，
            // 音标由 queryPhonetic() 从有道词典单独获取。
            val cleanTranslation = value
                .replace(Regex("""【[^】]*】"""), "")
                .trim()

            return TranslationResult(
                original = original,
                translation = cleanTranslation.ifBlank { value }
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 句子翻译 - MyMemory 机器翻译接口（免 API Key）
     *
     * GET https://api.mymemory.translated.net/get?q=xxx&langpair=en%7Czh-CN
     * 返回：{"responseData":{"translatedText":"..."},"responseStatus":200}
     *
     * 说明：匿名调用有每日额度限制（约 5000 词/天）。
     * 如需稳定用于生产，建议换成带密钥的翻译服务（如百度翻译开放平台/有道智云）。
     */
    private fun queryMyMemoryApi(text: String): TranslationResult? {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=en%7Czh-CN")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            conn.disconnect()
            return null
        }

        val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            .readText()
        conn.disconnect()

        return try {
            val root = org.json.JSONObject(response)
            val translated = root.optJSONObject("responseData")
                ?.optString("translatedText", "")
                .orEmpty()
                .trim()

            // 额度用尽时接口会把提示语直接塞进 translatedText
            if (translated.isBlank() || translated.contains("MYMEMORY WARNING", ignoreCase = true)) {
                null
            } else {
                TranslationResult(original = text, translation = translated)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== 音标（只对单个单词查询） =====

    /**
     * 取单词音标，返回 (英式, 美式)。
     *
     * 有道词典 jsonapi 返回英美两套 IPA：
     * {"ec":{"word":[{"usphone":"ˌɑːpərˈtuːnəti","ukphone":"ˌɒpəˈtjuːnəti"}]}}
     * 该接口从真机实测可用（约 0.3s）；免费词典 api.dictionaryapi.dev 在国内网络
     * 连不通，所以不采用。
     */
    private fun queryPhonetics(word: String): Pair<String, String> {
        val key = word.lowercase()
        phoneticCache[key]?.let { return it }

        val result = runCatching { fetchYoudaoPhonetics(word) }.getOrDefault("" to "")
        if (result.first.isNotBlank() || result.second.isNotBlank()) {
            phoneticCache[key] = result
        }
        return result
    }

    private fun fetchYoudaoPhonetics(word: String): Pair<String, String> {
        val url = URL("https://dict.youdao.com/jsonapi?q=${URLEncoder.encode(word, "UTF-8")}")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        if (conn.responseCode != 200) {
            conn.disconnect()
            return "" to ""
        }

        val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
        conn.disconnect()

        val words = org.json.JSONObject(response)
            .optJSONObject("ec")
            ?.optJSONArray("word")
            ?: return "" to ""
        if (words.length() == 0) return "" to ""

        val entry = words.getJSONObject(0)
        return entry.optString("ukphone", "").trim() to
            entry.optString("usphone", "").trim()
    }

    private companion object {
        /**
         * 单个英文单词（可含连字符/撇号）。
         * 只有命中的文本才去查音标，短语和整句都跳过。
         */
        val SINGLE_WORD = Regex("""^[A-Za-z\u00C0-\u024F][A-Za-z\u00C0-\u024F'-]*$""")
    }
}
