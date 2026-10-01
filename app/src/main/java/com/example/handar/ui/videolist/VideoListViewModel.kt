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

    // KHÔNG có `init { load() }`.
    //
    // VM này lazy (`by viewModels()`) nên nó chỉ được tạo khi Fragment chạm vào lần đầu — mà mọi chỗ
    // chạm đều nằm trong onViewCreated, nơi đã có `launch { viewModel.load() }`. Thêm init thì mỗi
    // lần vào màn với VM mới sẽ quét thư mục HAI lần bằng hai coroutine cùng ghi vào _uiState.
    //
    // Nguồn gọi load() duy nhất: VideoListFragment.onViewCreated. State khởi tạo có isLoading = true
    // nên UI hiện vòng chờ ngay, không nhấp nháy khoảng trống trước khi lần quét đầu xong.
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
