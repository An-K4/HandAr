package com.example.handar.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.handar.R
import com.example.handar.utils.isPointing
import com.example.handar.utils.palmLength
import com.example.handar.utils.pointDistance
import kotlin.math.max

class GojoVisual(
    context: Context,
    private val model: GojoModel
) : EffectVisual {
    private val blueBall =
        BitmapFactory.decodeResource(context.resources, R.drawable.blue_ball)
    private val redBall =
        BitmapFactory.decodeResource(context.resources, R.drawable.red_ball)
    private val purpleBall =
        BitmapFactory.decodeResource(context.resources, R.drawable.stranger_things_monster)
    private val mergeAnim = AnimatedGifVisual(
        context,
        EffectAsset.AnimatedGif(R.drawable.stranger_things_clock, oneShot = true)
    )
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    companion object {
        private const val TOUCH_RATIO_THRESHOLD = 0.5f
    }

    override fun setActive(active: Boolean) {
        if (!active) {
            model.wasTouching = false
            mergeAnim.setActive(false)
        }
    }

    override fun onHandFrame(frame: HandFrame) {
        val hands = frame.hands
        if (hands.size < 2) {
            model.wasTouching = false
            return
        }

        val palmLen = (palmLength(hands[0], hands[0][0]) + palmLength(hands[1], hands[1][0])) / 2.0
        val touching =
            palmLen > 0 && pointDistance(hands[0][8], hands[1][8]) / palmLen < TOUCH_RATIO_THRESHOLD

        if (touching && !model.wasTouching) {
            mergeAnim.setActive(true)
        }
        model.wasTouching = touching
    }

    override fun draw(canvas: Canvas, frame: HandFrame) {
        val hands = frame.hands

        if (model.wasTouching && hands.size >= 2) {
            val midX = frame.px((hands[0][8].x() + hands[1][8].x()) / 2f)
            val midY = frame.py((hands[0][8].y() + hands[1][8].y()) / 2f)
            if (mergeAnim.hasFinishedPlaying()) {
                drawBall(canvas, purpleBall, midX, midY, frame.r * 0.6f)
            } else {
                mergeAnim.draw(canvas, frame)
            }
            return
        }

        hands.forEachIndexed { index, landmarks ->
            if (!isPointing(landmarks, landmarks[0])) return@forEachIndexed
            val side = frame.handedness.getOrNull(index) ?: HandSide.Unknown
            val bitmap = if (side == HandSide.Left) blueBall else redBall
            drawBall(
                canvas,
                bitmap,
                frame.px(landmarks[8].x()),
                frame.py(landmarks[8].y()),
                frame.r * 0.4f
            )
        }
    }

    private fun drawBall(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, r: Float) {
        val scale = r / max(bitmap.width, bitmap.height).toFloat()
        with(matrix) {
            reset()
            postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            postScale(scale, scale)
            postTranslate(cx, cy)
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }
}