package com.example.handar.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.handar.R

class CurvedNavBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** bán kính bo góc trên-trái/trên-phải của thanh nav. */
    var cornerRadiusDp: Float = 28f
        set(value) { field = value; rebuildAndInvalidate() }

    /** nửa bề rộng của chỗ lõm. */
    var notchRadiusDp: Float = 69f
        set(value) { field = value; rebuildAndInvalidate() }

    /** độ sâu chỗ lõm. */
    var notchDepthDp: Float = 24f
        set(value) { field = value; rebuildAndInvalidate() }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nav_bg)
        style = Paint.Style.FILL
    }

    private val path = Path()

    private val Float.px: Float get() = this * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPath(w.toFloat(), h.toFloat())
    }

    private fun rebuildAndInvalidate() {
        if (width > 0 && height > 0) rebuildPath(width.toFloat(), height.toFloat())
        invalidate()
    }

    private fun rebuildPath(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        val r = cornerRadiusDp.px.coerceAtMost(h)
        val nr = notchRadiusDp.px
        val nd = notchDepthDp.px
        val centerX = w / 2f

        path.reset()

        // bắt đầu ở mép trái, dưới chỗ bo góc
        path.moveTo(0f, r)
        // bo góc trên-trái (góc phần tư từ 180° -> 270°, tức từ "trái" quét lên "trên")
        path.arcTo(0f, 0f, 2 * r, 2 * r, 180f, 90f, false)

        // cạnh trên bên trái, chạy tới mép trái của chỗ lõm
        path.lineTo(centerX - nr, 0f)

        // lõm xuống rồi vòng lên lại thành hình chữ u (2 cubic bezier đối xứng)
        path.cubicTo(
            centerX - nr * 0.55f, 0f,
            centerX - nr * 0.55f, nd,
            centerX, nd
        )
        path.cubicTo(
            centerX + nr * 0.55f, nd,
            centerX + nr * 0.55f, 0f,
            centerX + nr, 0f
        )

        // cạnh trên bên phải, tới góc bo phải
        path.lineTo(w - r, 0f)
        path.arcTo(w - 2 * r, 0f, w, 2 * r, 270f, 90f, false)

        // xuống đáy, sang trái, đóng path
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, backgroundPaint)
    }
}
