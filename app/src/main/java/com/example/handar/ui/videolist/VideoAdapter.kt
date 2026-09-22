package com.example.handar.ui.videolist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.databinding.ItemVideoBinding

class VideoAdapter(
    private var items: List<VideoItem>,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemVideoBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setThumbnail(item.thumbnail)
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<VideoItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
