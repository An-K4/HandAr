package com.example.handar

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class MuxerCoordinator(outputPath: String) {
    private val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var videoTrack = -1
    private var audioTrack = -1
    private var started = false
    private val lock = Any()

    fun addVideoTrack(format: MediaFormat) = synchronized(lock) {
        videoTrack = muxer.addTrack(format)
        maybeStart()
    }

    fun addAudioTrack(format: MediaFormat) = synchronized(lock) {
        audioTrack = muxer.addTrack(format)
        maybeStart()
    }

    private fun maybeStart() {
        if (videoTrack >= 0 && audioTrack >= 0 && !started) {
            muxer.start()
            started = true
        }
    }

    fun writeVideo(buf: ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(lock) {
        if (started) muxer.writeSampleData(videoTrack, buf, info)
    }

    fun writeAudio(buf: ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(lock) {
        if (started) muxer.writeSampleData(audioTrack, buf, info)
    }

    fun release() = synchronized(lock) {
        if (started) muxer.stop()
        muxer.release()
    }
}