package com.example.handar.ui.effectlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.R
import com.example.handar.databinding.ItemEffectBinding
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.ui.widget.clipRoundedCorners

/**
 * Adapter không còn tự đọc/ghi `SharedPreferences`: [favouriteIds] do [EffectListViewModel] tính
 * sẵn và truyền vào, cú bấm tim chỉ báo lên [onToggleFavourite].
 */
class EffectAdapter(
    private var items: List<EffectDefinition>,
    private var favouriteIds: Set<String>,
    private val onClick: (EffectDefinition) -> Unit,
    private val onToggleFavourite: (String) -> Unit
) : RecyclerView.Adapter<EffectAdapter.VH>() {

    inner class VH(val binding: ItemEffectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEffectBinding.inflate(inflater, parent, false)
        val radiusPx = parent.resources.getDimension(R.dimen.card_corner_radius)
        binding.imgThumbnail.clipRoundedCorners(radiusPx)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.binding.textName.text = item.displayName
        holder.binding.imgThumbnail.setImageResource(item.thumbnailRes)
        holder.binding.root.setOnClickListener { onClick(item) }

        holder.binding.imgFavourite.setImageResource(
            if (item.id in favouriteIds) R.drawable.ic_favourite_selected else R.drawable.ic_favourite
        )
        holder.binding.imgFavourite.setOnClickListener { onToggleFavourite(item.id) }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<EffectDefinition>, newFavouriteIds: Set<String>) {
        items = newItems
        favouriteIds = newFavouriteIds
        notifyDataSetChanged()
    }
}
