package com.englishstudy.app.reader

import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.englishstudy.app.R
import com.englishstudy.app.databinding.ActivityReaderBinding
import com.englishstudy.app.util.AppSettings
import com.englishstudy.app.util.SampleFileExtractor
import java.io.File
import java.util.Locale

/**
 * 阅读器 Activity
 *
 * 界面分左右两栏，中间竖条可拖动调节宽度：
 * - 左侧：字幕阅读器（单击选词 / 长按选句）
 * - 右上：查询输入 + 翻译信息（原浮动窗内容）
 * - 右下：带地址栏的网页浏览器（etymology 词典）
 *
 * 选中文本后先弹出「查询 / 翻译」菜单：
 * - 翻译 → 弹出翻译浮窗（与原来一致）
 * - 查询 → 右侧显示翻译信息，并在下方打开 etymology 查询页
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var translationPopup: TranslationPopup
    private lateinit var selectionMenu: SelectionMenuPopup
    private lateinit var lookupPanel: LookupPanel
    private lateinit var browser: EtymologyBrowser

    private var currentEntries: List<SubtitleEntry> = emptyList()
    private var currentFileName: String = ""

    /** 阅读区当前字号（sp），由底部 A- / A+ 调节 */
    private var readerTextSizeSp: Float = DEFAULT_READER_TEXT_SIZE_SP

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

        applySystemBarInsets(binding.root)

        // 支持工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "阅读器"
        }

        // 浮动翻译弹窗（点击「翻译」时使用）
        translationPopup = TranslationPopup(this, lifecycleScope)

        // 选中文本后的「查询 / 翻译」菜单
        selectionMenu = SelectionMenuPopup(
            context = this,
            onLookup = { text, _ -> lookup(text) },
            onTranslate = { text, bounds -> translationPopup.show(binding.readerView, bounds, text) }
        )

        // 右侧上方：查询 + 翻译信息面板
        lookupPanel = LookupPanel(findViewById(R.id.panel_lookup), lifecycleScope).apply {
            onSubmit = { text -> lookup(text) }
        }

        // 右侧下方：网页浏览器
        browser = EtymologyBrowser(this, findViewById(R.id.panel_browser))

        // 阅读器回调 - 使用 readerView 作为锚点
        binding.readerView.onWordSelected = { word, bounds ->
            showSelectionMenu(word, bounds)
        }

        binding.readerView.onRangeSelected = { text, bounds ->
            showSelectionMenu(text, bounds)
        }

        binding.readerView.onSelectionCleared = {
            dismissFloatingPopups()
        }

        // 打开文件按钮
        binding.fabOpenFile.setOnClickListener {
            showFileSourceMenu()
        }

        // 阅读字号缩放
        binding.readerView.setTextSizeSp(readerTextSizeSp)
        updateReaderZoomLabel()
        binding.btnReaderZoomOut.setOnClickListener { changeReaderTextSize(-TEXT_SIZE_STEP_SP) }
        binding.btnReaderZoomIn.setOnClickListener { changeReaderTextSize(TEXT_SIZE_STEP_SP) }

        // 左右分栏拖动
        setupSplitter()
    }

    /** 调节阅读区字号 */
    private fun changeReaderTextSize(delta: Float) {
        val newSize = (readerTextSizeSp + delta)
            .coerceIn(MIN_READER_TEXT_SIZE_SP, MAX_READER_TEXT_SIZE_SP)
        if (newSize == readerTextSizeSp) return

        readerTextSizeSp = newSize
        binding.readerView.setTextSizeSp(readerTextSizeSp)
        updateReaderZoomLabel()
    }

    private fun updateReaderZoomLabel() {
        binding.tvReaderZoom.text = String.format(Locale.US, "%.0f", readerTextSizeSp)
    }

    /** 选中文本后先弹「查询 / 翻译」菜单 */
    private fun showSelectionMenu(text: String, bounds: Rect) {
        translationPopup.dismiss()
        selectionMenu.show(binding.readerView, bounds, text)
    }

    private fun dismissFloatingPopups() {
        selectionMenu.dismiss()
        translationPopup.dismiss()
    }

    /**
     * 查询：右侧上方展示翻译信息，右侧下方打开 etymology 对应的查询页。
     */
    private fun lookup(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return

        dismissFloatingPopups()
        lookupPanel.show(query)
        browser.searchEtymology(query)
    }

    // ===== 左右分栏 =====

    private fun setupSplitter() {
        val divider = binding.splitDivider
        var startX = 0f
        var startWidth = 0

        divider.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startWidth = binding.leftPane.width
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateLeftPaneWidth(startWidth + (event.rawX - startX).toInt())
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }

        // 首次布局后给左侧一个默认宽度，之后只由拖动决定
        divider.post {
            val containerWidth = binding.splitContainer.width
            if (containerWidth > 0) {
                updateLeftPaneWidth((containerWidth * DEFAULT_LEFT_RATIO).toInt())
            }
        }

        // 容器尺寸变化（旋转/分屏）后重新夹取，避免某一栏被挤没
        binding.splitContainer.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if ((right - left) != (oldRight - oldLeft)) {
                updateLeftPaneWidth(binding.leftPane.width)
            }
        }
    }

    /** 设置左侧面板宽度，并保证左右两栏都不小于最小宽度 */
    private fun updateLeftPaneWidth(width: Int) {
        val containerWidth = binding.splitContainer.width
        if (containerWidth <= 0) return

        val minWidth = (MIN_PANE_WIDTH_DP * resources.displayMetrics.density).toInt()
        val maxWidth = containerWidth - binding.splitDivider.width - minWidth
        if (maxWidth < minWidth) return

        val newWidth = width.coerceIn(minWidth, maxWidth)
        val lp = binding.leftPane.layoutParams as LinearLayout.LayoutParams
        if (lp.width == newWidth && lp.weight == 0f) return

        lp.width = newWidth
        // 必须清掉 weight：否则固定宽度之外还会再按权重分一份剩余空间，
        // 导致左栏比预期的宽得多（右栏被挤扁）。
        lp.weight = 0f
        binding.leftPane.layoutParams = lp
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // 每次打开菜单都同步一次全局开关的勾选状态
        menu.findItem(R.id.action_ipa_sound)?.isChecked = AppSettings.isIpaSoundEnabled
        return super.onPrepareOptionsMenu(menu)
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
            R.id.action_ipa_sound -> {
                val enabled = !item.isChecked
                item.isChecked = enabled
                AppSettings.isIpaSoundEnabled = enabled
                Toast.makeText(
                    this,
                    if (enabled) "点击音标发声：已开启" else "点击音标发声：已关闭",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        dismissFloatingPopups()
        browser.destroy()
        super.onDestroy()
    }

    /**
     * 把状态栏/导航栏/刘海区域的高度作为根布局的内边距，
     * 避免系统栏（尤其是横屏时底部的任务栏/导航条）压住 App 界面。
     *
     * targetSdk 35 起 Android 15 会强制边到边显示，必须自行处理；
     * 在更低版本上系统已经把内容区域让开，这里拿到的 insets 为 0，不会重复留白。
     */
    private fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // 软键盘弹出时把整体顶上去，否则会挡住查询框/地址栏
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                maxOf(bars.bottom, ime.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
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
        binding.layoutEmptyHint.visibility = View.GONE
        binding.readerView.visibility = View.VISIBLE

        // 每段展示完整信息：序号 + 时间码 / 原文 / 译文
        val blocks = currentEntries.map { entry ->
            ReaderBlock(
                meta = if (entry.hasTimeCode) "#${entry.index}  ${entry.timeCodeRange()}" else "",
                text = entry.text,
                translation = entry.translation
            )
        }
        binding.readerView.setBlocks(blocks)

        // 换文件后回到顶部
        binding.scrollReader.scrollTo(0, 0)

        // 更新标题
        supportActionBar?.title = fileName

        // 统计信息
        val wordCount = currentEntries
            .joinToString(" ") { it.text }
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }
        val hasTranslation = currentEntries.any { it.translation.isNotBlank() }
        binding.tvFileInfo.text = buildString {
            append("${currentEntries.size} 段 · $wordCount 词")
            if (hasTranslation) append(" · 双语")
        }
        binding.tvFileInfo.visibility = View.VISIBLE

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

    private companion object {
        /** 左栏默认占分栏总宽度的比例 */
        const val DEFAULT_LEFT_RATIO = 0.6f

        /** 左右两栏各自的最小宽度（dp） */
        const val MIN_PANE_WIDTH_DP = 220

        /** 阅读区字号默认值 / 步长 / 上下限（sp） */
        const val DEFAULT_READER_TEXT_SIZE_SP = 22f
        const val TEXT_SIZE_STEP_SP = 2f
        const val MIN_READER_TEXT_SIZE_SP = 12f
        const val MAX_READER_TEXT_SIZE_SP = 44f
    }
}