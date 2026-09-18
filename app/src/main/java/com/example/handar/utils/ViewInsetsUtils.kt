package com.example.handar.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * Tự thêm PADDING đúng bằng kích thước status bar / navigation bar hiện tại,
 * để nội dung không bị thanh hệ thống che khi app chạy edge-to-edge.
 * Dùng cho View lấp đầy khu vực của nó (root layout, RecyclerView, hàng nút chạy full width...).
 * KHÔNG dùng cho CameraRecordFragment root (PreviewView/OverlayView) — xem HandAr_Plan.md Phase F.
 */
fun View.applySystemBarsInsetsPadding(
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
    includeDisplayCutout: Boolean = false
) {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom

    val types = if (includeDisplayCutout) {
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    } else {
        WindowInsetsCompat.Type.systemBars()
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(types)
        view.setPadding(
            baseLeft + if (left) bars.left else 0,
            baseTop + if (top) bars.top else 0,
            baseRight + if (right) bars.right else 0,
            baseBottom + if (bottom) bars.bottom else 0
        )
        insets
    }
}

/**
 * Tự thêm MARGIN đúng bằng kích thước status bar / navigation bar hiện tại.
 * Dùng cho View nổi lên trên nội dung khác, định vị bằng layout_gravity + margin
 * (ví dụ nút bấm tròn nổi ở đáy màn hình) — nơi dùng padding sẽ làm phình kích thước View.
 */
fun View.applySystemBarsInsetsMargin(
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false
) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val baseLeft = lp.leftMargin
    val baseTop = lp.topMargin
    val baseRight = lp.rightMargin
    val baseBottom = lp.bottomMargin

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = baseLeft + if (left) bars.left else 0
            topMargin = baseTop + if (top) bars.top else 0
            rightMargin = baseRight + if (right) bars.right else 0
            bottomMargin = baseBottom + if (bottom) bars.bottom else 0
        }
        insets
    }
}