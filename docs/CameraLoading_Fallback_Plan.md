# Kế hoạch: Loading khi mở camera + Fallback GPU→CPU an toàn

> Nối tiếp `DelegatePerf_Plan.md` (đo CPU/GPU) và fix ANR (`getOrCreate()` chuyển sang `backgroundExecutor`, xem `Code_Walkthrough.md` mục 6.2). Sau fix ANR, tạo `HandLandmarker` chạy nền nên không treo UI nữa — nhưng camera hiện ra trong khi hiệu ứng AR chưa sẵn sàng (im lặng, gây bối rối). Ngoài ra GPU delegate trên máy yếu/ít RAM có thể mất rất lâu để khởi tạo — cần chặn trước, không để người dùng chờ vô thời hạn.

## 0. Bối cảnh đã xác nhận (đọc source Google, `mediapipe-samples/.../HandLandmarkerHelper.kt` + `CameraFragment.kt`)

- Đổi delegate CPU/GPU không có cách nào "tức thời" — kể cả code mẫu chính thức của Google cũng là đóng model cũ + tạo lại từ đầu (`clearHandLandmarker()` + `setupHandLandmarker()`), chạy trong `backgroundExecutor.execute { ... }`, không có 2 model dựng sẵn song song.
- `HandLandmarker.createFromOptions()` là lệnh gọi native đồng bộ, **không có API huỷ giữa chừng**. Muốn giới hạn thời gian chờ chỉ có cách chạy trên 1 thread phụ và `Future.get(timeout)` — hết giờ thì ngừng chờ (không giết được luồng build, nó có thể chạy nốt ở nền rồi bị bỏ kết quả).
- Code mẫu Google bắt `RuntimeException` khi tạo GPU thất bại ("This occurs if the model being used does not support GPU") — xác nhận đây là tình huống có thật, không phải lý thuyết.
- App mẫu của Google không có loading overlay hay preload — chấp nhận AR "biến mất rồi xuất hiện lại" im lặng. Với app của mình (effect có chủ đích rõ ràng hơn) thì cần overlay để không gây bối rối.

## 1. Việc sẽ làm

### Bước 1 — Cơ chế fallback trong `HandLandmarkerProvider`

- **Kiểm tra RAM máy trước khi thử GPU**, cache kết quả 1 lần cho cả phiên app (RAM máy không đổi giữa chừng): `ActivityManager.isLowRamDevice()` HOẶC `ActivityManager.MemoryInfo().totalMem < 3GB` → nếu đúng 1 trong 2, dùng thẳng `Delegate.CPU`, không thử GPU.
- **Giới hạn thời gian chờ GPU init = 5 giây** cho máy còn lại: build GPU trên 1 `Executors.newSingleThreadExecutor()` riêng, `future.get(5, TimeUnit.SECONDS)`. Hết 5s chưa xong → ngừng chờ, `future.cancel(true)` (best-effort), chuyển sang build CPU ngay trên thread khác.
- **Bắt `RuntimeException`/lỗi khi tạo GPU** → fallback CPU ngay, không crash.
- **Nhớ trạng thái đã fallback cho cả phiên app** (`@Volatile forcedCpuForSession`): timeout/lỗi GPU 1 lần → mọi lần tạo `HandLandmarker` sau đó (đổi effect khác) dùng thẳng CPU, không thử lại GPU mỗi lần.
- Log rõ ràng (`Log.w`) mỗi khi fallback xảy ra, kèm lý do (RAM thấp / timeout / lỗi) để chẩn đoán sau này.

### Bước 2 — Loading overlay trong `CameraRecordFragment`

- Thêm 1 lớp phủ nhẹ (`ProgressBar` + chữ ngắn "Đang chuẩn bị hiệu ứng…") lên đúng phần khung preview/hiệu ứng trong `fragment_camera_record.xml`, không che top bar (vẫn bấm Back được). *(Thực tế khác kế hoạch: overlay nền đen che kín cả top bar + bottom bar, nên nút Back trên top bar không bấm được; chỉ Back hệ thống còn hoạt động.)*
- Hiện ngay khi mở camera, trễ ~150-200ms trước khi thực sự hiện (tránh nhấp nháy nếu model đã có sẵn từ cache), tự ẩn ngay khi `handLandmarker` gán xong. *(Thực tế: hiện NGAY, không trễ — XML để `visible` mặc định để có mặt từ khung hình đầu; chống nhấp nháy bằng cách giữ tối thiểu 500ms thay vì trễ khi hiện.)*
- Cân nhắc đổi text nếu chờ quá 3-4s (dòng phụ "máy đang xử lý hơi lâu…") để không giống app treo. *(Đã làm: ngưỡng 3,5s; chuỗi thật trong `strings.xml` là "Sắp xong…" / "Almost done…", không phải câu này — xem `Camera_X_Hand_Landmarker.md` mục 14.3.)*

### Bước 3 — Test lại + cập nhật checklist

- Mở camera bình thường (máy hiện tại, RAM đủ) → GPU vẫn được dùng, overlay biến mất nhanh.
- Verify qua log: sau khi fallback CPU 1 lần, các lần đổi effect tiếp theo trong cùng phiên dùng CPU ngay, không còn chờ GPU.
- Case D7/D8 cũ (đổi effect nhanh, back nhanh) test lại không regression.
- **Giới hạn đã biết:** không có máy RAM thấp thật để test dòng "skip GPU"/"timeout 5s" — chỉ verify được qua log, hoặc tạm hạ ngưỡng RAM/timeout để ép trigger thử khi cần.

## 2. Docs cần cập nhật sau khi code xong

`Camera_X_Hand_Landmarker.md` (tính năng mới liên quan hand landmarker), `AGENTS.md` (kinh nghiệm cơ chế fallback), `Perf_Notes.md` mục 9 (ngưỡng RAM/timeout đã chọn), `Test_Checklist.md` mục D (case mới).

## 3. Tiến độ

- [x] Bước 1 — Fallback trong `HandLandmarkerProvider` (RAM check + timeout 5s + bắt lỗi + nhớ trạng thái phiên)
- [x] Bước 2 — Loading overlay trong `CameraRecordFragment` (đã test pass: hiện ngay không nhấp nháy, che kín top bar/bottom bar, disable nút, back hệ thống vẫn thoát được)
- [ ] Bước 3 — Test lại + cập nhật `Test_Checklist.md`
