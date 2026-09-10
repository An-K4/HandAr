package com.example.handar.utils

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.util.Log

/**
 * TẠM THỜI — dùng để phân tích hiện tượng tụt FPS khi quay clip dài.
 * Sau khi phân tích xong: xoá file này + 3 chỗ gọi trong CameraRecordFragment.
 *
 * Mỗi [windowMs] in ra một dòng, đọc theo cột:
 *   fps      tốc độ ghi thực tế trong cửa sổ này (mục tiêu 25)
 *   work     thời gian trung bình xử lý 1 frame: vẽ + encode (ngân sách 40ms)
 *   qua_han  % số frame vượt ngân sách 40ms (vòng lặp không kịp nghỉ)
 *   cam      tốc độ frame từ camera analyzer — dùng để phân biệt
 *            chậm toàn hệ thống (cam tụt theo) với chậm riêng đường ghi (cam giữ nguyên)
 *   gc       số lần GC (và tổng thời gian GC) trong cửa sổ — áp lực bộ nhớ, phần lớn chạy đồng thời
 *   chan     số lần GC LÀM ĐỨNG thread (và tổng ms) — đây mới là GC thực sự ăn vào fps
 *   nhiet    trạng thái nhiệt của máy — nghi vấn throttling
 */
class RecordingPerfLogger(
    context: Context,
    private val analyzerFrameCount: () -> Int,
    private val windowMs: Long = 5_000L
) {
    companion object {
        private const val TAG = "RecPerf"
    }

    private val powerManager =
        context.applicationContext.getSystemService(PowerManager::class.java)

    private var recordStartNs = 0L
    private var windowStartNs = 0L

    private var frames = 0
    private var workNsSum = 0L
    private var overBudgetFrames = 0

    private var analyzerAtWindowStart = 0
    private var gcCountAtWindowStart = 0L
    private var gcTimeAtWindowStart = 0L
    private var blockingGcCountAtWindowStart = 0L
    private var blockingGcTimeAtWindowStart = 0L

    private var totalFrames = 0

    fun start() {
        recordStartNs = System.nanoTime()
        windowStartNs = recordStartNs
        totalFrames = 0
        resetWindow()
        Log.i(TAG, "===== BAT DAU GHI  (nhiet luc bat dau: ${thermalStatus()}) =====")
    }

    /**
     * Gọi ở cuối mỗi vòng lặp ghi hình.
     * @param workNs thời gian thực sự xử lý frame này (chưa tính thời gian ngủ)
     * @param budgetNs ngân sách mỗi frame, 1000/fps ms
     */
    fun onFrame(workNs: Long, budgetNs: Long) {
        frames++
        totalFrames++
        workNsSum += workNs
        if (workNs > budgetNs) overBudgetFrames++

        val now = System.nanoTime()
        val windowNs = now - windowStartNs
        if (windowNs < windowMs * 1_000_000L) return

        val fps = frames * 1e9 / windowNs
        val avgWorkMs = workNsSum / frames / 1e6
        val overPercent = overBudgetFrames * 100 / frames
        val camFps = (analyzerFrameCount() - analyzerAtWindowStart) * 1e9 / windowNs
        val gcCount = runtimeStat("art.gc.gc-count") - gcCountAtWindowStart
        val gcTimeMs = runtimeStat("art.gc.gc-time") - gcTimeAtWindowStart
        val blockingGcCount = runtimeStat("art.gc.blocking-gc-count") - blockingGcCountAtWindowStart
        val blockingGcTimeMs = runtimeStat("art.gc.blocking-gc-time") - blockingGcTimeAtWindowStart
        val elapsedS = (now - recordStartNs) / 1_000_000_000

        Log.i(
            TAG,
            "t=%3ds  fps=%4.1f  work=%5.1fms  qua_han=%3d%%  cam=%4.1ffps  gc=%d(%dms)  chan=%d(%dms)  nhiet=%s"
                .format(
                    elapsedS, fps, avgWorkMs, overPercent, camFps,
                    gcCount, gcTimeMs, blockingGcCount, blockingGcTimeMs, thermalStatus()
                )
        )

        windowStartNs = now
        resetWindow()
    }

    fun finish() {
        val totalS = (System.nanoTime() - recordStartNs) / 1e9
        Log.i(
            TAG,
            "===== KET THUC  tong %d frame / %.1fs = %.2f fps  (nhiet luc ket thuc: %s) ====="
                .format(totalFrames, totalS, totalFrames / totalS, thermalStatus())
        )
    }

    private fun resetWindow() {
        frames = 0
        workNsSum = 0L
        overBudgetFrames = 0
        analyzerAtWindowStart = analyzerFrameCount()
        gcCountAtWindowStart = runtimeStat("art.gc.gc-count")
        gcTimeAtWindowStart = runtimeStat("art.gc.gc-time")
        blockingGcCountAtWindowStart = runtimeStat("art.gc.blocking-gc-count")
        blockingGcTimeAtWindowStart = runtimeStat("art.gc.blocking-gc-time")
    }

    private fun runtimeStat(key: String): Long =
        Debug.getRuntimeStat(key)?.toLongOrNull() ?: 0L

    private fun thermalStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "n/a"
        return when (powerManager?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "0-BINH_THUONG"
            PowerManager.THERMAL_STATUS_LIGHT -> "1-NHE"
            PowerManager.THERMAL_STATUS_MODERATE -> "2-VUA"
            PowerManager.THERMAL_STATUS_SEVERE -> "3-NANG"
            PowerManager.THERMAL_STATUS_CRITICAL -> "4-NGHIEM_TRONG"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "5-KHAN_CAP"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "6-TAT_MAY"
            else -> "?"
        }
    }
}
