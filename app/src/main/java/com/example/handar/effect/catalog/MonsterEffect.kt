package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBackground
import com.example.handar.effect.model.EffectBgm
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.image.OneShotGifVisual

fun monsterEffect(): EffectDefinition = EffectDefinition(
    id = "monster",
    displayName = "Quái vật",
    thumbnailRes = R.drawable.monster_thumbnail,
    requiredNumHands = 1,
    states = listOf(
        EffectState(
            id = "monster",
            gesture = Gestures.singleHandPalmOpen,
            asset = EffectAsset.StaticImage(R.drawable.monster_appear),
            soundRes = null
        ),
        EffectState(
            id = "monster_disappear",
            gesture = Gestures.singleHandFist,
            asset = EffectAsset.Procedural("monster_wave") { ctx, _ ->
                OneShotGifVisual(ctx, R.drawable.monster_wave)
            },
            soundRes = null
        )
    ),
    background = EffectBackground.Image(R.drawable.monster_bg),
    bgm = EffectBgm(R.raw.monster_bgm)
)
