package com.englishstudy.app.reader

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.englishstudy.app.R
import com.englishstudy.app.api.AppTtsManager
import com.englishstudy.app.api.BaiduTranslator
import com.englishstudy.app.api.TranslationResult
import com.englishstudy.app.api.Translator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 翻译悬浮弹窗
 *
 * 类似 Chrome 翻译插件的弹出效果
 * - 选择单词/词组后立即显示
 * - 异步加载翻译结果，不阻塞 UI
 * - 支持点击发音朗读
 */
class TranslationPopup(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private var popupWindow: PopupWindow? = null
    private var currentJob: Job? = null

    // 视图缓存
    private var popupView: View? = null
    private var tvOriginal: TextView? = null
    private var tvTranslation: TextView? = null
    private var tvPhonetic: TextView? = null
    private var btnSpeak: AppCompatImageView? = null
    private var btnClose: AppCompatImageView? = null
    private var loadingView: View? = null

    private val translator: Translator = BaiduTranslator()
    private val tts: AppTtsManager = AppTtsManager.getInstance()
    private var currentText: String = ""

    companion object {
        private const val POPUP_MARGIN_DP = 8
        private const val POPUP_MAX_WIDTH_DP = 400
    }

    /**
     * 在指定锚点位置显示弹窗
     * @param anchorView 用于确定窗口的锚点 View（Activity 的 root 或任意 View）
     * @param anchorBounds 选中文本在屏幕上的边界
     * @param selectedText 选中的文本
     */
    fun show(anchorView: View, anchorBounds: Rect, selectedText: String) {
        dismiss()
        currentText = selectedText

        // 1. 立即创建并显示 Popup（显示加载中）
        showPopupImmediately(anchorView, anchorBounds)

        // 2. 异步发起翻译请求，不阻塞 UI
        currentJob = coroutineScope.launch {
            showLoading()
            val result = translator.translate(selectedText)
            withContext(Dispatchers.Main) {
                updateContent(result)
            }
        }
    }

    /** 立即显示弹窗（加载中状态），不等待网络请求 */
    private fun showPopupImmediately(anchorView: View, anchorBounds: Rect) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.popup_translation, null)

        // 缓存视图引用
        popupView = view
        tvOriginal = view.findViewById(R.id.tv_popup_original)
        tvTranslation = view.findViewById(R.id.tv_popup_translation)
        tvPhonetic = view.findViewById(R.id.tv_popup_phonetic)
        btnSpeak = view.findViewById(R.id.btn_popup_speak)
        btnClose = view.findViewById(R.id.btn_popup_close)
        loadingView = view.findViewById(R.id.layout_popup_loading)

        // 设置原文
        tvOriginal?.text = currentText

        // 发音按钮
        btnSpeak?.setOnClickListener {
            speakCurrentText()
        }

        // 关闭按钮
        btnClose?.setOnClickListener { dismiss() }

        // 创建 PopupWindow
        popupWindow = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            animationStyle = android.R.style.Animation_Dialog
            elevation = 16f
        }

        // 计算弹窗位置
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        // 测量弹窗宽高
        val maxWidth = (POPUP_MAX_WIDTH_DP * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupW = view.measuredWidth.coerceAtMost(maxWidth)
        val popupH = view.measuredHeight
        val margin = (POPUP_MARGIN_DP * density).toInt()

        // 计算位置
        var x = anchorBounds.centerX() - popupW / 2
        var y = anchorBounds.bottom + margin

        // 下方空间不够则显示在上方
        if (y + popupH > screenHeight) {
            y = anchorBounds.top - popupH - margin
        }
        // 上下都不够则居中
        if (y < margin) {
            y = screenHeight / 2 - popupH / 2
        }
        x = x.coerceIn(margin, screenWidth - popupW - margin)

        // 显示弹窗（使用锚点 View 来确定窗口位置）
        popupWindow?.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)

        // 淡入动画
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(200).start()
    }

    /** 显示加载状态 */
    private fun showLoading() {
        loadingView?.visibility = View.VISIBLE
        tvTranslation?.text = ""
        tvPhonetic?.text = ""
    }

    /** 更新翻译内容 */
    private fun updateContent(result: TranslationResult) {
        loadingView?.visibility = View.GONE
        tvTranslation?.text = result.translation
        tvPhonetic?.text = if (result.phonetic.isNotBlank()) {
            "音标 ${result.phonetic}"
        } else {
            ""
        }
    }

    /** 朗读当前文本 */
    private fun speakCurrentText() {
        if (currentText.isBlank()) return
        tts.speak(currentText)
    }

    /** 关闭弹窗 */
    fun dismiss() {
        currentJob?.cancel()
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
    }

    /** 弹窗是否正在显示 */
    val isShowing: Boolean get() = popupWindow?.isShowing == true
}