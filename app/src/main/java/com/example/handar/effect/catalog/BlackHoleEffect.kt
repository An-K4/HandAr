package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.AnchorSource
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBackground
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.model.SizeSource

fun blackHoleEffect(): EffectDefinition = EffectDefinition(
    id = "black_hole",
    displayName = "Hố đen",
    thumbnailRes = R.drawable.black_hole_thumbnail,
    requiredNumHands = 2,
    states = listOf(
        EffectState(
            id = "black_hole",
            gesture = Gestures.bothHandsPalmOpen,
            asset = EffectAsset.AnimatedGif(R.drawable.black_hole_portal),
            soundRes = R.raw.black_hole_loop,
            sizeSource = SizeSource.TwoHandDistance,
            anchorSource = AnchorSource.TwoHandMidpoint
        )
    ),
    background = EffectBackground.Image(R.drawable.black_hole_bg)
)
