package com.example.handar.effect

import android.graphics.Canvas

class SkeletonOnlyVisual : EffectVisual {
    private val skeleton = HandSkeletonRenderer()

    override fun setActive(active: Boolean) = Unit

    override fun draw(canvas: Canvas, frame: HandFrame) {
        skeleton.draw(canvas, frame)
    }
}
