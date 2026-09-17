package com.example.handar.effect

import com.example.handar.R
import com.example.handar.effect.gesture.Gestures
import com.example.handar.effect.model.AnchorSource
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.model.EffectBackground
import com.example.handar.effect.model.EffectBgm
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.EffectState
import com.example.handar.effect.model.SizeSource
import com.example.handar.effect.model.StateMode

object EffectRepository {
    val all = listOf(
        EffectDefinition(
            id = "cat_meme_1",
            displayName = "Meme mèo 1",
            thumbnailRes = R.drawable.happy_happy_happy_cat,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "happy_happy_happy_cat",
                    gesture = Gestures.singleHandPalmOpen,
                    asset = EffectAsset.AnimatedGif(R.drawable.happy_happy_happy_cat),
                    soundRes = R.raw.happy_happy_happy_cat
                ),
                EffectState(
                    id = "banana_cat_crying",
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
        ),
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
        EffectDefinition(
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
        ),
        EffectDefinition(
            id = "cat_meme_2",
            displayName = "Meme mèo 2",
            thumbnailRes = R.drawable.hello,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "hello_cat",
                    gesture = Gestures.singleHandPeaceSign,
                    asset = EffectAsset.AnimatedGif(R.drawable.hello),
                    soundRes = R.raw.hello
                ),
                EffectState(
                    id = "point_cat",
                    gesture = Gestures.singleHandPointing,
                    asset = EffectAsset.AnimatedGif(R.drawable.you),
                    soundRes = R.raw.you
                ),
                EffectState(
                    id = "call_cat",
                    gesture = Gestures.singleHandCall,
                    asset = EffectAsset.AnimatedGif(R.drawable.call),
                    soundRes = R.raw.call
                )
            )
        ),
        EffectDefinition(
            id = "mood_meter",
            displayName = "Đo tâm trạng",
            thumbnailRes = R.drawable.like_meme_emoji,
            requiredNumHands = 1,
            states = listOf(
                EffectState(
                    id = "like",
                    gesture = Gestures.singleHandThumbsUp,
                    asset = EffectAsset.AnimatedGif(R.drawable.like_meme_emoji),
                    soundRes = R.raw.like_meme_emoji
                ),
                EffectState(
                    id = "dislike",
                    gesture = Gestures.singleHandFist,
                    asset = EffectAsset.AnimatedGif(R.drawable.sad_meme_emoji),
                    soundRes = R.raw.sad_meme_emoji
                ),
                EffectState(
                    id = "neutral",
                    gesture = Gestures.singleHandPalmOpen,
                    asset = EffectAsset.AnimatedGif(R.drawable.neutral_meme_emoji),
                    soundRes = null
                )
            )
        ),
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
        EffectDefinition(
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
        ),
        EffectDefinition(
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
        ),
        EffectDefinition(
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
        ),
        EffectDefinition(
            id = "black_hole",
            displayName = "Hố đen",
            thumbnailRes = R.drawable.black_hole,
            requiredNumHands = 2,
            states = listOf(
                EffectState(
                    id = "black_hole",
                    gesture = Gestures.anyHandPresent,
                    asset = EffectAsset.AnimatedGif(R.drawable.black_hole),
                    soundRes = null,
                    sizeSource = SizeSource.TwoHandDistance,
                    anchorSource = AnchorSource.TwoHandMidpoint
                )
            )
        ),
        EffectDefinition(
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
    )

    fun findById(id: String): EffectDefinition = all.first { it.id == id }
}