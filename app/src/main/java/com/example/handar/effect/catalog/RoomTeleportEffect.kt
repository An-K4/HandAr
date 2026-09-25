package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBackground
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.model.StateMode

/**
 * Dịch chuyển tức thời giữa các phòng: mỗi cử chỉ (1 / 2 / 3 ngón / nắm tay) chuyển sang 1 phòng khác
 * (nền + tiếng riêng), nhân vật hoạt hình bám theo tay và dùng chung cho cả 4 phòng.
 * StateMode.Latched: mất tay thì vẫn giữ nền + tiếng của phòng vừa chọn.
 */
fun roomTeleportEffect(): EffectDefinition = EffectDefinition(
    id = "room_teleport",
    displayName = "Dịch chuyển giữa các phòng",
    thumbnailRes = R.drawable.room_teleport_thumbnail,
    requiredNumHands = 1,
    stateMode = StateMode.Latched,
    states = listOf(
        EffectState(
            id = "living_room",
            gesture = Gestures.singleHandPointing,
            asset = EffectAsset.StaticImage(R.drawable.room_teleport_character),
            soundRes = R.raw.room_teleport_living_room,
            background = EffectBackground.Image(R.drawable.room_teleport_bg_living_room)
        ),
        EffectState(
            id = "bathroom",
            gesture = Gestures.singleHandPeaceSign,
            asset = EffectAsset.StaticImage(R.drawable.room_teleport_character),
            soundRes = R.raw.room_teleport_bathroom,
            background = EffectBackground.Image(R.drawable.room_teleport_bg_bathroom)
        ),
        EffectState(
            id = "kitchen",
            gesture = Gestures.singleHandThreeFingers,
            asset = EffectAsset.StaticImage(R.drawable.room_teleport_character),
            soundRes = R.raw.room_teleport_kitchen,
            background = EffectBackground.Image(R.drawable.room_teleport_bg_kitchen)
        ),
        EffectState(
            id = "bedroom",
            gesture = Gestures.singleHandFist,
            asset = EffectAsset.StaticImage(R.drawable.room_teleport_character),
            soundRes = R.raw.room_teleport_bedroom,
            background = EffectBackground.Image(R.drawable.room_teleport_bg_bedroom)
        )
    ),
    background = EffectBackground.Solid(R.color.black)
)
