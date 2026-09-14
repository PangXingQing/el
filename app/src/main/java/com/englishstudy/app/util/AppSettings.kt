package com.englishstudy.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局设置（持久化到 SharedPreferences，重启后仍生效）
 */
object AppSettings {

    private const val PREFS_NAME = "english_study_settings"
    private const val KEY_IPA_SOUND = "ipa_sound_on_tap"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 点击音素标签时是否播放发音。
     * 关闭后，点音素既不放音素录音、也不念例词（例词旁边的 ▶ 按钮不受影响）。
     */
    var isIpaSoundEnabled: Boolean
        get() = prefs?.getBoolean(KEY_IPA_SOUND, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_IPA_SOUND, value)?.apply()
        }
}
