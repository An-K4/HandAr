package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.AnchorSource
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.model.SizeSource

fun earthEffect(): EffectDefinition = EffectDefinition(
    id = "earth",
    displayName = "Trái đất",
    thumbnailRes = R.drawable.earth,
    requiredNumHands = 1,
    states = listOf(
        EffectState(
            id = "earth",
            gesture = Gestures.anyHandPresent,
            asset = EffectAsset.StaticImage(R.drawable.earth),
            soundRes = null,
            sizeSource = SizeSource.PinchDistance,
            anchorSource = AnchorSource.PinchMidpoint
        )
    )
)
