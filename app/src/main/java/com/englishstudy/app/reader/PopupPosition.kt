package com.englishstudy.app.reader

import android.graphics.Rect
import android.view.View

/**
 * 计算弹窗在屏幕上的位置。
 *
 * anchorBounds 是相对于 anchorView 的坐标，这里统一换算成屏幕坐标，
 * 并夹取到系统栏以外的可见区域内，避免弹窗被状态栏/导航栏遮住。
 *
 * @return x、y（可直接传给 PopupWindow.showAtLocation）
 */
internal fun computePopupPosition(
    anchorView: View,
    anchorBounds: Rect,
    popupWidth: Int,
    popupHeight: Int,
    margin: Int
): Pair<Int, Int> {
    val anchorLocation = IntArray(2)
    anchorView.getLocationOnScreen(anchorLocation)

    val anchorLeft = anchorLocation[0] + anchorBounds.left
    val anchorRight = anchorLocation[0] + anchorBounds.right
    val anchorTop = anchorLocation[1] + anchorBounds.top
    val anchorBottom = anchorLocation[1] + anchorBounds.bottom

    val visibleFrame = Rect()
    anchorView.getWindowVisibleDisplayFrame(visibleFrame)
    if (visibleFrame.isEmpty) {
        val dm = anchorView.resources.displayMetrics
        visibleFrame.set(0, 0, dm.widthPixels, dm.heightPixels)
    }

    val minX = visibleFrame.left + margin
    val maxX = (visibleFrame.right - popupWidth - margin).coerceAtLeast(minX)
    val minY = visibleFrame.top + margin
    val maxY = (visibleFrame.bottom - popupHeight - margin).coerceAtLeast(minY)

    // 默认显示在选区下方，下方空间不够则显示在选区上方
    var y = anchorBottom + margin
    if (y > maxY) {
        val above = anchorTop - popupHeight - margin
        y = if (above >= minY) above
        else (visibleFrame.top + visibleFrame.bottom) / 2 - popupHeight / 2
    }

    var x = (anchorLeft + anchorRight) / 2 - popupWidth / 2
    x = x.coerceIn(minX, maxX)
    y = y.coerceIn(minY, maxY)

    return x to y
}
