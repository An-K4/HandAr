package com.example.handar.effect.visual.canvas.fingerframe

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame

/**
 * TẠM — chỉ để kiểm chứng Mốc 1 của `docs/Finger_Frame_Filter_Plan.md` (bitmap camera tới được visual
 * và `HandFrame.cameraMatrix()` đúng phép chiếu). Vẽ TOÀN BỘ ảnh camera nửa trong suốt đè lên preview:
 * nếu đúng thì ảnh chồng khít lên hình camera bên dưới (live) / khớp nền video (ghi hình).
 * Mốc 2 sẽ thay bằng FingerFrameVisual — xoá file này khi đó.
 */
class CameraFrameDebugVisual : EffectVisual {
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 128 }

    override fun setActive(active: Boolean) = Unit

    override fun draw(canvas: Canvas, frame: HandFrame) {
        val bitmap = frame.cameraFrame ?: return
        frame.cameraMatrix(matrix)
        canvas.drawBitmap(bitmap, matrix, paint)
    }
}
