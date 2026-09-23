package com.example.handar.ui.camera

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import com.example.handar.R
import com.example.handar.databinding.DialogGestureGuideBinding
import com.example.handar.effect.gesture.toDisplay
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.ui.widget.GridSpacingItemDecoration

class GestureGuideDialog(
    context: Context,
    effect: EffectDefinition,
) : Dialog(context, R.style.Theme_HandAr) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogGestureGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCancelable(true)
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        binding.textTitle.text = context.getString(R.string.camera_action)

        val gestureItems = effect.states.map { it.gesture.toDisplay() }.distinct()
        binding.recyclerGesture.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerGesture.addItemDecoration(GridSpacingItemDecoration(context))
        binding.recyclerGesture.adapter = GestureAdapter(gestureItems)

        binding.btnGotIt.setOnClickListener { dismiss() }
    }
}
