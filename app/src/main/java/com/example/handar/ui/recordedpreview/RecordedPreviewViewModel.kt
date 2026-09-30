package com.example.handar.ui.recordedpreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.handar.ui.videolist.VideoFileRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * State duy nhất của màn preview nằm ở Fragment (ExoPlayer, ConfirmDialog — đều gắn View), nên VM
 * này chỉ giữ hai hành động và phát sự kiện cho Fragment điều hướng.
 *
 * `save()` hiện không làm gì ngoài phát sự kiện (video đã lưu sẵn từ lúc dừng ghi). Giữ nó ở VM là
 * cố ý: khi cần thêm bước xử lý trước khi sang màn Share (ghi lịch sử cho màn Bộ sưu tập chẳng
 * hạn), chỉ phải sửa đúng chỗ này.
 */
class RecordedPreviewViewModel(
    private val fileRepository: VideoFileRepository,
    private val videoPath: String
) : ViewModel() {

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        /** Đã xoá file xong, Fragment thoát màn. */
        data object Discarded : Event

        /** Không đổi gì về file, Fragment điều hướng sang màn Share. */
        data object Saved : Event
    }

    fun discardAndExit() {
        viewModelScope.launch {
            fileRepository.delete(videoPath)
            _events.send(Event.Discarded)
        }
    }

    fun save() {
        viewModelScope.launch { _events.send(Event.Saved) }
    }

    companion object {
        fun factory(videoPath: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecordedPreviewViewModel(VideoFileRepository(), videoPath) }
        }
    }
}
