package com.example.handar.effect.visual.canvas.magicshield

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame
import com.example.handar.effect.visual.image.AnimatedGifVisual

class ShieldHideVisual(context: Context, resId: Int) : EffectVisual {
    private val gif = AnimatedGifVisual(context, EffectAsset.AnimatedGif(resId, oneShot = false))

    @Volatile
    private var activatedAtMs = 0L

    override fun setActive(active: Boolean) {
        gif.setActive(active)
        activatedAtMs = when {
            !active -> 0L
            activatedAtMs == 0L -> SystemClock.elapsedRealtime()
            else -> activatedAtMs
        }
    }

    override fun draw(canvas: Canvas, frame: HandFrame) {
        val startedAt = activatedAtMs
        if (startedAt == 0L) return

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val scale = 1f - (elapsed.toFloat() / HIDE_DURATION_MS).coerceIn(0f, 1f)
        if (scale <= 0f) return // thu hết cỡ — không vẽ gì nữa, "biến mất"

        // mượn tạm frame.r để AnimatedGifVisual tự tính kích thước theo tỉ lệ đã thu nhỏ, xong trả
        // lại giá trị gốc — frame là instance dùng chung giữa nhiều lần vẽ, không được giữ thay đổi.
        val originalR = frame.r
        frame.r = originalR * scale
        gif.draw(canvas, frame)
        frame.r = originalR
    }

    companion object {
        private const val HIDE_DURATION_MS = 500
    }
}
