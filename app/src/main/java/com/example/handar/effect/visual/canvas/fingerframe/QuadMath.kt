package com.example.handar.effect.visual.canvas.fingerframe

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Toán tứ giác cho Finger Frame (xem docs/Finger_Frame_Filter_Theory.md §4).
 * Mọi hàm làm việc trên `FloatArray(8)` = (x0,y0,x1,y1,x2,y2,x3,y3), không cấp phát trong vòng vẽ.
 */
object QuadMath {

    /**
     * Sắp 4 điểm theo góc `atan2(y - cy, x - cx)` quanh trọng tâm, tăng dần, TẠI CHỖ.
     * Nối các điểm theo thứ tự này luôn ra tứ giác lồi/không tự cắt (không bao giờ ra hình "cái nơ"
     * dù 2 tay bắt chéo), vì thứ tự đi theo vòng quanh tâm chứ không theo "tay nào, ngón nào".
     */
    fun sortByAngle(pts: FloatArray) {
        val cx = (pts[0] + pts[2] + pts[4] + pts[6]) / 4f
        val cy = (pts[1] + pts[3] + pts[5] + pts[7]) / 4f
        // sắp chèn 4 phần tử, kèm góc tính 1 lần
        val ang = FloatArray(4) { atan2(pts[it * 2 + 1] - cy, pts[it * 2] - cx) }
        for (i in 1 until 4) {
            val a = ang[i]
            val x = pts[i * 2]
            val y = pts[i * 2 + 1]
            var j = i - 1
            while (j >= 0 && ang[j] > a) {
                ang[j + 1] = ang[j]
                pts[(j + 1) * 2] = pts[j * 2]
                pts[(j + 1) * 2 + 1] = pts[j * 2 + 1]
                j--
            }
            ang[j + 1] = a
            pts[(j + 1) * 2] = x
            pts[(j + 1) * 2 + 1] = y
        }
    }

    /**
     * Tìm phép quay vòng `r` (0..3) sao cho góc `cur[(i + r) % 4]` khớp góc `prev[i]` nhất (tổng bình phương
     * khoảng cách nhỏ nhất). Cả hai đã được [sortByAngle], nên chỉ cần thử 4 phép quay vòng — nhờ vậy khung
     * không "xoắn" khi điểm xuất phát của thứ tự góc nhảy sang điểm khác (Theory §4.2). Không cấp phát.
     */
    fun bestRotation(cur: FloatArray, prev: FloatArray): Int {
        var best = 0
        var bestCost = Float.MAX_VALUE
        for (r in 0 until 4) {
            var cost = 0f
            for (i in 0 until 4) {
                val j = (i + r) % 4
                val dx = cur[j * 2] - prev[i * 2]
                val dy = cur[j * 2 + 1] - prev[i * 2 + 1]
                cost += dx * dx + dy * dy
            }
            if (cost < bestCost) {
                bestCost = cost
                best = r
            }
        }
        return best
    }

    /**
     * Hệ số làm mượt thích nghi theo tốc độ (ý tưởng của 1€ Filter, Theory §5.3): điểm gần như đứng yên → `alphaMin`
     * (mượt, hết rung); điểm di chuyển nhanh (`dist >= speedRef`) → 1 (bám tức thì, không bị trễ).
     * EMA cố định (alpha nhỏ) sẽ khử rung nhưng làm khung trễ khi tay vung nhanh — đúng điều cần tránh.
     */
    fun adaptiveAlpha(dist: Float, speedRef: Float, alphaMin: Float): Float {
        if (speedRef <= 0f) return 1f
        val t = (dist / speedRef).coerceIn(0f, 1f)
        return alphaMin + (1f - alphaMin) * t
    }

    /** Diện tích tứ giác theo công thức dây giày (shoelace), luôn dương. Điểm phải đã được [sortByAngle]. */
    fun shoelaceArea(pts: FloatArray): Float {
        var sum = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            sum += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
        }
        return abs(sum) / 2f
    }
}
