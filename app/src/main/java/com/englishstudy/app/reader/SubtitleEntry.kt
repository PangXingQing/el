package com.englishstudy.app.reader

import java.util.Locale

/**
 * 字幕条目数据模型
 *
 * @param text 原文（英文为主），多行时用 \n 分隔
 * @param translation 译文（双语字幕的第二语言），单语字幕时为空
 * @param startTime 开始时间（毫秒），SRT 格式使用
 * @param endTime 结束时间（毫秒），SRT 格式使用
 * @param index 序号
 */
data class SubtitleEntry(
    val text: String,
    val translation: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val index: Int = 0
) {
    /** 是否为有效条目 */
    val isValid: Boolean get() = text.isNotBlank() || translation.isNotBlank()

    /** 是否带有时间码 */
    val hasTimeCode: Boolean get() = endTime > startTime

    /** 格式化为 SRT 时间码，例如 00:00:01,200 → 00:00:03,070 */
    fun timeCodeRange(): String =
        "${formatTimeCode(startTime)} → ${formatTimeCode(endTime)}"
}

/** 把毫秒格式化成 SRT 时间码，例如 00:00:01,200 */
fun formatTimeCode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val hours = safe / 3_600_000
    val minutes = safe % 3_600_000 / 60_000
    val seconds = safe % 60_000 / 1_000
    val millis = safe % 1_000
    return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
}
