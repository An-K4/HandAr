package com.example.handar.ui.videolist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoListUiState(
    val isLoading: Boolean = true,
    val items: List<VideoItem> = emptyList(),
    val isEmpty: Boolean = false
)

class VideoListViewModel(private val repository: VideoRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()

    // Chạy đúng một lần theo vòng đời VM, không phải theo vòng đời view: rời màn rồi quay lại
    // (view bị huỷ, VM còn sống) không quét lại thư mục nữa.
    //
    // Làm mới có điều kiện, không phải mỗi lần onResume: sau khi XOÁ hoặc ĐỔI TÊN video,
    // VideoPlayerFragment đặt cờ VideoListFragment.KEY_VIDEO_LIST_STALE rồi popBackStack,
    // VideoListFragment.onResume đọc cờ và gọi load(). Nhờ vậy vừa không giữ dữ liệu cũ, vừa giữ
    // được lợi ích cache khi chỉ vào xem rồi back ra.
    //
    // Rename BẮT BUỘC phải làm mới, dù item trong grid không hiện tên file: VideoItem giữ File, và
    // VideoListFragment truyền file.absolutePath sang player khi bấm vào item. Path cũ sau rename
    // thì player không mở được -> Toast can_not_play_video + popBackStack ngay.
    // Bước 3 sẽ đổi cờ này sang sự kiện của VideoPlayerViewModel.
    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = repository.loadAll()
            _uiState.value = VideoListUiState(
                isLoading = false,
                items = items,
                isEmpty = items.isEmpty()
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { VideoListViewModel(VideoRepository(context.applicationContext)) }
        }
    }
}
