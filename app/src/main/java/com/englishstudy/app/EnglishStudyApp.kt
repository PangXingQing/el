package com.englishstudy.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.englishstudy.app.api.AppTtsManager
import com.englishstudy.app.util.AppSettings
import com.englishstudy.app.util.SampleFileExtractor
import com.englishstudy.app.words.WordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EnglishStudyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // App 的界面配色是固定的浅色（米白背景 + 深色文字），
        // 若跟随系统进入深色模式，Material 控件（弹出菜单、对话框）会变成
        // 深色主题，出现"浅色底 + 白色字"看不清的问题，这里统一固定为浅色。
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 全局设置（音标发声开关等）
        AppSettings.init(this)

        // 初始化 TTS 语音引擎
        AppTtsManager.init(this)

        // 词库（已知单词表）：init 只准备数据库对象，不读盘
        WordRepository.init(this)

        // 后台预热词库，避免首次分析生词时在界面上等建库
        // （首次运行会建库并导入 assets 里的默认词表，约 8000 词）
        CoroutineScope(Dispatchers.IO).launch {
            WordRepository.warmUp()
        }

        // 在后台解压示例文件到 Documents 目录
        CoroutineScope(Dispatchers.IO).launch {
            SampleFileExtractor.extractSamples(this@EnglishStudyApp)
        }
    }
}