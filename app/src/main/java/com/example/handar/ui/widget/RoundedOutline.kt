package com.example.handar.ui.widget

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider

/**
 * bo góc cho 1 view bằng ViewOutlineProvider + clipToOutline, tự cắt luôn
 * view con. dùng để cắt ảnh đúng phần bo góc của item list giúp ảnh không tràn ra ngoài viền.
 */
fun View.clipRoundedCorners(radiusPx: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    clipToOutline = true
}
