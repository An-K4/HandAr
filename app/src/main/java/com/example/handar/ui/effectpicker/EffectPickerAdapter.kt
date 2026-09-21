package com.example.handar.ui.effectpicker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.handar.R
import com.example.handar.databinding.ItemEffectPickerBinding
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.ui.widget.clipRoundedCorners

/**
 * adapter cho lưới chọn hiệu ứng: chọn item, item được chọn hiện viền cyan (state_selected
 * của card_effect, xem selector_effect_picker_border).
 * adapter tự cập nhật viền; Fragment chỉ cần biết item nào vừa được chọn qua [onSelected].
 */
class EffectPickerAdapter(
    private val items: List<EffectDefinition>,
    private var selectedId: String?,
    private val onSelected: (EffectDefinition) -> Unit
) : RecyclerView.Adapter<EffectPickerAdapter.VH>() {

    inner class VH(val binding: ItemEffectPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEffectPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.imgThumbnail.clipRoundedCorners(parent.resources.getDimension(R.dimen.card_corner_radius))
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.textName.text = item.displayName
        holder.binding.imgThumbnail.setImageResource(item.thumbnailRes)
        holder.binding.cardEffect.isSelected = item.id == selectedId
        holder.binding.root.setOnClickListener { select(item) }
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        holder.binding.cardEffect.isSelected = items[position].id == selectedId
    }

    override fun getItemCount() = items.size

    private fun select(item: EffectDefinition) {
        if (item.id == selectedId) return
        val oldPosition = items.indexOfFirst { it.id == selectedId }
        val newPosition = items.indexOfFirst { it.id == item.id }
        selectedId = item.id
        if (oldPosition >= 0) notifyItemChanged(oldPosition, PAYLOAD_SELECTION)
        notifyItemChanged(newPosition, PAYLOAD_SELECTION)
        onSelected(item)
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
