package com.englishstudy.app.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.englishstudy.app.R
import kotlin.math.roundToInt

/**
 * 右侧下方的网页浏览器（自带地址栏与缩放按钮）。
 *
 * 主要用途是直接展示 etymology 词典的查询页：
 * https://www.etymonline.com/search?q=<查询内容>
 */
class EtymologyBrowser(
    private val context: Context,
    private val root: View
) {

    private val webView = root.findViewById<WebView>(R.id.web_view)
    private val urlBar = root.findViewById<EditText>(R.id.et_browser_url)
    private val progress = root.findViewById<ProgressBar>(R.id.browser_progress)
    private val zoomLabel = root.findViewById<TextView>(R.id.tv_browser_zoom)

    /**
     * WebView 的文字大小 ≈ 系统 fontScale × textZoom/100。
     * 这台平板的系统字体缩放是 1.5，会把网页文字整体放大 1.5 倍；
     * 而阅读器的字号是固定像素、不跟随 fontScale，两边就会明显不一致
     * （看起来就像"网页被放大过"）。所以默认用 1/fontScale 抵消掉。
     */
    private var zoomPercent: Int = (100f / systemFontScale()).roundToInt()
        .coerceIn(MIN_ZOOM, MAX_ZOOM)

    init {
        webView.settings.apply {
            javaScriptEnabled = true      // etymonline 是 Next.js 站点，必须有 JS
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            // 返回 false：所有跳转都留在面板内，不要丢给系统浏览器
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank()) urlBar.setText(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        root.findViewById<View>(R.id.btn_browser_go).setOnClickListener {
            load(urlBar.text.toString())
        }
        root.findViewById<View>(R.id.btn_browser_refresh).setOnClickListener {
            webView.reload()
        }
        root.findViewById<View>(R.id.btn_browser_back).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        root.findViewById<View>(R.id.btn_browser_zoom_out).setOnClickListener {
            changeZoom(-ZOOM_STEP)
        }
        root.findViewById<View>(R.id.btn_browser_zoom_in).setOnClickListener {
            changeZoom(ZOOM_STEP)
        }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            val submitted = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN)
            if (submitted) {
                load(urlBar.text.toString())
                true
            } else {
                false
            }
        }

        applyZoom()

        // 先打开词典首页，避免面板一开始是空白的
        load(HOME_URL)
    }

    // ===== 网页缩放 =====

    private fun systemFontScale(): Float =
        context.resources.configuration.fontScale.takeIf { it > 0f } ?: 1f

    private fun changeZoom(delta: Int) {
        val newZoom = (zoomPercent + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoomPercent) return
        zoomPercent = newZoom
        applyZoom()
    }

    private fun applyZoom() {
        webView.settings.textZoom = zoomPercent
        zoomLabel.text = "$zoomPercent%"
    }

    /** 打开 etymology 中该词的查询页 */
    fun searchEtymology(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        load(SEARCH_URL + Uri.encode(query))
    }

    /** 地址栏内容既可以是完整网址，也可以直接是单词 */
    fun load(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return

        val url = if (text.startsWith("http://") || text.startsWith("https://")) {
            text
        } else {
            SEARCH_URL + Uri.encode(text)
        }

        urlBar.setText(url)
        hideKeyboard()
        webView.loadUrl(url)
    }

    private fun hideKeyboard() {
        urlBar.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    companion object {
        private const val HOME_URL = "https://www.etymonline.com/"
        private const val SEARCH_URL = "https://www.etymonline.com/search?q="

        private const val ZOOM_STEP = 10
        private const val MIN_ZOOM = 50
        private const val MAX_ZOOM = 200
    }
}
