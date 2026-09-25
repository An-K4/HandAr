package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.lightning.LightningVisual

/**
 * Tia sét: ≥1 ngón (trừ ngón cái) đang duỗi → mỗi ngón đang duỗi có 1 tia sét riêng ở đầu ngón,
 * xoay theo hướng ngón đó (LightningVisual duyệt qua từng ngón, không có khái niệm "state cho mỗi
 * ngón" vì số ngón/tổ hợp ngón là biến thiên tự do, khác các cử chỉ số cố định khác).
 */
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
