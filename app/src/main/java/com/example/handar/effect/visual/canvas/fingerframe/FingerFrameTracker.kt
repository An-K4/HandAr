package com.example.handar.effect.visual.canvas.fingerframe

import android.util.Log
import com.example.handar.effect.gesture.palmLength
import com.example.handar.effect.gesture.thumbIndexPinchRatio
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Trạng thái "sống" của khung Finger Frame (Mốc 3, xem docs/Finger_Frame_Filter_Plan.md):
 * sắp 4 đầu ngón theo góc → khớp với frame trước (quay vòng) → làm mượt thích nghi → mở/khép có hysteresis
 * → presence `p` (mờ dần/hiện dần).
 *
 * Mọi toạ độ ở dạng CHUẨN HOÁ [0,1] của ảnh camera (chưa mirror/crop) — việc chiếu ra pixel do visual làm trong
 * `draw()` bằng `frame.px()/py()` (Camera_X_Hand_Landmarker.md 13.3, 13.5).
 *
 * Luồng: [update] chạy trên main thread (nhịp MediaPipe); `draw()` — kể cả của bản recording ở thread ghi hình —
 * chỉ đọc [snapshot]: mảng BẤT BIẾN đã công bố qua `@Volatile`, nên không bao giờ đọc phải dữ liệu ghi dở (rủi ro R5).
 * Mỗi visual (live / recording) có tracker riêng, nhận cùng dãy `onHandFrame` nên ra cùng kết quả.
 */
class FingerFrameTracker {

    /** 9 số: 8 toạ độ chuẩn hoá của 4 góc (đã sắp góc, đã làm mượt) + presence `p` ở chỉ số 8. `null` = chưa có khung. */
    @Volatile
    var snapshot: FloatArray? = null
        private set

    private val raw = FloatArray(8)
    private val rotated = FloatArray(8)
    private val smoothed = FloatArray(8)
    private var hasSmoothed = false
    private var open = false
    private var presence = 0f
    private var lastMs = 0L

    fun update(hands: List<List<NormalizedLandmark>>, nowMs: Long) {
        val dt = if (lastMs == 0L) 0L else (nowMs - lastMs).coerceIn(0L, MAX_DT_MS)
        lastMs = nowMs

        var ratioA = -1.0
        var ratioB = -1.0
        var areaRatio = 0f
        var alphaLog = 0f

        val valid = hands.size >= 2 && hands[0].size >= 9 && hands[1].size >= 9
        if (valid) {
            val h0 = hands[0]
            val h1 = hands[1]
            // landmark 4 = đầu ngón cái, 8 = đầu ngón trỏ
            raw[0] = h0[4].x(); raw[1] = h0[4].y()
            raw[2] = h0[8].x(); raw[3] = h0[8].y()
            raw[4] = h1[4].x(); raw[5] = h1[4].y()
            raw[6] = h1[8].x(); raw[7] = h1[8].y()
            QuadMath.sortByAngle(raw)

            ratioA = thumbIndexPinchRatio(h0, h0[0])
            ratioB = thumbIndexPinchRatio(h1, h1[0])
            val palm = (palmLength(h0, h0[0]) + palmLength(h1, h1[0])) / 2.0
            val area = QuadMath.shoelaceArea(raw)
            areaRatio = if (palm > 0.0) (area / (palm * palm)).toFloat() else 0f

            // hysteresis: ngưỡng bật cao hơn ngưỡng tắt để không nhấp nháy quanh 1 ngưỡng duy nhất
            open = if (!open) {
                ratioA > RATIO_ON && ratioB > RATIO_ON && areaRatio >= MIN_AREA_RATIO
            } else {
                !(ratioA < RATIO_OFF || ratioB < RATIO_OFF || areaRatio < MIN_AREA_RATIO * AREA_OFF_FACTOR)
            }

            // chỉ cập nhật góc khi đang mở: lúc khép ngón 4 điểm sẽ co lại, nên đóng băng khung để nó mờ dần tại chỗ
            if (open) alphaLog = smooth(area)
        } else {
            open = false
        }

        val step = if (dt == 0L) 0f else dt.toFloat() / (if (open) FADE_IN_MS else FADE_OUT_MS)
        presence = if (open) (presence + step).coerceAtMost(1f) else (presence - step).coerceAtLeast(0f)

        publish()

        // TẠM (Mốc 3): log để chỉnh ngưỡng theo số liệu thật — gỡ ở Mốc 5. Lọc: adb logcat -s FingerFrameDbg
        Log.d(
            TAG,
            "ratioA=%.2f ratioB=%.2f area/palm2=%.2f open=%b p=%.2f alpha=%.2f".format(
                ratioA, ratioB, areaRatio, open, presence, alphaLog
            )
        )
    }

