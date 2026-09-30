package com.example.handar.ui.camera

import android.os.SystemClock

/**
 * Phần thuần Kotlin của việc nhận diện cử chỉ, tách khỏi `CameraRecordFragment`: debounce state và
 * giữ effect âm thanh đang hoạt động. Không chạm `Context`, `binding`, hay bất kỳ Android API nào
 * ngoài [SystemClock].
 *
 * Mọi lời gọi Android (`soundEffectPlayer`, `videoRecorder?.audioMixer`) ở lại Fragment, chạy theo
 * [Result] mà [onMatchedState] trả về.
 *
 * **Luồng gọi:** [onMatchedState] chạy trên thread của HandLandmarker (backgroundExecutor), còn
 * [activeEffect] được đọc từ main thread lúc bắt đầu ghi hình — nên nó `@Volatile`, đúng như field
 * `activeEffect` của bản gốc. Các field debounce còn lại chỉ dùng trong [onMatchedState] nên không
 * cần, cũng giống bản gốc.
 */
class GestureStateMachine(private val debounceMs: Long = DEFAULT_DEBOUNCE_MS) {

    data class ActiveEffect(val pcm: ShortArray, val startedAtMs: Long)

    private var lastStateId: String? = null
    private var pendingState: String? = null
    private var pendingStateSince = 0L

    @Volatile
    var activeEffect: ActiveEffect? = null
        private set

    sealed interface Result {
        /** Không làm gì. */
        data object NoChange : Result

        /** Fragment: đọc pcm theo [stateId], gọi [setActiveEffect], rồi `triggerEffect(pcm)` + `playForSound(soundRes)`. */
        data class Activate(val stateId: String, val soundRes: Int) : Result

        /** Fragment: `soundEffectPlayer.stopEffect()` + `videoRecorder?.audioMixer?.triggerEffect(null)`. */
        data object Clear : Result
    }

    /**
     * @param matchedStateId state khớp cử chỉ, `null` khi không có bàn tay hoặc không state nào khớp.
     *   Việc `effect.states.firstOrNull { it.gesture.recognize(hands) }` gọi bên ngoài để class này
     *   không phụ thuộc `EffectDefinition`/`HandLandmarkerResult`.
     * @param soundRes âm thanh của state đó, `null` nếu state không có tiếng.
     * @param momentaryClearsOnNull `effect.stateMode == StateMode.Momentary`.
     */
    fun onMatchedState(
        matchedStateId: String?,
        soundRes: Int?,
        momentaryClearsOnNull: Boolean
    ): Result {
        if (matchedStateId == null) {
            if (!momentaryClearsOnNull) return Result.NoChange
            clear()
            return Result.Clear
        }

        val now = SystemClock.uptimeMillis()
        if (matchedStateId != pendingState) {
            pendingState = matchedStateId
            pendingStateSince = now
            return Result.NoChange
        }

        if (lastStateId == matchedStateId || now - pendingStateSince < debounceMs) return Result.NoChange

        lastStateId = matchedStateId

        if (soundRes == null) {
            clear(keepPendingState = true)
            return Result.Clear
        }

        return Result.Activate(matchedStateId, soundRes)
    }

    fun setActiveEffect(pcm: ShortArray, startedAtMs: Long) {
        activeEffect = ActiveEffect(pcm, startedAtMs)
    }

    /**
     * Chỉ dọn state, KHÔNG phát sự kiện — tương ứng `resetGestureState()` của bản gốc, vốn cũng
     * không gọi `stopEffect`/`triggerEffect(null)`.
     */
    fun reset() {
        lastStateId = null
        pendingState = null
        pendingStateSince = 0
        activeEffect = null
    }

    private fun clear(keepPendingState: Boolean = false) {
        activeEffect = null
        lastStateId = null
        if (!keepPendingState) {
            pendingState = null
            pendingStateSince = 0
        }
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MS = 200L
    }
}
