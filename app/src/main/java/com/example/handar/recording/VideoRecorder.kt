package com.example.handar.recording

import android.content.Context
import android.graphics.Canvas
import android.media.MediaCodec
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val effectClock = EffectAudioClock(sampleRate)

    val audioMixer = AudioMixer()

    private var muxer: MuxerCoordinator? = null
    private var totalAudioSamples = 0L
    private var recordStartTimeNs = -1L

    var onFirstFrame: (() -> Unit)? = null
    var isRecording: Boolean = false
        private set
    var outputFile: File? = null
        private set

    private val lock = Any()

    @Volatile
    private var lastStopHadValidOutput = false
    fun hadValidOutput(): Boolean = lastStopHadValidOutput

    @Volatile
    var writeFailed: Boolean = false
        private set

    fun start(): File {
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputFile = File(outDir, "hand_ar_record_${System.currentTimeMillis()}.mp4")
        this.outputFile = outputFile

        muxer = MuxerCoordinator(outputFile.absolutePath)
        videoEncoder.prepare()
        audioEncoder.prepare()
        totalAudioSamples = 0L

        isRecording = true
        return outputFile
    }

    fun pushFrame(draw: (Canvas) -> Unit) {
        synchronized(lock) {
            if (!isRecording) return

            if (recordStartTimeNs < 0) {
                recordStartTimeNs = System.nanoTime()
                effectClock.start { pcmChunk, len ->
                    val mixed = audioMixer.mix(pcmChunk, len)
                    val ptsUs = totalAudioSamples * 1_000_000 / sampleRate
                    totalAudioSamples += len
                    if (!audioEncoder.encodeAndWrite(mixed, len, ptsUs, muxer)) {
                        writeFailed = true
                    }
                }
                onFirstFrame?.let { callback ->
                    Handler.createAsync(Looper.getMainLooper()).post(callback)
                }
            }

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
                        if (muxer?.writeVideo(outBuf, bufferInfo) == false) {
                            writeFailed = true
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }

                else -> break
            }
        }
    }

    fun stop(onStopped: (() -> Unit)? = null) {
        synchronized(lock) {
            if (!isRecording) return
            isRecording = false
        }

        effectClock.stop()

        Thread {
            synchronized(lock) {
                try {
                    videoEncoder.codec.signalEndOfInputStream()
                } catch (e: Exception) {
                    Log.e("VideoRecorder", "signalEndOfInputStream: ${e.message}")
                }
                drainVideoEncoder()

                lastStopHadValidOutput = muxer?.hasStarted == true
                try {
                    videoEncoder.release()
                } catch (e: Exception) {
                    Log.e("VideoRecorder", "release video error: ${e.message}")
                }
                audioEncoder.release()

                muxer?.release()
                muxer = null
            }

            onStopped?.let { callback ->
                Handler.createAsync(Looper.getMainLooper()).post(callback)
            }
        }.start()
    }
}