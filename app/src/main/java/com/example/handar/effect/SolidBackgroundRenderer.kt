package com.example.handar.effect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat

class SolidBackgroundRenderer(context: Context, colorRes: Int) : BackgroundRenderer {
    private val paint = Paint().apply { color = ContextCompat.getColor(context, colorRes) }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    override fun release() = Unit
    // tương đương với override fun release() {}
}