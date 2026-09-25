package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.lightning.LightningVisual

fun lightningEffect(): EffectDefinition = EffectDefinition(
    id = "lightning",
    displayName = "Tia sét",
    thumbnailRes = R.drawable.lightning_thumbnail,
    requiredNumHands = 1,
    states = listOf(
        EffectState(
            id = "lightning",
            gesture = Gestures.anyFingerExtendedNoThumb,
            asset = EffectAsset.Procedural("lightning") { ctx, _ -> LightningVisual(ctx) },
            soundRes = R.raw.lightning_zap
        )
    )
)
