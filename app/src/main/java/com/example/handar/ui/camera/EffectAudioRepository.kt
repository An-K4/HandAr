package com.example.handar.ui.camera

import android.content.Context
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.utils.loadWavPcm

/**
 * Decode PCM của một effect: tiếng theo từng state và nhạc nền. Bọc [loadWavPcm] (cần `Context`)
 * thành `class` nhận `Context` ở constructor theo quy ước `MVVM_Migration_Plan.md` mục 0.2, để
 * `CameraRecordViewModel` không phải giữ `Context`.
 *
 * ⚠️ **Cố ý ĐỒNG BỘ, không `suspend`/`Dispatchers.IO`.** Bản gốc decode đồng bộ trong
 * `onViewCreated`, nên `statePcmMap` luôn sẵn sàng trước khi người dùng có thể bấm Record hoặc giơ
 * cử chỉ. Đẩy sang IO ở mốc này sẽ mở ra cửa sổ thời gian mà map còn rỗng → mất tiếng hiệu ứng,
 * đúng loại bug D1 trong `Test_Checklist.md`. Việc chuyển sang IO + cờ chặn nút Record là mốc 5.5
 * riêng, xem mục 7.4 của kế hoạch.
 */
class EffectAudioRepository(context: Context) {
    private val appContext = context.applicationContext

    /** Map `stateId` → PCM. State không có `soundRes` thì không có mặt trong map (đúng bản gốc). */
    fun loadStatePcm(effect: EffectDefinition?): Map<String, ShortArray> =
        effect?.states.orEmpty().mapNotNull { state ->
            state.soundRes?.let { state.id to loadWavPcm(appContext, it) }
        }.toMap()

    fun loadBgmPcm(effect: EffectDefinition?): ShortArray? =
        effect?.bgm?.let { loadWavPcm(appContext, it.resId) }
}
