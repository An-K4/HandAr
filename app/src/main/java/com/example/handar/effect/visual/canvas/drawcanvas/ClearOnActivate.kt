package com.example.handar.effect.visual.canvas.drawcanvas

import android.graphics.Canvas
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame

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