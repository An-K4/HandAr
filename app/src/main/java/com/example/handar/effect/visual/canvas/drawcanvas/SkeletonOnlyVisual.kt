package com.example.handar.effect.visual.canvas.drawcanvas

import android.graphics.Canvas
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame

class SkeletonOnlyVisual : EffectVisual {
    private val skeleton = HandSkeletonRenderer()

    override fun setActive(active: Boolean) = Unit

    override fun draw(canvas: Canvas, frame: HandFrame) {
        skeleton.draw(canvas, frame)
    }
}
