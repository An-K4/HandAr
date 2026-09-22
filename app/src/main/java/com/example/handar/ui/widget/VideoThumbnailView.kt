package com.example.handar.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.view.isVisible
import com.example.handar.R
import androidx.core.content.withStyledAttributes

class VideoThumbnailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val imgThumbnail: ImageView
    private val btnExpand: ImageButton

    var showExpandButton: Boolean = false
        set(value) {
            field = value
            btnExpand.isVisible = value
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_video_thumbnail, this, true)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        btnExpand = findViewById(R.id.btn_expand)

        // clipToOutline của view cha không tự cắt view con (xem RoundedOutline.kt) — ảnh thumbnail
        // phải tự bo góc riêng, dùng chung bán kính với "card" bọc ngoài.
        imgThumbnail.clipRoundedCorners(resources.getDimension(R.dimen.card_corner_radius))

        context.withStyledAttributes(attrs, R.styleable.VideoThumbnailView, defStyleAttr, 0) {
            showExpandButton = getBoolean(R.styleable.VideoThumbnailView_showExpandButton, false)
        }
    }

    /** [bitmap] null thì hiện icon video hỏng (ic_broken_video), giữ đúng hành vi cũ của VideoAdapter. */
    fun setThumbnail(bitmap: Bitmap?) {
        if (bitmap != null) {
            imgThumbnail.setImageBitmap(bitmap)
        } else {
            imgThumbnail.setImageResource(R.drawable.ic_broken_video)
        }
    }

    fun setOnExpandClickListener(listener: (() -> Unit)?) {
        btnExpand.setOnClickListener { listener?.invoke() }
    }
}
