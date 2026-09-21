package com.example.handar.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * tự thêm padding bằng kích thước thanh hệ thống hiện tại,
 * dùng cho view lấp đầy khu vực của nó (root layout, recyclerview, nút full width...)
 * LƯU Ý: không dùng cho CameraRecordFragment root — đọc HandAr_Plan.md phase F để rõ lý do
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
 * thêm margin bằng kích thước thanh hệ thống hiện tại.
 * dùng cho view nổi lên trên nội dung khác (ví dụ nút quay, thanh điều hướng ứng dụng)
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

/**
 * co chiều cao view bằng đúng inset dưới của thanh hệ thống (gesture bar/3-nút).
 * dùng làm 1 lớp nền chắn ngang đúng vùng đó khi layout kiểu edge-to-edge để content
 * (ví dụ recyclerview) tràn hết xuống đáy màn hình phía sau thanh nav của app.
 */
fun View.matchSystemBarsBottomInsetHeight() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        view.updateLayoutParams<ViewGroup.LayoutParams> {
            height = bottomInset
        }
        insets
    }
}
