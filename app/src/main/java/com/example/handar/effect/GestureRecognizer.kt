package com.example.handar.effect

import com.example.handar.utils.isIndexCurled
import com.example.handar.utils.isIndexExtended
import com.example.handar.utils.isMiddleExtended
import com.example.handar.utils.isPalmOpen
import com.example.handar.utils.isPinkyCurled
import com.example.handar.utils.isPinkyExtended
import com.example.handar.utils.isRingExtended
import com.example.handar.utils.isThumbExtended
import com.example.handar.utils.palmLength
import com.example.handar.utils.pointDistance
import com.example.handar.utils.segmentsCross
import com.example.handar.utils.thumbIndexPinchRatio
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

fun interface GestureRecognizer {
    fun recognize(hands: List<List<NormalizedLandmark>>): Boolean
}

object Gestures {
    // MỘT TAY
    /** ✋ xòe tay */
    val singleHandPalmOpen = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        isPalmOpen(landmark, landmark[0])
    }

    /** ✊ nắm tay */
    val singleHandFist = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        !isPalmOpen(landmark, landmark[0])
    }

    /** ☝️ chỉ tay - giơ ngón trỏ - ký hiệu số 1 */
    val singleHandPointing = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        isIndexExtended(landmark, wrist) && !isMiddleExtended(landmark, wrist) && !isRingExtended(
            landmark,
            wrist
        ) && !isPinkyExtended(landmark, wrist) && !isThumbExtended(landmark, wrist)
    }

    /** ✌️ ký hiệu hi - số 2 */
    val singleHandPeaceSign = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        isIndexExtended(landmark, wrist) && isMiddleExtended(
            landmark,
            wrist
        ) && !isRingExtended(landmark, wrist) && !isPinkyExtended(
            landmark,
            wrist
        ) && !isThumbExtended(landmark, wrist)
    }

    /** ký hiệu số 3: trỏ + giữa + áp út duỗi thẳng, cái và út gập */
    val singleHandThreeFingers = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        isIndexExtended(landmark, wrist) && isMiddleExtended(landmark, wrist) &&
                isRingExtended(landmark, wrist) && !isPinkyExtended(landmark, wrist) &&
                !isThumbExtended(landmark, wrist)
    }

    /** 👍 ký hiệu like - đồng ý - tán thành */
    val singleHandThumbsUp = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        isThumbExtended(landmark, wrist) && !isIndexExtended(landmark, wrist) && !isMiddleExtended(
            landmark,
            wrist
        ) && !isRingExtended(landmark, wrist) && !isPinkyExtended(landmark, wrist)
    }

    /** 🤘 ký hiệu rock on - tuyệt vời - trỏ + út xoè, cái + giữa + áp út gập */
    val singleHandRockOn = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        !isThumbExtended(landmark, wrist) && isIndexExtended(landmark, wrist) && isPinkyExtended(
            landmark,
            wrist
        ) && !isMiddleExtended(landmark, wrist) && !isRingExtended(landmark, wrist)
    }

    /** 🤙 ký hiệu gọi điện (phone, call) */
    val singleHandCall = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        isThumbExtended(landmark, wrist) && !isIndexExtended(landmark, wrist) && !isMiddleExtended(
            landmark,
            wrist
        ) && !isRingExtended(landmark, wrist) && isPinkyExtended(landmark, wrist)
    }

    /** 👌 ký hiệu ok */
    val singleHandOkSign = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        val ratio = thumbIndexPinchRatio(landmark, wrist)
        ratio < 0.35 &&
                isMiddleExtended(landmark, wrist) && isRingExtended(landmark, wrist) && isPinkyExtended(landmark, wrist)
    }

    /** 🤟 ký hiệu "ILY" i love you trong ngôn ngữ ký hiệu */
    val singleHandILoveYou = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        val wrist = landmark[0]
        isThumbExtended(landmark, wrist) && isIndexExtended(landmark, wrist) && isPinkyExtended(
            landmark,
            wrist
        ) &&
                !isMiddleExtended(landmark, wrist) && !isRingExtended(landmark, wrist)
    }

    // HAI TAY
    /** 🤚✋ xòe 2 tay */
    val bothHandsPalmOpen = GestureRecognizer { hands ->
        hands.size >= 2 && hands.all { isPalmOpen(it, it[0]) }
    }

    /** ✊✊ nắm 2 tay */
    val bothHandsFist = GestureRecognizer { hands ->
        hands.size >= 2 && hands.all { !isPalmOpen(it, it[0]) }
    }

    /** 🫶 ký hiệu trái tim 2 tay */
    val twoHandsHeart = GestureRecognizer { hands ->
        if (hands.size < 2) return@GestureRecognizer false
        val handA = hands[0]
        val handB = hands[1]
        val scale = (palmLength(handA, handA[0]) + palmLength(handB, handB[0])) / 2.0
        if (scale == 0.0) return@GestureRecognizer false

        val indexRatio = pointDistance(handA[8], handB[8]) / scale
        val thumbRatio = pointDistance(handA[4], handB[4]) / scale
        val indexTouch = indexRatio < 0.5
        val thumbTouch = thumbRatio < 0.5
        val indexAboveThumb = (handA[8].y() + handB[8].y()) < (handA[4].y() + handB[4].y())

        val indexOrPinkyCurled = (isPinkyCurled(handA) && isPinkyCurled(handB)) || (isIndexCurled(handA) && isIndexCurled(handB))
        indexTouch && thumbTouch && indexAboveThumb && indexOrPinkyCurled
    }

    /** ❌ ký hiệu dấu X do 2 ngón bắt chéo nhau */
    val twoHandsCrossedFingers = GestureRecognizer { hands ->
        if (hands.size < 2) return@GestureRecognizer false
        val handA = hands[0]
        val handB = hands[1]
        val wristA = handA[0]
        val wristB = handB[0]

        fun crossedWith(mcp: Int, tip: Int, straightA: Boolean, straightB: Boolean): Boolean =
            straightA && straightB && segmentsCross(handA[mcp], handA[tip], handB[mcp], handB[tip])

        val indexCross = crossedWith(5, 8, isIndexExtended(handA, wristA), isIndexExtended(handB, wristB))
        val middleCross = crossedWith(9, 12, isMiddleExtended(handA, wristA), isMiddleExtended(handB, wristB))
        val ringCross = crossedWith(13, 16, isRingExtended(handA, wristA), isRingExtended(handB, wristB))
        val pinkyCross = crossedWith(17, 20, isPinkyExtended(handA, wristA), isPinkyExtended(handB, wristB))

        indexCross || middleCross || ringCross || pinkyCross
    }
}