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
import com.example.handar.utils.isPalmOpen
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    companion object {
        private const val GIF_BUFFER_SIZE = 256
    }

    private var result: HandLandmarkerResult? = null
    private var imgWidth = 1
    private var imgHeight = 1

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
            if (active) {
                if (!drawable.isRunning) drawable.start()
            } else drawable.stop()
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

    private val happy3Layer by lazy { GifLayer(context!!, R.drawable.happy_happy_happy_cat) }
    private val bananaCryingLayer by lazy { GifLayer(context!!, R.drawable.banana_cat_crying) }

    private val happy3LayerForRecording by lazy {
        GifLayer(
            context!!,
            R.drawable.happy_happy_happy_cat
        )
    }
    private val bananaCryingLayerForRecording by lazy {
        GifLayer(
            context!!,
            R.drawable.banana_cat_crying
        )
    }

    fun setResult(handResult: HandLandmarkerResult, imgWidth: Int, imgHeight: Int) {
        result = handResult
        this.imgWidth = imgWidth
        this.imgHeight = imgHeight
        invalidate()
    }

    fun drawHandEffects(
        canvas: Canvas,
        handResult: HandLandmarkerResult,
        mirrorX: Boolean,
        forRecording: Boolean
    ) {
        val targetW = if (forRecording) canvas.width.toFloat() else width.toFloat()
        val targetH = if (forRecording) canvas.height.toFloat() else height.toFloat()

        val localScale = max(targetW / imgWidth, targetH / imgHeight)
        val localOffsetX = (targetW - imgWidth * localScale) / 2f
        val localOffsetY = (targetH - imgHeight * localScale) / 2f

        for (landmark in handResult.landmarks()) {
            val wrist = landmark[0]
            val middleMcp = landmark[9]

            val normalizeX = if (mirrorX) 1f - middleMcp.x() else middleMcp.x()
            val cx = (normalizeX * imgWidth * localScale) + localOffsetX
            val cy = (middleMcp.y() * imgHeight * localScale) + localOffsetY

            val dx = (wrist.x() - middleMcp.x()) * imgWidth * localScale
            val dy = (wrist.y() - middleMcp.y()) * imgHeight * localScale
            val r = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            val open = isPalmOpen(landmark, wrist)
            val (happy3L, bananaCryingL) = if (forRecording) happy3LayerForRecording to bananaCryingLayerForRecording
            else happy3Layer to bananaCryingLayer

            happy3L.setActive(open)
            bananaCryingL.setActive(!open)

            val activeLayer = if (open) happy3L else bananaCryingL
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
            drawHandEffects(canvas, handResult, true, forRecording = false)
        }

        postInvalidateOnAnimation()
    }
}