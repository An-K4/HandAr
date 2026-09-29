package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBgm
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.magicshield.ShieldHideVisual

/**
 * Hệ số nhân lên frame.r (mặc định = khoảng cách cổ tay→khớp ngón giữa). Mục tiêu: khiên PHẢI to hơn bàn tay (bao trọn tay), không chỉ 90% độ dài
 * cổ tay→đầu ngón giữa; đã tăng gấp đôi từ 1.6 lên 3.2 — vẫn chưa đo, chỉnh theo mắt trên máy thật.
 * `shield_show` và `shield_hide` PHẢI dùng cùng hệ số: `ShieldHideVisual` thu nhỏ bắt đầu từ frame.r của state
 * đang khớp, lệch hệ số thì khiên sẽ nhảy cỡ ngay lúc chuyển xòe tay → nắm tay.
 */
private const val MAGIC_SHIELD_SIZE_SCALE = 3.2f

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
            soundRes = null,
            sizeScale = MAGIC_SHIELD_SIZE_SCALE
        ),
        EffectState(
            id = "shield_hide",
            gesture = Gestures.singleHandFist,
            asset = EffectAsset.Procedural("magic_shield_hide") { ctx, _ ->
                ShieldHideVisual(ctx, R.drawable.magic_shield)
            },
            soundRes = null,
            sizeScale = MAGIC_SHIELD_SIZE_SCALE
        )
    ),
    bgm = EffectBgm(R.raw.magic_shield_bgm)
)
