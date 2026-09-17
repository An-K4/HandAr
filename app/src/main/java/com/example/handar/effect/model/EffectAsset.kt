package com.example.handar.effect.model

import android.content.Context
import com.example.handar.effect.EffectScope
import com.example.handar.effect.EffectVisual

sealed class EffectAsset {
    data class StaticImage(val resId: Int) : EffectAsset()
    data class AnimatedGif(val resId: Int, val oneShot: Boolean = false) : EffectAsset()
    data class SpriteSheet(
        val resId: Int,
        val columns: Int,
        val rows: Int,
        val frameCount: Int,
        val frameDurationMs: Int
    ) : EffectAsset()

    class Procedural(
        val id: String,
        val create: (Context, EffectScope) -> EffectVisual
    ) : EffectAsset()
}
