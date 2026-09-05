package com.example.handar

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.media.MediaCodec
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.handar.wrapper.AudioEncoderWrapper
import com.example.handar.wrapper.VideoEncoderWrapper
import java.io.File

class VideoRecorder(
    private val context: Context,
    private val width: Int,
    private val height: Int,
    private val fps: Int = 24,
    private val sampleRate: Int = 44_100
) {
    private val videoEncoder = VideoEncoderWrapper(width, height, fps)
    private val audioEncoder = AudioEncoderWrapper(sampleRate)
    private val micReader = MicReader(sampleRate)

    val audioMixer = AudioMixer()

    private var muxer: MuxerCoordinator? = null
    private var totalAudioSamples = 0L
    private var recordStartTimeNs = -1L
    private var hasAudio = false

    var isRecording: Boolean = false
        private set

    private val lock = Any()

    fun start(): File {
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputFile = File(outDir, "hand_ar_record_${System.currentTimeMillis()}.mp4")
        recordStartTimeNs = System.nanoTime()
        hasAudio = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        muxer = MuxerCoordinator(outputFile.absolutePath)
        videoEncoder.prepare()

        if (hasAudio) {
            audioEncoder.prepare()
            totalAudioSamples = 0L
            micReader.start { pcmChunk, len ->
                val mixed = audioMixer.mix(pcmChunk, len)
                val ptsUs = totalAudioSamples * 1_000_000L / sampleRate
                totalAudioSamples += len
                audioEncoder.encodeAndWrite(mixed, len, ptsUs, muxer)
            }
        }

        isRecording = true
        return outputFile
    }

    fun pushFrame(draw: (Canvas) -> Unit) {
        synchronized(lock) {
            if (!isRecording) return
            val surface = videoEncoder.inputSurface
            val canvas = try {
                surface.lockHardwareCanvas()
            } catch (e: Exception) {
                Log.e("VideoRecorder", "lockHardwareCanvas failed: ${e.message}")
                return
            }
            try {
                draw(canvas)
            } finally {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    Log.e("VideoRecorder", "unlockCanvasAndPost failed: ${e.message}")
                }
            }
            drainVideoEncoder()
        }
    }

    private fun drainVideoEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        val codec = videoEncoder.codec
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxer?.addVideoTrack(codec.outputFormat)
                }
                outIndex >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        bufferInfo.presentationTimeUs -= (recordStartTimeNs / 1000L)
                        muxer?.writeVideo(outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
                else -> break
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!isRecording) return
            isRecording = false
        }

        micReader.stop()

        try { videoEncoder.codec.signalEndOfInputStream() } catch (e: Exception) {
            Log.e("VideoRecorder", "signalEndOfInputStream: ${e.message}")
        }
        drainVideoEncoder()

        try { videoEncoder.release() } catch (e: Exception) {
            Log.e("VideoRecorder", "release video error: ${e.message}")
        }
        if (hasAudio) audioEncoder.release()

        muxer?.release()
        muxer = null
    }
}