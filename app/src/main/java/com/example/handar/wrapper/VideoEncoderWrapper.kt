package com.example.handar.wrapper

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

class VideoEncoderWrapper(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitRate: Int = computeBitrate(width, height, fps)
) {
    companion object {
        private const val BITS_PER_PIXEL = 0.1

        fun computeBitrate(width: Int, height: Int, fps: Int): Int {
            return (width * height * fps * BITS_PER_PIXEL).toInt()
        }
    }


    lateinit var codec: MediaCodec
        private set
    lateinit var inputSurface: Surface
        private set

    fun prepare() {
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    fun release() {
        codec.stop()
        codec.release()
        inputSurface.release()
    }
}