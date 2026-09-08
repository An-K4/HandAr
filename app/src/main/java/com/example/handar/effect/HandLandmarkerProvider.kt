package com.example.handar.effect

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object HandLandmarkerProvider {
    private var cached: HandLandmarker? = null
    private var cachedNumHands: Int = -1

    private val _results = MutableSharedFlow<Pair<HandLandmarkerResult, MPImage>>(
        extraBufferCapacity = 1
    )
    val results = _results.asSharedFlow()

    @Synchronized
    fun getOrCreate(context: Context, numHands: Int): HandLandmarker {
        if (cached == null || cachedNumHands != numHands) {
            cached?.close()

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands(numHands)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, inputImage ->
                    _results.tryEmit(result to inputImage)
                }
                .build()

            cached = HandLandmarker.createFromOptions(context.applicationContext, options)
            cachedNumHands = numHands
        }
        return cached!!
    }

    @Synchronized
    fun release() {
        cached?.close()
        cached = null
        cachedNumHands = -1
    }
}