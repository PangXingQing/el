package com.englishstudy.app.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * 音素发音播放器。
 *
 * 播放 assets/ipa/ 下的音素录音（来源与授权见 assets/ipa/SOURCES.txt）。
 * 之所以不用 TTS：TTS 读不出 IPA 符号（ɒ、ʃ、θ 这些会被忽略或读成乱码），
 * 单个音素的发音只能用真实录音。
 *
 * 没有对应录音的音素（双元音、/i/ /ɝ/ /ɚ/）[play] 返回 false，
 * 由调用方回退成朗读例词。
 */
class IpaAudio(private val context: Context) {

    private var player: MediaPlayer? = null

    /** 播放音素录音，返回是否成功播放 */
    fun play(symbol: String): Boolean {
        val asset = ASSET_BY_SYMBOL[symbol.trim()] ?: return false

        return try {
            release()
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            // .ogg 在 APK 中不压缩（见 app/build.gradle.kts 的 noCompress），
            // 所以可以直接拿 AssetFileDescriptor 播放，不用先拷到缓存目录。
            context.assets.openFd("ipa/$asset.ogg").use { fd ->
                mediaPlayer.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mediaPlayer.setOnCompletionListener { release() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                release()
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            true
        } catch (e: Exception) {
            // 播放失败就当作没有音频，交给调用方回退成朗读例词；
            // 保留一条日志，方便排查资源缺失或解码失败
            android.util.Log.w("IpaAudio", "play $symbol ($asset) failed", e)
            release()
            false
        }
    }

    fun release() {
        val mediaPlayer = player ?: return
        player = null
        try {
            mediaPlayer.stop()
        } catch (e: IllegalStateException) {
            // 还没 prepare 就停会抛这个，忽略
        }
        mediaPlayer.release()
    }

    private companion object {
        /** 音素符号 → assets/ipa 下的文件名（不含扩展名） */
        val ASSET_BY_SYMBOL: Map<String, String> = mapOf(
            // 辅音
            "p" to "p", "b" to "b", "t" to "t", "d" to "d", "k" to "k", "g" to "g",
            "f" to "f", "v" to "v", "θ" to "th", "ð" to "dh", "s" to "s", "z" to "z",
            "ʃ" to "sh", "ʒ" to "zh", "h" to "h", "tʃ" to "tch", "dʒ" to "dj",
            "m" to "m", "n" to "n", "ŋ" to "ng", "l" to "l", "r" to "r",
            "j" to "y", "w" to "w",
            // 单元音（/ɑ/ 和 /ɑː/ 共用一份录音）
            "iː" to "i_long", "ɪ" to "i_short", "e" to "e", "æ" to "ae",
            "ɑː" to "a_long", "ɑ" to "a_long", "ɒ" to "o_short", "ɔː" to "o_long",
            "ʊ" to "u_short", "uː" to "u_long", "ʌ" to "uh",
            "ɜː" to "er_long", "ə" to "schwa"
        )
    }
}
