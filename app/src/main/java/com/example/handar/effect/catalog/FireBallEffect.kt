package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.fireball.FireBallBurstVisual

fun fireBallEffect(): EffectDefinition = EffectDefinition(
    id = "fire_ball",
    displayName = "Cầu lửa",
    thumbnailRes = R.drawable.fire_ball_thumbnail,
    requiredNumHands = 1,
    states = listOf(
        EffectState(
            id = "small",
            gesture = Gestures.singleHandFist,
            asset = EffectAsset.AnimatedGif(R.drawable.fire_ball_small),
            soundRes = R.raw.fire_ball_burn
        ),
        EffectState(
            id = "burst_to_big",
            gesture = Gestures.singleHandPalmOpen,
            asset = EffectAsset.Procedural("fire_ball_burst_to_big") { ctx, _ ->
                FireBallBurstVisual(ctx)
            },
            soundRes = R.raw.fire_ball_burst
        )
    )
)
