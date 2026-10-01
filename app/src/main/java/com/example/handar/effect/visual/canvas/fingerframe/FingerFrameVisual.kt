package com.example.handar.effect.visual.canvas.fingerframe

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame

/**
 * Finger Frame — MỐC 3: viền tứ giác 4 đầu ngón đã qua [FingerFrameTracker] (khớp góc, làm mượt thích nghi,
 * mở/khép có hysteresis, mờ dần). Chưa đổ filter (Mốc 4). Xem docs/Finger_Frame_Filter_Plan.md.
 *
 * Không kế thừa `ProceduralVisual` vì cần tự xử lý `setActive()` để `reset()` tracker (`ProceduralVisual.setActive()` là final).
 */
class FingerFrameVisual : EffectVisual {
    private val tracker = FingerFrameTracker()
    private var wasActive = false

    private val pts = FloatArray(8)
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeJoin = Paint.Join.ROUND
    }

    override fun setActive(active: Boolean) {
        // mất cử chỉ / đổi state → xoá trạng thái làm mượt, lần sau hiện lại từ đầu
        if (!active && wasActive) tracker.reset()
        wasActive = active
    }

    override fun onHandFrame(frame: HandFrame) {
        tracker.update(frame.hands, SystemClock.uptimeMillis())
    }

    override fun draw(canvas: Canvas, frame: HandFrame) {
        val snap = tracker.snapshot ?: return // đọc 1 lần: mảng bất biến, thread nào đọc cũng nhất quán
        val p = snap[8]
        if (p < MIN_VISIBLE_P) return

        // snap là toạ độ chuẩn hoá; chiếu sang canvas (đã mirror + center-crop) ngay tại đây
        for (i in 0 until 4) {
            pts[i * 2] = frame.px(snap[i * 2])
            pts[i * 2 + 1] = frame.py(snap[i * 2 + 1])
        }
        path.rewind()
        path.moveTo(pts[0], pts[1])
        path.lineTo(pts[2], pts[3])
        path.lineTo(pts[4], pts[5])
        path.lineTo(pts[6], pts[7])
        path.close()

        // độ dày theo tỉ lệ bề rộng canvas (live và video có kích thước khác nhau)
        stroke.strokeWidth = canvas.width * 0.006f
        stroke.alpha = (p * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(path, stroke)
    }

    private companion object {
        const val MIN_VISIBLE_P = 0.01f
    }
}
