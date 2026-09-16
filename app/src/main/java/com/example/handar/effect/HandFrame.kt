package com.example.handar.effect

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

enum class HandSide { Left, Right, Unknown }

class HandFrame {
    var hands: List<List<NormalizedLandmark>> = emptyList()
    var handedness: List<HandSide> = emptyList()

    var cx = 0f
    var cy = 0f
    var r = 0f
    var elapsedMs = 0L

    private var mirrorX = true
    private var imgWidth = 1
    private var imgHeight = 1
    private var localScale = 1f
    private var localScaleOffsetX = 0f
    private var localScaleOffsetY = 0f

    fun setProjection(
        mirrorX: Boolean,
        imgWidth: Int,
        imgHeight: Int,
        localScale: Float,
        localScaleOffsetX: Float,
        localScaleOffsetY: Float,
    ) {
        this.mirrorX = mirrorX
        this.imgWidth = imgWidth
        this.imgHeight = imgHeight
        this.localScale = localScale
        this.localScaleOffsetX = localScaleOffsetX
        this.localScaleOffsetY = localScaleOffsetY
    }

    fun px(normX: Float): Float {
        val nx = if (mirrorX) 1f - normX else normX
        return nx * imgWidth * localScale + localScaleOffsetX
    }

    fun py(normY: Float): Float = normY * imgHeight * localScale + localScaleOffsetY

    fun px(lm: NormalizedLandmark): Float = px(lm.x())
    fun py(lm: NormalizedLandmark): Float = py(lm.y())
}