package com.example.handar.effect.visual.canvas.fingerframe

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame

/**
 * Finger Frame — MỐC 4: trong tứ giác 4 đầu ngón (đã qua [FingerFrameTracker]) hiển thị ẢNH CAMERA ĐẢO MÀU,
 * bên ngoài giữ nguyên. Công thức: `out = F(ảnh) * mask + ảnh * (1 - mask)`, ở đây `mask` là hình tứ giác
 * (Theory §7–9) và phần "ảnh bên ngoài" chính là nền camera có sẵn bên dưới.
 *
 * Vẽ bằng `BitmapShader` + `drawPath` (có khử răng cưa) thay vì `clipPath` (không khử răng cưa trên canvas phần cứng).
 * Xem docs/Finger_Frame_Filter_Plan.md.
 *
 * Không kế thừa `ProceduralVisual` vì cần tự xử lý `setActive()` để `reset()` tracker (`ProceduralVisual.setActive()` là final).
 *
 * Mọi field vẽ ([shader], [lastBitmap], [dashWidth]...) chỉ được đụng trong `draw()`; mỗi visual (live / recording)
 * là 1 instance riêng nên không có chuyện 2 thread cùng ghi.
 */
class FingerFrameVisual : EffectVisual {
    private val tracker = FingerFrameTracker()
    private var wasActive = false

    private val pts = FloatArray(8)
    private val path = Path()
    private val matrix = Matrix()

    /** Đảo màu: out = 255 - in. Cột cuối là 255 (thang ColorMatrix là 0–255, không phải 0–1 — Theory §9.1). */
    private val negativeFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.FILL
        colorFilter = negativeFilter
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // BitmapShader gắn cứng 1 bitmap mà bitmap camera đổi mỗi frame → cache theo THAM CHIẾU, chỉ tạo mới khi bitmap đổi
    private var lastBitmap: Bitmap? = null
    private var shader: BitmapShader? = null

    // nét đứt phụ thuộc bề rộng canvas (live ≠ video) → chỉ tạo lại khi bề rộng đổi
    private var dashWidth = -1
    private var dashEffect: DashPathEffect? = null

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
        val alpha = (p * 255f).toInt().coerceIn(0, 255)

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

        // 1) phần ruột: ảnh camera đảo màu. Không có bitmap (null / sai kích thước) → bỏ qua, chỉ vẽ viền, không crash
        val bitmap = frame.cameraFrame
        if (bitmap != null) {
            if (bitmap !== lastBitmap) {
                lastBitmap = bitmap
                shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val s = shader
            if (s != null) {
                frame.cameraMatrix(matrix) // đúng phép chiếu của px()/py() → ruột khớp tuyệt đối với viền
                s.setLocalMatrix(matrix)
                fillPaint.shader = s
                fillPaint.alpha = alpha
                canvas.drawPath(path, fillPaint)
            }
        }

        // 2) viền nét đứt + chấm 4 góc; độ dày/bán kính theo bề rộng canvas (live và video có kích thước khác nhau)
        val w = canvas.width
        if (w != dashWidth) {
            dashWidth = w
            dashEffect = DashPathEffect(floatArrayOf(w * 0.025f, w * 0.015f), 0f)
        }
        stroke.strokeWidth = w * 0.005f
        stroke.pathEffect = dashEffect
        stroke.alpha = alpha
        canvas.drawPath(path, stroke)

        dot.alpha = alpha
        val r = w * 0.011f
        for (i in 0 until 4) canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], r, dot)
    }

    private companion object {
        const val MIN_VISIBLE_P = 0.01f
    }
}
