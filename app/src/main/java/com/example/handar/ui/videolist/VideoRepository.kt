package com.example.handar.ui.videolist

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

data class VideoItem(
    val file: File,
    val durationMs: Long,
    val createdAt: Long,
    val thumbnail: Bitmap?
)

object VideoRepository {
    private const val THUMB_SIZE = 256

    suspend fun loadAll(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val files = dir?.listFiles { f ->
            f.isFile && f.extension.equals("mp4", ignoreCase = true) && f.length() > 0
        } ?: return@withContext emptyList()

        files.sortedByDescending { it.lastModified() }
            .map {
                coroutineContext.ensureActive()
                readMetadata(it)
            }
    }

    private fun readMetadata(file: File): VideoItem {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val thumb = retriever.getScaledFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_SIZE, THUMB_SIZE
            )

            VideoItem(file, duration, file.lastModified(), thumb)
        } catch (e: Exception) {
            Log.w("VideoRepository", "Không đọc được metadata: ${file.name}", e)
            VideoItem(file, 0L, file.lastModified(), null)
        } finally {
            retriever.release()
        }
    }
}