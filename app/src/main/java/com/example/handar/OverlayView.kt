package com.example.handar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import androidx.core.graphics.toColorInt
import kotlin.math.hypot
import androidx.core.graphics.withTranslation
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?): View(context, attrs) {
    private var result: HandLandmarkerResult? = null
    private var rotationAngle: Float = 0f

    private var imgWidth = 1
    private var imgHeight = 1
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val egg: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.egg)
    }
    private val eggCracked: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.egg_cracked)
    }

    fun setResult(handResult: HandLandmarkerResult, imgWidth: Int, imgHeight: Int){
        result = handResult

        this.imgWidth = imgWidth
        this.imgHeight = imgHeight

        scaleFactor = max(width * 1f / imgWidth, height * 1f / imgHeight)

        offsetX = (width - imgWidth * scaleFactor) / 2f
        offsetY = (height - imgHeight * scaleFactor) / 2f

        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        result?.let { handResult ->
            for(landmark in handResult.landmarks()) {
                val wrist = landmark[0]
                val middleMcp = landmark[9]

                val normCenterX = 1f - middleMcp.x()
                val normCenterY = middleMcp.y()

                val cx = (normCenterX * imgWidth * scaleFactor) + offsetX
                val cy = (normCenterY * imgHeight * scaleFactor) + offsetY

                val dx = (wrist.x() - middleMcp.x()) * imgWidth * scaleFactor
                val dy = (wrist.y() - middleMcp.y()) * imgHeight * scaleFactor

                val handSize = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val isPalmOpen = listOf(Pair(8, 6), Pair(12, 10), Pair(16, 14), Pair(20, 18)).count { (tip, pip) ->
                    hypot(landmark[tip].x() - wrist.x(), landmark[tip].y() - wrist.y()) > hypot(landmark[pip].x() - wrist.x(), landmark[pip].y() - wrist.y())
                } >= 3

                val bitmap = if(isPalmOpen) egg else eggCracked
                val scale = (handSize * 1.5f) / bitmap.width

                val matrix = Matrix().apply {
                    postTranslate(-bitmap.width/2f, -bitmap.height/2f)
                    postScale(scale, scale)
                    postTranslate(cx, cy)
                }

                canvas.drawBitmap(bitmap, matrix, null)
            }
        }

        rotationAngle = (rotationAngle + 3f) % 360f
        postInvalidateOnAnimation()
    }
}