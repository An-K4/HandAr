package com.example.handar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.example.handar.effect.AnchorSource
import com.example.handar.effect.BackgroundRenderer
import com.example.handar.effect.EffectBackground
import com.example.handar.effect.EffectDefinition
import com.example.handar.effect.EffectScope
import com.example.handar.effect.EffectVisual
import com.example.handar.effect.HandFrame
import com.example.handar.effect.HandSide
import com.example.handar.effect.SizeSource
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
    private var liveAllBackgrounds: List<BackgroundRenderer> = emptyList()
    private var recordingAllBackgrounds: List<BackgroundRenderer> = emptyList()
    private var liveDefaultBackground: BackgroundRenderer? = null
    private var recordingDefaultBackground: BackgroundRenderer? = null

    @Volatile
    private var latchedIndex = -1

    private val gestureFrame = HandFrame()
    private val liveDrawFrame = HandFrame()
    private val recordingDrawFrame = HandFrame()

    fun setEffect(effect: EffectDefinition) {
        releaseBackgrounds()
        this.effect = effect

        val liveScope = EffectScope()
        val recordingScope = EffectScope()
        liveVisuals =
            effect.states.map { st ->
                st.asset?.let {
                    createEffectVisual(
                        context!!,
                        it,
                        liveScope
                    )
                }
            }
        recordingVisuals =
            effect.states.map { st ->
                st.asset?.let {
                    createEffectVisual(
                        context!!,
                        it,
                        recordingScope
                    )
                }
            }

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
        liveAllBackgrounds = (liveStateBackgrounds + liveDefaultBackground).filterNotNull().distinct()
        recordingAllBackgrounds = (recordingStateBackgrounds + recordingDefaultBackground).filterNotNull().distinct()

        this.liveCache = liveCache
        this.recordingCache = recordingCache
        latchedIndex = -1
        matchedIndex = -1
    }

    private fun releaseBackgrounds() {
        liveCache?.values?.forEach { it.release() }
        recordingCache?.values?.forEach { it.release() }
    }

    /**
     * Nhận diện cử chỉ chạy đúng MỘT lần cho mỗi kết quả MediaPipe, ở đây.
     * `onDraw` (live) và thread ghi hình chỉ đọc lại kết quả này — không tự nhận diện lại:
     * vừa đỡ ~55 lần nhận diện thừa mỗi giây, vừa bỏ được chuyện hai thread cùng ghi `latchedIndex`.
     */
    @Volatile
    private var matchedIndex = -1

    fun setResult(handResult: HandLandmarkerResult, imgWidth: Int, imgHeight: Int) {
        result = handResult
        this.imgWidth = imgWidth
        this.imgHeight = imgHeight

        val hands = handResult.landmarks()
        val matched = resolveMatchedIndex(hands)
        matchedIndex = matched

        liveVisuals.forEachIndexed { index, visual -> visual?.setActive(index == matched) }
        recordingVisuals.forEachIndexed { index, visual -> visual?.setActive(index == matched) }

        if (matched != -1 && hands.isNotEmpty()) {
            gestureFrame.hands = hands
            liveVisuals.getOrNull(matched)?.onHandFrame(gestureFrame)
            recordingVisuals.getOrNull(matched)?.onHandFrame(gestureFrame)
        }

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
        val matchedIndex = this.matchedIndex

        val stateBackgrounds = if (forRecording) recordingStateBackgrounds else liveStateBackgrounds
        val defaultBackground =
            if (forRecording) recordingDefaultBackground else liveDefaultBackground
        val background = matchedIndex.takeIf { it != -1 }?.let { stateBackgrounds.getOrNull(it) }
            ?: defaultBackground
        background?.draw(canvas)
        val allBackgrounds = if (forRecording) recordingAllBackgrounds else liveAllBackgrounds
        allBackgrounds.forEach { it.setActive(it === background) }

        if (hands.isEmpty() || matchedIndex == -1) return

        val visuals = if (forRecording) recordingVisuals else liveVisuals
        val frame = if (forRecording) recordingDrawFrame else liveDrawFrame

        val targetW = if (forRecording) canvas.width.toFloat() else width.toFloat()
        val targetH = if (forRecording) canvas.height.toFloat() else height.toFloat()
        val localScale = max(targetW / imgWidth, targetH / imgHeight)
        val localOffsetX = (targetW - imgWidth * localScale) / 2f
        val localOffsetY = (targetH - imgHeight * localScale) / 2f

        frame.hands = hands
        frame.handedness = handResult?.handednesses()?.map { categories ->
            val raw = categories.firstOrNull()?.categoryName()
            val label = if (mirrorX) when (raw) {
                "Left" -> "Right"; "Right" -> "Left"; else -> raw
            } else raw
            when (label) {
                "Left" -> HandSide.Left
                "Right" -> HandSide.Right
                else -> HandSide.Unknown
            }
        } ?: emptyList()
        frame.setProjection(mirrorX, imgWidth, imgHeight, localScale, localOffsetX, localOffsetY)

        val anchorSource =
            currentEffect.states.getOrNull(matchedIndex)?.anchorSource ?: AnchorSource.PalmCenter
        when (anchorSource) {
            AnchorSource.PalmCenter -> {
                var sumX = 0f
                var sumY = 0f
                for (h in hands) {
                    sumX += h[9].x()
                    sumY += h[9].y()
                }
                frame.cx = frame.px(sumX / hands.size)
                frame.cy = frame.py(sumY / hands.size)
            }

            AnchorSource.PinchMidpoint -> {
                var sumX = 0f; var sumY = 0f
                for (h in hands) { sumX += (h[4].x() + h[8].x()) / 2f; sumY += (h[4].y() + h[8].y()) / 2f }
                frame.cx = frame.px(sumX / hands.size)
                frame.cy = frame.py(sumY / hands.size)
            }

            AnchorSource.IndexFingertip -> {
                var sumX = 0f; var sumY = 0f
                for (h in hands) { sumX += h[8].x(); sumY += h[8].y() }
                frame.cx = frame.px(sumX / hands.size)
                frame.cy = frame.py(sumY / hands.size)
            }

            AnchorSource.TwoHandMidpoint -> {
                if (hands.size >= 2) {
                    val normMidX = (hands[0][9].x() + hands[1][9].x()) / 2f
                    val normMidY = (hands[0][9].y() + hands[1][9].y()) / 2f
                    frame.cx = frame.px(normMidX)
                    frame.cy = frame.py(normMidY)
                } else {
                    frame.cx = frame.px(hands[0][9].x())
                    frame.cy = frame.py(hands[0][9].y())
                }
            }
        }

        val sizeSource =
            currentEffect.states.getOrNull(matchedIndex)?.sizeSource ?: SizeSource.PalmRadius
        frame.r = when (sizeSource) {
            SizeSource.PalmRadius -> {
                var sum = 0.0
                for (landmark in hands) {
                    val wrist = landmark[0]; val middleMcp = landmark[9]
                    val dx = (wrist.x() - middleMcp.x()) * imgWidth * localScale
                    val dy = (wrist.y() - middleMcp.y()) * imgHeight * localScale
                    sum += hypot(dx.toDouble(), dy.toDouble())
                }
                (sum / hands.size).toFloat()
            }

            SizeSource.PinchDistance -> {
                var sum = 0.0
                for (landmark in hands) {
                    val thumbTip = landmark[4]; val indexTip = landmark[8]
                    val dx = (thumbTip.x() - indexTip.x()) * imgWidth * localScale
                    val dy = (thumbTip.y() - indexTip.y()) * imgHeight * localScale
                    sum += hypot(dx.toDouble(), dy.toDouble())
                }
                (sum / hands.size).toFloat()
            }

            SizeSource.TwoHandDistance -> {
                if (hands.size >= 2) {
                    val a = hands[0][9]
                    val b = hands[1][9]
                    val dx = (a.x() - b.x()) * imgWidth * localScale
                    val dy = (a.y() - b.y()) * imgHeight * localScale
                    hypot(dx.toDouble(), dy.toDouble()).toFloat()
                } else 0f
            }
        }

        visuals.getOrNull(matchedIndex)?.draw(canvas, frame)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFrame(canvas, result, mirrorX = true, forRecording = false)
        postInvalidateOnAnimation()
    }
}