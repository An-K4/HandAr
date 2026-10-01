package com.example.handar.effect.model

import androidx.annotation.StringRes

data class EffectDefinition(
    val id: String,
    @StringRes val nameRes: Int,
    val thumbnailRes: Int,
    val requiredNumHands: Int,
    val states: List<EffectState>,
    val background: EffectBackground? = null,
    val bgm: EffectBgm? = null,
    val stateMode: StateMode = StateMode.Momentary
) {
    init {
        require(states.none { it.background != null } || background != null) {
            "Effect có chứa state mang background riêng thì phải khai báo background mặc định: $id"
        }
    }
}
