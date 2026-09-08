package com.example.handar.effect

sealed class EffectAsset {
    data class StaticImage(val resId: Int): EffectAsset()
    data class AnimatedGif(val resId: Int): EffectAsset()
    data class SpriteSheet(
        val resId: Int,
        val columns: Int,
        val rows: Int,
        val frameCount: Int,
        val frameDurationMs: Int
    ): EffectAsset()
}