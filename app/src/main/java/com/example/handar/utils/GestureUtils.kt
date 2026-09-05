package com.example.handar.utils

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

fun isPalmOpen(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark): Boolean {
    return listOf(Pair(8, 6), Pair(12, 10), Pair(16, 14), Pair(20, 18)).count { (tip, pip) ->
        hypot((landmark[tip].x() - wrist.x()).toDouble(), (landmark[tip].y() - wrist.y()).toDouble()) >
                hypot((landmark[pip].x() - wrist.x()).toDouble(), (landmark[pip].y() - wrist.y()).toDouble())
    } >= 3
}