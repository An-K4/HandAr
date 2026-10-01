package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.fingerframe.FingerFrameVisual

/**
 * Khung đảo màu (Finger Frame Filter) — xem docs/Finger_Frame_Filter_Plan.md.
 * Mốc 2: cử chỉ [Gestures.twoHandsFrame] (lỏng, chỉ cần 2 tay) + [FingerFrameVisual] bản thô (chỉ viền).
 * Mốc 3 thêm tracker (làm mượt, mở/khép, fade), Mốc 4 thêm filter đảo màu trong khung.
 */
fun fingerFrameEffect(): EffectDefinition = EffectDefinition(
    id = "finger_frame",
    nameRes = R.string.effect_name_finger_frame,
    thumbnailRes = R.drawable.black_hole_thumbnail, // TẠM: mượn thumbnail Hố đen, thay khi có asset riêng
    requiredNumHands = 2,
    states = listOf(
        EffectState(
            id = "finger_frame_negative",
            gesture = Gestures.twoHandsFrame,
            asset = EffectAsset.Procedural("finger_frame") { _, _ -> FingerFrameVisual() },
            soundRes = null
        )
    )
)
