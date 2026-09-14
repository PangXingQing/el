package com.englishstudy.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 翻译结果
 */
data class TranslationResult(
    val original: String,       // 原文
    val translation: String,    // 翻译
    val phonetic: String = "",  // 音标
    val from: String = "en",    // 源语言
    val to: String = "zh"       // 目标语言
)

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
 * 百度翻译实现（使用公开 API）
 *
 * 使用百度翻译的 /sug 接口进行单词查询
 * 使用 /transapi 进行句子翻译
 * 无需 API Key，使用公开接口
 */
class BaiduTranslator : Translator {

    override val name: String = "百度翻译"

    override suspend fun translate(text: String): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext TranslationResult(trimmed, "")
        }

        try {
            // 尝试使用 sug 接口（适合单词/短语）
            val sugResult = querySugApi(trimmed)
            if (sugResult != null) {
                return@withContext sugResult
            }

            // 如果是长句，使用完整翻译接口
            queryTranslateApi(trimmed)
        } catch (e: Exception) {
            TranslationResult(trimmed, "翻译失败: ${e.message ?: "网络错误"}")
        }
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

            val key = bestMatch.optString("k", "")
            val value = bestMatch.optString("v", "")

            // 尝试提取音标（百度结果中可能包含音标）
            val phonetic = extractPhonetic(value)

            // 清理翻译文本（去掉音标等元信息）
            val cleanTranslation = value
                .replace(Regex("""[医]|美|英"""), "")
                .replace(Regex("""/[^/]+/"""), "")
                .trim()

            return TranslationResult(
                original = original,
                translation = cleanTranslation.ifBlank { value },
                phonetic = phonetic
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 百度翻译完整接口 - 适合句子
     * POST https://fanyi.baidu.com/transapi
     */
    private fun queryTranslateApi(text: String): TranslationResult {
        val url = URL("https://fanyi.baidu.com/transapi")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            setRequestProperty("Referer", "https://fanyi.baidu.com/")
        }

        val postData = "from=en&to=zh&query=${URLEncoder.encode(text, "UTF-8")}&source=txt&transtype=translang"
        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(postData) }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            conn.disconnect()
            return TranslationResult(text, "服务暂时不可用")
        }

        val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            .readText()
        conn.disconnect()

        return parseTransApiResponse(response, text)
    }

    private fun parseTransApiResponse(json: String, original: String): TranslationResult {
        try {
            val root = org.json.JSONObject(json)
            val transResult = root.optJSONArray("data")
            if (transResult != null && transResult.length() > 0) {
                val dst = transResult.getJSONObject(0).optString("dst", "")
                if (dst.isNotBlank()) {
                    return TranslationResult(original, dst)
                }
            }
        } catch (_: Exception) { }

        // 如果 transapi 失败，尝试另一个公开接口
        return queryYoudaoSugApi(original)
    }

    /**
     * 备用：有道翻译 sug 接口
     * POST https://dict.youdao.com/sug
     */
    private fun queryYoudaoSugApi(text: String): TranslationResult {
        try {
            val url = URL("https://dict.youdao.com/sug")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Referer", "https://dict.youdao.com/")
            }

            val postData = "q=${URLEncoder.encode(text, "UTF-8")}"
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(postData) }

            val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                .readText()
            conn.disconnect()

            val root = org.json.JSONObject(response)
            val data = root.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val entry = data.getJSONObject(0)
                val explains = entry.optString("explain", "")
                if (explains.isNotBlank()) {
                    return TranslationResult(text, explains)
                }
            }
        } catch (_: Exception) { }

        return TranslationResult(text, "暂无翻译结果")
    }

    /** 从翻译结果中提取音标 */
    private fun extractPhonetic(text: String): String {
        val match = Regex("""/[^/]+/""").find(text)
        return match?.value ?: ""
    }
}