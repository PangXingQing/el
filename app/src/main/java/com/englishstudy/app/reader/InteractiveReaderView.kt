package com.englishstudy.app.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * 阅读器中的一段内容
 *
 * @param meta 附加信息（如字幕序号与时间码），以暗色小字显示在段首，可为空
 * @param text 原文（可含 \n 多行）
 * @param translation 译文（可含 \n 多行），以次要颜色显示，可为空
 */
data class ReaderBlock(
    val meta: String = "",
    val text: String = "",
    val translation: String = ""
)

/**
 * 可交互的阅读器视图
 *
 * 功能：
 * - 单击选择单词
 * - 长按选择整句，并支持按住拖动双向扩展/收缩选区
 * - 回调选中的文本及其在视图内的位置
 */
class InteractiveReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ===== 数据 =====
    private var blocks: List<ReaderBlock> = emptyList()
    private val displayText = SpannableStringBuilder()
    private var textLayout: Layout? = null

    // ===== 绘制样式 =====
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 48f  // 默认 36sp ≈ 48px
        typeface = Typeface.DEFAULT
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#402196F3")
    }
    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        isAntiAlias = true
    }

    // ===== 选择状态 =====
    private var selectionStart = -1
    private var selectionEnd = -1
    private var isSelecting = false

    /** 长按确定的锚点选区（整句），拖动时以它为基础双向扩展 */
    private var anchorStart = -1
    private var anchorEnd = -1

    // ===== 手势检测 =====
    private val gestureDetector: GestureDetector

    // ===== 回调 =====
    /** 单击选词：文本 + 该词在视图内的边界 */
    var onWordSelected: ((word: String, wordBounds: Rect) -> Unit)? = null

    /** 长按/拖动选区结束：文本 + 选区在视图内的边界 */
    var onRangeSelected: ((text: String, anchorBounds: Rect) -> Unit)? = null

    var onSelectionCleared: (() -> Unit)? = null

    init {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            // 必须返回 true，否则长按/单击都收不到后续事件
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.x, e.y)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                handleLongPressStart(e.x, e.y)
            }
        })
        gestureDetector.setIsLongpressEnabled(true)

        // 初始显示内容
        updateDisplayText()
    }

    /** 设置内容（每段可带时间码、原文与译文） */
    fun setBlocks(blocks: List<ReaderBlock>) {
        this.blocks = blocks
        clearSelection()
        updateDisplayText()
        requestLayout()
        invalidate()
    }

    /** 设置字体大小（sp） */
    fun setTextSizeSp(sizeSp: Float) {
        textPaint.textSize = sizeSp * context.resources.displayMetrics.density
        updateDisplayText()
        requestLayout()
        invalidate()
    }

    /** 获取当前字体大小（sp） */
    fun getTextSizeSp(): Float =
        textPaint.textSize / context.resources.displayMetrics.density

    // ===== 触摸事件 =====
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        if (isSelecting && action == MotionEvent.ACTION_MOVE) {
            handleSelectionDrag(event.x, event.y)
        }

        // 事件始终转发给 GestureDetector，保持其内部状态一致；
        // 并且**必须返回 true 消费 ACTION_DOWN**，否则父容器不会再派发
        // MOVE/UP，长按之后就永远收不到 ACTION_UP，选中结果无法回调出去。
        gestureDetector.onTouchEvent(event)

        when (action) {
            MotionEvent.ACTION_UP -> {
                if (isSelecting) finishSelection() else performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isSelecting) abortSelection()
            }
        }

        return true
    }

    // ===== 布局测量 =====
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        if (width > 0 && displayText.isNotEmpty()) {
            val paddingH = paddingLeft + paddingRight
            val usableWidth = (width - paddingH).coerceAtLeast(1)

            textLayout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, usableWidth)
                .setLineSpacing(12f, 1.0f)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(true)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()

            val textHeight = textLayout?.height ?: 0
            val desiredHeight = textHeight + paddingTop + paddingBottom

            // 外层是 ScrollView 时高度约束为 UNSPECIFIED，必须按内容真实高度上报，
            // 否则会被夹成 0 或者夹成一屏高，导致永远无法滚动。
            val finalHeight = when (heightMode) {
                MeasureSpec.UNSPECIFIED -> desiredHeight
                MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize)
                else -> heightSize
            }
            setMeasuredDimension(width, finalHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    // ===== 绘制 =====
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = textLayout ?: return

        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        // 绘制选中背景
        if (selectionStart >= 0 && selectionEnd > selectionStart) {
            val selStart = selectionStart
            val selEnd = selectionEnd

            val startLine = layout.getLineForOffset(selStart)
            val endLine = layout.getLineForOffset(selEnd)

            for (line in startLine..endLine) {
                val isFirstLine = line == startLine
                val isLastLine = line == endLine

                val left = if (isFirstLine) layout.getPrimaryHorizontal(selStart)
                           else layout.getLineLeft(line)
                val right = if (isLastLine) layout.getPrimaryHorizontal(selEnd)
                            else layout.getLineRight(line)
                val top = layout.getLineTop(line).toFloat()
                val bottom = layout.getLineBottom(line).toFloat()

                canvas.drawRect(left, top, right, bottom, selectionPaint)
            }

            // 绘制选择手柄（在选中文本末尾）
            val handleX = layout.getPrimaryHorizontal(selEnd)
            val handleY = layout.getLineBottom(endLine).toFloat()
            canvas.drawCircle(handleX, handleY + 12f, 10f, selectionHandlePaint)
        }

        // 绘制文本
        layout.draw(canvas)

        canvas.restore()
    }

    // ===== 交互处理 =====
    private fun handleTap(x: Float, y: Float) {
        clearSelection()
        val offset = getOffsetAtPosition(x, y) ?: return
        val wordRange = findWordAtOffset(offset) ?: return

        selectionStart = wordRange.first
        selectionEnd = wordRange.second
        isSelecting = false
        invalidate()

        val word = displayText.substring(selectionStart, selectionEnd)
        val bounds = getSelectionBounds(selectionStart, selectionEnd)
        onWordSelected?.invoke(word, bounds)
    }

    private fun handleLongPressStart(x: Float, y: Float) {
        val offset = getOffsetAtPosition(x, y) ?: return
        val sentence = findSentenceAtOffset(offset)

        anchorStart = sentence.first
        anchorEnd = sentence.second
        selectionStart = anchorStart
        selectionEnd = anchorEnd
        isSelecting = true

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // 长按之后由本 View 独占手势，避免被父容器（如 ScrollView）抢走
        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
    }

    private fun handleSelectionDrag(x: Float, y: Float) {
        val offset = getOffsetAtPosition(x, y) ?: return
        if (anchorStart < 0 || anchorEnd < 0) return

        val text = displayText.toString()
        val wordStart = findWordStart(text, offset)
        val wordEnd = findWordEnd(text, offset)

        // 以长按选中的整句为锚点，向前/向后都能扩展
        selectionStart = minOf(anchorStart, wordStart)
        selectionEnd = maxOf(anchorEnd, wordEnd)
        invalidate()
    }

    private fun finishSelection() {
        isSelecting = false
        parent?.requestDisallowInterceptTouchEvent(false)

        if (selectionStart in 0 until selectionEnd) {
            val selText = displayText.substring(selectionStart, selectionEnd)
            val bounds = getSelectionBounds(selectionStart, selectionEnd)
            onRangeSelected?.invoke(selText, bounds)
        } else {
            clearSelection()
        }
    }

    private fun abortSelection() {
        isSelecting = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    fun clearSelection() {
        if (selectionStart >= 0 || selectionEnd >= 0) {
            selectionStart = -1
            selectionEnd = -1
            anchorStart = -1
            anchorEnd = -1
            isSelecting = false
            invalidate()
            onSelectionCleared?.invoke()
        }
    }

    // ===== 文本工具 =====
    private fun updateDisplayText() {
        displayText.clear()
        displayText.clearSpans()

        if (blocks.isEmpty()) {
            displayText.append("请打开一个文本文件开始阅读")
            return
        }

        blocks.forEachIndexed { i, block ->
            if (i > 0) {
                displayText.append("\n\n")  // 段落分隔
            }

            // 时间码等信息：暗色小字
            if (block.meta.isNotBlank()) {
                val start = displayText.length
                displayText.append(block.meta.trim())
                displayText.append("\n")
                applyBlockSpan(start, displayText.length, META_COLOR, META_SIZE_SCALE)
            }

            // 原文
            if (block.text.isNotBlank()) {
                displayText.append(block.text.trim())
                displayText.append("\n")
            }

            // 附加内容（生词列表里的原文句子）：次要颜色 + 略小字号
            if (block.translation.isNotBlank()) {
                val start = displayText.length
                displayText.append(block.translation.trim())
                applyBlockSpan(start, displayText.length, TRANSLATION_COLOR, SECONDARY_SIZE_SCALE)
            }
        }

        // 去掉结尾多余的换行
        while (displayText.isNotEmpty() && displayText.last() == '\n') {
            displayText.delete(displayText.length - 1, displayText.length)
        }
    }

    private fun applyBlockSpan(start: Int, end: Int, color: Int, sizeScale: Float) {
        if (end <= start) return
        displayText.setSpan(
            ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (sizeScale != 1f) {
            displayText.setSpan(
                RelativeSizeSpan(sizeScale), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun getOffsetAtPosition(x: Float, y: Float): Int? {
        val layout = textLayout ?: return null
        if (displayText.isEmpty()) return null

        val adjustedX = x - paddingLeft
        val adjustedY = y - paddingTop

        if (adjustedY < 0 || adjustedY > layout.height) return null

        val line = layout.getLineForVertical(adjustedY.toInt())
        if (line < 0 || line >= layout.lineCount) return null

        val offset = layout.getOffsetForHorizontal(line, adjustedX)
        return offset.coerceIn(0, displayText.length - 1)
    }

    private fun findWordAtOffset(offset: Int): Pair<Int, Int>? {
        if (offset < 0 || offset >= displayText.length) return null
        val text = displayText.toString()

        val start = findWordStart(text, offset)
        val end = findWordEnd(text, offset)

        return if (end > start) Pair(start, end) else null
    }

    /**
     * 取 offset 所在的整句范围。
     * 句末标点（.!?。！？；;）与换行都视为句子边界。
     */
    private fun findSentenceAtOffset(offset: Int): Pair<Int, Int> {
        val text = displayText.toString()
        if (text.isEmpty()) return 0 to 0

        val pos = offset.coerceIn(0, text.length - 1)

        var start = pos
        while (start > 0 && !isSentenceBoundary(text[start - 1])) start--

        var end = pos
        while (end < text.length && !isSentenceBoundary(text[end])) end++
        // 把句末标点一起选进来（换行除外）
        while (end < text.length && isSentenceBoundary(text[end]) && text[end] != '\n') end++

        // 去掉首尾空白
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--

        return if (end > start) start to end else pos to (pos + 1).coerceAtMost(text.length)
    }

    private fun isSentenceBoundary(c: Char): Boolean =
        c == '.' || c == '!' || c == '?' || c == ';' || c == '\n' ||
        c == '。' || c == '！' || c == '？' || c == '；'

    private fun findWordStart(text: String, offset: Int): Int {
        var pos = offset
        while (pos > 0 && isWordChar(text[pos - 1])) pos--
        return pos
    }

    private fun findWordEnd(text: String, offset: Int): Int {
        var pos = offset
        while (pos < text.length && isWordChar(text[pos])) pos++
        return pos
    }

    private fun isWordChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '\'' || c == '-' || c == '_'

    /** 返回选区在**视图坐标系**中的边界，调用方需自行换算到屏幕坐标 */
    private fun getSelectionBounds(start: Int, end: Int): Rect {
        val layout = textLayout ?: return Rect()
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end)

        val x = layout.getPrimaryHorizontal(start).toInt() + paddingLeft
        val y = layout.getLineTop(startLine) + paddingTop
        val right = layout.getPrimaryHorizontal(end).toInt() + paddingLeft
        val bottom = layout.getLineBottom(endLine) + paddingTop

        return Rect(x, y, right, bottom)
    }

    private companion object {
        /** 时间码等附加信息：浅灰 + 缩小字号 */
        val META_COLOR: Int = Color.parseColor("#9E9E9E")
        const val META_SIZE_SCALE = 0.75f

        /** 附加内容（生词下方的原文句子）：中等灰色 + 略小字号，视觉上退到单词之后 */
        val TRANSLATION_COLOR: Int = Color.parseColor("#7A7A7A")
        const val SECONDARY_SIZE_SCALE = 0.85f
    }
}
