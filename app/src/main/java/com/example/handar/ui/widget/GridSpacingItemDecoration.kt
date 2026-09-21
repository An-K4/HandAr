package com.example.handar.ui.widget

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * khoảng cách giữa 2 cột của 1 lưới cố định 2 cột theo chiều ngang (dùng cho cả
 * EffectListFragment và VideoListFragment). item của cả 2 màn đều không có layout_marginHorizontal riêng —
 * decoration này chỉ chèn gap giữa 2 cột.
 */
class GridSpacingItemDecoration(
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
