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
            ),
        )
    )

    fun findById(id: String): EffectDefinition = all.first { it.id == id }
}