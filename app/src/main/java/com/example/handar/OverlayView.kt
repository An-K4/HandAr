package com.example.handar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.example.handar.effect.BackgroundRenderer
import com.example.handar.effect.EffectBackground
import com.example.handar.effect.EffectDefinition
import com.example.handar.effect.EffectVisual
import com.example.handar.effect.StateMode
import com.example.handar.effect.createBackgroundRenderer
import com.example.handar.effect.createEffectVisual
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var result: HandLandmarkerResult? = null
    private var imgWidth = 1
    private var imgHeight = 1

    private var effect: EffectDefinition? = null
    private var liveCache: HashMap<EffectBackground, BackgroundRenderer>? = null
    private var recordingCache: HashMap<EffectBackground, BackgroundRenderer>? = null
    private var liveVisuals: List<EffectVisual?> = emptyList()
    private var recordingVisuals: List<EffectVisual?> = emptyList()

    private var liveStateBackgrounds: List<BackgroundRenderer?> = emptyList()
    private var recordingStateBackgrounds: List<BackgroundRenderer?> = emptyList()
    private var liveDefaultBackground: BackgroundRenderer? = null
    private var recordingDefaultBackground: BackgroundRenderer? = null

    @Volatile
    private var latchedIndex = -1

    fun setEffect(effect: EffectDefinition) {
        releaseBackgrounds()
        this.effect = effect
        liveVisuals =
            effect.states.map { st -> st.asset?.let { createEffectVisual(context!!, it) } }
        recordingVisuals =
            effect.states.map { st -> st.asset?.let { createEffectVisual(context!!, it) } }

        val liveCache = HashMap<EffectBackground, BackgroundRenderer>()
        val recordingCache = HashMap<EffectBackground, BackgroundRenderer>()

        liveStateBackgrounds = effect.states.map { st ->
            st.background?.let { bg ->
                liveCache.getOrPut(bg) {
                    createBackgroundRenderer(
                        context!!,
                        bg
                    )
                }
            }
        }
        recordingStateBackgrounds = effect.states.map { st ->
            st.background?.let { bg ->
                recordingCache.getOrPut(bg) {
                    createBackgroundRenderer(
                        context!!,
                        bg
                    )
                }
            }
        }
        liveDefaultBackground = effect.background?.let { bg ->
            liveCache.getOrPut(bg) {
                createBackgroundRenderer(
                    context!!,
                    bg
                )
            }
        }
        recordingDefaultBackground = effect.background?.let { bg ->
            recordingCache.getOrPut(bg) {
                createBackgroundRenderer(
                    context!!,
                    bg
                )
            }
        }

        this.liveCache = liveCache
        this.recordingCache = recordingCache
        latchedIndex = -1
    }

    private fun releaseBackgrounds() {
        liveCache?.values?.forEach { it.release() }
        recordingCache?.values?.forEach { it.release() }
    }

    fun setResult(handResult: HandLandmarkerResult, imgWidth: Int, imgHeight: Int) {
        result = handResult
        this.imgWidth = imgWidth
        this.imgHeight = imgHeight
        invalidate()
    }

    private fun resolveMatchedIndex(hands: List<List<NormalizedLandmark>>): Int {
        val currentEffect = effect ?: return -1
        val liveMatch = currentEffect.states.indexOfFirst { it.gesture.recognize(hands) }
        return when (currentEffect.stateMode) {
            StateMode.Momentary -> liveMatch
            StateMode.Latched -> {
                if (liveMatch != -1) latchedIndex = liveMatch
                latchedIndex
            }
        }
    }

    fun drawFrame(
        canvas: Canvas,
        handResult: HandLandmarkerResult?,
        mirrorX: Boolean,
        forRecording: Boolean
    ) {
        val currentEffect = effect ?: return
        val hands = handResult?.landmarks() ?: emptyList()
        val matchedIndex = resolveMatchedIndex(hands)

        val stateBackgrounds = if (forRecording) recordingStateBackgrounds else liveStateBackgrounds
        val defaultBackground =
            if (forRecording) recordingDefaultBackground else liveDefaultBackground
        val background = matchedIndex.takeIf { it != -1 }?.let { stateBackgrounds.getOrNull(it) }
            ?: defaultBackground
        background?.draw(canvas)
        val allBackgrounds = (stateBackgrounds + defaultBackground).filterNotNull().distinct()
        allBackgrounds.forEach { it.setActive(it === background) }

        if (hands.isEmpty() || matchedIndex == -1) return

        val visuals = if (forRecording) recordingVisuals else liveVisuals
        val targetW = if (forRecording) canvas.width.toFloat() else width.toFloat()
        val targetH = if (forRecording) canvas.height.toFloat() else height.toFloat()
        val localScale = max(targetW / imgWidth, targetH / imgHeight)
        val localOffsetX = (targetW - imgWidth * localScale) / 2f
        val localOffsetY = (targetH - imgHeight * localScale) / 2f

        val normMidX = hands.map { it[9].x() }.average().toFloat()
        val normMidY = hands.map { it[9].y() }.average().toFloat()
        val normalizeX = if (mirrorX) 1f - normMidX else normMidX
        val cx = (normalizeX * imgWidth * localScale) + localOffsetX
        val cy = (normMidY * imgHeight * localScale) + localOffsetY

        val r = hands.map { landmark ->
            val wrist = landmark[0]
            val middleMcp = landmark[9]
            val dx = (wrist.x() - middleMcp.x()) * imgWidth * localScale
            val dy = (wrist.y() - middleMcp.y()) * imgHeight * localScale
            hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }.average().toFloat()

        visuals.forEachIndexed { index, visual -> visual?.setActive(index == matchedIndex) }
        visuals[matchedIndex]?.draw(canvas, cx, cy, r)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFrame(canvas, result, mirrorX = true, forRecording = false)
        postInvalidateOnAnimation()
    }
}