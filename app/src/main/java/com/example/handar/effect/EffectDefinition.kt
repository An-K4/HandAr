package com.example.handar.effect

data class EffectDefinition(
    val id: String,
    val displayName: String,
    val thumbnailRes: Int,
    val requiredNumHands: Int,
    val states: List<EffectState>,
    val background: EffectBackground? = null,
    val bgm: EffectBgm? = null
)
