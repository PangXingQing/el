package com.englishstudy.app.reader

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.englishstudy.app.R
import com.englishstudy.app.api.AppTtsManager
import com.englishstudy.app.ui.FlowLayout
import com.englishstudy.app.util.AppSettings

/**
 * IPA 音标学习卡。
 *
 * 针对"没学过音标"的场景设计：
 * 1. 英式/美式音标分行显示，点击可切换，音素标签跟着切换；
 * 2. 音标按音素拆开，元音/辅音/重音用不同颜色区分；
 * 3. 点任意音素，下方给出中文发音要领和例词，例词可朗读。
 *
 * 翻译浮窗和右侧信息面板共用同一套布局与逻辑。
 */
class IpaCard(private val root: View) {

    private val rowUk = root.findViewById<View>(R.id.row_ipa_uk)
    private val rowUs = root.findViewById<View>(R.id.row_ipa_us)
    private val tvUk = root.findViewById<TextView>(R.id.tv_ipa_uk)
    private val tvUs = root.findViewById<TextView>(R.id.tv_ipa_us)
    private val flow = root.findViewById<FlowLayout>(R.id.flow_phonemes)
    private val detailLayout = root.findViewById<View>(R.id.layout_phoneme_detail)
    private val placeholder = root.findViewById<TextView>(R.id.tv_phoneme_placeholder)
    private val contentGroup = root.findViewById<View>(R.id.layout_phoneme_content)
    private val tvSymbol = root.findViewById<TextView>(R.id.tv_phoneme_symbol)
    private val tvCategory = root.findViewById<TextView>(R.id.tv_phoneme_category)
    private val tvTip = root.findViewById<TextView>(R.id.tv_phoneme_tip)
    private val tvExample = root.findViewById<TextView>(R.id.tv_phoneme_example)
    private val btnSpeakExample = root.findViewById<AppCompatImageView>(R.id.btn_phoneme_speak)

    /** 卡片内容高度变化时回调：浮窗需要据此重新调整大小和位置 */
    var onContentChanged: (() -> Unit)? = null

    private val tts = AppTtsManager.getInstance()
    private val ipaAudio = IpaAudio(root.context)

    private var ukIpa = ""
    private var usIpa = ""
    private var useUk = true

    private var tokens: List<IpaToken> = emptyList()
    private var chipViews: List<TextView> = emptyList()
    private var selectedIndex = -1

    private val density = root.resources.displayMetrics.density

    init {
        // 还没查到音标前整张卡片不占位（否则会露出一行空白的"英美"标签）
        root.visibility = View.GONE

        rowUk.setOnClickListener {
            if (ukIpa.isNotBlank()) selectAccent(true)
        }
        rowUs.setOnClickListener {
            if (usIpa.isNotBlank()) selectAccent(false)
        }
    }

    /**
     * 展示音标。两者都为空（例如选中的是整句）时整张卡片隐藏。
     */
    fun show(uk: String, us: String) {
        ukIpa = normalize(uk)
        usIpa = normalize(us)

        if (ukIpa.isBlank() && usIpa.isBlank()) {
            root.visibility = View.GONE
            return
        }

        root.visibility = View.VISIBLE
        rowUk.visibility = if (ukIpa.isNotBlank()) View.VISIBLE else View.GONE
        rowUs.visibility = if (usIpa.isNotBlank()) View.VISIBLE else View.GONE

        tvUk.text = "  /$ukIpa/"
        tvUs.text = "  /$usIpa/"

        selectAccent(ukIpa.isNotBlank())
    }

    fun hide() {
        root.visibility = View.GONE
    }

    private fun normalize(raw: String): String = raw.trim().trim('/').trim()

    private fun selectAccent(uk: Boolean) {
        useUk = uk
        val ipa = if (uk) ukIpa else usIpa

        // 两种音标都有时，未选中的那行变灰，让当前正在看的一目了然
        val both = ukIpa.isNotBlank() && usIpa.isNotBlank()
        tvUk.setTextColor(
            if (!both || uk) COLOR_UK else COLOR_DIM
        )
        tvUs.setTextColor(
            if (!both || !uk) COLOR_US else COLOR_DIM
        )
        tvUk.setTypeface(null, if (!both || uk) Typeface.BOLD else Typeface.NORMAL)
        tvUs.setTypeface(null, if (!both || !uk) Typeface.BOLD else Typeface.NORMAL)

        buildChips(ipa)
    }

