package com.example.handar.ui.widget

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.example.handar.R
import com.example.handar.databinding.DialogPermissionDeniedBinding

/**
 * Dialog khi người dùng từ chối quyền kèm "Don't ask again": hệ thống không cho hỏi lại nữa,
 * nên nút chính dẫn thẳng sang màn cài đặt quyền của app để bật thủ công.
 */
class PermissionDeniedDialog(
    context: Context,
    title: String = context.getString(R.string.permission_denied),
    message: String = context.getString(R.string.denied_permission_message),
    negativeText: String = context.getString(R.string.exit),
    positiveText: String = context.getString(R.string.settings),
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
) : Dialog(context, R.style.Theme_HandAr) {
    // dùng theme app giống ConfirmDialog/GestureGuideDialog để giữ font noto serif.

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogPermissionDeniedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCancelable(false)
        // theme app là theme toàn màn của activity nên phải tự ép kích thước/gravity/dim.
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        binding.textTitle.text = title
        binding.textMessage.text = message

        binding.btnNegative.apply {
            text = negativeText
            setOnClickListener { dismiss(); onExit() }
        }

        binding.btnPositive.apply {
            text = positiveText
            setOnClickListener { dismiss(); onOpenSettings() }
        }
    }
}
