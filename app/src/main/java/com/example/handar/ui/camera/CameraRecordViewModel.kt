package com.example.handar.ui.camera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.handar.effect.EffectRepository
import com.example.handar.effect.model.EffectDefinition

/**
 * VM **phạm vi hẹp có chủ đích**: chỉ giữ 4 thứ thuần dữ liệu của màn quay. Mọi tài nguyên gắn
 * View/Context (`VideoRecorder`, `BgmPlayer`, `SoundEffectPlayer`, `HandLandmarker`,
 * `backgroundExecutor`, thread ghi hình, `OverlayView`, timer, loading overlay) **ở lại Fragment** —
 * đưa chúng vào đây sẽ phá bất biến "camera nằm lại back stack = instance sống, view chết"
 * (`AGENTS.md` mục 5) và chỉ chuyển chỗ phức tạp. Xem `MVVM_Migration_Plan.md` mục 5.4.
 *
 * Lợi ích thật: [statePcmMap] và [bgmPcm] trước đây được decode lại trong `onViewCreated`, nên vào
 * màn chọn effect rồi Back là decode lại toàn bộ WAV trên main thread. Ở đây chúng chỉ chạy một lần
 * theo vòng đời VM. ([currentEffect] và [gestureStateMachine] vốn đã không bị tạo lại theo view —
 * gom vào đây là để nhất quán chỗ đặt state, không phải sửa lãng phí.)
 *
 * ⚠️ Decode **đồng bộ** trong khởi tạo, không `viewModelScope`/`Dispatchers.IO` — xem lý do ở
 * [EffectAudioRepository].
 */
class CameraRecordViewModel(
    audioRepository: EffectAudioRepository,
    effectId: String
) : ViewModel() {

    /**
     * Gán đúng một lần. Bản gốc cũng vậy: `onCreate` gán, 16 chỗ chỉ đọc, không nơi nào gán lại —
     * mọi lối vào camera với effect khác đều `popUpToInclusive` chính `cameraRecordFragment` nên
     * luôn là Fragment mới + VM mới (xem `nav_graph.xml`).
     */
    val currentEffect: EffectDefinition? = effectId.takeIf { it.isNotEmpty() }
        ?.let { EffectRepository.findByIdOrNull(it) }

    val statePcmMap: Map<String, ShortArray> = audioRepository.loadStatePcm(currentEffect)
    val bgmPcm: ShortArray? = audioRepository.loadBgmPcm(currentEffect)

    /** Fragment gọi `reset()` ở mỗi `onViewCreated` — xem `resetGestureState()`. */
    val gestureStateMachine = GestureStateMachine()

    companion object {
        fun factory(context: Context, effectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CameraRecordViewModel(
                    EffectAudioRepository(context.applicationContext),
                    effectId
                )
            }
        }
    }
}
