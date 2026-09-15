package com.example.handar.effect

import android.content.Context
import android.graphics.Canvas

interface BackgroundRenderer {
    fun draw(canvas: Canvas)
    fun setActive(active: Boolean) {}
    fun release()
}

fun createBackgroundRenderer(context: Context, background: EffectBackground): BackgroundRenderer =
    when (background) {
        is EffectBackground.Solid -> SolidBackgroundRenderer(context, background.colorRes)
        is EffectBackground.Image -> ImageBackgroundRenderer(context, background.resId)
        is EffectBackground.Animated -> AnimatedBackgroundRenderer(context, background.resId)
    }