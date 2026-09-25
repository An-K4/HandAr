package com.example.handar.effect.visual.image

import android.content.Context
import android.graphics.Canvas
import com.example.handar.effect.model.EffectAsset
import com.example.handar.effect.visual.EffectVisual
import com.example.handar.effect.visual.HandFrame

/**
 * ảnh động chạy đúng 1 lần mỗi lần state được kích hoạt, chạy xong thì không vẽ gì nữa.
 *
 * khác `EffectAsset.AnimatedGif(oneShot = true)` dùng trực tiếp: AnimatedGifVisual dừng ở khung cuối
 * và vẫn vẽ khung đó cho tới khi state tắt. Wrapper này dùng cho hiệu ứng "bùng ra rồi biến mất"
 * muốn giữ khung cuối thì dùng AnimatedGif oneShot thẳng.
 */
class OneShotGifVisual(context: Context, resId: Int) : EffectVisual {
    private val gif = AnimatedGifVisual(context, EffectAsset.AnimatedGif(resId, oneShot = true))

    override fun setActive(active: Boolean) = gif.setActive(active)

    override fun onHandFrame(frame: HandFrame) = gif.onHandFrame(frame)

    override fun draw(canvas: Canvas, frame: HandFrame) {
        if (!gif.hasFinishedPlaying()) gif.draw(canvas, frame)
    }
}
