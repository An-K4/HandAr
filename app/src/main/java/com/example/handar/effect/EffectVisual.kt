package com.example.handar.effect

import android.content.Context
import android.graphics.Canvas
import com.example.handar.effect.model.EffectAsset

interface EffectVisual {
    fun setActive(active: Boolean)
    fun draw(canvas: Canvas, frame: HandFrame)
    fun onHandFrame(frame: HandFrame) = Unit
}

fun createEffectVisual(context: Context, asset: EffectAsset, scope: EffectScope): EffectVisual =
    when (asset) {
        is EffectAsset.StaticImage -> StaticImageVisual(context, asset.resId)
        is EffectAsset.AnimatedGif -> AnimatedGifVisual(context, asset)
        is EffectAsset.SpriteSheet -> SpriteSheetVisual(context, asset)
        is EffectAsset.Procedural -> asset.create(context, scope)
    }
