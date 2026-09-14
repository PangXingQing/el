package com.englishstudy.app

import android.app.Application
import com.englishstudy.app.api.AppTtsManager
import com.englishstudy.app.util.SampleFileExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EnglishStudyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化 TTS 语音引擎
        AppTtsManager.init(this)

        // 在后台解压示例文件到 Documents 目录
        CoroutineScope(Dispatchers.IO).launch {
            SampleFileExtractor.extractSamples(this@EnglishStudyApp)
        }
    }
}