package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBgm
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.magicshield.ShieldHideVisual

/**
 * Vòng khiên năng lượng: xòe tay → khiên hiện (loop); nắm tay → khiên thu nhỏ dần rồi biến mất
 * (ShieldHideVisual, dựng bằng code, dùng chung asset với state xòe tay).
 * Không có tiếng riêng cho từng state, chỉ có nhạc nền.
 */
fun magicShieldEffect(): EffectDefinition = EffectDefinition(
    id = "magic_shield",
    displayName = "Vòng khiên năng lượng",
    thumbnailRes = R.drawable.magic_shield_thumbnail,
    requiredNumHands = 1,
    states = listOf(
        EffectState(
            id = "shield_show",
            gesture = Gestures.singleHandPalmOpen,
            asset = EffectAsset.AnimatedGif(R.drawable.magic_shield),
            soundRes = null
        ),
        EffectState(
            id = "shield_hide",
            gesture = Gestures.singleHandFist,
            asset = EffectAsset.Procedural("magic_shield_hide") { ctx, _ ->
                ShieldHideVisual(ctx, R.drawable.magic_shield)
            },
            soundRes = null
        )
    ),
    bgm = EffectBgm(R.raw.magic_shield_bgm)
)
