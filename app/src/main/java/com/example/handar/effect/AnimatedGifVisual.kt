package com.example.handar.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.example.handar.effect.model.EffectAsset
import kotlin.math.min

class AnimatedGifVisual(context: Context, private val asset: EffectAsset.AnimatedGif) :
    EffectVisual {
    companion object {
        private const val GIF_BUFFER_SIZE = 256
    }

    private val drawable: AnimatedImageDrawable = (ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(context.resources, asset.resId)
    ) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    } as AnimatedImageDrawable).apply {
        repeatCount = if (asset.oneShot) 0 else AnimatedImageDrawable.REPEAT_INFINITE
        if (asset.oneShot) {
            registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    finished = true
                }
            })
        }
    }

    @Volatile
    private var finished = false
    fun hasFinishedPlaying(): Boolean = finished

    private val buffer: Bitmap = createBitmap(GIF_BUFFER_SIZE, GIF_BUFFER_SIZE)
    private val bufferCanvas = Canvas(buffer)

    private val matrix = Matrix()

    override fun setActive(active: Boolean) {
        if (active) {
            if (asset.oneShot) finished = false
            if (!drawable.isRunning) drawable.start()
        } else {
            if (drawable.isRunning) drawable.stop()
        }
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
        frame: HandFrame
    ) {
        renderToBuffer()

        val drawScale = frame.r / GIF_BUFFER_SIZE
        with(matrix) {
            reset()
            postTranslate(-GIF_BUFFER_SIZE / 2f, -GIF_BUFFER_SIZE / 2f)
            postScale(drawScale, drawScale)
            postTranslate(frame.cx, frame.cy)
        }
        canvas.drawBitmap(buffer, matrix, null)
    }
}