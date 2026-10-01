package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.fingerframe.CameraFrameDebugVisual

/**
 * Khung đảo màu (Finger Frame Filter) — xem docs/Finger_Frame_Filter_Plan.md.
 * Mốc 1: TẠM dùng [CameraFrameDebugVisual] + cử chỉ [Gestures.anyHandPresent] chỉ để kiểm chứng đường truyền bitmap camera;
 * Mốc 2 đổi sang cử chỉ twoHandsFrame + FingerFrameVisual.
 */
fun fingerFrameEffect(): EffectDefinition = EffectDefinition(
    id = "finger_frame",
    nameRes = R.string.effect_name_finger_frame,
    thumbnailRes = R.drawable.black_hole_thumbnail, // TẠM: mượn thumbnail Hố đen, thay khi có asset riêng
    requiredNumHands = 2,
    states = listOf(
        EffectState(
            id = "finger_frame_negative",
            gesture = Gestures.anyHandPresent,
            asset = EffectAsset.Procedural("finger_frame_debug") { _, _ -> CameraFrameDebugVisual() },
            soundRes = null
        )
    )
)
