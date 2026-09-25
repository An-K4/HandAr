package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.AnchorSource
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.dragonball.KamehamehaVisual

/**
 * Chưởng năng lượng Dragon Ball: 1 tay xòe → quả cầu năng lượng nhỏ, bám lòng bàn tay (mặc định
 * PalmCenter/PalmRadius). 2 cổ tay chụm + cả 2 tay xòe → kamehameha to hơn, xoáy nhanh hơn
 * (KamehamehaVisual), neo ở TwoWristMidpoint (không phải TwoHandMidpoint) vì mô tả đúng hành động
 * "chụm cổ tay" hơn.
 *
 * ⚠️ Thứ tự khai `states`: state kamehameha PHẢI đứng TRƯỚC state charge — cả OverlayView lẫn
 * CameraRecordFragment đều chọn state đầu tiên khớp (indexOfFirst/firstOrNull), nên nếu đảo thứ tự,
 * charge (chỉ cần palmOpen 1 tay) sẽ luôn khớp trước và kamehameha (điều kiện chặt hơn — cần đủ 2
 * tay xòe + 2 cổ tay chụm) sẽ không bao giờ được chọn dù điều kiện của nó cũng đang đúng.
 */
fun dragonBallEffect(): EffectDefinition = EffectDefinition(
    id = "dragon_ball",
    displayName = "Chưởng năng lượng Dragon Ball",
    thumbnailRes = R.drawable.dragon_ball_thumbnail,
    requiredNumHands = 2,
    states = listOf(
        EffectState(
            id = "kamehameha",
            gesture = Gestures.twoHandsWristsTogetherOpen,
            asset = EffectAsset.Procedural("dragon_ball_kamehameha") { ctx, _ ->
                KamehamehaVisual(ctx)
            },
            soundRes = null,
            anchorSource = AnchorSource.TwoWristMidpoint
        ),
        EffectState(
            id = "charge",
            gesture = Gestures.singleHandPalmOpen,
            asset = EffectAsset.AnimatedGif(R.drawable.dragon_ball_energy),
            soundRes = R.raw.dragon_ball_charge
        )
    )
)
