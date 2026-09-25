package com.example.handar.effect.visual.canvas.fireball

import android.content.Context
import android.graphics.Canvas
import com.example.handar.R
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame
import com.example.handar.effect.visual.image.AnimatedGifVisual

/**
 * State "xòe tay" của Cầu lửa: phát `fire_ball_burst` (chạy 1 lần, lửa nhỏ bùng lên to) rồi CHUYỂN
 * SANG `fire_ball_big` (lặp vô hạn) và giữ nguyên cho tới khi state tắt.
 *
 * Composition theo đúng mẫu GojoVisual (mục 3.7 Code_Walkthrough.md): chứa 2 AnimatedGifVisual con,
 * tự quyết định con nào đang được vẽ dựa vào `burst.hasFinishedPlaying()`. `wasActive` chỉ dùng để
 * phát hiện cạnh lên (bắt đầu xòe tay) — mỗi lần xòe lại là 1 lượt "bùng lên" mới, không phát burst
 * lặp lại liên tục trong lúc tay vẫn đang xòe (setActive(true) bị OverlayView gọi lại mỗi frame).
 */
class FireBallBurstVisual(context: Context) : EffectVisual {
    private val burst =
        AnimatedGifVisual(context, EffectAsset.AnimatedGif(R.drawable.fire_ball_burst, oneShot = true))
    private val big =
        AnimatedGifVisual(context, EffectAsset.AnimatedGif(R.drawable.fire_ball_big, oneShot = false))

    private var wasActive = false

    override fun setActive(active: Boolean) {
        if (active && !wasActive) {
            burst.setActive(true)
        }
        if (!active) {
            burst.setActive(false)
            big.setActive(false)
        }
        wasActive = active
    }

    override fun draw(canvas: Canvas, frame: HandFrame) {
        if (!burst.hasFinishedPlaying()) {
            burst.draw(canvas, frame)
        } else {
            big.setActive(true) // idempotent — AnimatedGifVisual chỉ start() nếu chưa isRunning
            big.draw(canvas, frame)
        }
    }
}
