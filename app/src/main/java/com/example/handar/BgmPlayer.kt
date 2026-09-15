package com.example.handar

import android.content.Context
import android.media.MediaPlayer

class BgmPlayer(context: Context, resId: Int, gainPercent: Int) {
    private var player: MediaPlayer? = MediaPlayer.create(context, resId)?.apply {
        isLooping = true
        val v = gainPercent / 100f
        setVolume(v, v)
    }

    fun startFromBeginning() {
        player?.seekTo(0)
        player?.start()
    }

    fun pause() {
        player?.pause()
    }

    fun release() {
        player?.release()
        player = null
    }
}