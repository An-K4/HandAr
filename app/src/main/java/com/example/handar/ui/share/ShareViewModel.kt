package com.example.handar.ui.share

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class ShareUiState(val isFullscreen: Boolean = false)

/**
 * VM mỏng có chủ đích: màn Share không có thao tác file nào, state thật chỉ có [ShareUiState.isFullscreen].
 * Việc **thi hành** fullscreen (dời `playerView` giữa hai container, đổi visibility hai group) bắt
 * buộc ở lại Fragment — VM không cầm View. Nghĩa là có hai tầng: VM đổi state, Fragment đọc state
 * rồi dựng UI. Chấp nhận tầng thừa này để sau này thêm state thật vào màn Share (ví dụ trạng thái
 * đang share, kết quả share) đã có sẵn chỗ, không phải refactor rời rạc một màn giữa các màn khác.
 */
class ShareViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        /** Fragment: `popBackStack(R.id.effectListFragment, false)`. */
        data object GoHome : Event

        /** Fragment: `navigate(actionShareToCameraRecord(effectId))`. */
        data object TryAgain : Event
    }

    fun showFullscreen() {
        if (_uiState.value.isFullscreen) return
        _uiState.value = ShareUiState(isFullscreen = true)
    }

    fun showCard() {
        if (!_uiState.value.isFullscreen) return
        _uiState.value = ShareUiState(isFullscreen = false)
    }

    /**
     * Back hệ thống: đang fullscreen thì thu nhỏ trước, đang ở card thì về home. Logic quyết định
     * nằm ở VM vì nó chỉ đọc state; việc điều hướng vẫn do Fragment làm khi nhận [Event.GoHome].
     */
    fun onBackPressed() {
        if (_uiState.value.isFullscreen) showCard() else goHome()
    }

    fun goHome() {
        _events.trySend(Event.GoHome)
    }

    fun tryAgain() {
        _events.trySend(Event.TryAgain)
    }
}
