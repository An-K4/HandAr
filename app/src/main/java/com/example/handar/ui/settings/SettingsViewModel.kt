package com.example.handar.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface SettingsEvent {
    data object OpenPlayStore : SettingsEvent
    data object ShareApp : SettingsEvent
    data object SendFeedback : SettingsEvent
    data object OpenAboutApp : SettingsEvent
    data object OpenPrivacyPolicy : SettingsEvent
}

/**
 * 5 mục Đánh giá / Chia sẻ / Góp ý / Về ứng dụng / Chính sách hiện **chưa có logic thật**. VM này chỉ
 * định nghĩa sự kiện và chuẩn bị sẵn chỗ: khi nối logic thật (intent mở Play Store, share sheet,
 * mailto…), chỉ phải sửa nhánh tương ứng ở Fragment — không phải đi gắn lại listener.
 *
 * Nối logic thật là việc khác, **ngoài phạm vi** "chuyển MVVM" (xem `MVVM_Migration_Plan.md` mốc 6.4).
 *
 * Mục Ngôn ngữ **không** đi qua VM: đó là điều hướng nội bộ app, Fragment gọi `findNavController()`
 * thẳng như cũ, đúng quy ước 0.2.
 */
class SettingsViewModel : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onRateUsClicked() {
        _events.trySend(SettingsEvent.OpenPlayStore)
    }

    fun onShareAppClicked() {
        _events.trySend(SettingsEvent.ShareApp)
    }

    fun onFeedbackClicked() {
        _events.trySend(SettingsEvent.SendFeedback)
    }

    fun onAboutAppClicked() {
        _events.trySend(SettingsEvent.OpenAboutApp)
    }

    fun onPrivacyPolicyClicked() {
        _events.trySend(SettingsEvent.OpenPrivacyPolicy)
    }
}
