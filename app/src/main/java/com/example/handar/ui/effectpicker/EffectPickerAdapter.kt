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
 * Adapter KHÔNG còn tự giữ lựa chọn: cú bấm chỉ báo lên [onSelected], id đang chọn do
 * [EffectPickerViewModel] quyết định rồi truyền xuống qua [setSelectedId]. [selectedId] ở đây chỉ là
 * bản ghi "viền đang vẽ ở item nào" để còn tính được vị trí cần notify — giữ nguyên việc cập nhật
 * từng item bằng payload thay vì notifyDataSetChanged cả lưới.
 */
class EffectPickerAdapter(
    private val items: List<EffectDefinition>,
    private val onSelected: (EffectDefinition) -> Unit
) : RecyclerView.Adapter<EffectPickerAdapter.VH>() {

    private var selectedId: String? = null

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
        holder.binding.root.setOnClickListener { onSelected(item) }
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        holder.binding.cardEffect.isSelected = items[position].id == selectedId
    }

    override fun getItemCount() = items.size

    fun setSelectedId(newSelectedId: String?) {
        if (newSelectedId == selectedId) return
        val oldPosition = items.indexOfFirst { it.id == selectedId }
        val newPosition = items.indexOfFirst { it.id == newSelectedId }
        selectedId = newSelectedId
        if (oldPosition >= 0) notifyItemChanged(oldPosition, PAYLOAD_SELECTION)
        // newPosition có thể là -1 khi newSelectedId là null — bản gốc không cần chốt này vì nó chỉ
        // được gọi từ cú bấm vào một item có thật.
        if (newPosition >= 0) notifyItemChanged(newPosition, PAYLOAD_SELECTION)
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
