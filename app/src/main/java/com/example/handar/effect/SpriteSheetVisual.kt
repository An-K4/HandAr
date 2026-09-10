package com.example.handar.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.max

class SpriteSheetVisual(
    context: Context,
    private val asset: EffectAsset.SpriteSheet
) : EffectVisual {
    private val sheet: Bitmap = requireNotNull(
        BitmapFactory.decodeResource(context.resources, asset.resId)
    ) { "Không decode được sheet '${context.resources.getResourceEntryName(asset.resId)}'" }
    private val frameW = sheet.width / asset.columns
    private val frameH = sheet.height / asset.rows
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val src = Rect()
    private val dst = RectF()

    @Volatile private var activatedAtMs = 0L

    init {
        require(sheet.width % asset.columns == 0 && sheet.height % asset.rows == 0) {
            "Sheet ${sheet.width}x${sheet.height} không chia hết cho lưới " +
                    "${asset.columns}x${asset.rows} — sẽ lệch ô"
        }
        require(asset.frameCount <= asset.columns * asset.rows) {
            "frameCount=${asset.frameCount} vượt số ô ${asset.columns * asset.rows}"
        }
    }

    override fun setActive(active: Boolean) {
        if (active) { if (activatedAtMs == 0L) activatedAtMs = SystemClock.elapsedRealtime() }
        else activatedAtMs = 0L
    }

    override fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val startedAt = activatedAtMs
        val elapsed = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt
        val frameIndex = ((elapsed / asset.frameDurationMs) % asset.frameCount).toInt()
        val col = frameIndex % asset.columns
        val row = frameIndex / asset.columns

        src.set(
            col * frameW,
            row * frameH,
            (col + 1) * frameW,
            (row + 1) * frameH
        )

        val scale = r / max(frameW, frameH).toFloat()

        val halfW = (frameW * scale) / 2f
        val halfH = (frameH * scale) / 2f
        dst.set(
            cx - halfW,
            cy - halfH,
            cx + halfW,
            cy + halfH
        )
        canvas.drawBitmap(sheet, src, dst, paint)
    }
}