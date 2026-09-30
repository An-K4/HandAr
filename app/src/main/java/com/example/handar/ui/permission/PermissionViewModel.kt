package com.example.handar.ui.permission

import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionUiState(
    val cameraGranted: Boolean = false,
    /** Mặc định true trên API < 33 — không có quyền nào để xin, thông báo coi như luôn được phép. */
    val notificationGranted: Boolean = true,
    val notificationApplicable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
)

/**
 * VM **mỏng có chủ đích**. Việc đọc `ContextCompat.checkSelfPermission` vẫn ở Fragment: nó cần
 * `Context`, và đây đúng loại "trạng thái hệ thống" phải đọc trực tiếp mỗi `onResume` (người dùng có
 * thể vừa tự cấp quyền trong màn cài đặt hệ thống) — cache trong VM là cache sai.
 *
 * Giá trị của VM ở đây là chuẩn hoá trạng thái 2 quyền thành **một nguồn duy nhất** cho UI, dọn đường
 * cho phần dialog dùng chung với màn camera sau này.
 */
class PermissionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    fun refresh(cameraGranted: Boolean, notificationGranted: Boolean) {
        _uiState.value = _uiState.value.copy(
            cameraGranted = cameraGranted,
            notificationGranted = notificationGranted
        )
    }
}
