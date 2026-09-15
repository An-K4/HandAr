package com.example.handar.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import kotlin.math.max

class ImageBackgroundRenderer(context: Context, resId: Int): BackgroundRenderer {
    private val bitmap: Bitmap = requireNotNull(
        BitmapFactory.decodeResource(context.resources, resId)
    ) {
        "Không decode được ảnh nền ${context.resources.getResourceEntryName(resId)}"
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)

        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = max(cw / bw, ch / bh)
        val offsetX = (cw - bw * scale) / 2f
        val offsetY = (ch - bh * scale) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(offsetX, offsetY)
        }
        canvas.drawBitmap(bitmap, matrix, null)
    }

    override fun release() = Unit
}