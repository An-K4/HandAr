package com.example.handar.ui.videolist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thao tác file trên video đã ghi: xoá, đổi tên, kiểm tra tồn tại. Tất cả chạy trên
 * [Dispatchers.IO] — bản gốc gọi `File.delete()`/`renameTo()`/`exists()` thẳng trên main thread ở
 * `VideoPlayerFragment` và `RecordedPreviewFragment`.
 *
 * Không cần `Context`: chỉ làm việc trên đường dẫn tuyệt đối đã có sẵn (khác [VideoRepository],
 * cần `Context` để biết `getExternalFilesDir`). Đây là chủ đích, không phải quên.
 */
class VideoFileRepository {

    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).exists()
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).delete()
    }

    /**
     * Đổi tên file, giữ nguyên phần mở rộng cũ. Giữ đúng thứ tự kiểm tra của bản gốc ở
     * `VideoPlayerFragment.renameVideo()`: tên rỗng → trùng chính nó → đã tồn tại → thử rename.
     */
    suspend fun rename(path: String, newName: String): RenameResult = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withContext RenameResult.EmptyName

        val oldFile = File(path)
        val newFile = File(oldFile.parentFile, "$trimmed.${oldFile.extension}")

        if (newFile == oldFile) return@withContext RenameResult.Unchanged
        if (newFile.exists()) return@withContext RenameResult.NameAlreadyExists

        if (oldFile.renameTo(newFile)) {
            RenameResult.Success(newFile.absolutePath)
        } else {
            RenameResult.Failed
        }
    }

    sealed interface RenameResult {
        data class Success(val newPath: String) : RenameResult

        /** Tên mới trùng chính file đang có — bản gốc `return` im lặng, không Toast. */
        data object Unchanged : RenameResult

        /** Đã có file khác cùng tên — bản gốc Toast `video_name_already_exists`. */
        data object NameAlreadyExists : RenameResult

        /** `renameTo` trả về false — bản gốc Toast `can_not_rename_video`. */
        data object Failed : RenameResult

        /** Tên rỗng sau khi trim — bản gốc `return` im lặng, không Toast. */
        data object EmptyName : RenameResult
    }
}
