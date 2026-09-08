package com.example.handar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot
import kotlin.math.max
import com.example.handar.effect.EffectDefinition
import com.example.handar.effect.EffectVisual
import com.example.handar.effect.createEffectVisual

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var result: HandLandmarkerResult? = null
    private var imgWidth = 1
    private var imgHeight = 1

    private var effect: EffectDefinition? = null
    private var liveVisuals: List<EffectVisual> = emptyList()
    private var recordingVisuals: List<EffectVisual> = emptyList()

    fun setEffect(effect: EffectDefinition) {
        this.effect = effect
        liveVisuals = effect.states.map { createEffectVisual(context!!, it.asset) }
        recordingVisuals = effect.states.map { createEffectVisual(context!!, it.asset) }
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
        val currentEffect = effect ?: return
        val visuals = if (forRecording) recordingVisuals else liveVisuals

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

            val matchedIndex =
                currentEffect.states.indexOfFirst { it.gesture.recognize(listOf(landmark)) }
            if (matchedIndex == -1) continue

            visuals.forEachIndexed { index, visual -> visual.setActive(index == matchedIndex) }
            visuals[matchedIndex].draw(canvas, cx, cy, r)
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        result?.let { handResult ->
            drawHandEffects(canvas, handResult, mirrorX = true, forRecording = false)
        }
        postInvalidateOnAnimation()
    }
}