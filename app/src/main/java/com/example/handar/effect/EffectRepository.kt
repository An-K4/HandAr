package com.example.handar.effect

import com.example.handar.R

object EffectRepository {
    val all = listOf(
        EffectDefinition(
            id = "happy_cat",
            displayName = "Mèo vui / Chuối khóc",
            thumbnailRes = R.drawable.happy_happy_happy_cat,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "open",
                    gesture = Gestures.singleHandPalmOpen,
                    asset = EffectAsset.AnimatedGif(R.drawable.happy_happy_happy_cat),
                    soundRes = R.raw.happy_happy_happy_cat
                ),
                EffectState(
                    id = "closed",
                    gesture = Gestures.singleHandFist,
                    asset = EffectAsset.AnimatedGif(R.drawable.banana_cat_crying),
                    soundRes = R.raw.banana_cat_crying
                )
            )
        ),
        EffectDefinition(
            id = "egg",
            displayName = "Trứng",
            thumbnailRes = R.drawable.egg,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "egg",
                    gesture = Gestures.singleHandPalmOpen,
                    asset = EffectAsset.StaticImage(R.drawable.egg),
                    soundRes = null
                ),
                EffectState(
                    id = "egg_cracked",
                    gesture = Gestures.singleHandFist,
                    asset = EffectAsset.StaticImage(R.drawable.egg_cracked),
                    soundRes = R.raw.egg_cracked
                )
            )
        ),
        EffectDefinition(
            id = "weather",
            displayName = "Thời tiết",
            thumbnailRes = R.drawable.sunny,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "sunny",
                    gesture = Gestures.singleHandPalmOpen,
                    asset = EffectAsset.AnimatedGif(R.drawable.sunny),
                    soundRes = R.raw.sunny
                ),
                EffectState(
                    id = "lightning",
                    gesture = Gestures.singleHandFist,
                    asset = EffectAsset.AnimatedGif(R.drawable.lightning),
                    soundRes = R.raw.lightning
                )
            )
        ),
        EffectDefinition(
            id = "stranger_things",
            displayName = "Stranger things",
            thumbnailRes = R.drawable.stranger_things_monster,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "monster",
                    gesture = Gestures.singleHandPalmOpen,
                    asset = EffectAsset.StaticImage(R.drawable.stranger_things_monster),
                    soundRes = R.raw.stranger_things_monster
                ),
                EffectState(
                    id = "monster_disappear",
                    gesture = Gestures.singleHandFist,
                    asset = EffectAsset.AnimatedGif(R.drawable.stranger_things_clock),
                    soundRes = R.raw.stranger_things_clock
                )
            )
        )
    )

    fun findById(id: String): EffectDefinition = all.first { it.id == id }
}