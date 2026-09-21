package com.example.handar.effect

import com.example.handar.R
import com.example.handar.effect.catalog.blackHoleEffect
import com.example.handar.effect.catalog.cameraShutterEffect
import com.example.handar.effect.catalog.canvasDrawEffect
import com.example.handar.effect.catalog.earthEffect
import com.example.handar.effect.catalog.gojoEffect
import com.example.handar.effect.catalog.testEffectBackgroundEffect
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBackground
import com.example.handar.effect.model.EffectBgm
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState

object EffectRepository {
    val all = listOf(
        EffectDefinition(
            id = "black_background_with_monster",
            displayName = "Quái vật bóng đêm với tiếng đồng hồ kêu",
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
                    soundRes = null
                )
            ),
            background = EffectBackground.Animated(R.drawable.happy_happy_happy_cat),
            bgm = EffectBgm(R.raw.stranger_things_clock)
        ),
        EffectDefinition(
            id = "rock_on_ily",
            displayName = "Rock on / I love you",
            thumbnailRes = R.drawable.rock_on,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "rock_on",
                    gesture = Gestures.singleHandRockOn,
                    asset = EffectAsset.AnimatedGif(R.drawable.rock_on),
                    soundRes = R.raw.rock_on
                ),
                EffectState(
                    id = "ily",
                    gesture = Gestures.singleHandILoveYou,
                    asset = EffectAsset.AnimatedGif(R.drawable.i_love_you),
                    soundRes = R.raw.i_love_you
                )
            )
        ),
        cameraShutterEffect(),
        EffectDefinition(
            id = "absolute_cinema_two_hand",
            displayName = "Absolute cinema",
            thumbnailRes = R.drawable.absolute_cinema,
            requiredNumHands = 2,
            states = listOf(
                EffectState(
                    id = "absolute_cinema",
                    gesture = Gestures.bothHandsPalmOpen,
                    asset = EffectAsset.StaticImage(R.drawable.absolute_cinema),
                    soundRes = R.raw.absolute_cinema
                ),
                EffectState(
                    id = "absolute_garbage",
                    gesture = Gestures.bothHandsFist,
                    asset = EffectAsset.StaticImage(R.drawable.absolute_garbage),
                    soundRes = R.raw.absolute_garbage
                )
            )
        ),
        EffectDefinition(
            id = "heart_or_cross",
            displayName = "Trái tim và dấu X",
            thumbnailRes = R.drawable.heart,
            requiredNumHands = 2,
            states = listOf(
                EffectState(
                    id = "heart",
                    gesture = Gestures.twoHandsHeart,
                    asset = EffectAsset.StaticImage(R.drawable.heart),
                    soundRes = null
                ),
                EffectState(
                    id = "cross",
                    gesture = Gestures.twoHandsCrossedFingers,
                    asset = EffectAsset.StaticImage(R.drawable.cross),
                    soundRes = null
                )
            )
        ),
        testEffectBackgroundEffect(),
        canvasDrawEffect(),
        earthEffect(),
        blackHoleEffect(),
        gojoEffect()
    )

    fun findById(id: String): EffectDefinition = all.first { it.id == id }

    fun findByName(query: String): List<EffectDefinition> =
        if (query.isBlank()) all else all.filter { it.displayName.contains(query, ignoreCase = true) }
}
