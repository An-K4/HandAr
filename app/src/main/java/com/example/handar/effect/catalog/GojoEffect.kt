package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.AnchorSource
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.gojo.GojoModel
import com.example.handar.effect.visual.canvas.gojo.GojoVisual

fun gojoEffect(): EffectDefinition = EffectDefinition(
    id = "gojo",
    displayName = "Gojo",
    thumbnailRes = R.drawable.stranger_things_monster,
    requiredNumHands = 2,
    states = listOf(
        EffectState(
            id = "gojo",
            gesture = Gestures.anyHandPointing,
            asset = EffectAsset.Procedural("gojo") { ctx, scope ->
                GojoVisual(ctx, scope.shared("gojo_model") { GojoModel() })
            },
            soundRes = null,
            anchorSource = AnchorSource.IndexFingertip
        )
    )
)
