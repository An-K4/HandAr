package com.example.handar.effect.gesture

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.handar.R

// class map tên hiển thị + icon của 1 cử chỉ, dùng cho dialog hướng dẫn cử chỉ ở màn camera
data class GestureDisplay(@StringRes val nameRes: Int, @DrawableRes val iconRes: Int)

val gestureDisplayMap: Map<GestureRecognizer, GestureDisplay> = mapOf(
    Gestures.anyHandPresent to GestureDisplay(R.string.gesture_any_hand, R.drawable.ic_action),
    Gestures.singleHandPalmOpen to GestureDisplay(R.string.gesture_open_palm, R.drawable.ic_action),
    Gestures.singleHandFist to GestureDisplay(R.string.gesture_fist, R.drawable.ic_action),
    Gestures.singleHandPointing to GestureDisplay(R.string.gesture_one_finger, R.drawable.ic_action),
    Gestures.singleHandPeaceSign to GestureDisplay(R.string.gesture_two_finger, R.drawable.ic_action),
    Gestures.singleHandThreeFingers to GestureDisplay(R.string.gesture_three_fingers, R.drawable.ic_action),
    Gestures.singleHandThumbsUp to GestureDisplay(R.string.gesture_thumbs_up, R.drawable.ic_action),
    Gestures.singleHandRockOn to GestureDisplay(R.string.gesture_rock_on, R.drawable.ic_action),
    Gestures.singleHandCall to GestureDisplay(R.string.gesture_call, R.drawable.ic_action),
    Gestures.singleHandOkSign to GestureDisplay(R.string.gesture_ok_sign, R.drawable.ic_action),
    Gestures.singleHandILoveYou to GestureDisplay(R.string.gesture_i_love_you, R.drawable.ic_action),
    Gestures.bothHandsPalmOpen to GestureDisplay(R.string.gesture_both_hands_open, R.drawable.ic_action),
    Gestures.bothHandsFist to GestureDisplay(R.string.gesture_both_hands_fist, R.drawable.ic_action),
    Gestures.twoHandsHeart to GestureDisplay(R.string.gesture_two_hands_heart, R.drawable.ic_action),
    Gestures.twoHandsCrossedFingers to GestureDisplay(R.string.gesture_crossed_fingers, R.drawable.ic_action),
    // Gestures.anyHandPointing: hiện không effect nào dùng, không đưa vào map này.
)

// fallback khi không thấy cử chỉ khớp trong map
private val unknownGestureDisplay = GestureDisplay(R.string.gesture_unknown, R.drawable.ic_action)

fun GestureRecognizer.toDisplay(): GestureDisplay = gestureDisplayMap[this] ?: unknownGestureDisplay
