package com.example.handar

import android.content.Context
import android.graphics.Canvas
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream

class VideoRecorder(
    val context: Context,
    val width: Int,
    val height: Int,
    val fps: Int = 24
) {
    private var mediaRecorder: MediaRecorder? = null
    var recordingSurface: Surface? = null
        private set
    var isRecording: Boolean = false
        private set

    fun start(): File {
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputFile = File(outDir, "ar_record_${System.currentTimeMillis()}.mp4")

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(fps)
            setVideoEncodingBitRate(4_000_000)
            setOutputFile(outputFile.absolutePath)
            prepare()
        }

        recordingSurface = mediaRecorder!!.surface
        mediaRecorder!!.start()
        isRecording = true
        Log.d("VideoRecorder", "Started -> ${outputFile.absolutePath}")
        return outputFile
    }

    fun pushFrame(draw: (Canvas) -> Unit) {
        val surface = recordingSurface ?: return
        if (!isRecording) return

        val canvas = try {
            surface.lockHardwareCanvas()
        } catch (e: Exception) {
            Log.e("VideoRecorder", "lockHardwareCanvas failed: ${e.message}")
            return
        }

        try {
            draw(canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("VideoRecorder", "stop() lỗi, file có thể hỏng: ${e.message}")
        }

        mediaRecorder?.release()
        mediaRecorder = null
        recordingSurface = null
    }
}