package com.example.handar.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.handar.ui.videolist.VideoFileRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ExoPlayer, dialog và menu dropdown ở lại Fragment (đều gắn View). VM này giữ đúng hai thứ:
 * vị trí phát (phải sống qua process death) và hai thao tác file.
 */
class VideoPlayerViewModel(
    private val fileRepository: VideoFileRepository,
    private val savedStateHandle: SavedStateHandle,
    private val videoPath: String
) : ViewModel() {

    /**
     * Thay cho `onSaveInstanceState` thủ công của Fragment. `SavedStateHandle` tự được ghi vào
     * bundle của hệ thống nên sống qua cả process death, không chỉ qua việc view bị tạo lại.
     */
    var playbackPosition: Long
        get() = savedStateHandle[KEY_PLAYBACK_POSITION] ?: 0L
        set(value) {
            savedStateHandle[KEY_PLAYBACK_POSITION] = value
        }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        /** Đã xoá file xong. Fragment: báo list phải load lại rồi thoát màn. */
        data object Deleted : Event

        /** Đổi tên thành công. Fragment: Toast `rename_successfully`, báo list, rồi thoát màn. */
        data object Renamed : Event

        /** Fragment: Toast `video_name_already_exists`, **ở lại màn**. */
        data object RenameNameExists : Event

        /** Fragment: Toast `can_not_rename_video`, **ở lại màn**. */
        data object RenameFailed : Event
    }

    fun delete() {
        viewModelScope.launch {
            fileRepository.delete(videoPath)
            _events.send(Event.Deleted)
        }
    }

    /**
     * Tên rỗng và tên trùng chính nó **không phát sự kiện nào** — đúng bản gốc, hai nhánh đó
     * `return` im lặng, không Toast và không thoát màn.
     */
    fun rename(newName: String) {
        viewModelScope.launch {
            when (fileRepository.rename(videoPath, newName)) {
                is VideoFileRepository.RenameResult.Success -> _events.send(Event.Renamed)
                VideoFileRepository.RenameResult.NameAlreadyExists -> _events.send(Event.RenameNameExists)
                VideoFileRepository.RenameResult.Failed -> _events.send(Event.RenameFailed)
                VideoFileRepository.RenameResult.EmptyName,
                VideoFileRepository.RenameResult.Unchanged -> Unit
            }
        }
    }

    companion object {
        private const val KEY_PLAYBACK_POSITION = "playback_position"

        fun factory(videoPath: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VideoPlayerViewModel(VideoFileRepository(), createSavedStateHandle(), videoPath)
            }
        }
    }
}
