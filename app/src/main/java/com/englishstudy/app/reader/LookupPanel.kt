package com.englishstudy.app.reader

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.englishstudy.app.R
import com.englishstudy.app.api.AppTtsManager
import com.englishstudy.app.api.BaiduTranslator
import com.englishstudy.app.api.Translator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 右侧上方的「查询 + 翻译信息」面板。
 *
 * 最上方是查询输入框；下面展示的内容与翻译浮窗完全一致
 * （原文 / 音标 / 译文 / 发音），方便和右边下侧的网页对照着看。
 */
class LookupPanel(
    private val root: View,
    private val coroutineScope: CoroutineScope
) {

    private val etQuery = root.findViewById<EditText>(R.id.et_lookup_query)
    private val tvOriginal = root.findViewById<TextView>(R.id.tv_lookup_original)
    private val tvTranslation = root.findViewById<TextView>(R.id.tv_lookup_translation)
    private val ipaCard = IpaCard(root.findViewById(R.id.ipa_card))
    private val btnSpeak = root.findViewById<AppCompatImageView>(R.id.btn_lookup_speak)
    private val loadingView = root.findViewById<View>(R.id.layout_lookup_loading)

    private val translator: Translator = BaiduTranslator()
    private val tts: AppTtsManager = AppTtsManager.getInstance()

    private var currentJob: Job? = null
    private var currentText: String = ""

    private val placeholderText: String = tvTranslation.text.toString()

    /** 用户在面板里主动点「查询」时回调，由 Activity 决定后续动作（显示信息 + 打开网页） */
    var onSubmit: ((String) -> Unit)? = null

    init {
        root.findViewById<View>(R.id.btn_lookup_query).setOnClickListener {
            submit(etQuery.text.toString())
        }

        // 清空：清掉输入框与下方的翻译信息
        root.findViewById<View>(R.id.btn_lookup_clear).setOnClickListener {
            hideKeyboard()
            reset()
        }

        etQuery.setOnEditorActionListener { _, actionId, event ->
            val submitted = actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN)
            if (submitted) {
                submit(etQuery.text.toString())
                true
            } else {
                false
            }
        }

        btnSpeak.setOnClickListener {
            if (currentText.isNotBlank()) tts.speak(currentText)
        }
    }

    private fun submit(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        hideKeyboard()
        onSubmit?.invoke(query)
    }

    /** 收起软键盘，避免挡住下方的翻译信息与网页 */
    private fun hideKeyboard() {
        etQuery.clearFocus()
        val imm = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(root.windowToken, 0)
    }

    /** 展示要查询的内容：填入查询框并异步翻译 */
    fun show(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return

        currentText = query
        if (etQuery.text.toString() != query) {
            etQuery.setText(query)
            etQuery.setSelection(query.length)
        }

        tvOriginal.text = query
        tvTranslation.text = ""
        tvTranslation.setTextColor(TRANSLATION_COLOR)
        ipaCard.hide()
        btnSpeak.visibility = View.VISIBLE
        loadingView.visibility = View.VISIBLE

        currentJob?.cancel()
        currentJob = coroutineScope.launch {
            val result = translator.translate(query)
            withContext(Dispatchers.Main) {
                loadingView.visibility = View.GONE
                tvTranslation.text = result.translation
                ipaCard.show(result.phoneticUk, result.phoneticUs)
            }
        }
    }

    /** 恢复成初始提示状态 */
    fun reset() {
        currentJob?.cancel()
        currentText = ""
        etQuery.setText("")
        tvOriginal.text = ""
        ipaCard.hide()
        tvTranslation.setTextColor(PLACEHOLDER_COLOR)
        tvTranslation.text = placeholderText
        btnSpeak.visibility = View.INVISIBLE
        loadingView.visibility = View.GONE
    }

    private companion object {
        val TRANSLATION_COLOR: Int = android.graphics.Color.parseColor("#555555")
        val PLACEHOLDER_COLOR: Int = android.graphics.Color.parseColor("#999999")
    }
}
