package com.englishstudy.app.api

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * TTS 语音合成管理器
 *
 * 使用 Android 内置 TTS 引擎
 * 支持中英文发音
 */
class AppTtsManager private constructor(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false

    /** 初始化 TTS 引擎 */
    fun initialize(onReady: (() -> Unit)? = null) {
        if (isInitialized) {
            onReady?.invoke()
            return
        }

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true

                // 设置语言首选英文
                val enResult = tts?.setLanguage(Locale.US)
                val cnResult = tts?.setLanguage(Locale.CHINESE)

                // 设置语速稍慢（适合学习）
                tts?.setSpeechRate(0.85f)

                onReady?.invoke()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: AppTtsManager? = null

        private lateinit var applicationContext: Context

        fun init(context: Context) {
            applicationContext = context.applicationContext
        }

        fun getInstance(): AppTtsManager {
            return instance ?: synchronized(this) {
                instance ?: AppTtsManager(applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 朗读文本（英文用美式发音，中文用普通话）
     * @param text 要朗读的文本
     * @param onDone 朗读完成回调
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            initialize { speak(text, onDone) }
            return
        }

        if (isSpeaking) {
            tts?.stop()
        }

        // 检测语言
        val locale = detectLocale(text)
        tts?.setLanguage(locale)

        isSpeaking = true

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                onDone?.invoke()
            }
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                onDone?.invoke()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    /** 停止朗读 */
    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    /** 是否正在朗读 */
    fun isPlaying(): Boolean = isSpeaking

    /** 检测文本语言 */
    private fun detectLocale(text: String): Locale {
        val hasChinese = text.any { it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' }
        return if (hasChinese) Locale.CHINESE else Locale.US
    }

    /** 释放资源 */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}