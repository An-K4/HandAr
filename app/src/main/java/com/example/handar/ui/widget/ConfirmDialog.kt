package com.example.handar.ui.widget

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.example.handar.R
import com.example.handar.databinding.DialogConfirmBinding
import androidx.core.graphics.drawable.toDrawable

class ConfirmDialog(
    context: Context,
    message: String,
    negativeText: String,
    positiveText: String,
    onNegative: () -> Unit,
    onPositive: () -> Unit,
) : Dialog(context, R.style.Theme_HandAr) {
    // truyền thẳng theme app (không dùng theme dialog hệ thống mặc định): nếu không, TextView trong
    // dialog_confirm.xml sẽ mất font noto serif mà theme khai báo.

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCancelable(false)
        // theme app vốn là theme toàn màn của activity (windowIsFloating không chắc chắn true), nên
        // phải tự ép kích thước/gravity/dim, nếu không thì dialog có thể tràn toàn màn hình.
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        binding.textMessage.text = message

        binding.btnNegative.apply {
            text = negativeText
            setOnClickListener { dismiss(); onNegative() }
        }

        binding.btnPositive.apply {
            text = positiveText
            setOnClickListener { dismiss(); onPositive() }
        }

        binding.btnClose.setOnClickListener { dismiss() }
    }
}
