package com.example.handar.effect

sealed class EffectBackground {
    data class Solid(val colorRes: Int) : EffectBackground()
    data class Image(val resId: Int) : EffectBackground()
    data class Animated(val resId: Int) : EffectBackground()
}