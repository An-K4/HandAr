package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.lightning.LightningVisual

fun lightningEffect(): EffectDefinition = EffectDefinition(
    id = "lightning",
    nameRes = R.string.effect_name_lightning,
    thumbnailRes = R.drawable.lightning_thumbnail,
    // 2 tay: mỗi ngón duỗi của MỖI tay đều có tia sét riêng (xem LightningVisual.draw).
    requiredNumHands = 2,
    states = listOf(
        EffectState(
            id = "lightning",
            gesture = Gestures.anyFingerExtended,
            asset = EffectAsset.Procedural("lightning") { ctx, _ -> LightningVisual(ctx) },
            soundRes = R.raw.lightning_zap
        )
    )
)
