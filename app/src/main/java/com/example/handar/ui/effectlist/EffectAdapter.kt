package com.example.handar.ui.effectlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.databinding.ItemEffectBinding
import com.example.handar.effect.model.EffectDefinition

class EffectAdapter(
    private val items: List<EffectDefinition>,
    private val onClick: (EffectDefinition) -> Unit
) : RecyclerView.Adapter<EffectAdapter.VH>() {

    inner class VH(val binding: ItemEffectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemEffectBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.textName.text = item.displayName
        holder.binding.imgThumbnail.setImageResource(item.thumbnailRes)
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}