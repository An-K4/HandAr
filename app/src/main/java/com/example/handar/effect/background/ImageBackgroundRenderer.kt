package com.example.handar.effect.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import kotlin.math.max

class ImageBackgroundRenderer(context: Context, resId: Int) : BackgroundRenderer {
    private val bitmap: Bitmap = requireNotNull(
        BitmapFactory.decodeResource(context.resources, resId, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    ) {
        "Không decode được ảnh nền ${context.resources.getResourceEntryName(resId)}"
    }
    private val matrix = Matrix()
    private var lastW = -2
    private var lastH = -1

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)

        if (canvas.width != lastW || canvas.height != lastH) {
            lastW = canvas.width
            lastH = canvas.height
            val cw = canvas.width.toFloat()
            val ch = canvas.height.toFloat()
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val scale = max(cw / bw, ch / bh)
            val offsetX = (cw - bw * scale) / 2f
            val offsetY = (ch - bh * scale) / 2f
            with(matrix) {
                reset()
                setScale(scale, scale)
                postTranslate(offsetX, offsetY)
            }
        }

        canvas.drawBitmap(bitmap, matrix, null)
    }

    override fun release() = Unit
}