package com.example.handar.ui.videolist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.R
import com.example.handar.databinding.ItemVideoBinding
import com.example.handar.ui.widget.clipRoundedCorners

class VideoAdapter(
    private var items: List<VideoItem>,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemVideoBinding.inflate(inflater, parent, false)
        val radiusPx = parent.resources.getDimension(R.dimen.card_corner_radius)
        binding.imgThumbnail.clipRoundedCorners(radiusPx)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.root.setOnClickListener { onClick(item) }

        if (item.thumbnail != null) {
            holder.binding.imgThumbnail.setImageBitmap(item.thumbnail)
        } else {
            holder.binding.imgThumbnail.setImageResource(R.drawable.ic_broken_video)
        }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<VideoItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
