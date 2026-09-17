package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState

fun cameraShutterEffect(): EffectDefinition = EffectDefinition(
    id = "camera_shutter",
    displayName = "Chụp ảnh",
    thumbnailRes = R.drawable.camera_shutter,
    requiredNumHands = 2,
    states = listOf(
        EffectState(
            id = "shutter_ok",
            gesture = Gestures.singleHandOkSign,
            asset = EffectAsset.AnimatedGif(R.drawable.camera_shutter),
            soundRes = R.raw.camera_shutter
        ),
        EffectState(
            id = "shutter_hi",
            gesture = Gestures.singleHandPeaceSign,
            asset = EffectAsset.AnimatedGif(R.drawable.camera_shutter),
            soundRes = R.raw.camera_shutter
        ),
        EffectState(
            id = "shutter_like",
            gesture = Gestures.singleHandThumbsUp,
            asset = EffectAsset.AnimatedGif(R.drawable.camera_shutter),
            soundRes = R.raw.camera_shutter
        ),
        EffectState(
            id = "shutter_rock_on",
            gesture = Gestures.singleHandRockOn,
            asset = EffectAsset.AnimatedGif(R.drawable.camera_shutter),
            soundRes = R.raw.camera_shutter
        ),
        EffectState(
            id = "shutter_call",
            gesture = Gestures.singleHandCall,
            asset = EffectAsset.AnimatedGif(R.drawable.camera_shutter),
            soundRes = R.raw.camera_shutter
        )
    )
)
