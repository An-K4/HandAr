package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBackground
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.model.StateMode

fun testEffectBackgroundEffect(): EffectDefinition = EffectDefinition(
    id = "test_effect_background",
    displayName = "Thay đổi nền",
    thumbnailRes = R.color.black,
    requiredNumHands = 1,
    stateMode = StateMode.Latched,
    states = listOf(
        EffectState(
            id = "happy_cat",
            gesture = Gestures.singleHandPointing,
            asset = EffectAsset.StaticImage(R.drawable.stranger_things_monster),
            soundRes = R.raw.happy_happy_happy_cat,
            background = EffectBackground.Animated(R.drawable.happy_happy_happy_cat)
        ),
        EffectState(
            id = "clock",
            gesture = Gestures.singleHandPeaceSign,
            asset = EffectAsset.StaticImage(R.drawable.stranger_things_monster),
            soundRes = R.raw.stranger_things_clock,
            background = EffectBackground.Animated(R.drawable.stranger_things_clock)
        ),
        EffectState(
            id = "egg",
            gesture = Gestures.singleHandThreeFingers,
            asset = EffectAsset.StaticImage(R.drawable.stranger_things_monster),
            soundRes = R.raw.egg_cracked,
            background = EffectBackground.Image(R.drawable.egg_cracked)
        ),
        EffectState(
            id = "absolute_cinema",
            gesture = Gestures.singleHandFist,
            asset = EffectAsset.StaticImage(R.drawable.stranger_things_monster),
            soundRes = R.raw.absolute_cinema,
            background = EffectBackground.Image(R.drawable.absolute_cinema)
        )
    ),
    background = EffectBackground.Solid(R.color.black)
)
