package com.example.handar.effect

import android.graphics.Canvas

class ClearOnActivate(private val model: StrokeModel) : EffectVisual {
    private var wasActive = false
    private val skeleton = HandSkeletonRenderer()

    override fun setActive(active: Boolean) {
        if (active && !wasActive) model.clear()
        wasActive = active
    }

    override fun draw(canvas: Canvas, frame: HandFrame) {
        skeleton.draw(canvas, frame)
    }
}
