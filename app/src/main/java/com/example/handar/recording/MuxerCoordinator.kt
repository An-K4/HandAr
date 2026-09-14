package com.example.handar.recording

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer

class MuxerCoordinator(outputPath: String) {
    private val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var videoTrack = -1
    private var audioTrack = -1
    private var started = false
    private val lock = Any()

    val hasStarted: Boolean
        get() = synchronized(lock) { started }

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

    fun writeVideo(buf: ByteBuffer, info: MediaCodec.BufferInfo): Boolean = synchronized(lock) {
        if (!started) return true
        return try {
            muxer.writeSampleData(videoTrack, buf, info)
            true
        } catch (e: Exception) {
            Log.e("MuxerCoordinator", "writeVideo thất bại (đĩa đầy?): ${e.message}")
            false
        }
    }

    fun writeAudio(buf: ByteBuffer, info: MediaCodec.BufferInfo): Boolean = synchronized(lock) {
        if (!started) return true
        return try {
            muxer.writeSampleData(audioTrack, buf, info)
            true
        } catch (e: Exception) {
            Log.e("MuxerCoordinator", "writeAudio thất bại (đĩa đầy?): ${e.message}")
            false
        }
    }

    fun release() = synchronized(lock) {
        if (started) muxer.stop()
        muxer.release()
    }
}