package com.englishstudy.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

/**
 * 自动换行的流式布局。
 *
 * 用来排音素标签：宽度不够时自动折到下一行。
 * 项目里没有引入 flexbox 依赖，所以自己实现一个轻量的。
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    /** 行间距（px） */
    var verticalGap: Int = 0

    /** 元素水平间距（px） */
    var horizontalGap: Int = 0

    init {
        val density = context.resources.displayMetrics.density
        verticalGap = (6 * density).toInt()
        horizontalGap = (6 * density).toInt()
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val availableWidth = if (widthMode == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            (widthSize - paddingLeft - paddingRight).coerceAtLeast(1)
        }

        var lineWidth = 0
        var lineHeight = 0
        var contentHeight = 0
        var maxLineWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (lineWidth > 0 && lineWidth + horizontalGap + childWidth > availableWidth) {
                // 换行
                contentHeight += lineHeight + verticalGap
                maxLineWidth = maxOf(maxLineWidth, lineWidth)
                lineWidth = childWidth
                lineHeight = childHeight
            } else {
                lineWidth += if (lineWidth == 0) childWidth else childWidth + horizontalGap
                lineHeight = maxOf(lineHeight, childHeight)
            }
        }
        contentHeight += lineHeight
        maxLineWidth = maxOf(maxLineWidth, lineWidth)

        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST ->
                (maxLineWidth + paddingLeft + paddingRight).coerceAtMost(widthSize)
            else -> maxLineWidth + paddingLeft + paddingRight
        }

        setMeasuredDimension(
            measuredWidth,
            contentHeight + paddingTop + paddingBottom
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availableWidth = (r - l - paddingLeft - paddingRight).coerceAtLeast(1)

        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (x > paddingLeft && x + childWidth > paddingLeft + availableWidth) {
                x = paddingLeft
                y += lineHeight + verticalGap
                lineHeight = 0
            }

            child.layout(x, y, x + childWidth, y + childHeight)
            x += childWidth + horizontalGap
            lineHeight = maxOf(lineHeight, childHeight)
        }
    }
}
