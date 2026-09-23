package com.example.handar.ui.camera

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.databinding.ItemGestureBinding
import com.example.handar.effect.gesture.GestureDisplay

class GestureAdapter(private val items: List<GestureDisplay>) :
    RecyclerView.Adapter<GestureAdapter.VH>() {

    inner class VH(val binding: ItemGestureBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGestureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.imgGestureIcon.setImageResource(item.iconRes)
        holder.binding.textGestureName.setText(item.nameRes)
    }

    override fun getItemCount() = items.size
}
