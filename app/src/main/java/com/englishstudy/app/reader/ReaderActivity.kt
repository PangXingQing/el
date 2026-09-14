package com.englishstudy.app.reader

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.englishstudy.app.R
import com.englishstudy.app.databinding.ActivityReaderBinding
import com.englishstudy.app.util.SampleFileExtractor
import java.io.File

/**
 * 阅读器 Activity
 *
 * 功能：
 * - 打开 TXT/SRT 字幕文件
 * - 交互式文本选择（单击选词、长按选句）
 * - 弹出翻译+发音浮窗
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var popup: TranslationPopup

    private var currentEntries: List<SubtitleEntry> = emptyList()
    private var currentFileName: String = ""

    // 文件选择器
    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { loadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 支持工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "阅读器"
        }

        // 初始化悬浮翻译弹窗
        popup = TranslationPopup(this, lifecycleScope)

        // 阅读器回调 - 使用 readerView 作为锚点
        binding.readerView.onWordSelected = { word, bounds ->
            popup.show(binding.readerView, bounds, word)
        }

        binding.readerView.onRangeSelected = { text, bounds ->
            popup.show(binding.readerView, bounds, text)
        }

        binding.readerView.onSelectionCleared = {
            popup.dismiss()
        }

        // 点击空白处清除选择
        binding.readerView.setOnClickListener { }

        // 打开文件按钮
        binding.fabOpenFile.setOnClickListener {
            showFileSourceMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_open_file -> {
                showFileSourceMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        popup.dismiss()
        super.onDestroy()
    }

    /** 显示文件来源菜单 */
    private fun showFileSourceMenu() {
        val popupMenu = PopupMenu(this, binding.fabOpenFile, Gravity.END)
        popupMenu.menu.add(0, 1, 0, "从设备选择文件")
        popupMenu.menu.add(0, 2, 0, "打开示例文件")

        // 检查 Documents 中是否有示例文件
        val sampleDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: filesDir
        val sampleFiles = sampleDir.listFiles { f -> f.extension in listOf("txt", "srt") }
        if (!sampleFiles.isNullOrEmpty()) {
            popupMenu.menu.add(0, 3, 0, "打开示例文件 (Documents)")
        }

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> pickFile()
                2 -> openSampleFiles()
                3 -> openDocumentsSamples()
            }
            true
        }
        popupMenu.show()
    }

    /** 从系统文件选择器选择文件 */
    private fun pickFile() {
        openFileLauncher.launch(arrayOf(
            "text/plain",
            "application/x-subrip",
            "*/*"
        ))
    }

    /** 从 Assets 打开示例文件 */
    private fun openSampleFiles() {
        val samples = SampleFileExtractor.listAssetsSamples(this)
        if (samples.isEmpty()) {
            Toast.makeText(this, "没有找到示例文件", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示文件列表
        val names = samples.map { extractFileName(it) }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("选择示例文件")
            .setItems(names) { _, which ->
                val assetPath = samples[which]
                val content = SampleFileExtractor.readAssetContent(this, assetPath)
                if (content != null) {
                    loadContent(content, extractFileName(assetPath))
                }
            }
            .show()
    }

    /** 打开 Documents 目录中的示例文件 */
    private fun openDocumentsSamples() {
        val docsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: filesDir
        val files = docsDir.listFiles { f ->
            f.isFile && f.extension in listOf("txt", "srt")
        } ?: return

        if (files.isEmpty()) {
            Toast.makeText(this, "Documents 中没有示例文件", Toast.LENGTH_SHORT).show()
            return
        }

        val names = files.map { it.name }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("选择文件")
            .setItems(names) { _, which ->
                loadFileContent(files[which])
            }
            .show()
    }

    /** 从 URI 加载文件 */
    private fun loadFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()

            val fileName = getFileNameFromUri(uri) ?: "unknown.txt"
            loadContent(content, fileName)
        } catch (e: Exception) {
            Toast.makeText(this, "文件读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 加载文件内容 */
    private fun loadFileContent(file: File) {
        try {
            val content = file.readText()
            loadContent(content, file.name)
        } catch (e: Exception) {
            Toast.makeText(this, "文件读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 解析并显示内容 */
    private fun loadContent(content: String, fileName: String) {
        currentFileName = fileName
        val parser = SubtitleParserFactory.getParser(fileName)

        if (parser == null) {
            Toast.makeText(this, "不支持的文件格式: $fileName", Toast.LENGTH_SHORT).show()
            return
        }

        currentEntries = parser.parse(content)
        if (currentEntries.isEmpty()) {
            Toast.makeText(this, "文件内容为空", Toast.LENGTH_SHORT).show()
            return
        }

        // 隐藏空状态提示，显示阅读器内容
        binding.layoutEmptyHint.visibility = android.view.View.GONE
        binding.readerView.visibility = android.view.View.VISIBLE

        // 提取纯文本段落
        val paragraphs = currentEntries.map { it.text }
        binding.readerView.setParagraphs(paragraphs)

        // 更新标题
        supportActionBar?.title = fileName

        // 统计信息
        val wordCount = paragraphs.joinToString(" ").split(Regex("\\s+")).size
        binding.tvFileInfo.text = "${currentEntries.size} 段 · $wordCount 词"
        binding.tvFileInfo.visibility = android.view.View.VISIBLE

        Toast.makeText(this, "已加载: $fileName", Toast.LENGTH_SHORT).show()
    }

    /** 从 URI 提取文件名 */
    private fun getFileNameFromUri(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return it.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }

    /** 从路径中提取文件名 */
    private fun extractFileName(path: String): String {
        return path.split("/").lastOrNull() ?: path
    }
}