package com.englishstudy.app.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * 可交互的阅读器视图
 *
 * 功能：
 * - 单击选择单词
 * - 长按拖动选择词组/句子
 * - 回调选中的文本及其屏幕位置
 */
class InteractiveReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ===== 数据 =====
    private var paragraphs: List<String> = emptyList()
    private val displayText = SpannableStringBuilder()
    private var textLayout: Layout? = null

    // ===== 绘制样式 =====
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 48f  // 默认 36sp ≈ 48px
        typeface = Typeface.DEFAULT
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#402196F3")
    }
    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        isAntiAlias = true
    }
    private val paragraphSpacing = 32f

    // ===== 选择状态 =====
    private var selectionStart = -1
    private var selectionEnd = -1
    private var isSelecting = false
    private var touchSlop = 20

    // ===== 手势检测 =====
    private val gestureDetector: GestureDetector
    private val mainHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    // ===== 回调 =====
    var onWordSelected: ((word: String, wordBounds: Rect) -> Unit)? = null
    var onRangeSelected: ((text: String, anchorBounds: Rect) -> Unit)? = null
    var onSelectionCleared: (() -> Unit)? = null

    init {
        touchSlop = (context.resources.displayMetrics.density * 8).roundToInt()

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
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

    /** 设置段落内容 */
    fun setParagraphs(paragraphs: List<String>) {
        this.paragraphs = paragraphs
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
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    handleSelectionDrag(event.x, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSelecting) {
                    finishSelection()
                    return true
                }
                cancelLongPressTask()
            }
        }
        return gestureDetector.onTouchEvent(event)
    }

    // ===== 布局测量 =====
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

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
            val totalHeight = textHeight + paddingTop + paddingBottom
            setMeasuredDimension(width, totalHeight.coerceAtMost(height))
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
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionEnd > selectionStart) {
            val selStart = selectionStart.coerceAtMost(selectionEnd)
            val selEnd = selectionEnd.coerceAtLeast(selectionStart)

            val startLine = layout.getLineForOffset(selStart)
            val endLine = layout.getLineForOffset(selEnd)

            for (line in startLine..endLine) {
                val lineStart = layout.getLineStart(line)
                val lineEnd = layout.getLineEnd(line)
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
        val bounds = getWordBounds(selectionStart, selectionEnd)
        onWordSelected?.invoke(word, bounds)
    }

    private fun handleLongPressStart(x: Float, y: Float) {
        val offset = getOffsetAtPosition(x, y) ?: return
        val wordRange = findWordAtOffset(offset) ?: return

        selectionStart = wordRange.first
        selectionEnd = wordRange.second
        isSelecting = true
        invalidate()
    }

    private fun handleSelectionDrag(x: Float, y: Float) {
        val offset = getOffsetAtPosition(x, y) ?: return
        if (offset > selectionStart) {
            selectionEnd = offset
            // 扩展到单词边界
            val text = displayText.toString()
            val wordEnd = findWordEnd(text, offset)
            if (wordEnd > selectionEnd) selectionEnd = wordEnd
        }
        invalidate()
    }

    private fun finishSelection() {
        isSelecting = false
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionEnd > selectionStart) {
            val selText = displayText.substring(selectionStart, selectionEnd)
            val bounds = getWordBounds(selectionStart, selectionEnd)
            onRangeSelected?.invoke(selText, bounds)
        }
    }

    fun clearSelection() {
        if (selectionStart >= 0 || selectionEnd >= 0) {
            selectionStart = -1
            selectionEnd = -1
            isSelecting = false
            invalidate()
            onSelectionCleared?.invoke()
        }
    }

    // ===== 文本工具 =====
    private fun updateDisplayText() {
        displayText.clear()
        displayText.clearSpans()

        if (paragraphs.isEmpty()) {
            displayText.append("请打开一个字幕文件开始阅读")
            return
        }

        for (i in paragraphs.indices) {
            if (i > 0) {
                displayText.append("\n\n")  // 段落分隔
            }
            displayText.append(paragraphs[i])
        }
    }

    private fun getOffsetAtPosition(x: Float, y: Float): Int? {
        val layout = textLayout ?: return null
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

    private fun getWordBounds(start: Int, end: Int): Rect {
        val layout = textLayout ?: return Rect()
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end)

        val x = layout.getPrimaryHorizontal(start).toInt() + paddingLeft
        val y = layout.getLineTop(startLine) + paddingTop
        val right = layout.getPrimaryHorizontal(end).toInt() + paddingLeft
        val bottom = layout.getLineBottom(endLine) + paddingTop

        return Rect(x, y, right, bottom)
    }

    private fun cancelLongPressTask() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
    }
}