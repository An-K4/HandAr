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

    // nét đứt phụ thuộc bề rộng canvas (live ≠ video) → chỉ tạo lại khi bề rộng đổi.
    // `DashPathEffect` bất biến theo `phase`, mà tạo mới mỗi frame thì cấp phát/GC → dựng sẵn DASH_STEPS hiệu ứng lệch phase
    // đều nhau trong 1 chu kỳ (gạch + khoảng trống), mỗi frame chỉ chọn 1 cái theo đồng hồ.
    private var dashWidth = -1
    private val dashEffects = arrayOfNulls<DashPathEffect>(DASH_STEPS)

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

        // snap là toạ độ chuẩn hoá; chiếu sang canvas (đã mirror + center-crop) ngay tại đây.
        // 5b: ở live cộng thêm `lead` (dẫn trước) để bù trễ đường ống; ở video mặc định KHÔNG cộng vì video vẽ đúng bitmap
        // của frame đó (nền và khung cùng nguồn) — dẫn trước sẽ làm viền chạy lố so với ngón tay trong video.
        val useLead = !frame.forRecording || PREDICT_IN_RECORDING
        for (i in 0 until 4) {
            val nx = snap[i * 2] + if (useLead) snap[9 + i * 2] else 0f
            val ny = snap[i * 2 + 1] + if (useLead) snap[9 + i * 2 + 1] else 0f
            pts[i * 2] = frame.px(nx)
            pts[i * 2 + 1] = frame.py(ny)
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
            val dash = w * 0.025f
            val gap = w * 0.015f
            val period = dash + gap
            for (k in 0 until DASH_STEPS) {
                // phase tăng → gạch lùi về đầu path; lấy dấu âm để gạch tiến theo chiều path (đổi DASH_FORWARD nếu muốn ngược lại)
                val phase = if (DASH_FORWARD) -period * k / DASH_STEPS else period * k / DASH_STEPS
                dashEffects[k] = DashPathEffect(floatArrayOf(dash, gap), phase)
            }
        }
        stroke.strokeWidth = w * 0.005f
        // 1 vòng chu kỳ mỗi DASH_PERIOD_MS, đồng hồ riêng (frame.elapsedMs không do visual này ghi); live và video chạy cùng nhịp
        val step = ((SystemClock.uptimeMillis() % DASH_PERIOD_MS) * DASH_STEPS / DASH_PERIOD_MS).toInt()
        stroke.pathEffect = dashEffects[step]
        stroke.alpha = alpha
        canvas.drawPath(path, stroke)

        dot.alpha = alpha
        val r = w * 0.011f
        for (i in 0 until 4) canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], r, dot)
    }

    private companion object {
        const val MIN_VISIBLE_P = 0.01f

        /** số phase dựng sẵn cho nét đứt chạy; càng nhiều càng mượt nhưng tốn bộ nhớ lúc tạo lại khi đổi bề rộng canvas */
        const val DASH_STEPS = 24

        /** thời gian để nét đứt trượt hết 1 chu kỳ (gạch + khoảng trống); nhỏ hơn = chạy nhanh hơn */
        const val DASH_PERIOD_MS = 500L

        /** true = gạch chạy theo chiều đường path (thứ tự góc đã sắp theo góc); false = ngược lại. Chỉnh khi test 5c */
        const val DASH_FORWARD = true

        /** bật = video cũng dẫn trước (mặc định tắt, xem chú thích trong draw()); chỉnh khi test 5b */
        const val PREDICT_IN_RECORDING = false
    }
}
