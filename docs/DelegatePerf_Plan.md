# Kế hoạch: Đo hiệu năng Delegate.CPU vs Delegate.GPU (real-time)

> ⚠️ **Đã lỗi thời một phần (29/09/2026, commit `ff9d279`):** `HandLandmarkerProvider` **không còn hard-code `Delegate.CPU`** nữa — delegate do `createWithFallback()` quyết định (GPU trước, tự rơi về CPU). Cách "sửa tay `.setDelegate(...)`" ghi ở Mục 0/Bước 3 dưới đây là mô tả **lúc viết kế hoạch** và không dùng được nữa — xem hướng dẫn mới ở "Cách ép delegate khi đo lại" cuối file. Các đoạn còn lại (thiết kế `DelegatePerfLogger`, vị trí gắn) vẫn đúng.

> Mục tiêu: có công cụ log + toast tại chỗ trong lúc quay/xem live, để so sánh độ mượt khi `HandLandmarkerProvider` dùng `Delegate.CPU` so với `Delegate.GPU`. Khác với `VideoStatsLogger.kt` (đo file video *sau khi* quay xong: size, resolution, avgFps dồn), công cụ này đo **latency suy luận** (thời gian MediaPipe xử lý 1 frame) và **FPS kết quả thực tế** trong lúc chạy — chỉ số phản ánh đúng cảm giác "mượt hay giật" mà CPU/GPU delegate gây ra.

## 0. Bối cảnh kỹ thuật (đã xác nhận trong code)

- `HandLandmarkerProvider.kt` tạo `HandLandmarker` với `RunningMode.LIVE_STREAM` + `.setDelegate(Delegate.CPU)` (đang hard-code CPU) + `setResultListener { result, inputImage -> ... }` bắn qua `MutableSharedFlow`.
- `CameraRecordFragment.kt`:
  - Analyzer (`ImageAnalysis`, `backgroundExecutor`) build bitmap mỗi frame rồi gọi `handLandmarker?.detectAsync(mpImage, timestamp)` với `timestamp = SystemClock.uptimeMillis()`.
  - `setupMediaPipe()` collect `HandLandmarkerProvider.results` (chạy trên `viewLifecycleOwner.lifecycleScope`, tức main thread) để cập nhật overlay + gesture.
  - Đã có biến tạm `analyzerFrames` + log 1 dòng đầu (đánh dấu `// TẠM: đo hiệu năng`) — không phải công cụ đo liên tục.
- `RecordingPerfLogger.kt` đã có sẵn, nhưng đo **work time của vòng lặp ghi/encode**, không đụng tới MediaPipe/delegate. Giữ nguyên, không đổi.
- API MediaPipe Tasks: `HandLandmarkerResult` kế thừa `TaskResult`, có `timestampMs()` — trả về đúng giá trị `timestamp` đã truyền vào `detectAsync`. Đây là cách chuẩn (dùng trong ví dụ chính thức của Google) để tính latency: `latency = SystemClock.uptimeMillis() - result.timestampMs()` tại thời điểm nhận callback.
- Không có tính năng "live stream" (phát trực tiếp/RTMP) riêng trong app — "live stream" trong yêu cầu là đang nói tới `RunningMode.LIVE_STREAM` của MediaPipe (tức là camera đang chạy, có thể đang quay hoặc chỉ xem preview). Do đó chỉ có **một điểm gắn** cần sửa: `CameraRecordFragment`.

## 1. Việc sẽ làm

### Bước 1 — Tạo file đo mới: `utils/DelegatePerfLogger.kt`

File **riêng**, không gộp vào `VideoStatsLogger.kt` (vì khác hẳn về thời điểm đo: một cái đo *sau khi* quay xong 1 file, một cái đo *liên tục trong lúc* camera chạy — gộp chung sẽ rối).

Thiết kế (dạng hàm mở rộng/tiện ích, theo đúng phong cách file hiện có, chỉ chạy khi `BuildConfig.DEBUG`):

- `markFrameSent()` — gọi ngay trước `detectAsync`, chỉ để đếm số frame đã gửi cho MediaPipe (phát hiện tụt hậu: gửi nhiều nhưng nhận kết quả ít nghĩa là delegate không theo kịp camera).
- `markResultReceived(resultTimestampMs: Long)` — gọi trong `results.collect { ... }`, tính `latency = now - resultTimestampMs`, cộng dồn vào cửa sổ đo hiện tại (mặc định 3 giây, giống phong cách 5 giây của `RecordingPerfLogger`).
- Sau mỗi cửa sổ: in 1 dòng `Log.i("DelegatePerf", ...)` gồm: `label` (CPU/GPU — tự khai báo tay khi test), latency trung bình/min/max (ms), số kết quả nhận được trong cửa sổ (≈ FPS xử lý thực), số frame đã gửi (để so lệch = frame rớt), và bắn kèm 1 `Toast` ngắn gọn (đẩy qua main thread) để xem trực tiếp khi đang cầm máy quay, không cần nhìn logcat.
- `start(label)` / `stop()` để bật/tắt đo đúng lúc, tránh in log khi không cần.

