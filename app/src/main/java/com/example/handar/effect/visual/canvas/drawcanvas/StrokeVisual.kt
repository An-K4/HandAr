package com.example.handar.effect.visual.canvas.drawcanvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.handar.effect.visual.HandFrame
import com.example.handar.effect.visual.ProceduralVisual

class StrokeVisual(private val model: StrokeModel) : ProceduralVisual() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val skeleton = HandSkeletonRenderer()
    private var builtVersion = -1

    override fun onHandFrame(frame: HandFrame) {
        val hand = frame.hands.firstOrNull() ?: return
        val indexTip = hand[8]
        model.addPoint(indexTip.x(), indexTip.y())
    }

    override fun onDraw(canvas: Canvas, frame: HandFrame) {
        paint.strokeWidth = canvas.width * STROKE_WIDTH_RATIO

        if (model.version != builtVersion) {
            path.rewind()
            model.withPoints { xs, ys, count ->
                if (count >= 2) {
                    path.moveTo(frame.px(xs[0]), frame.py(ys[0]))
                    for (i in 1 until count) {
                        path.lineTo(frame.px(xs[i]), frame.py(ys[i]))
                    }
                }
            }
            builtVersion = model.version
        }

        canvas.drawPath(path, paint)                       // 👈 luôn vẽ, kể cả khi không dựng lại
        skeleton.draw(canvas, frame)
    }

    private companion object {
        const val STROKE_WIDTH_RATIO = 0.011f
    }
}
