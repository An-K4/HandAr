package com.example.handar.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import kotlin.math.min

class AnimatedGifVisual(context: Context, resId: Int) : EffectVisual {
    companion object {
        private const val GIF_BUFFER_SIZE = 256
    }

    private val drawable: AnimatedImageDrawable = (ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(context.resources, resId)
    ) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    } as AnimatedImageDrawable).apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
    }

    private val buffer: Bitmap = createBitmap(GIF_BUFFER_SIZE, GIF_BUFFER_SIZE)
    private val bufferCanvas = Canvas(buffer)

    override fun setActive(active: Boolean) {
        if (active) {
            if (!drawable.isRunning) drawable.start()
        } else drawable.stop()
    }

    private fun renderToBuffer() {
        buffer.eraseColor(Color.TRANSPARENT)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

        val uniformScale = min(
            GIF_BUFFER_SIZE / drawable.intrinsicWidth.toFloat(),
            GIF_BUFFER_SIZE / drawable.intrinsicHeight.toFloat()
        )
        val dx = (GIF_BUFFER_SIZE - drawable.intrinsicWidth * uniformScale) / 2f
        val dy = (GIF_BUFFER_SIZE - drawable.intrinsicHeight * uniformScale) / 2f

        bufferCanvas.withSave {
            translate(dx, dy)
            scale(uniformScale, uniformScale)
            drawable.draw(this)
        }
    }

    override fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float
    ) {
        renderToBuffer()

        val drawScale = r / GIF_BUFFER_SIZE
        val matrix = Matrix().apply {
            postTranslate(-GIF_BUFFER_SIZE / 2f, -GIF_BUFFER_SIZE / 2f)
            postScale(drawScale, drawScale)
            postTranslate(cx, cy)
        }
        canvas.drawBitmap(buffer, matrix, null)
    }
}