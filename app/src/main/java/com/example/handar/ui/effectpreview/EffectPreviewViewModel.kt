package com.example.handar.ui.effectpreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.handar.effect.EffectRepository
import com.example.handar.effect.model.EffectDefinition
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Màn này gần như không có state: [effect] tra một lần từ [EffectRepository], còn
 * `AnimatedImageDrawable` là tài nguyên gắn View nên ở lại Fragment. VM tồn tại để đồng bộ với các
 * màn khác — sau này thêm state thật (media preview theo từng effect, nút yêu thích) thì đã có chỗ.
 */
class EffectPreviewViewModel(val effect: EffectDefinition) : ViewModel() {

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        /** Fragment: `popBackStack()`. */
        data object GoBack : Event

        /** Fragment: `navigate(actionEffectPreviewToCameraRecord(effect.id))`. */
        data object CreateVideo : Event
    }

    fun onBackClicked() {
        _events.trySend(Event.GoBack)
    }

    fun onCreateClicked() {
        _events.trySend(Event.CreateVideo)
    }

    companion object {
        fun factory(effectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { EffectPreviewViewModel(EffectRepository.findById(effectId)) }
        }
    }
}
