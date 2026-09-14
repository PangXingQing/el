package com.englishstudy.app.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/**
 * 示例文件解压工具
 *
 * 将 APK 中 assets/samples 目录下的示例文件
 * 解压到 Documents 目录供用户使用
 */
object SampleFileExtractor {

    private const val ASSETS_SAMPLE_DIR = "samples"

    /** 示例文件列表（assets 中的路径） */
    private val SAMPLE_FILES = listOf(
        "$ASSETS_SAMPLE_DIR/hello_world.txt",
        "$ASSETS_SAMPLE_DIR/daily_conversation.en.srt",
        "$ASSETS_SAMPLE_DIR/daily_conversation.zh.srt",
        "$ASSETS_SAMPLE_DIR/interview.txt"
    )

    /**
     * 获取示例文件的目标目录（Documents）
     */
    fun getTargetDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(context.filesDir, "Documents")
        } else {
            File(context.filesDir, "Documents")
        }
    }

    /**
     * 提取示例文件到目标目录
     * 应在后台线程调用
     */
    fun extractSamples(context: Context): List<File> {
        val targetDir = getTargetDirectory(context)
        if (!targetDir.exists()) targetDir.mkdirs()

        val extractedFiles = mutableListOf<File>()

        for (assetPath in SAMPLE_FILES) {
            try {
                val fileName = assetPath.substringAfterLast('/')
                val targetFile = File(targetDir, fileName)

                // 如果文件已存在且不为空，跳过
                if (targetFile.exists() && targetFile.length() > 0) continue

                // 从 assets 读取并写入
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                extractedFiles.add(targetFile)
            } catch (e: Exception) {
                // 忽略单个文件失败
                e.printStackTrace()
            }
        }

        return extractedFiles
    }

    /**
     * 列出 assets 中的所有示例文件
     */
    fun listAssetsSamples(context: Context): List<String> {
        return try {
            context.assets.list(ASSETS_SAMPLE_DIR)
                ?.filter { it.endsWith(".txt") || it.endsWith(".srt") }
                ?.map { "$ASSETS_SAMPLE_DIR/$it" }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 读取 assets 中的示例文件内容
     */
    fun readAssetContent(context: Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }
}