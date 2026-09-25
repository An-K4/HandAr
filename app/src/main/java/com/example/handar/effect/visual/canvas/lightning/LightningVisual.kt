package com.example.handar.effect.visual.canvas.lightning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.example.handar.R
import com.example.handar.effect.gesture.isIndexExtended
import com.example.handar.effect.gesture.isMiddleExtended
import com.example.handar.effect.gesture.isPinkyExtended
import com.example.handar.effect.gesture.isRingExtended
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.atan2
import kotlin.math.min

class LightningVisual(context: Context) : EffectVisual {
    companion object {
        private const val BUFFER_SIZE = 256
        private const val BOLT_LENGTH_SCALE = 1.15f // chiều dài tia so với frame.r (bán kính lòng bàn tay)
    }

    private val drawable: AnimatedImageDrawable = (ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(context.resources, R.drawable.lightning_bolt)
    ) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    } as AnimatedImageDrawable).apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
    }

    private val buffer: Bitmap = createBitmap(BUFFER_SIZE, BUFFER_SIZE)
    private val bufferCanvas = Canvas(buffer)
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override fun setActive(active: Boolean) {
        if (active) {
            if (!drawable.isRunning) drawable.start()
        } else {
            if (drawable.isRunning) drawable.stop()
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

        for (hand in frame.hands) {
            val wrist = hand[0]
            drawIfExtended(canvas, frame, hand, tip = 8, pip = 6, extended = isIndexExtended(hand, wrist))
            drawIfExtended(canvas, frame, hand, tip = 12, pip = 10, extended = isMiddleExtended(hand, wrist))
            drawIfExtended(canvas, frame, hand, tip = 16, pip = 14, extended = isRingExtended(hand, wrist))
            drawIfExtended(canvas, frame, hand, tip = 20, pip = 18, extended = isPinkyExtended(hand, wrist))
        }
    }

    private fun drawIfExtended(
        canvas: Canvas,
        frame: HandFrame,
        hand: List<NormalizedLandmark>,
        tip: Int,
        pip: Int,
        extended: Boolean
    ) {
        if (!extended) return

        val tipX = frame.px(hand[tip])
        val tipY = frame.py(hand[tip])
        val pipX = frame.px(hand[pip])
        val pipY = frame.py(hand[pip])
        val angleDeg = computeAngleDeg(tipX - pipX, tipY - pipY)

        val boltScale = (frame.r * BOLT_LENGTH_SCALE) / BUFFER_SIZE
        matrix.reset()
        matrix.postTranslate(-BUFFER_SIZE / 2f, -BUFFER_SIZE.toFloat()) // pivot: giữa cạnh dưới ảnh
        matrix.postScale(boltScale, boltScale)
        matrix.postRotate(angleDeg)
        matrix.postTranslate(tipX, tipY) // đặt chân tia đúng vào đầu ngón tay
        canvas.drawBitmap(buffer, matrix, paint)
    }

    /** góc xoay (độ) để vector "lên" mặc định của ảnh (0,-1) trùng hướng (dx, dy) từ pip → tip. */
    private fun computeAngleDeg(dx: Float, dy: Float): Float =
        Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
}
