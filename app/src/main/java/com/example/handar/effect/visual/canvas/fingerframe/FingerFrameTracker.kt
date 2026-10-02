package com.example.handar.effect.visual.canvas.fingerframe

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
 * Mốc 5b: ngoài vị trí đã làm mượt, tracker ước lượng vận tốc từng góc và công bố thêm "độ dời dẫn trước" (`lead`) để
 * visual cộng vào khi tay di chuyển nhanh — bù phần trễ của đường ống (MediaPipe + analyzer). Đứng yên → lead = 0.
 *
 * Mọi toạ độ ở dạng CHUẨN HOÁ [0,1] của ảnh camera (chưa mirror/crop) — việc chiếu ra pixel do visual làm trong
 * `draw()` bằng `frame.px()/py()` (Camera_X_Hand_Landmarker.md 13.3, 13.5).
 *
 * Luồng: [update] chạy trên main thread (nhịp MediaPipe); `draw()` — kể cả của bản recording ở thread ghi hình —
 * chỉ đọc [snapshot]: mảng BẤT BIẾN đã công bố qua `@Volatile`, nên không bao giờ đọc phải dữ liệu ghi dở (rủi ro R5).
 * Mỗi visual (live / recording) có tracker riêng, nhận cùng dãy `onHandFrame` nên ra cùng kết quả.
 */
class FingerFrameTracker {

    /**
     * 17 số: [0..7] toạ độ chuẩn hoá của 4 góc (đã sắp góc, đã làm mượt), [8] presence `p`,
     * [9..16] độ dời dẫn trước `lead` của 4 góc (cộng vào toạ độ khi muốn bù trễ). `null` = chưa có khung.
     */
    @Volatile
    var snapshot: FloatArray? = null
        private set

    private val raw = FloatArray(8)
    private val rotated = FloatArray(8)
    private val smoothed = FloatArray(8)
    private val prevRotated = FloatArray(8)
    private val vel = FloatArray(8)  // vận tốc từng toạ độ, đơn vị chuẩn hoá / ms (đã làm mượt)
    private val lead = FloatArray(8)
    private var hasPrev = false
    private var prevMs = 0L
    private var hasSmoothed = false
    private var open = false
    private var presence = 0f
    private var lastMs = 0L

    fun update(hands: List<List<NormalizedLandmark>>, nowMs: Long) {
        val dt = if (lastMs == 0L) 0L else (nowMs - lastMs).coerceIn(0L, MAX_DT_MS)
        lastMs = nowMs

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

            val ratioA = thumbIndexPinchRatio(h0, h0[0])
            val ratioB = thumbIndexPinchRatio(h1, h1[0])
            val palm = (palmLength(h0, h0[0]) + palmLength(h1, h1[0])) / 2.0
            val area = QuadMath.shoelaceArea(raw)
            val areaRatio = if (palm > 0.0) (area / (palm * palm)).toFloat() else 0f

            // hysteresis: ngưỡng bật cao hơn ngưỡng tắt để không nhấp nháy quanh 1 ngưỡng duy nhất
            open = if (!open) {
                ratioA > RATIO_ON && ratioB > RATIO_ON && areaRatio >= MIN_AREA_RATIO
            } else {
                !(ratioA < RATIO_OFF || ratioB < RATIO_OFF || areaRatio < MIN_AREA_RATIO * AREA_OFF_FACTOR)
            }

            // chỉ cập nhật góc khi đang mở: lúc khép ngón 4 điểm sẽ co lại, nên đóng băng khung để nó mờ dần tại chỗ
            if (open) smooth(area, nowMs) else lead.fill(0f)
        } else {
            open = false
            lead.fill(0f)
        }

        val step = if (dt == 0L) 0f else dt.toFloat() / (if (open) FADE_IN_MS else FADE_OUT_MS)
        presence = if (open) (presence + step).coerceAtMost(1f) else (presence - step).coerceAtLeast(0f)

