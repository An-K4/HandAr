package com.example.handar.effect.visual.canvas.fireball

import android.content.Context
import android.graphics.Canvas
import com.example.handar.R
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame
import com.example.handar.effect.visual.image.AnimatedGifVisual

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
