package com.example.handar.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentPermissionBinding
import com.example.handar.utils.applySystemBarsInsetsPadding
import com.google.android.material.switchmaterial.SwitchMaterial

class PermissionFragment : Fragment() {
    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    // trên android < 13 không tồn tại quyền POST_NOTIFICATIONS,
    // thông báo mặc định được phép nên coi như luôn "granted".
    private val notificationPermissionApplicable =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> binding.switchCamera.isChecked = granted }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> binding.switchNotification.isChecked = granted }

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

        refreshSwitchStates()

        binding.switchCamera.setOnClickListener {
            handleSwitchClicked(
                switch = binding.switchCamera,
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
            handleSwitchClicked(
                switch = binding.switchNotification,
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
        refreshSwitchStates()
    }

    private fun refreshSwitchStates() {
        binding.switchCamera.isChecked = isGranted(Manifest.permission.CAMERA)
        binding.switchNotification.isChecked =
            if (notificationPermissionApplicable) isGranted(Manifest.permission.POST_NOTIFICATIONS) else true
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    /**
     * switch chỉ phản ánh trạng thái quyền thật, không tự ý bật/tắt:
     * - đang tắt (chưa cấp) mà user bấm bật -> đi xin quyền, kết quả trả về mới quyết định trạng thái cuối.
     * - đang bật (đã cấp) mà user bấm tắt -> Android không cho app tự thu hồi quyền, trả switch về bật.
     */
    private fun handleSwitchClicked(
        switch: SwitchMaterial,
        permission: String,
        launcher: ActivityResultLauncher<String>
    ) {
        val currentlyGranted = isGranted(permission)
        when {
            !currentlyGranted && switch.isChecked -> launcher.launch(permission)
            currentlyGranted && !switch.isChecked -> switch.isChecked = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
