package com.englishstudy.app.reader

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.englishstudy.app.R

/**
 * 选中文本后先弹出的操作菜单：查询 / 翻译
 *
 * 位置计算与翻译浮窗保持一致（紧贴选区，超出屏幕时自动上移/夹取）。
 */
class SelectionMenuPopup(
    private val context: Context,
    private val onLookup: (text: String, bounds: Rect) -> Unit,
    private val onTranslate: (text: String, bounds: Rect) -> Unit
) {

    private var popupWindow: PopupWindow? = null

    companion object {
        private const val POPUP_MARGIN_DP = 8
    }

    fun show(anchorView: View, anchorBounds: Rect, selectedText: String) {
        dismiss()

        val text = selectedText.trim()
        if (text.isEmpty()) return

        val view = LayoutInflater.from(context).inflate(R.layout.popup_selection_menu, null)

        val popup = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 12f
        }
        popupWindow = popup

        view.findViewById<View>(R.id.btn_menu_lookup).setOnClickListener {
            dismiss()
            onLookup(text, Rect(anchorBounds))
        }
        view.findViewById<View>(R.id.btn_menu_translate).setOnClickListener {
            dismiss()
            onTranslate(text, Rect(anchorBounds))
        }

        val dm = context.resources.displayMetrics
        val margin = (POPUP_MARGIN_DP * dm.density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec((dm.widthPixels * 0.6f).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val (x, y) = computePopupPosition(
            anchorView = anchorView,
            anchorBounds = anchorBounds,
            popupWidth = view.measuredWidth,
            popupHeight = view.measuredHeight,
            margin = margin
        )
        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)

        view.alpha = 0f
        view.animate().alpha(1f).setDuration(150).start()
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    val isShowing: Boolean get() = popupWindow?.isShowing == true
}
