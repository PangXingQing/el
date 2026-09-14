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

/**
 * TXT 文本解析器
 * - 按空行分段，每段作为一个条目
 * - 支持换行合并
 */
class TxtParser : SubtitleParser {
    override fun parse(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val reader = BufferedReader(StringReader(content))
        val currentParagraph = StringBuilder()
        var index = 0

        reader.forEachLine { line ->
            if (line.isBlank()) {
                // 空行表示段落结束
                val text = currentParagraph.toString().trim()
                if (text.isNotBlank()) {
                    entries.add(SubtitleEntry(text = text, index = index++))
                }
                currentParagraph.clear()
            } else {
                if (currentParagraph.isNotEmpty()) currentParagraph.append(" ")
                currentParagraph.append(line.trim())
            }
        }

        // 处理最后一段
        val lastText = currentParagraph.toString().trim()
        if (lastText.isNotBlank()) {
            entries.add(SubtitleEntry(text = lastText, index = index))
        }

        // 如果没有任何分段，则将所有非空行作为一个段落
        if (entries.isEmpty()) {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                entries.add(SubtitleEntry(text = lines.joinToString(" "), index = 0))
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
 */
class SrtParser : SubtitleParser {

    private val timePattern = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

    override fun parse(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val lines = content.lines()
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

                            // 文本行（可能有多行）
                            val textLines = mutableListOf<String>()
                            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                                textLines.add(lines[i].trim())
                                i++
                            }

                            val text = textLines.joinToString(" ")
                            if (text.isNotBlank()) {
                                entries.add(SubtitleEntry(
                                    text = text,
                                    startTime = startTime,
                                    endTime = endTime,
                                    index = index
                                ))
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
 * 字幕解析器工厂
 */
object SubtitleParserFactory {
    private val parsers = listOf(TxtParser(), SrtParser())

    fun getParser(fileName: String): SubtitleParser? =
        parsers.firstOrNull { it.supports(fileName) }

    fun supportedExtensions(): List<String> = listOf("txt", "srt")
}