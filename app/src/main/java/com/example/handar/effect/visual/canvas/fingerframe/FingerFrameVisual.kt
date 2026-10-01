package com.example.handar.effect.visual.canvas.fingerframe

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame

/**
 * Finger Frame — MỐC 2 (bản thô): chỉ vẽ VIỀN tứ giác nối 4 đầu ngón (cái + trỏ của mỗi tay) lấy thẳng
 * từ landmark thô, chưa làm mượt/mở-khép/fade (Mốc 3) và chưa đổ filter (Mốc 4).
 * Xem docs/Finger_Frame_Filter_Plan.md.
 */
class FingerFrameVisual : EffectVisual {
    private val pts = FloatArray(8)
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeJoin = Paint.Join.ROUND
    }

    override fun setActive(active: Boolean) = Unit

    override fun draw(canvas: Canvas, frame: HandFrame) {
        if (frame.hands.size < 2) return
        val h0 = frame.hands[0]
        val h1 = frame.hands[1]
        if (h0.size < 9 || h1.size < 9) return
        // landmark 4 = đầu ngón cái, 8 = đầu ngón trỏ; chiếu sang toạ độ canvas (đã mirror + center-crop)
        pts[0] = frame.px(h0[4]); pts[1] = frame.py(h0[4])
        pts[2] = frame.px(h0[8]); pts[3] = frame.py(h0[8])
        pts[4] = frame.px(h1[4]); pts[5] = frame.py(h1[4])
        pts[6] = frame.px(h1[8]); pts[7] = frame.py(h1[8])
        QuadMath.sortByAngle(pts)

        path.rewind()
        path.moveTo(pts[0], pts[1])
        path.lineTo(pts[2], pts[3])
        path.lineTo(pts[4], pts[5])
        path.lineTo(pts[6], pts[7])
        path.close()
        // độ dày theo tỉ lệ bề rộng canvas (live và video có kích thước khác nhau)
        stroke.strokeWidth = canvas.width * 0.006f
        canvas.drawPath(path, stroke)
    }
}