    /** Làm mượt `raw` vào `smoothed`; trả về alpha trung bình (chỉ để log). */
    private fun smooth(area: Float): Float {
        // khung mới hiện (p≈0) hoặc chưa từng có: đặt thẳng tại vị trí hiện tại, không "bay" từ chỗ cũ
        if (!hasSmoothed || presence < APPEAR_SNAP_P) {
            raw.copyInto(smoothed)
            hasSmoothed = true
            return 1f
        }
        // khớp góc: raw đã sắp góc nhưng điểm xuất phát có thể nhảy → quay vòng cho khớp frame trước
        val r = QuadMath.bestRotation(raw, smoothed)
        for (i in 0 until 4) {
            val j = (i + r) % 4
            rotated[i * 2] = raw[j * 2]
            rotated[i * 2 + 1] = raw[j * 2 + 1]
        }
        val size = sqrt(area.coerceAtLeast(1e-6f))
        var alphaSum = 0f
        for (i in 0 until 4) {
            val cx = rotated[i * 2]
            val cy = rotated[i * 2 + 1]
            val px = smoothed[i * 2]
            val py = smoothed[i * 2 + 1]
            // tốc độ chuẩn theo cỡ khung → tay xa/gần camera cho cùng cảm giác
            val dist = hypot(cx - px, cy - py) / size
            val alpha = QuadMath.adaptiveAlpha(dist, SPEED_REF, ALPHA_MIN)
            alphaSum += alpha
            smoothed[i * 2] = px + alpha * (cx - px)
            smoothed[i * 2 + 1] = py + alpha * (cy - py)
        }
        return alphaSum / 4f
    }

    private fun publish() {
        if (!hasSmoothed) {
            snapshot = null
            return
        }
        // đối tượng mới mỗi lần (~30 mảng nhỏ/giây ở nhịp MediaPipe, không phải trong draw()); đã công bố thì không sửa nữa
        val s = FloatArray(9)
        smoothed.copyInto(s)
        s[8] = presence
        snapshot = s
    }

    /** Mất tay / đổi trạng thái: xoá hết để lần sau hiện lại từ đầu thay vì bay từ vị trí cũ. */
    fun reset() {
        snapshot = null
        hasSmoothed = false
        open = false
        presence = 0f
        lastMs = 0L
    }

    companion object {
        private const val TAG = "FingerFrameDbg"

        // Hằng số KHỞI ĐIỂM — chỉnh theo log thật (quy trình Camera_X_Hand_Landmarker.md 11.9)
        /** tỉ lệ cái–trỏ / palmLength: > ngưỡng này (cả 2 tay) mới BẬT khung */
        private const val RATIO_ON = 0.70
        /** hạ xuống dưới ngưỡng này (1 trong 2 tay) thì TẮT khung */
        private const val RATIO_OFF = 0.45
        /** diện tích khung / palmLength² tối thiểu để bật; tắt khi dưới MIN * AREA_OFF_FACTOR */
        private const val MIN_AREA_RATIO = 0.60f
        private const val AREA_OFF_FACTOR = 0.70f
        /** alpha khi đứng yên (nhỏ = mượt hơn nhưng trễ hơn); tăng dần lên 1 khi tay di chuyển nhanh */
        private const val ALPHA_MIN = 0.35f
        /** quãng dịch chuyển mỗi frame (theo cỡ khung) mà tại đó alpha đạt 1 */
        private const val SPEED_REF = 0.08f
        private const val FADE_IN_MS = 150f
        private const val FADE_OUT_MS = 250f
        /** p dưới mức này coi như đang ẩn: khung mở lại sẽ đặt thẳng tại vị trí mới */
        private const val APPEAR_SNAP_P = 0.01f
        private const val MAX_DT_MS = 100L
    }
}
