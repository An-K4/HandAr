package com.example.handar.ui.splash

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Đếm thời gian splash. Đồng hồ gắn với **vòng đời VM**, không phải vòng đời view như bản gốc
 * (`view.postDelayed`).
 *
 * ⚠️ **Đây là thay đổi hành vi có chủ đích**, không phải tác dụng phụ: nếu view của splash bị tạo lại
 * giữa chừng (hiếm — đã khoá portrait, nhưng đổi font scale hệ thống thì vẫn xảy ra), bản cũ **đếm
 * lại từ đầu** còn bản mới **đếm tiếp**. Người dùng không phải chờ thêm 5s nữa.
 *
 * `Channel.CONFLATED` thay vì `BUFFERED`: sự kiện này chỉ có nghĩa "đã tới lúc đi", phát hai lần là
 * vô nghĩa. Màn splash bị `popUpToInclusive` gỡ khỏi back stack khi điều hướng nên VM bị clear ngay,
 * không có chuyện sự kiện cũ còn nằm lại rồi phát lại.
 */
class SplashViewModel : ViewModel() {

    private val _ready = Channel<Unit>(Channel.CONFLATED)
    val ready = _ready.receiveAsFlow()

    /**
     * Mốc bắt đầu đếm. `uptimeMillis` (không phải `elapsedRealtime`) vì `delay` trên Main dispatcher
     * chạy qua `Handler`, cùng gốc đồng hồ với `uptimeMillis` — dùng lệch đồng hồ thì
     * [remainingMs] sẽ không khớp với thời điểm [ready] thật sự phát.
     */
    private val startedAtUptimeMs = SystemClock.uptimeMillis()

    /**
     * Thời gian còn lại tới khi [ready] phát. Fragment dùng nó để **tiếp tục** thanh loading từ đúng
     * chỗ đang dở khi view bị tạo lại, thay vì chạy lại đủ SPLASH_DELAY_MS từ 0 — không có cái này thì thanh loading
     * và thời gian chờ thật lệch nhau (thanh mới chạy được một phần thì đã chuyển màn).
     */
    val remainingMs: Long
        get() = (SPLASH_DELAY_MS - (SystemClock.uptimeMillis() - startedAtUptimeMs))
            .coerceIn(0L, SPLASH_DELAY_MS)

    init {
        viewModelScope.launch {
            delay(SPLASH_DELAY_MS)
            _ready.send(Unit)
        }
    }

    companion object {
        // TODO: hiện chỉ là delay giả để có hiệu ứng loading. khi có logic thật cần chờ ở splash
        //  thì thay bằng việc chờ thật, và thanh loading ở Fragment phải chuyển sang chạy theo tiến
        //  độ thật thay vì animate cứng theo hằng này.
        const val SPLASH_DELAY_MS = 5000L
    }
}
