package com.example.handar

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class MicReader(private val sampleRate: Int = 44_100) {
    private val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    private var audioRecord: AudioRecord? = null
    @Volatile private var isReading = false

    @SuppressLint("MissingPermission")
    fun start(onPcmChunk: (ShortArray, Int) -> Unit) {
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 2
        )
        audioRecord = record
        record.startRecording()
        isReading = true

        Thread {
            val buffer = ShortArray(minBufferSize)
            while (isReading) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) onPcmChunk(buffer, read)
            }
        }.start()
    }

    fun stop() {
        if (!isReading) return
        isReading = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}