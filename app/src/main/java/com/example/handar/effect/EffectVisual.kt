package com.example.handar.effect

import android.content.Context
import android.graphics.Canvas

interface EffectVisual {
    fun setActive(active: Boolean)
    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float)
}

fun createEffectVisual(context: Context, asset: EffectAsset): EffectVisual = when(asset) {
    is EffectAsset.StaticImage -> StaticImageVisual(context, asset.resId)
    is EffectAsset.AnimatedGif -> AnimatedGifVisual(context, asset.resId)
    is EffectAsset.SpriteSheet -> TODO("Chưa làm")
}