        publish()
    }

    /** Làm mượt `raw` vào `smoothed`. */
    private fun smooth(area: Float, nowMs: Long) {
        // khung mới hiện (p≈0) hoặc chưa từng có: đặt thẳng tại vị trí hiện tại, không "bay" từ chỗ cũ
        if (!hasSmoothed || presence < APPEAR_SNAP_P) {
            raw.copyInto(smoothed)
            hasSmoothed = true
            resetMotion(raw, nowMs)
            return
        }
        // khớp góc: raw đã sắp góc nhưng điểm xuất phát có thể nhảy → quay vòng cho khớp frame trước
        val r = QuadMath.bestRotation(raw, smoothed)
        for (i in 0 until 4) {
            val j = (i + r) % 4
            rotated[i * 2] = raw[j * 2]
            rotated[i * 2 + 1] = raw[j * 2 + 1]
        }
        val size = sqrt(area.coerceAtLeast(1e-6f))
        updateLead(size, nowMs)
        for (i in 0 until 4) {
            val cx = rotated[i * 2]
            val cy = rotated[i * 2 + 1]
            val px = smoothed[i * 2]
            val py = smoothed[i * 2 + 1]
            // tốc độ chuẩn theo cỡ khung → tay xa/gần camera cho cùng cảm giác
            val dist = hypot(cx - px, cy - py) / size
            val alpha = QuadMath.adaptiveAlpha(dist, SPEED_REF, ALPHA_MIN)
            smoothed[i * 2] = px + alpha * (cx - px)
            smoothed[i * 2 + 1] = py + alpha * (cy - py)
        }
    }

    /** Đặt lại trạng thái chuyển động: vận tốc 0, mốc so sánh là `pts` tại `nowMs`. */
    private fun resetMotion(pts: FloatArray, nowMs: Long) {
        pts.copyInto(prevRotated)
        vel.fill(0f)
        lead.fill(0f)
        hasPrev = true
        prevMs = nowMs
    }

    /**
     * 5b: ước lượng vận tốc từng góc từ vị trí ĐO (`rotated`, đã khớp thứ tự với `smoothed`) giữa 2 frame, làm mượt vận tốc
     * bằng EMA, rồi tính `lead = vận tốc × PREDICT_MS × gain`. `gain` chỉ > 0 khi tốc độ (theo cỡ khung) vượt `PREDICT_START`
     * — đứng yên/rung nhẹ thì không dẫn trước (tránh khuếch đại nhiễu). Độ dời bị chặn ở `MAX_LEAD_SIZE × cỡ khung`.
     */
    private fun updateLead(size: Float, nowMs: Long) {
        val dtVel = nowMs - prevMs
        if (!hasPrev || dtVel > MAX_VEL_DT_MS) {
            resetMotion(rotated, nowMs) // mất mốc quá lâu (vd. khép rồi mở lại): bắt đầu đo lại
            return
        }
        if (dtVel < MIN_VEL_DT_MS) return // 2 kết quả dồn sát nhau: giữ mốc cũ, đợi khoảng cách thời gian đủ lớn
        for (i in 0 until 8) {
            val v = (rotated[i] - prevRotated[i]) / dtVel
            vel[i] += VEL_ALPHA * (v - vel[i])
        }
        rotated.copyInto(prevRotated)
        prevMs = nowMs
        for (i in 0 until 4) {
            val vx = vel[i * 2]
            val vy = vel[i * 2 + 1]
            // tốc độ theo "cỡ khung mỗi frame tham chiếu" — cùng đơn vị với SPEED_REF của làm mượt
            val speed = hypot(vx, vy) * REF_FRAME_MS / size
            val gain = ((speed - PREDICT_START) / (PREDICT_FULL - PREDICT_START)).coerceIn(0f, 1f)
            var lx = vx * PREDICT_MS * gain
            var ly = vy * PREDICT_MS * gain
            val len = hypot(lx, ly)
            val maxLen = size * MAX_LEAD_SIZE
            if (len > maxLen && len > 0f) {
                val k = maxLen / len
                lx *= k
                ly *= k
            }
            lead[i * 2] = lx
            lead[i * 2 + 1] = ly
        }
    }

    private fun publish() {
        if (!hasSmoothed) {
            snapshot = null
            return
        }
        // đối tượng mới mỗi lần (~30 mảng nhỏ/giây ở nhịp MediaPipe, không phải trong draw()); đã công bố thì không sửa nữa
        val s = FloatArray(17)
        smoothed.copyInto(s)
        s[8] = presence
        lead.copyInto(s, 9)
        snapshot = s
    }

    /** Mất tay / đổi trạng thái: xoá hết để lần sau hiện lại từ đầu thay vì bay từ vị trí cũ. */
    fun reset() {
        snapshot = null
        hasSmoothed = false
        hasPrev = false
        vel.fill(0f)
        lead.fill(0f)
        open = false
        presence = 0f
        lastMs = 0L
    }

    companion object {
        // Hằng số KHỞI ĐIỂM — chỉnh theo log thật (quy trình Camera_X_Hand_Landmarker.md 11.9)
        /** tỉ lệ cái–trỏ / palmLength: > ngưỡng này (cả 2 tay) mới BẬT khung */
        private const val RATIO_ON = 0.30
        /** hạ xuống dưới ngưỡng này (1 trong 2 tay) thì TẮT khung */
        private const val RATIO_OFF = 0.15
        /** diện tích khung / palmLength² tối thiểu để bật; tắt khi dưới MIN * AREA_OFF_FACTOR */
        private const val MIN_AREA_RATIO = 0.05f
        private const val AREA_OFF_FACTOR = 0.10f
        /** alpha khi đứng yên (nhỏ = mượt hơn nhưng trễ hơn); tăng dần lên 1 khi tay di chuyển nhanh */
        private const val ALPHA_MIN = 0.35f
        /** quãng dịch chuyển mỗi frame (theo cỡ khung) mà tại đó alpha đạt 1 */
        private const val SPEED_REF = 0.08f
        private const val FADE_IN_MS = 150f
        private const val FADE_OUT_MS = 250f
        /** p dưới mức này coi như đang ẩn: khung mở lại sẽ đặt thẳng tại vị trí mới */
        private const val APPEAR_SNAP_P = 0.01f
        private const val MAX_DT_MS = 100L

        // 5b — dẫn trước vị trí khi tay nhanh (hằng số KHỞI ĐIỂM, chỉnh theo cảm giác khi test)
        /** dẫn trước bao nhiêu ms theo vận tốc (≈ 1 frame analyzer ở 25–30 fps) */
        private const val PREDICT_MS = 40f
        /** tốc độ (cỡ khung / frame tham chiếu) dưới mức này: không dẫn trước; trên PREDICT_FULL: dẫn trước đủ */
        private const val PREDICT_START = 0.03f
        private const val PREDICT_FULL = 0.10f
        private const val REF_FRAME_MS = 33f
        /** độ dời dẫn trước tối đa, theo cỡ khung — chặn "vọt quá" khi đổi hướng đột ngột */
        private const val MAX_LEAD_SIZE = 0.30f
        /** hệ số làm mượt vận tốc (0..1, lớn = phản ứng nhanh nhưng nhiễu hơn) */
        private const val VEL_ALPHA = 0.5f
        /** khoảng thời gian tối thiểu giữa 2 mẫu để tính vận tốc */
        private const val MIN_VEL_DT_MS = 8L

        /** Ngưỡng reset vận tốc, riêng với MAX_DT_MS của làm mượt: kết quả MediaPipe jitter 100–150 ms (17% > 100 ms) không được làm lead tụt về 0. */
        private const val MAX_VEL_DT_MS = 250L
    }
}
