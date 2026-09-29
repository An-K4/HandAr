package com.example.handar.effect

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * TẠM: cơ chế fallback GPU→CPU (RAM thấp / timeout / lỗi init), xem docs/CameraLoading_Fallback_Plan.md.
 * Gỡ ghi chú TẠM khi cơ chế này đã chạy ổn định qua nhiều đợt release, không phải gỡ code.
 */
object HandLandmarkerProvider {
    private const val TAG = "HandLandmarkerProvider"
    private const val GPU_INIT_TIMEOUT_SEC = 5L
    private const val LOW_RAM_THRESHOLD_BYTES = 3L * 1024 * 1024 * 1024 // 3GB

    private var cached: HandLandmarker? = null
    private var cachedNumHands: Int = -1

    // Một khi đã fallback về CPU (timeout/lỗi GPU, hoặc máy RAM thấp) thì không thử lại GPU
    // trong suốt vòng đời process này — tránh bắt người dùng chờ lặp lại ở mỗi lần đổi effect.
    @Volatile
    private var forcedCpuForSession = false

    private val _results = MutableSharedFlow<Pair<HandLandmarkerResult, MPImage>>(
        extraBufferCapacity = 1
    )
    val results = _results.asSharedFlow()

    @Synchronized
    fun getOrCreate(context: Context, numHands: Int): HandLandmarker {
        if (cached == null || cachedNumHands != numHands) {
            cached?.close()
            cached = createWithFallback(context, numHands)
            cachedNumHands = numHands
        }
        return cached!!
    }

    @Synchronized
    fun release() {
        cached?.close()
        cached = null
        cachedNumHands = -1
    }

    /**
     * Chọn CPU hay GPU rồi tạo HandLandmarker, có 3 lớp bảo vệ để không bắt người dùng chờ vô
     * thời hạn:
     *  1. Máy RAM thấp (`ActivityManager.isLowRamDevice()` hoặc tổng RAM < 3GB) → dùng thẳng
     *     CPU, không thử GPU (tránh phí thời gian chờ vô ích trên máy chắc chắn không hợp).
     *  2. GPU init quá [GPU_INIT_TIMEOUT_SEC] giây → ngừng chờ, chuyển sang CPU. Lưu ý:
     *     `HandLandmarker.createFromOptions()` là lệnh gọi native đồng bộ, KHÔNG có API huỷ giữa
     *     chừng — build GPU chạy trên 1 thread phụ riêng để có thể "ngừng chờ" bằng
     *     `Future.get(timeout)`, luồng bị bỏ rơi có thể vẫn chạy nốt ở nền rồi bị bỏ kết quả,
     *     đây là đánh đổi chấp nhận được vì không có cách nào sạch hơn.
     *  3. GPU init ném lỗi (ví dụ `RuntimeException` khi máy/model không hỗ trợ GPU — chính
     *     MediaPipe ném ra, xem code mẫu chính thức của Google) → chuyển sang CPU, không crash.
     * Case 2 và 3 đều bật [forcedCpuForSession] để không thử lại GPU trong suốt phiên app.
     */
    private fun createWithFallback(context: Context, numHands: Int): HandLandmarker {
        val appContext = context.applicationContext

        if (forcedCpuForSession) {
            return build(appContext, numHands, Delegate.CPU)
        }

        if (isLowRamDevice(appContext)) {
            Log.w(TAG, "May RAM thap, dung CPU ngay, khong thu GPU")
            forcedCpuForSession = true
            return build(appContext, numHands, Delegate.CPU)
        }

        val timeoutExecutor = Executors.newSingleThreadExecutor()
        return try {
            val future = timeoutExecutor.submit(
                Callable { build(appContext, numHands, Delegate.GPU) }
            )
            future.get(GPU_INIT_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "GPU init qua ${GPU_INIT_TIMEOUT_SEC}s, chuyen ve CPU cho phan con lai cua phien")
            forcedCpuForSession = true
            build(appContext, numHands, Delegate.CPU)
        } catch (e: Exception) {
            Log.w(TAG, "GPU init loi (${e.message}), chuyen ve CPU")
            forcedCpuForSession = true
            build(appContext, numHands, Delegate.CPU)
        } finally {
            // shutdownNow() thay vì shutdown(): cố interrupt luồng GPU init đang bị bỏ rơi nếu có
            // (best-effort — lệnh native bên trong thường không phản hồi interrupt).
            timeoutExecutor.shutdownNow()
        }
    }

    private fun build(context: Context, numHands: Int, delegate: Delegate): HandLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(delegate)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(numHands)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, inputImage ->
                _results.tryEmit(result to inputImage)
            }
            .build()

        return HandLandmarker.createFromOptions(context, options)
    }

    private fun isLowRamDevice(context: Context): Boolean {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (activityManager.isLowRamDevice) return true

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem < LOW_RAM_THRESHOLD_BYTES
    }
}
