package com.example.handar.effect.catalog

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBackground
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.visual.canvas.drawcanvas.ClearOnActivate
import com.example.handar.effect.visual.canvas.drawcanvas.SkeletonOnlyVisual
import com.example.handar.effect.visual.canvas.drawcanvas.StrokeModel
import com.example.handar.effect.visual.canvas.drawcanvas.StrokeVisual

fun canvasDrawEffect(): EffectDefinition = EffectDefinition(
    id = "canvas_draw",
    displayName = "Vẽ canvas",
    thumbnailRes = R.drawable.stranger_things_monster,
    requiredNumHands = 1,
    states = listOf(
        EffectState(
            id = "stroke",
            gesture = Gestures.singleHandPointing,
            asset = EffectAsset.Procedural("stroke") { _, scope ->
                StrokeVisual(scope.shared("stroke_model") { StrokeModel() })
            },
            soundRes = null
        ),
        EffectState(
            id = "stroke_clear",
            gesture = Gestures.singleHandFist,
            asset = EffectAsset.Procedural("stroke_clear") { _, scope ->
                ClearOnActivate(scope.shared("stroke_model") { StrokeModel() })
            },
            soundRes = R.raw.paper_tear
        ),
        EffectState(
            id = "idle_skeleton",
            gesture = Gestures.anyHandPresent,
            asset = EffectAsset.Procedural("idle_skeleton") { _, _ -> SkeletonOnlyVisual() },
            soundRes = null
        )
    ),
    background = EffectBackground.Solid(R.color.black)
)