    private fun buildChips(ipa: String) {
        flow.removeAllViews()
        selectedIndex = -1
        placeholder.visibility = View.VISIBLE
        contentGroup.visibility = View.GONE

        tokens = IpaKnowledge.tokenize(ipa)
        val views = mutableListOf<TextView>()
        val context = root.context

        tokens.forEachIndexed { index, token ->
            val chip = TextView(context).apply {
                text = token.text
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(9), dp(5), dp(9), dp(5))
                setOnClickListener { showPhoneme(index) }
            }
            styleChip(chip, token.info?.group, selected = false)
            flow.addView(chip)
            views.add(chip)
        }

        chipViews = views
    }

    private fun showPhoneme(index: Int) {
        val token = tokens.getOrNull(index) ?: return

        selectedIndex = index
        chipViews.forEachIndexed { i, chip ->
            styleChip(chip, tokens[i].info?.group, selected = i == index)
        }

        placeholder.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE
        tvSymbol.text = token.text

        val info = token.info
        if (info == null) {
            tvCategory.text = "词库里暂无这个符号"
            tvTip.text = "这个符号一般出现在机器生成的音标里，可以先跳过它。"
            tvExample.text = ""
            btnSpeakExample.visibility = View.GONE
        } else {
            tvCategory.text = info.category
            tvTip.text = info.tip

            if (info.exampleWord.isNotBlank()) {
                tvExample.text = "例词  ${info.exampleWord}  ${info.exampleIpa}"
                btnSpeakExample.visibility = View.VISIBLE
                btnSpeakExample.setOnClickListener { tts.speak(info.exampleWord) }
            } else {
                tvExample.text = ""
                btnSpeakExample.visibility = View.GONE
            }
        }

        playPhonemeSound(info)
        revealDetail()
    }

    /**
     * 播放音素发音。
     * 优先播放真实录音；双元音等没有独立录音的音素，回退成朗读例词。
     * 受"点击音标发声"全局开关控制（工具栏菜单里切换）。
     */
    private fun playPhonemeSound(info: IpaInfo?) {
        if (info == null) return
        if (!AppSettings.isIpaSoundEnabled) return

        if (ipaAudio.play(info.symbol)) return
        if (info.exampleWord.isNotBlank()) {
            tts.speak(info.exampleWord)
        }
    }

    /**
     * 详情可能落在滚动区之外（右侧面板是 ScrollView），主动滚到可见位置；
     * 同时通知宿主（浮窗）重新调整尺寸。
     */
    private fun revealDetail() {
        detailLayout.post {
            detailLayout.requestRectangleOnScreen(
                Rect(0, 0, detailLayout.width, detailLayout.height), true
            )
            onContentChanged?.invoke()
        }
    }

    private fun styleChip(chip: TextView, group: IpaGroup?, selected: Boolean) {
        val background: Int
        val foreground: Int

        if (selected) {
            background = COLOR_SELECTED
            foreground = Color.WHITE
        } else {
            when (group) {
                IpaGroup.VOWEL -> {
                    background = BG_VOWEL
                    foreground = FG_VOWEL
                }
                IpaGroup.CONSONANT -> {
                    background = BG_CONSONANT
                    foreground = FG_CONSONANT
                }
                IpaGroup.STRESS -> {
                    background = BG_STRESS
                    foreground = FG_STRESS
                }
                else -> {
                    background = BG_OTHER
                    foreground = FG_OTHER
                }
            }
        }

        chip.setTextColor(foreground)
        chip.background = chipBackground(background)
        chip.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun chipBackground(fillColor: Int): Drawable {
        val shape = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = 7f * density
        }
        return RippleDrawable(ColorStateList.valueOf(0x22000000), shape, null)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        val COLOR_UK = Color.parseColor("#1565C0")
        val COLOR_US = Color.parseColor("#C62828")
        val COLOR_DIM = Color.parseColor("#BDBDBD")
        val COLOR_SELECTED = Color.parseColor("#1976D2")

        val BG_VOWEL = Color.parseColor("#E3F2FD")
        val FG_VOWEL = Color.parseColor("#0D47A1")

        val BG_CONSONANT = Color.parseColor("#E8F5E9")
        val FG_CONSONANT = Color.parseColor("#1B5E20")

        val BG_STRESS = Color.parseColor("#FFF3E0")
        val FG_STRESS = Color.parseColor("#E65100")

        val BG_OTHER = Color.parseColor("#ECEFF1")
        val FG_OTHER = Color.parseColor("#546E7A")
    }
}
