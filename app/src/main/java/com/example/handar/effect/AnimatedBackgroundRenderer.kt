package com.example.handar.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedImageDrawable
import androidx.core.graphics.createBitmap
import kotlin.math.max

class AnimatedBackgroundRenderer(context: Context, resId: Int) : BackgroundRenderer {
    private val drawable: AnimatedImageDrawable = (ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(context.resources, resId)
    ) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    } as AnimatedImageDrawable).apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        start()
        setBounds(
            0,
            0,
            intrinsicWidth,
            intrinsicHeight
        )   // đúng kích thước gốc — KHÔNG cần scale ở bước này
    }

    private val buffer: Bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
    private val bufferCanvas = Canvas(buffer)

    private val matrix = Matrix()
    private var lastW = -1
    private var lastH = -1

    override fun draw(canvas: Canvas) {
        buffer.eraseColor(Color.TRANSPARENT)
        drawable.draw(bufferCanvas)

        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)

        if (canvas.width != lastW || canvas.height != lastH) {
            lastW = canvas.width
            lastH = canvas.height
            val cw = canvas.width.toFloat()
            val ch = canvas.height.toFloat()
            val bw = buffer.width.toFloat()
            val bh = buffer.height.toFloat()
            val scale = max(cw / bw, ch / bh)

            with(matrix) {
                reset()
                setScale(scale, scale)
                matrix.postTranslate((cw - bw * scale) / 2f, (ch - bh * scale) / 2f)
            }
        }

        canvas.drawBitmap(buffer, matrix, null)
    }

    override fun setActive(active: Boolean) {
        if (active) drawable.start() else drawable.stop()
    }

    override fun release() {
        drawable.stop()
    }
}