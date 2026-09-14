package com.englishstudy.app.reader

/**
 * 字幕条目数据模型
 * @param text 文本内容
 * @param startTime 开始时间（毫秒），SRT格式使用
 * @param endTime 结束时间（毫秒），SRT格式使用
 * @param index 序号
 */
data class SubtitleEntry(
    val text: String,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val index: Int = 0
) {
    /** 是否为有效条目（非空文本） */
    val isValid: Boolean get() = text.isNotBlank()
}