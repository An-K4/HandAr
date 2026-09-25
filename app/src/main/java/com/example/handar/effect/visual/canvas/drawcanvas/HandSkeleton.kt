package com.example.handar.effect.visual.canvas.drawcanvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.handar.effect.visual.HandFrame

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20,
    0 to 17
)

/**
 * vẽ khung xương bàn tay.
 *
 * mỗi EffectVisual giữ một instance riêng — hai bản live/recording không được dùng chung
 * paint. gom vào một lớp để ba hiệu ứng đang vẽ khung
 * xương không mỗi nơi khai một bộ màu — trước khi gom, SkeletonOnlyVisual dùng
 * `argb(225, 225, 225, 0)` còn hai chỗ kia dùng `argb(255, 255, 255, 0)`, nên khung xương
 * đổi màu mỗi lần đổi trạng thái.
 */
class HandSkeletonRenderer {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 229, 255)
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 255, 0)
        style = Paint.Style.FILL
    }

    fun draw(canvas: Canvas, frame: HandFrame) {
        // Kích thước suy từ bề ngang canvas, không dùng số pixel cố định: canvas ghi hình
        // (~720) hẹp hơn view live (~1080), nét cố định sẽ dày hơn khoảng 1,5 lần trong video.
        linePaint.strokeWidth = canvas.width * LINE_WIDTH_RATIO
        val dotRadius = canvas.width * DOT_RADIUS_RATIO

        frame.hands.forEach { landmarks ->
            HAND_CONNECTIONS.forEach { (a, b) ->
                canvas.drawLine(
                    frame.px(landmarks[a]), frame.py(landmarks[a]),
                    frame.px(landmarks[b]), frame.py(landmarks[b]),
                    linePaint
                )
            }
            landmarks.forEach { lm ->
                canvas.drawCircle(frame.px(lm), frame.py(lm), dotRadius, dotPaint)
            }
        }
    }

    private companion object {
        const val LINE_WIDTH_RATIO = 0.0037f   // ~4px trên canvas rộng 1080
        const val DOT_RADIUS_RATIO = 0.0056f   // ~6px trên canvas rộng 1080
    }
}
