package com.example.handar.ui.effectpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.handar.effect.EffectRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Giữ effect đang được chọn (chưa xác nhận) và quyết định xác nhận thì đi đâu.
 *
 * [currentEffectId] là effect camera đang dùng, truyền vào để so lúc xác nhận: chọn lại đúng effect
 * cũ thì chỉ đóng màn, không điều hướng sang preview.
 */
class EffectPickerViewModel(private val currentEffectId: String) : ViewModel() {

    // null = chưa chọn gì (currentEffectId rỗng hoặc không có trong repository, ví dụ khi vào màn
    // camera mà chưa có effect nào).
    private val _selectedEffectId = MutableStateFlow(
        currentEffectId.takeIf { id -> EffectRepository.all.any { it.id == id } }
    )
    val selectedEffectId: StateFlow<String?> = _selectedEffectId.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        /** Fragment: `popBackStack()`. Dùng cho cả nút back và xác nhận mà không đổi effect. */
        data object Close : Event

        /** Fragment: `navigate(actionEffectPickerToEffectPreview(effectId))`. */
        data class OpenPreview(val effectId: String) : Event
    }

    fun select(effectId: String) {
        _selectedEffectId.value = effectId
    }

    fun onBackClicked() {
        _events.trySend(Event.Close)
    }

    /** Chưa chọn gì thì không làm gì — đúng bản gốc `val id = selectedEffectId ?: return`. */
    fun onConfirmClicked() {
        val id = _selectedEffectId.value ?: return
        _events.trySend(if (id == currentEffectId) Event.Close else Event.OpenPreview(id))
    }

    companion object {
        fun factory(currentEffectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { EffectPickerViewModel(currentEffectId) }
        }
    }
}
