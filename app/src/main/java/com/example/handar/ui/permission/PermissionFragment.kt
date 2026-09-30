package com.example.handar.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentPermissionBinding
import com.example.handar.ui.widget.PermissionDeniedDialog
import com.example.handar.utils.applySystemBarsInsetsPadding
import com.example.handar.utils.openAppSettings
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class PermissionFragment : Fragment() {
    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PermissionViewModel by viewModels()

    // trên android < 13 không tồn tại quyền POST_NOTIFICATIONS,
    // thông báo mặc định được phép nên coi như luôn "granted".
    private val notificationPermissionApplicable =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private var permissionDeniedDialog: PermissionDeniedDialog? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissionState()
        if (!granted) handleDenial(Manifest.permission.CAMERA)
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissionState()
        if (!granted) handleDenial(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applySystemBarsInsetsPadding()

        // android < 13: không có quyền để xin, hiện switch chỉ gây hiểu nhầm nên ẩn cả card.
        binding.cardPermissionNotification.isVisible = notificationPermissionApplicable

        refreshPermissionState()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.switchCamera.isChecked = state.cameraGranted
                    binding.switchNotification.isChecked = state.notificationGranted
                }
            }
        }

        binding.switchCamera.setOnClickListener {
            onSwitchClicked(
                switch = binding.switchCamera,
                granted = viewModel.uiState.value.cameraGranted,
                permission = Manifest.permission.CAMERA,
                launcher = requestCameraPermission
            )
        }

        binding.switchNotification.setOnClickListener {
            if (!notificationPermissionApplicable) {
                // không có gì để xin, luôn giữ bật.
                binding.switchNotification.isChecked = true
                return@setOnClickListener
            }
            onSwitchClicked(
                switch = binding.switchNotification,
                granted = viewModel.uiState.value.notificationGranted,
                permission = Manifest.permission.POST_NOTIFICATIONS,
                launcher = requestNotificationPermission
            )
        }

        binding.btnGetStarted.setOnClickListener {
            findNavController().navigate(R.id.action_permission_to_effectList)
        }
    }

    override fun onResume() {
        super.onResume()
        // user có thể vừa quay lại từ màn cài đặt hệ thống sau khi tự cấp quyền thủ công.
        refreshPermissionState()
    }

    /**
     * Đọc trạng thái quyền thật từ hệ thống rồi đẩy vào VM; UI do collector vẽ. Cố ý đọc lại mỗi lần
     * chứ không cache trong VM — xem docstring [PermissionViewModel].
     */
    private fun refreshPermissionState() {
        // Chốt cửa: hàm này cũng được gọi từ callback hệ thống, có thể về sau khi Fragment đã detach.
        val ctx = context ?: return
        viewModel.refresh(
            cameraGranted = isGranted(ctx, Manifest.permission.CAMERA),
            notificationGranted =
                if (notificationPermissionApplicable) isGranted(ctx, Manifest.permission.POST_NOTIFICATIONS) else true
        )
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * switch chỉ phản ánh trạng thái quyền thật, không tự ý bật/tắt:
     * - đang tắt (chưa cấp) mà user bấm bật -> đi xin quyền, kết quả trả về mới quyết định trạng thái cuối.
     * - đang bật (đã cấp) mà user bấm tắt -> Android không cho app tự thu hồi quyền, trả switch về bật.
     *
     * ⚠️ **Dòng `switch.isChecked = granted` là bắt buộc, đừng gỡ.** `SwitchMaterial` là compound
     * button: cú tap tự đổi `isChecked` TRƯỚC khi listener này chạy, nên View lệch khỏi state trong VM
     * ngay tại thời điểm đó. Không trả nó về ngay thì khi người dùng **từ chối** quyền, VM nhận lại
     * đúng giá trị cũ (`false`) → `MutableStateFlow` bỏ qua giá trị bằng nhau, **không emit** →
     * collector không chạy → switch nằm lại ở trạng thái bật dù quyền chưa được cấp.
     *
     * Nguyên tắc chung: không để View tự đổi state của chính nó rồi trông đợi `StateFlow` đẩy về —
     * `StateFlow` chỉ phát khi giá trị thay đổi.
     */
    private fun onSwitchClicked(
        switch: SwitchMaterial,
        granted: Boolean,
        permission: String,
        launcher: ActivityResultLauncher<String>
    ) {
        switch.isChecked = granted
        if (!granted) launcher.launch(permission)
    }

    /**
     * Từ chối kèm "Don't ask again": hệ thống nuốt luôn các lần xin sau, bấm switch sẽ không có
     * phản hồi gì -> phải chỉ đường sang màn cài đặt quyền của app.
     * Từ chối thường thì để nguyên, user bấm switch lần nữa là xin lại được.
     */
    private fun handleDenial(permission: String) {
        if (shouldShowRequestPermissionRationale(permission)) return
        showPermissionDeniedDialog(permission)
    }

    private fun showPermissionDeniedDialog(permission: String) {
        val ctx = context ?: return
        if (permissionDeniedDialog?.isShowing == true) return

        val message = when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> getString(R.string.denied_notification_permission_message)
            else -> getString(R.string.denied_permission_message)
        }

        permissionDeniedDialog = PermissionDeniedDialog(
            context = ctx,
            message = message,
            // màn này là bước onboarding, không có gì để thoát ra: chỉ đóng dialog và ở lại.
            negativeText = getString(R.string.close),
            onExit = {},
            onOpenSettings = { ctx.openAppSettings() },
        ).also { it.show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        permissionDeniedDialog?.dismiss()
        permissionDeniedDialog = null
        _binding = null
    }
}
