package com.example.handar.effect.gesture

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.handar.R

// class map tên hiển thị + icon của 1 cử chỉ, dùng cho dialog hướng dẫn cử chỉ ở màn camera
data class GestureDisplay(@StringRes val nameRes: Int, @DrawableRes val iconRes: Int)

val gestureDisplayMap: Map<GestureRecognizer, GestureDisplay> = mapOf(
    Gestures.anyFingerExtended to GestureDisplay(R.string.gesture_finger_extended, R.drawable.ic_action_index_point),
    Gestures.anyHandPresent to GestureDisplay(R.string.gesture_any_hand, R.drawable.ic_action_open),
    Gestures.pinchTracking to GestureDisplay(R.string.gesture_pinch, R.drawable.ic_action_pinch),
    Gestures.anyHandPointing to GestureDisplay(R.string.gesture_pointing, R.drawable.ic_action_index_point),
    Gestures.singleHandPalmOpen to GestureDisplay(R.string.gesture_open_palm, R.drawable.ic_action_open),
    Gestures.singleHandFist to GestureDisplay(R.string.gesture_fist, R.drawable.ic_action_fist),
    Gestures.singleHandPointing to GestureDisplay(R.string.gesture_one_finger, R.drawable.ic_action_index_point),
    Gestures.singleHandPeaceSign to GestureDisplay(R.string.gesture_two_finger, R.drawable.ic_action_peace_sign),
    Gestures.singleHandThreeFingers to GestureDisplay(R.string.gesture_three_fingers, R.drawable.ic_action_three),
    Gestures.singleHandThumbsUp to GestureDisplay(R.string.gesture_thumbs_up, R.drawable.ic_action_like),
    Gestures.singleHandRockOn to GestureDisplay(R.string.gesture_rock_on, R.drawable.ic_action_rockon),
    Gestures.singleHandCall to GestureDisplay(R.string.gesture_call, R.drawable.ic_action_call),
    Gestures.singleHandOkSign to GestureDisplay(R.string.gesture_ok_sign, R.drawable.ic_action_ok),
    Gestures.singleHandILoveYou to GestureDisplay(R.string.gesture_i_love_you, R.drawable.ic_action_iloveyou),
    Gestures.bothHandsPalmOpen to GestureDisplay(R.string.gesture_both_hands_open, R.drawable.ic_action_open_2hands),
    Gestures.bothHandsFist to GestureDisplay(R.string.gesture_both_hands_fist, R.drawable.ic_action_fist_2hands),
    Gestures.twoHandsHeart to GestureDisplay(R.string.gesture_two_hands_heart, R.drawable.ic_action_heart_2hands),
    Gestures.twoHandsCrossedFingers to GestureDisplay(R.string.gesture_crossed_fingers, R.drawable.ic_action_cross_2hands),
    Gestures.twoHandsWristsTogetherOpen to GestureDisplay(R.string.gesture_wrists_together, R.drawable.ic_action_wrist_touch_2hands),
    Gestures.twoHandsFrame to GestureDisplay(R.string.gesture_two_hands_frame, R.drawable.ic_action_frame_2hands)
)

// fallback khi không thấy cử chỉ khớp trong map
private val unknownGestureDisplay = GestureDisplay(R.string.gesture_unknown, R.drawable.ic_action_open)

fun GestureRecognizer.toDisplay(): GestureDisplay = gestureDisplayMap[this] ?: unknownGestureDisplay
