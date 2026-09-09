package com.example.handar.ui.videolist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.R
import com.example.handar.databinding.ItemVideoBinding
import com.example.handar.utils.formatDate
import com.example.handar.utils.formatDuration

class VideoAdapter(
    private var items: List<VideoItem>,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemVideoBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.textDuration.text = formatDuration(item.durationMs)
        holder.binding.textDate.text = formatDate(item.createdAt)
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