package com.example.handar.effect.model

sealed class EffectBackground {
    data class Solid(val colorRes: Int) : EffectBackground()
    data class Image(val resId: Int) : EffectBackground()
    data class Animated(val resId: Int) : EffectBackground()
}