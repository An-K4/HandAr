package com.example.handar.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.handar.BuildConfig
import android.widget.Toast
import java.io.File

fun logRecordingStats(context: Context, file: File) {
    if (!BuildConfig.DEBUG) return
    if (!file.exists()) {
        Log.e("VideoStats", "File không tồn tại: ${file.absolutePath}")
        return
    }

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0L
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0L
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L

        val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()

        val sizeBytes = file.length()
        val sizeMb = sizeBytes / 1024.0 /1024.0
        val durationSec = durationMs / 1000.0
        val avgFps = if (frameCount != null && durationSec > 0) frameCount / durationSec else null

        val report = buildString {
            appendLine("== VideoStats: ${file.name} ==")
            appendLine("Size: %.2f MB (%d bytes)".format(sizeMb, sizeBytes))
            appendLine("Duration: %.2f s".format(durationSec))
            appendLine("Resolution: ${width}x${height}")
            appendLine("Bitrate (retriever): $bitrate bps")
            appendLine(
                if (avgFps != null) "Avg FPS: %.2f (frameCount=$frameCount)".format(avgFps)
                else "Avg FPS: không đọc được dữ liệu"
            )
        }

        Log.i("VideoStats", report)
        Toast.makeText(context, report, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("VideoStats", "Lỗi đọc metadata: ${e.message}")
    } finally {
        retriever.release()
    }
}