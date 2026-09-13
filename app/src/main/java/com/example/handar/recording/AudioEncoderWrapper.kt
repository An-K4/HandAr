package com.example.handar.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEncoderWrapper(private val sampleRate: Int = 44_100) {
    lateinit var codec: MediaCodec
        private set

    fun prepare() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun encodeAndWrite(pcm: ShortArray, len: Int, presentationTimeUs: Long, muxer: MuxerCoordinator?) {
        val byteBuf = ByteBuffer.allocate(len * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteBuf.asShortBuffer().put(pcm, 0, len)
        val pcmBytes = byteBuf.array()
        val totalBytes = len * 2
        var offset = 0

        while (offset < totalBytes) {
            val inIndex = codec.dequeueInputBuffer(10_000)
            if (inIndex < 0) break

            val inputBuffer = codec.getInputBuffer(inIndex)!!
            inputBuffer.clear()

            val chunkSize = minOf(inputBuffer.capacity(), totalBytes - offset)
            inputBuffer.put(pcmBytes, offset, chunkSize)
            codec.queueInputBuffer(inIndex, 0, chunkSize, presentationTimeUs, 0)

            offset += chunkSize
        }

        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxer?.addAudioTrack(codec.outputFormat)
                }
                outIndex >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        muxer?.writeAudio(outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
                else -> break
            }
        }
    }

    fun release() {
        try { codec.stop() } catch (_: Exception) {}
        try { codec.release() } catch (_: Exception) {}
    }
}