package com.example.handar.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.example.handar.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * TẠM THỜI — đo hiệu năng suy luận (inference) của HandLandmarker theo thời gian thực,
 * dùng để so sánh Delegate.CPU vs Delegate.GPU khi đang quay hoặc xem live
 * (RunningMode.LIVE_STREAM). Xem docs/DelegatePerf_Plan.md.
 *
 * Khác với [logRecordingStats] (đo file video *sau khi* quay xong: size, resolution,
 * avgFps dồn), lớp này đo *liên tục trong lúc* camera + MediaPipe đang chạy:
 *   - latency  : thời gian từ lúc gửi frame cho detectAsync tới lúc nhận được kết quả
 *                (chỉ số phản ánh đúng độ mượt/giật do CPU/GPU delegate gây ra)
 *   - fps      : số kết quả thực nhận được mỗi giây (khác với fps của camera analyzer —
 *                nếu delegate không theo kịp, fps này sẽ thấp hơn fps camera)
 *   - rot (drop): số frame đã gửi nhưng không có kết quả tương ứng trong cửa sổ đo
 *                (MediaPipe LIVE_STREAM tự bỏ frame nếu đang bận xử lý frame trước)
 *
 * Cách dùng (xem CameraRecordFragment.setupMediaPipe / results.collect):
 *   1. `start()` khi bắt đầu chạy pipeline MediaPipe.
 *   2. `markFrameSent()` ngay trước `detectAsync(...)`.
 *   3. `markResultReceived(result.timestampMs())` ngay trong ResultListener/collect
 *      (timestamp truyền vào đây phải là giá trị đã truyền cho `detectAsync`,
 *      `HandLandmarkerResult.timestampMs()` trả về đúng giá trị đó).
 *   4. `stop()` khi dừng pipeline (onDestroyView).
 *
 * Cứ mỗi [windowMs] (mặc định 3s) in 1 dòng Log (tag "DelegatePerf") + 1 Toast ngắn.
 * Chỉ chạy ở bản DEBUG, gọi ở bản release là no-op.
 *
 * Gỡ cùng lúc với các chỗ đánh dấu `// TẠM: do delegate perf` trong CameraRecordFragment.
 */
class DelegatePerfLogger(
    private val context: Context,
    private val label: String,
    private val windowMs: Long = 3_000L
) {
    companion object {
        private const val TAG = "DelegatePerf"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    private var sessionStartMs = 0L
    private var windowStartMs = 0L

    private val sentInWindow = AtomicInteger(0)
    private val resultsInWindow = AtomicInteger(0)
    private val latencySumMsInWindow = AtomicLong(0)
    @Volatile private var latencyMinMsInWindow = Long.MAX_VALUE
    @Volatile private var latencyMaxMsInWindow = Long.MIN_VALUE

    private var totalSent = 0
    private var totalResults = 0

    @Synchronized
    fun start() {
        if (!BuildConfig.DEBUG) return
        running = true
        sessionStartMs = SystemClock.uptimeMillis()
        totalSent = 0
        totalResults = 0
        resetWindow()
        Log.i(TAG, "===== BAT DAU DO [$label] =====")
    }

    /** Gọi ngay trước detectAsync(). An toàn để gọi từ thread khác (analyzer). */
    fun markFrameSent() {
        if (!running) return
        sentInWindow.incrementAndGet()
        totalSent++
    }

    /**
     * Gọi trong ResultListener/collect, truyền [resultTimestampMs] = HandLandmarkerResult.timestampMs()
     * (chính là timestamp đã truyền vào detectAsync cho frame này).
     */
    @Synchronized
    fun markResultReceived(resultTimestampMs: Long) {
        if (!running) return

        val now = SystemClock.uptimeMillis()
        val latencyMs = (now - resultTimestampMs).coerceAtLeast(0)

        resultsInWindow.incrementAndGet()
        totalResults++
        latencySumMsInWindow.addAndGet(latencyMs)
        if (latencyMs < latencyMinMsInWindow) latencyMinMsInWindow = latencyMs
        if (latencyMs > latencyMaxMsInWindow) latencyMaxMsInWindow = latencyMs

        val elapsedWindowMs = now - windowStartMs
        if (elapsedWindowMs < windowMs) return

        flushWindow(elapsedWindowMs)
        windowStartMs = now
        resetWindow()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        val totalS = (SystemClock.uptimeMillis() - sessionStartMs) / 1000.0
        val avgFps = if (totalS > 0) totalResults / totalS else 0.0
        Log.i(
            TAG,
            "===== KET THUC [$label]  tong gui=%d nhan=%d (%.1fs, %.1f fps TB) ====="
                .format(totalSent, totalResults, totalS, avgFps)
        )
    }

    private fun flushWindow(elapsedWindowMs: Long) {
        val sent = sentInWindow.get()
        val results = resultsInWindow.get()
        val dropped = (sent - results).coerceAtLeast(0)
        val fps = if (elapsedWindowMs > 0) results * 1000.0 / elapsedWindowMs else 0.0
        val avgLatency = if (results > 0) latencySumMsInWindow.get().toDouble() / results else 0.0
        val minLatency = if (latencyMinMsInWindow == Long.MAX_VALUE) 0L else latencyMinMsInWindow
        val maxLatency = if (latencyMaxMsInWindow == Long.MIN_VALUE) 0L else latencyMaxMsInWindow

        val logLine = "[$label] fps=%.1f  latency_tb=%.1fms  min=%dms  max=%dms  gui=%d  nhan=%d  rot=%d"
            .format(fps, avgLatency, minLatency, maxLatency, sent, results, dropped)
        Log.i(TAG, logLine)
        // TẠM: tắt toast khi đo tổng thể, chỉ giữ Log — bật lại showToast(...) nếu cần xem trực tiếp trên máy
    }

    private fun resetWindow() {
        sentInWindow.set(0)
        resultsInWindow.set(0)
        latencySumMsInWindow.set(0)
        latencyMinMsInWindow = Long.MAX_VALUE
        latencyMaxMsInWindow = Long.MIN_VALUE
    }

    private fun showToast(message: String) {
        val appContext = context.applicationContext
        mainHandler.post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
