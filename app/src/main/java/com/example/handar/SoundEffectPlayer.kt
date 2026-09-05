package com.example.handar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundEffectPlayer(context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var happy3SoundId = 0
    private var bananaCryingSoundId = 0
    private var isReady = false
    private var currentStreamId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) isReady = true
        }
        happy3SoundId = soundPool.load(context, R.raw.happy_happy_happy_cat, 1)
        bananaCryingSoundId = soundPool.load(context, R.raw.banana_cat_crying, 1)
    }

    fun playForGesture(open: Boolean) {
        if (!isReady) return
        val soundId = if (open) happy3SoundId else bananaCryingSoundId
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