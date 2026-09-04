package com.example.handar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot
import kotlin.math.max
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    companion object {
        private const val GIF_BUFFER_SIZE = 256
    }

    private var result: HandLandmarkerResult? = null
    private var imgWidth = 1
    private var imgHeight = 1
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private class GifLayer(context: Context, resId: Int) {
        val drawable: AnimatedImageDrawable = (ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(context.resources, resId)
        ) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        } as AnimatedImageDrawable).apply {
            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }

        val buffer: Bitmap = createBitmap(GIF_BUFFER_SIZE, GIF_BUFFER_SIZE)
        val bufferCanvas = Canvas(buffer)

        fun setActive(active: Boolean) {
            if (active) { if (!drawable.isRunning) drawable.start() } else drawable.stop()
        }

        fun renderToBuffer() {
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
    }

    private val mooLayer by lazy { GifLayer(context!!, R.drawable.moo) }
    private val rollingLayer by lazy { GifLayer(context!!, R.drawable.rolling) }

    fun setResult(handResult: HandLandmarkerResult, imgWidth: Int, imgHeight: Int) {
        result = handResult
        this.imgWidth = imgWidth
        this.imgHeight = imgHeight

        scaleFactor = max(width * 1f / imgWidth, height * 1f / imgHeight)
        offsetX = (width - imgWidth * scaleFactor) / 2f
        offsetY = (height - imgHeight * scaleFactor) / 2f

        invalidate()
    }

    private fun isPalmOpen(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark): Boolean {
        return listOf(Pair(8, 6), Pair(12, 10), Pair(16, 14), Pair(20, 18)).count { (tip, pip) ->
            hypot(landmark[tip].x() - wrist.x(), landmark[tip].y() - wrist.y()) >
                    hypot(landmark[pip].x() - wrist.x(), landmark[pip].y() - wrist.y())
        } >= 3
    }

    fun drawHandEffects(canvas: Canvas, handResult: HandLandmarkerResult, mirrorX: Boolean) {
        for (landmark in handResult.landmarks()) {
            val wrist = landmark[0]
            val middleMcp = landmark[9]

            val normalizeX = if (mirrorX) 1f - middleMcp.x() else middleMcp.x()
            val cx = (normalizeX * imgWidth * scaleFactor) + offsetX
            val cy = (middleMcp.y() * imgHeight * scaleFactor) + offsetY

            val dx = (wrist.x() - middleMcp.x()) * imgWidth * scaleFactor
            val dy = (wrist.y() - middleMcp.y()) * imgHeight * scaleFactor
            val r = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            val open = isPalmOpen(landmark, wrist)
            mooLayer.setActive(open)
            rollingLayer.setActive(!open)

            val activeLayer = if (open) mooLayer else rollingLayer
            activeLayer.renderToBuffer()

            val drawScale = r / GIF_BUFFER_SIZE
            val matrix = Matrix().apply {
                postTranslate(-GIF_BUFFER_SIZE / 2f, -GIF_BUFFER_SIZE / 2f)
                postScale(drawScale, drawScale)
                postTranslate(cx, cy)
            }
            canvas.drawBitmap(activeLayer.buffer, matrix, null)
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        result?.let { handResult ->
            drawHandEffects(canvas, handResult, true)
        }

        postInvalidateOnAnimation()
    }
}