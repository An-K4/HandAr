package com.example.handar.effect

import com.example.handar.utils.isPalmOpen
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

fun interface GestureRecognizer {
    fun recognize(hands: List<List<NormalizedLandmark>>): Boolean
}

object Gestures {
    val singleHandPalmOpen = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        isPalmOpen(landmark, landmark[0])
    }

    val singleHandFist = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        !isPalmOpen(landmark, landmark[0])
    }
}