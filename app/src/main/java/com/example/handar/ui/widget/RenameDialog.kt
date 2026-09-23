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
import com.example.handar.databinding.DialogRenameBinding

class RenameDialog(
    context: Context,
    currentName: String,
    private val onSave: (newName: String) -> Unit,
) : Dialog(context, R.style.Theme_HandAr) {
    // truyền thẳng theme app

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogRenameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCancelable(false)
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        binding.editNewName.setText(currentName)
        binding.editNewName.setSelection(currentName.length)

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            val newName = binding.editNewName.text?.toString()?.trim().orEmpty()
            if (newName.isEmpty()) return@setOnClickListener
            dismiss()
            onSave(newName)
        }

        binding.btnClose.setOnClickListener { dismiss() }
    }
}
