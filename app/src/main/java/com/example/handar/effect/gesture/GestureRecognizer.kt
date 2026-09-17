package com.example.handar.effect.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

fun interface GestureRecognizer {
    fun recognize(hands: List<List<NormalizedLandmark>>): Boolean
}