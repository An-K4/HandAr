package com.example.handar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundEffectPlayer(context: Context, soundResList: List<Int>) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds: Map<Int, Int> = soundResList.associateWith { resId ->
        soundPool.load(context, resId, 1)
    }
    private var isReady = false
    private var currentStreamId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) isReady = true
        }
    }

    fun playForSound(soundRes: Int) {
        if (!isReady) return
        val soundId = soundIds[soundRes] ?: return
        currentStreamId = soundPool.play(soundId, 1f, 1f, 1, -1, 1f)
    }

    fun stopEffect() {
        if (currentStreamId != 0) {
            soundPool.stop(currentStreamId)
            currentStreamId = 0
        }
    }

    fun release() {
        soundPool.release()
    }
}