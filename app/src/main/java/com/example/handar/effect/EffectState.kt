package com.example.handar.effect

data class EffectState(
    val id: String,
    val gesture: GestureRecognizer,
    val asset: EffectAsset,
    val soundRes: Int
)
