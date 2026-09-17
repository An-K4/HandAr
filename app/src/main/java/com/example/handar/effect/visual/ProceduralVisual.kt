package com.example.handar.effect.visual

import android.graphics.Canvas
import android.os.SystemClock

abstract class ProceduralVisual : EffectVisual {
    @Volatile
    private var activateAtMs = 0L

    final override fun setActive(active: Boolean) {
        if (active) {
            if (activateAtMs == 0L) activateAtMs = SystemClock.elapsedRealtime()
        } else activateAtMs = 0L
    }

    final override fun draw(canvas: Canvas, frame: HandFrame) {
        val startedAt = activateAtMs
        frame.elapsedMs = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt
        onDraw(canvas, frame)
    }

    protected abstract fun onDraw(canvas: Canvas, frame: HandFrame)
}