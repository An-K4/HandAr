package com.example.handar.effect

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

class StrokeVisual(private val model: StrokeModel) : ProceduralVisual() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val skeleton = HandSkeletonRenderer()

    override fun onHandFrame(frame: HandFrame) {
        val hand = frame.hands.firstOrNull() ?: return
        val indexTip = hand[8]
        model.addPoint(indexTip.x(), indexTip.y())
    }

    override fun onDraw(
        canvas: Canvas,
        frame: HandFrame
    ) {
        // Độ dày nét theo bề ngang canvas: live và bản ghi khác độ phân giải, nếu để số pixel
        // cố định thì nét trong video dày hơn nét đang thấy trên màn hình.
        paint.strokeWidth = canvas.width * STROKE_WIDTH_RATIO

        val pts = model.snapshot()

        if (pts.size >= 2) {
            path.rewind()
            path.moveTo(frame.px(pts[0].x), frame.py(pts[0].y))
            for (i in 1 until pts.size) {
                path.lineTo(frame.px(pts[i].x), frame.py(pts[i].y))
            }
            canvas.drawPath(path, paint)
        }
        skeleton.draw(canvas, frame)
    }

    private companion object {
        const val STROKE_WIDTH_RATIO = 0.011f   // ~12px trên canvas rộng 1080
    }
}
