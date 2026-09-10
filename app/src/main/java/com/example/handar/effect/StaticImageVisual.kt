package com.example.handar.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max

class StaticImageVisual(context: Context, resId: Int) : EffectVisual {
    private val bitmap: Bitmap = requireNotNull(
        BitmapFactory.decodeResource(context.resources, resId)
    ) { "Không decode được ảnh '${context.resources.getResourceEntryName(resId)}'" }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override fun setActive(active: Boolean) = Unit   // ảnh tĩnh không có gì để bật/tắt

    override fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val scale = r / max(bitmap.width, bitmap.height).toFloat()
        val matrix = Matrix().apply {
            postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            postScale(scale, scale)
            postTranslate(cx, cy)
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }
}