package com.example.handar.ui.effectlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.R
import com.example.handar.databinding.ItemEffectBinding
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.ui.widget.clipRoundedCorners
import com.example.handar.utils.FavouriteManager

class EffectAdapter(
    private var items: List<EffectDefinition>,
    private val onClick: (EffectDefinition) -> Unit
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
        val context = holder.binding.root.context

        holder.binding.textName.text = item.displayName
        holder.binding.imgThumbnail.setImageResource(item.thumbnailRes)
        holder.binding.root.setOnClickListener { onClick(item) }

        fun renderFavourite(isFavourite: Boolean) {
            holder.binding.imgFavourite.setImageResource(
                if (isFavourite) R.drawable.ic_favourite_selected else R.drawable.ic_favourite
            )
        }

        renderFavourite(FavouriteManager.isFavourite(context, item.id))
        holder.binding.imgFavourite.setOnClickListener {
            renderFavourite(FavouriteManager.toggle(context, item.id))
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<EffectDefinition>) {
        items = newItems
        notifyDataSetChanged()
    }
}