### Bước 2 — Gắn vào `CameraRecordFragment.kt`

- Khởi tạo 1 instance (giống cách `RecordingPerfLogger` đang được giữ làm field), gọi `start("CPU")` (hoặc đọc từ hằng số) khi `setupMediaPipe()` chạy.
- Gọi `markFrameSent()` ngay trước dòng `handLandmarker?.detectAsync(mpImage, timestamp)`.
- Gọi `markResultReceived(result.timestampMs())` ngay đầu khối `HandLandmarkerProvider.results.collect { (result, inputImage) -> ... }`.
- `stop()` khi fragment view bị huỷ (`onDestroyView`, cạnh chỗ `backgroundExecutor.shutdown()`).
- Đánh dấu các chỗ chèn bằng `// TẠM: đo hiệu năng` giống quy ước đã dùng, để dễ tìm và gỡ sau này (theo đúng cách `RecordingPerfLogger` đang làm).

### Bước 3 — Hướng dẫn đo & cập nhật doc

- Ghi rõ trong log/toast cách đổi delegate để test: sửa tay `Delegate.CPU` ↔ `Delegate.GPU` trong `HandLandmarkerProvider.kt` dòng `.setDelegate(...)`, đồng thời đổi `label` truyền vào `start(...)` cho khớp, build lại, đo, so sánh 2 lần chạy.
- Bổ sung 1 mục ngắn vào `Perf_Notes.md` (nối tiếp mục công cụ `RecordingPerfLogger` đã có) mô tả công cụ mới + cách đọc số liệu, và cập nhật `AGENTS.md` nếu rút ra kinh nghiệm gì sau khi đo thật (theo quy ước ghi kinh nghiệm của dự án).

## 2. Tiêu chí xong

- Build chạy được, không có tính năng nào bị ảnh hưởng khi tắt đo (guard `BuildConfig.DEBUG`, giống `VideoStatsLogger`).
- Khi quay thử với `Delegate.CPU` rồi đổi sang `Delegate.GPU`, thấy được 2 con số khác nhau rõ ràng qua Toast/Log: latency trung bình (ms) và FPS xử lý — đủ để kết luận cái nào mượt hơn trên máy test.

## 3. Tiến độ

- [x] Bước 1 — Tạo `DelegatePerfLogger.kt`
- [x] Bước 2 — Gắn vào `CameraRecordFragment.kt`
- [x] Bước 3 — Cập nhật `Perf_Notes.md` (+ `AGENTS.md` nếu có kinh nghiệm mới)

> **Cập nhật 29/09/2026:** đã đo xong (kết quả ở `Perf_Notes.md` mục 9/9.1/9.2), quyết định chốt
> `Delegate.GPU` làm mặc định. Toàn bộ chỗ gọi `DelegatePerfLogger` trong `CameraRecordFragment` đã
> được gỡ (cùng `RecordingPerfLogger`, `logRecordingStats`) — file `utils/DelegatePerfLogger.kt` vẫn
> giữ nguyên để đo lại sau này nếu cần, xem `AGENTS.md` mục 5.

## 4. Cách ép delegate khi đo lại (thay cho "sửa tay `.setDelegate(...)`" ở Bước 3)

Từ `ff9d279`, `.setDelegate(...)` nằm trong `build(context, numHands, delegate)` và giá trị do `createWithFallback()` truyền vào, nên sửa dòng đó không còn có tác dụng ép delegate. Muốn đo CPU vs GPU công bằng, sửa **tạm** trong `effect/HandLandmarkerProvider.kt` (nhớ hoàn lại, đánh dấu `// TẠM`):

- **Ép CPU:** đổi giá trị khởi tạo `forcedCpuForSession = false` thành `true` — nhánh đầu của `createWithFallback()` sẽ build thẳng `Delegate.CPU`, không kiểm tra RAM, không thử GPU.
- **Ép GPU, không fallback** (để thấy đúng hiệu năng/lỗi của GPU, kể cả trên máy RAM thấp): tạm thay toàn bộ thân `createWithFallback()` bằng `return build(context.applicationContext, numHands, Delegate.GPU)`.
- **Đổi `label` truyền vào `DelegatePerfLogger` bằng tay cho khớp** (`DELEGATE_PERF_LABEL` là hằng cố định, **không tự đổi theo delegate thực tế**). Nếu chạy chế độ mặc định (không ép) thì đọc log tag `HandLandmarkerProvider` để biết lần đó có fallback CPU hay không — nhãn của `DelegatePerfLogger` có thể nói sai.
