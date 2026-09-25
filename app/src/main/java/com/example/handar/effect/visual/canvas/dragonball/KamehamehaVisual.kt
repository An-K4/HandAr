package com.example.handar.effect.visual.canvas.dragonball

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.example.handar.R
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame
import kotlin.math.min

class KamehamehaVisual(context: Context) : EffectVisual {
    companion object {
        private const val BUFFER_SIZE = 256
        private const val SIZE_MULTIPLIER = 2.2f
        private const val SPIN_DEGREES_PER_SEC = 320f
    }

    private val drawable: AnimatedImageDrawable = (ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(context.resources, R.drawable.dragon_ball_energy)
    ) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    } as AnimatedImageDrawable).apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
    }

    private val buffer: Bitmap = createBitmap(BUFFER_SIZE, BUFFER_SIZE)
    private val bufferCanvas = Canvas(buffer)
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    @Volatile
    private var activatedAtMs = 0L

    override fun setActive(active: Boolean) {
        if (active) {
            if (!drawable.isRunning) drawable.start()
            if (activatedAtMs == 0L) activatedAtMs = SystemClock.elapsedRealtime()
        } else {
            if (drawable.isRunning) drawable.stop()
            activatedAtMs = 0L
        }
    }

    private fun renderToBuffer() {
        buffer.eraseColor(Color.TRANSPARENT)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

        val uniformScale = min(
            BUFFER_SIZE / drawable.intrinsicWidth.toFloat(),
            BUFFER_SIZE / drawable.intrinsicHeight.toFloat()
        )
        val dx = (BUFFER_SIZE - drawable.intrinsicWidth * uniformScale) / 2f
        val dy = (BUFFER_SIZE - drawable.intrinsicHeight * uniformScale) / 2f

        bufferCanvas.withSave {
            translate(dx, dy)
            scale(uniformScale, uniformScale)
            drawable.draw(this)
        }
    }

    override fun draw(canvas: Canvas, frame: HandFrame) {
        renderToBuffer()

        val startedAt = activatedAtMs
        val elapsedMs = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt
        val extraSpinDeg = (elapsedMs / 1000f) * SPIN_DEGREES_PER_SEC % 360f

        val drawScale = (frame.r * SIZE_MULTIPLIER) / BUFFER_SIZE
        matrix.reset()
        matrix.postTranslate(-BUFFER_SIZE / 2f, -BUFFER_SIZE / 2f)
        matrix.postScale(drawScale, drawScale)
        matrix.postRotate(extraSpinDeg)
        matrix.postTranslate(frame.cx, frame.cy)
        canvas.drawBitmap(buffer, matrix, paint)
    }
}
