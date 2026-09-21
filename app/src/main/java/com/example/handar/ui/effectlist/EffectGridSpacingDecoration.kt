package com.example.handar.ui.effectlist

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

// khoảng cách giữa 2 cột của lưới hiệu ứng theo chiều ngang.
class EffectGridSpacingDecoration(
    context: Context,
    gapDp: Int = 12
) : RecyclerView.ItemDecoration() {

    private val halfGapPx = (gapDp * context.resources.displayMetrics.density / 2).toInt()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val isLeftColumn = parent.getChildAdapterPosition(view) % 2 == 0
        outRect.left = if (isLeftColumn) 0 else halfGapPx
        outRect.right = if (isLeftColumn) halfGapPx else 0
    }
}
