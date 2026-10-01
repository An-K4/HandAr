# Kế hoạch: Finger Frame Filter — hiệu ứng demo đầu tiên "Khung đảo màu"

> Lý thuyết nền (công thức, vai trò từng mảng, tài liệu/video): `Finger_Frame_Filter_Theory.md` — file này dẫn chiếu theo số mục của nó (ký hiệu **[T§x]**).
>
> Viết ngày 01/10/2026, sau khi đọc lại code tại commit `6d63617` (`OverlayView.kt`, `CameraRecordFragment.kt`, `HandFrame.kt`, `EffectVisual.kt`, `ProceduralVisual.kt`, `HandLandmarkerProvider.kt`, `Gesture.kt`, `GestureUtils.kt`, `GestureDisplay.kt`, `effect/model/*`, `effect/catalog/*`, `VideoRecorder.pushFrame`) và các doc `AGENTS.md`, `Code_Walkthrough.md`, `Camera_X_Hand_Landmarker.md` (mục 8, 10.5, 11.6–11.9, 12, 13, 14), `Perf_Notes.md`, `Test_Checklist.md`.
>
> **Quy trình** (theo hướng dẫn dự án): duyệt file này → code **từng mốc** ở Mục 4, không làm hết một lượt → báo cáo sau mỗi mốc → chờ xác nhận mới đánh dấu `[x]` ở Mục 8 và sang mốc tiếp.

---

## 0. Mục tiêu demo & tiêu chí "xong"

**Hiệu ứng demo:** giơ 2 tay, mỗi tay tách ngón cái và ngón trỏ ra → 4 đầu ngón tạo thành một khung tứ giác; **bên trong khung ảnh camera bị đảo màu (negative)**, bên ngoài giữ nguyên; khung có viền nét đứt + chấm ở 4 góc, mượt, hiện/ẩn dần; video ghi ra giống hệt những gì thấy khi live.

Vì sao chọn **Negative** làm filter đầu tiên (chi tiết [T§11]): chỉ cần `ColorMatrix` → chạy trên mọi máy từ minSdk 28, rủi ro filter ≈ 0, nên mọi lỗi lộ ra đều thuộc phần khung/hạ tầng — dễ khoanh vùng. Các filter khác (Thermal, Neon, Glitch…) để sau khi hạ tầng đã đứng vững (Mục 6).

**Tiêu chí hoàn thành demo:**
1. Khung bám đúng 4 đầu ngón ở live preview, ở mọi vị trí trên màn hình, tay gần lẫn xa camera.
2. Khung không rung khi tay đứng yên, không "xoắn"/nhảy góc khi xoay khung.
3. Khép ngón → khung mờ dần rồi tắt; mở lại → hiện dần.
4. Trong video ghi ra: khung + vùng đảo màu nằm đúng chỗ, khớp từng pixel với phần ảnh bên ngoài.
5. Không regression ở 10 hiệu ứng cũ (Checklist B, D, J, K, L của `Test_Checklist.md`), fps ghi hình không tụt rõ so với hiệu ứng cũ.

---

## 1. Hạ tầng hiện tại ĐÃ hỗ trợ những gì

| Nhu cầu của hiệu ứng | Đã có sẵn | Ở đâu |
|---|---|---|
| Nhận diện 2 tay, 21 landmark | ✅ `requiredNumHands = 2` → `HandLandmarkerProvider.getOrCreate(numHands)` tạo lại model 2 tay (có loading overlay che thời gian chờ) | `EffectDefinition`, `HandLandmarkerProvider`, `CameraRecordFragment.setupMediaPipe()` |
| Hiệu ứng vẽ bằng code, nhận toàn bộ landmark | ✅ `EffectAsset.Procedural` + `EffectVisual` (`draw(canvas, frame)`, `onHandFrame(frame)`, `setActive()`) | `effect/model/EffectAsset.kt`, `effect/visual/EffectVisual.kt` |
| Chiếu landmark → pixel đúng canvas (live/video), có mirror | ✅ `HandFrame.px()/py()` + `setProjection()` do `OverlayView.drawFrame()` nạp mỗi frame | `effect/visual/HandFrame.kt`, `OverlayView.kt` |
| Cập nhật trạng thái **1 lần mỗi kết quả MediaPipe** cho cả live và recording | ✅ `onHandFrame()` gọi cho cả `liveVisuals` lẫn `recordingVisuals` trong `setResult()` | `OverlayView.setResult()`; quy tắc ở `Camera_X_Hand_Landmarker.md` 13.2–13.3 |
| 2 bản visual độc lập cho live / recording (không chia sẻ state nội bộ) | ✅ `setEffect()` tạo 2 list + 2 `EffectScope` | `OverlayView.setEffect()` |
| Bitmap camera mỗi frame (đã xoay đứng, chưa mirror) | ✅ nhưng **chỉ Fragment giữ**: `latestCameraBitmap` (`@Volatile`), tạo mới mỗi frame, **không bao giờ `recycle()`** → an toàn đọc từ thread khác | `CameraRecordFragment.startCamera()` |
| Vẽ bitmap camera vào video đúng phép chiếu | ✅ vòng lặp ghi vẽ `latestCameraBitmap` bằng `bmpMatrix` (mirror + center-crop) **trước** khi gọi `overlay.drawFrame(...)` | `CameraRecordFragment.startRecordingFrameLoop()` |
| Canvas ghi hình tăng tốc phần cứng | ✅ `Surface.lockHardwareCanvas()` → `BitmapShader`, `ColorMatrixColorFilter`, `drawPath` khử răng cưa đều chạy trên GPU | `VideoRecorder.pushFrame()` |
| Công thức tỉ lệ ngón cái–trỏ / chiều dài lòng bàn tay | ✅ `thumbIndexPinchRatio()`, `palmLength()`, `pointDistance()` (public); `crossSign()` có nhưng đang `private` — chỉ cần nếu sau này kiểm tra khung lồi [T§3.5] | `effect/gesture/GestureUtils.kt` |
| Icon hướng dẫn cử chỉ khung 2 tay | ✅ `ic_action_frame_2hands.xml` — có sẵn, **đang không dùng** (của cử chỉ "khung máy ảnh" cũ đã gỡ) | `res/drawable/` |
| Effect không có tiếng | ✅ `soundRes = null` hợp lệ khi có `asset` (đã có 9 state như vậy, ví dụ `EarthEffect`, `GojoEffect`) | `EffectState.init` |
| Đo hiệu năng | ✅ `RecordingPerfLogger`, `VideoStatsLogger` (đang gỡ chỗ gọi, gắn lại theo `Perf_Notes.md` mục 9) | `utils/` |

**Kết luận:** khoảng 80% hạ tầng đã có. Không cần đụng `recording/` (đúng quy tắc `AGENTS.md` mục 5), không cần đổi model dữ liệu effect, không cần thư viện mới.

---

## 2. Cần thêm những gì — và tại sao

| # | Thêm gì | Tại sao cần | File |
|---|---|---|---|
| A | **Đưa bitmap camera tới visual**: field `HandFrame.cameraFrame: Bitmap?` + hàm `HandFrame.cameraMatrix(out: Matrix)` | Hiện visual chỉ nhận landmark, không có ảnh camera để filter. `cameraMatrix()` dựng đúng ma trận [T§1.3] từ chính `mirrorX/imgWidth/localScale/offset` mà `px()/py()` dùng → nội dung trong khung khớp tuyệt đối với khung | `effect/visual/HandFrame.kt` |
| B | `OverlayView.drawFrame(..., cameraFrame: Bitmap? = null)` gán vào `frame.cameraFrame`; field `@Volatile liveCameraFrame` + `setCameraFrame()` cho nhánh live (`onDraw`) | Live và recording lấy bitmap từ 2 nguồn khác nhau (xem B1/B2) nên phải truyền qua tham số, không đọc chung 1 biến. Tham số có giá trị mặc định → 2 chỗ gọi cũ không phải sửa nếu không muốn | `OverlayView.kt` |
| B1 | Live: trong `collect` của `setupMediaPipe()`, gọi `overlayView?.setCameraFrame(latestCameraBitmap)` **ngay trước** `setResult(...)` | Chạy trên main thread cùng nhịp với `setResult()`/`invalidate()` → bitmap và landmark của live lệch nhau tối đa 1 frame analyzer | `CameraRecordFragment.kt` |
| B2 | Recording: `overlay.drawFrame(canvas, handResult, mirrorX = true, forRecording = true, cameraFrame = bitmap)` — đúng **cùng object `bitmap`** vừa vẽ làm nền | Nền và nội dung trong khung là **một** bitmap → khớp từng pixel trong video, không có đường nối | `CameraRecordFragment.startRecordingFrameLoop()` |
| C | Cử chỉ `Gestures.twoHandsFrame` (lỏng: `hands.size >= 2`), **instance riêng** | (1) Bài học mục 11.6: cử chỉ khung có điều kiện góc vuông đã bị gỡ vì quá khó giữ — không lặp lại. (2) Nếu điều kiện "ngón mở" nằm trong gesture, khép ngón là `matchedIndex = -1` → `drawFrame()` `return` sớm → visual không được vẽ → **không fade-out được** [T§6]. Nên gesture chỉ kiểm tra "đủ 2 tay", còn "khung mở/hợp lệ" do visual tự quyết. (3) Instance riêng để `gestureDisplayMap` hiện đúng tên/icon (cùng lý do `pinchTracking`, `AGENTS.md` mục 5) | `effect/gesture/Gesture.kt` |
| D | Map hướng dẫn: `twoHandsFrame → GestureDisplay(R.string.gesture_two_hands_frame, R.drawable.ic_action_frame_2hands)` + chuỗi en/vi | Dialog Action phải có dòng hướng dẫn đúng (`AGENTS.md`: "thêm hiệu ứng mới luôn mở dialog Action kiểm tra") | `GestureDisplay.kt`, `values/strings.xml`, `values-vi/strings.xml` |
| E | **Toán khung thuần** (không Android API, trên `FloatArray` toạ độ chuẩn hoá): sắp theo `atan2` [T§3.3], diện tích shoelace [T§3.4], chọn phép quay vòng gần nhất [T§4.2], EMA [T§5.1] | Tách hàm thuần để đọc/kiểm tra dễ, có thể viết unit test JVM sau này (repo hiện chưa có thư mục test) | `effect/visual/canvas/fingerframe/QuadMath.kt` (mới) |
| F | **`FingerFrameTracker`**: giữ 8 toạ độ góc đã làm mượt + hysteresis "mở/khép" [T§2.3] + presence `p` [T§6]; cập nhật trong `onHandFrame()`; công bố ảnh chụp (snapshot) qua 1 tham chiếu `@Volatile` | Trạng thái cần nhớ qua frame → không đặt trong `GestureRecognizer` (vô trạng thái, mục 11.8). `onHandFrame()` chạy trên **main thread**, còn `draw()` của bản recording chạy trên **recording thread** → phải công bố dữ liệu an toàn xuyên thread (cùng tinh thần mục 13.11) | `.../fingerframe/FingerFrameTracker.kt` (mới) |
| G | **`FingerFrameVisual : EffectVisual`**: `onHandFrame` → tracker; `draw` → chiếu 4 góc ra pixel, dựng `Path`, tô bằng `BitmapShader(cameraFrame)` + `ColorMatrixColorFilter` (negative) + `alpha = p`, rồi vẽ viền nét đứt + chấm góc | Cách "vẽ đúng hình dạng" [T§7.2 cách B] có khử răng cưa, không dùng `clipPath` (tài liệu Android: clip phức tạp không khử răng cưa) | `.../fingerframe/FingerFrameVisual.kt` (mới) |
| H | Catalog `fingerFrameEffect()` + thêm vào `EffectRepository.all` + chuỗi `effect_name_finger_frame` en/vi + thumbnail | Quy trình thêm hiệu ứng chuẩn (`AGENTS.md` mục 7) | `effect/catalog/FingerFrameEffect.kt` (mới), `EffectRepository.kt`, strings, drawable |

**Không thêm** ở demo: interface "filter" chung, AGSL, đổi filter bằng cử chỉ, tiếng hiệu ứng — xem Mục 6 (tránh trừu tượng hoá khi mới có 1 filter).

---

## 3. Quyết định cần chốt trước khi code

| # | Quyết định | Đề xuất | Phương án khác |
|---|---|---|---|
| Q1 | Filter demo | **Negative** (`ColorMatrix`) | Mono/Sepia (cũng ColorMatrix) |
| Q2 | id / tên hiệu ứng | id `finger_frame`, state `finger_frame_negative`; tên "Khung đảo màu" / "Invert Frame" | Tên khác do anh/chị đặt |
| Q3 | Điều kiện kích hoạt | Gesture lỏng (đủ 2 tay) + visual tự kiểm tra "mở" bằng tỉ lệ cái–trỏ có hysteresis + diện tích khung | Gesture chặt (đưa tỉ lệ vào gesture) — mất fade-out (Mục 2 hàng C) |
| Q4 | Live preview: nguồn ảnh trong khung | **Phương án A trước**: giữ `PreviewView`, overlay chỉ vẽ phần trong khung từ bitmap analyzer. Đánh giá ở Mốc 4; nếu đường nối lộ rõ → Mốc 4b (phương án B) | **B**: ẩn `PreviewView` cho hiệu ứng này, overlay vẽ cả ảnh camera từ bitmap analyzer (giống video 100%, nhưng live mờ hơn) — xem rào cản R1 |
| Q5 | Thumbnail | Tạm dùng lại 1 thumbnail có sẵn, ghi chú `// TẠM` trong catalog, thay khi có asset đúng `Asset_Format_Guidelines.md` | Chuẩn bị asset trước |

---

## 4. Các mốc triển khai

Mỗi mốc: build xanh (`./gradlew :app:assembleDebug :app:lintDebug`) + kiểm chứng trên máy thật + báo cáo → chờ xác nhận.

### Mốc 1 — Đưa bitmap camera tới visual (hạ tầng A, B, B1, B2)

**Làm:**
1. `HandFrame`: thêm `var cameraFrame: Bitmap? = null` và
   ```kotlin
   /** Ma trận vẽ bitmap camera (imgWidth × imgHeight, chưa mirror) lên canvas đang vẽ — cùng phép chiếu với px()/py(). */
   fun cameraMatrix(out: Matrix) {
       if (mirrorX) {
           out.setScale(-localScale, localScale)
           out.postTranslate(imgWidth * localScale + localScaleOffsetX, localScaleOffsetY)
       } else {
           out.setScale(localScale, localScale)
           out.postTranslate(localScaleOffsetX, localScaleOffsetY)
       }
   }
   ```
2. `OverlayView`: `@Volatile private var liveCameraFrame: Bitmap?` + `fun setCameraFrame(bitmap: Bitmap?)`; `drawFrame(..., cameraFrame: Bitmap? = null)` gán `frame.cameraFrame = cameraFrame?.takeIf { it.width == imgWidth && it.height == imgHeight }` (chặn trường hợp bitmap và kết quả MediaPipe lệch kích thước — nếu lệch thì visual coi như không có ảnh, không vẽ sai chỗ); `onDraw` truyền `liveCameraFrame`.
3. `CameraRecordFragment`: B1 (trong `collect`), B2 (trong `startRecordingFrameLoop`).

**Kiểm chứng (dùng visual TẠM, gỡ ngay sau mốc):** một `Procedural` tạm vẽ **toàn bộ** `frame.cameraFrame` bằng `cameraMatrix()` với alpha ~50% đè lên preview, gắn tạm vào 1 effect 2 tay.
- Live: ảnh tạm phải chồng khít lên `PreviewView` (chỉ trễ nhẹ khi di chuyển nhanh, không lệch khi đứng yên, không ngược chiều).
- Video: ảnh tạm khớp tuyệt đối nền (cùng bitmap).
- 10 hiệu ứng cũ chạy y như trước (tham số mặc định `null`).

**Rủi ro:** quên `setProjection()` trước khi gọi `cameraMatrix()` → ảnh dồn về góc (cạm bẫy 13.5) — `cameraMatrix()` chỉ được gọi bên trong `draw()`, lúc `drawFrame()` đã nạp projection.

### Mốc 2 — Khung thô: cử chỉ + effect + tứ giác chưa làm mượt (C, D, H, một phần E/G)

**Làm:**
1. `Gestures.twoHandsFrame` (docstring nói rõ vì sao lỏng + vì sao instance riêng), map trong `gestureDisplayMap`, chuỗi `gesture_two_hands_frame` en/vi.
2. `QuadMath.kt`: `sortByAngle(pts: FloatArray)` (8 số, sắp tại chỗ), `shoelaceArea(pts)`.
3. `FingerFrameVisual` bản thô: trong `draw()` lấy 4 đầu ngón **thô** từ `frame.hands` (landmark 4, 8 của 2 tay), sắp theo góc, chiếu `px()/py()`, vẽ **chỉ viền** (`STROKE`). Độ dày nét theo tỉ lệ `canvas.width` (không pixel tuyệt đối — `Code_Walkthrough.md` mục 12 ý 7).
4. `fingerFrameEffect()` (`requiredNumHands = 2`, `soundRes = null`, thumbnail TẠM) + thêm vào `EffectRepository.all`, chuỗi `effect_name_finger_frame` en/vi.

**Kiểm chứng:** effect xuất hiện ở danh sách + tìm kiếm theo tên (vi/en); dialog Action hiện "khung 2 tay" với icon `ic_action_frame_2hands`; viền bám 4 đầu ngón ở live và video; 1 tay → không vẽ gì; không bao giờ ra hình "cái nơ" kể cả khi bắt chéo 2 tay.

### Mốc 3 — Khung "sống": tương ứng, làm mượt, mở/khép, fade (E, F)

**Làm:**
1. `QuadMath.kt`: `bestRotation(sorted, prev)` [T§4.2], `ema(prev, cur, alpha)` [T§5.1].
2. `FingerFrameTracker` (mỗi visual 1 instance — live và recording có tracker riêng, nhận cùng dãy `onHandFrame` nên ra cùng kết quả):
   - `update(hands)`: tính `ratio = thumbIndexPinchRatio` từng tay → hysteresis (`NGƯỠNG_BẬT`, `NGƯỠNG_TẮT`) → lấy 4 đầu ngón (toạ độ **chuẩn hoá**, không gọi `px()` — quy tắc 13.3) → sắp góc → quay vòng khớp frame trước → EMA → diện tích (so với `palmLength²`) → mục tiêu presence → cập nhật `p`.
   - Công bố: tạo `FloatArray(9)` mới (8 toạ độ + `p`) rồi gán vào `@Volatile var snapshot` — đối tượng đã công bố không bao giờ bị sửa nữa, nên thread vẽ đọc luôn nhất quán. (Cấp phát ~30 mảng nhỏ/giây ở **nhịp MediaPipe**, không phải trong `draw()`.)
   - `reset()` khi `setActive(false)` (mất tay): `snapshot = null`, xoá trạng thái làm mượt — lần sau hiện lại từ đầu thay vì "bay" từ vị trí cũ.
3. `FingerFrameVisual.draw()` đọc `snapshot` 1 lần ở đầu hàm (biến cục bộ), `p < 0.01` thì không vẽ.
4. Hằng số khởi điểm (`ALPHA`, 2 ngưỡng, `MIN_AREA_RATIO`, tốc độ fade) đặt ở `companion object`, **kèm log TẠM** in `ratio` từng tay + diện tích để chỉnh theo số liệu thật (quy trình `Camera_X_Hand_Landmarker.md` 11.9).

**Kiểm chứng:** tay đứng yên → viền không rung; xoay khung 360° → không có nhịp "xoắn"; khép ngón → mờ dần; khép/mở ở ngay ngưỡng → không nhấp nháy; tay xa và gần camera đều kích hoạt được; 2 tay chụm sát (khung dẹt) → không vẽ.

### Mốc 4 — Đảo màu trong khung (phần còn lại của G)

**Làm:** trong `draw()`:
- Nếu `frame.cameraFrame == null` → chỉ vẽ viền (không crash, không vẽ sai).
- Cache `BitmapShader` theo **tham chiếu** bitmap: chỉ tạo mới khi `cameraFrame !== lastBitmap` (bitmap đổi mỗi frame camera nên vẫn tạo ~25–30 lần/giây — xem rào cản R4).
- `shader.setLocalMatrix(matrix)` với `frame.cameraMatrix(matrix)`; `paint.colorFilter` = `ColorMatrixColorFilter` đảo màu tạo **1 lần** trong constructor (hằng số cột cuối là **255**, không phải 1 — [T§9.1]); `paint.alpha = (p * 255)`; `paint.isAntiAlias = true`; `canvas.drawPath(quadPath, paint)`; sau đó vẽ viền nét đứt (`DashPathEffect`) + 4 chấm góc.
- `Path`, `Matrix`, `Paint` là field tái dùng, không tạo trong `draw()` (trừ `BitmapShader` — R4).

**Kiểm chứng:**
- Video: vùng trong khung là ảnh đảo màu **khớp liền** với ảnh bên ngoài (không lệch, không có đường nối), mép khung không răng cưa.
- Live (phương án A): ghi lại mức độ khác biệt trong/ngoài khung — độ nét (bitmap 480×640 phóng ~3,75× trên màn 1080×2400) và độ trễ khi di chuyển. **Đây là điểm quyết định Q4** → báo cáo kèm ảnh chụp màn hình/quay màn hình để anh/chị chốt có làm Mốc 4b hay không.

### Mốc 4b — (Chỉ làm nếu chốt ở Mốc 4) Phương án B cho live

**Làm (phác thảo, chi tiết hoá khi cần):** thêm cờ ở `EffectDefinition` (ví dụ `rendersCameraFrame: Boolean = false`); `CameraRecordFragment` ẩn `PreviewView` khi cờ bật (giống nhánh `background != null` đang làm); `OverlayView.drawFrame()` nhánh live vẽ toàn bộ `liveCameraFrame` bằng `cameraMatrix()` **trước** visual (nhánh recording không cần vì Fragment đã vẽ nền). Hệ quả: live = video 100%, nhưng live mờ hơn và fps live = fps analyzer.

⚠️ Cẩn thận: nhánh `effectHasBackground` của vòng lặp ghi **không** được tính cờ mới này là "có background", nếu không vòng ghi sẽ bỏ vẽ nền camera.

### Mốc 5 — Hoàn thiện & đo

**Làm:** chỉnh màu/độ dày viền, tốc độ chạy nét đứt (`phase` theo một đồng hồ riêng của visual — `frame.elapsedMs` chỉ được `ProceduralVisual` ghi, mà `FingerFrameVisual` implement thẳng `EffectVisual` vì cần tự xử lý `setActive()` để `reset()` tracker, trong khi `ProceduralVisual.setActive()` là `final`), chốt các hằng số theo log, **gỡ toàn bộ log TẠM**.

**Đo:** gắn lại tạm `RecordingPerfLogger`/`VideoStatsLogger` theo `Perf_Notes.md` mục 9 — so `work`/fps giữa effect mới và 1 effect cũ 2 tay (`black_hole`) trên máy **nguội** (bài học throttling nhiệt). Thử cả khi bị fallback CPU (hạ tạm ngưỡng RAM như `CameraLoading_Fallback_Plan.md` gợi ý) để xem làm mượt có còn ổn ở ~22 fps.

**Regression:** `Test_Checklist.md` B, C, D (đặc biệt D7 đổi qua lại effect 1 tay/2 tay), J, K, L.

### Mốc 6 — Cập nhật tài liệu (theo quy định dự án)

- `Camera_X_Hand_Landmarker.md`: mục mới (15) — Finger Frame: `cameraFrame`/`cameraMatrix`, gesture lỏng + visual tự quyết, tracker/snapshot, `BitmapShader` thay `clipPath`, các ngưỡng đã đo, debug checklist.
- `Code_Walkthrough.md`: `HandFrame` (field + hàm mới), `OverlayView.drawFrame()` (tham số mới, `setCameraFrame`), 2 chỗ gọi trong `CameraRecordFragment`, file `fingerframe/*`, catalog mới; **sửa luôn chỗ đã lỗi thời** ở mục 1.1 (`displayName` → nay là `nameRes`).
- `AGENTS.md`: cây thư mục (`canvas/fingerframe/`), số hiệu ứng 10 → 11, bài học rút ra; cập nhật commit hash.
- `Test_Checklist.md`: mục mới **R. Finger Frame** (danh sách ở Mục 7).
- `README.md` mục "Danh sách hiệu ứng"; `Design_App_HandAr.md` nếu cần thêm dòng asset thumbnail.

---

## 5. Điều kiện & rào cản cần giải quyết

| # | Rào cản | Mức | Cách xử lý |
|---|---|---|---|
| R1 | **Live preview không cùng nguồn với ảnh trong khung.** `PreviewView` hiển thị luồng Preview (nét, trễ thấp); ảnh trong khung lấy từ bitmap analyzer **480×640** (CameraX mặc định, `Perf_Notes.md` mục 5) phóng to ~3,75× và trễ thêm thời gian xử lý → trong khung mờ hơn và chậm hơn bên ngoài khi di chuyển | Cao (trải nghiệm) | Mốc 4 đo thực tế → chốt A hoặc B (Mốc 4b). Video **không** bị vấn đề này (nền và khung cùng 1 bitmap). Hướng dài hạn (ngoài demo): tăng độ phân giải `ImageAnalysis` — đụng hiệu năng MediaPipe, cần đo riêng |
| R2 | **`drawFrame()` không vẽ visual khi không state nào khớp** → nếu điều kiện "mở" nằm trong gesture thì mất fade-out | Trung bình | Gesture lỏng (Q3). Giới hạn còn lại chấp nhận: rút **hẳn** 1 tay khỏi khung hình thì khung tắt ngay (không fade) |
| R3 | **Bài học cử chỉ "khung máy ảnh" đã gỡ** (mục 11.6): góc vuông khó giữ, che khuất đầu ngón làm sai landmark | Trung bình | Không đo góc; chỉ tỉ lệ cái–trỏ + diện tích khung; ngưỡng lấy từ log; hysteresis chống chập chờn |
| R4 | **Quy tắc "không cấp phát trong `draw()`"** vs `BitmapShader` gắn cứng 1 bitmap, mà bitmap camera đổi mỗi frame | Thấp | Cache theo tham chiếu, chỉ tạo khi bitmap đổi (~25–30 object nhỏ/giây, nhỏ hơn rất nhiều so với 2 bitmap ~1,2 MB/frame analyzer đang tạo — `Perf_Notes.md`). Phương án dự phòng không cấp phát: `clipPath` + `drawBitmap(bitmap, matrix, paint)` — đổi lại mép răng cưa. Ghi chú rõ ngoại lệ trong code |
| R5 | **Đa luồng**: `onHandFrame()` (main) ghi trạng thái, `draw()` bản recording (recording thread) đọc | Trung bình | Snapshot bất biến + `@Volatile` (Mốc 3). Tuyệt đối không đọc trạng thái đang ghi dở |
| R6 | **Thứ tự `hands[0]/hands[1]` và `handedness` không ổn định** giữa các frame | Trung bình | Không bám theo tay; khớp góc bằng phép quay vòng gần nhất [T§4.2] |
| R7 | **Bitmap dùng chung nhiều nơi** (Fragment, MediaPipe, vòng ghi, overlay) | Cao nếu sai | Visual **không bao giờ** `recycle()` hay sửa bitmap; chỉ đọc qua shader. Kích thước bitmap ≠ `imgWidth/imgHeight` thì bỏ qua (Mốc 1) |
| R8 | **Thang màu `ColorMatrix` là 0–255** — chép công thức shader (0–1) sang sẽ ra ảnh gần như đen | Thấp | Hằng số cột cuối = 255 [T§9.1]; kiểm chứng bằng mắt ở Mốc 4 |
| R9 | **Đổi model 1 tay ↔ 2 tay** khi chuyển effect phải `close()` + tạo lại `HandLandmarker` (chậm, nhất là GPU) | Thấp (đã có xử lý) | Loading overlay + chạy nền đã có (`Camera_X_Hand_Landmarker.md` mục 14); chỉ cần test lại D7 |
| R10 | **Kích thước canvas live ≠ video** | Thấp | Mọi độ dày/bán kính tính theo `canvas.width`; toạ độ luôn qua `px()/py()`/`cameraMatrix()` |
| R11 | **Hai bộ nhận diện gesture độc lập** (`OverlayView` & `handleGesture`) | Thấp | Demo không có tiếng nên chỉ `OverlayView` có tác dụng; nếu sau này thêm tiếng, nhớ `handleGesture` có debounce 200ms riêng (`Code_Walkthrough.md` mục 12) |
| R12 | **Bug "ma" SIGBUS chưa xác nhận** (`AGENTS.md` mục 5, `Camera_X_Hand_Landmarker.md` 14.4) | Không liên quan trực tiếp | Nếu gặp crash native khi test effect 2 tay: `adb logcat -b crash` trước, không sửa theo suy đoán |

---

## 6. Ngoài phạm vi demo (giai đoạn sau)

1. **Thêm filter ColorMatrix** (Mono, Sepia, nhuộm màu): lúc này mới tách interface filter (ví dụ `fun configure(paint: Paint)`), mỗi filter 1 state hoặc 1 effect.
2. **Filter AGSL** (Thermal, Neon, Glitch, Pixelate…) [T§9–10]: chỉ API ≥ 33 (`Build.VERSION.SDK_INT >= TIRAMISU`), máy cũ rơi về ColorMatrix hoặc ẩn filter; kiểm chứng `RuntimeShader` trên canvas `lockHardwareCanvas()` trước tiên. Glitch/Pixelate có thể làm trước bằng Canvas thuần (API 28) [T§9.4–9.5].
3. **Đổi filter bằng cử chỉ** (FingerLens dùng búng tay): không dùng mic (quyết định kiến trúc `AGENTS.md` mục 3); nếu làm thì bằng cử chỉ landmark — là cử chỉ **động** (cần nhớ frame trước), xem mục 11.8 tài liệu Hand.
4. **Dán ảnh/video vào khung** bằng homography [T§8].
5. **1€ Filter** dùng chung tay + mặt nếu EMA không đủ [T§5.3].

---

## 7. Checklist test dự kiến (đưa vào `Test_Checklist.md` mục R ở Mốc 6)

| # | Kịch bản | Kỳ vọng |
|---|---|---|
| R1 | Dựng khung giữa màn hình, đứng yên 5s | Khung bám đúng 4 đầu ngón, viền không rung, trong khung đảo màu |
| R2 | Di khung ra 4 góc màn hình | Không lệch, không ngược chiều (mirror đúng) |
| R3 | Xoay khung 360° chậm và nhanh | Không có nhịp "xoắn"/đổi góc |
| R4 | Khép ngón cái–trỏ từ từ rồi mở lại; giữ ở đúng ngưỡng | Mờ dần/hiện dần; không nhấp nháy ở ngưỡng |
| R5 | Chỉ 1 tay; rút hẳn 1 tay ra khỏi hình | Không vẽ gì; khung tắt (chấp nhận không fade — R2) |
| R6 | Bắt chéo 2 tay, đổi chỗ 2 tay | Vẫn là tứ giác hợp lệ, không "cái nơ" |
| R7 | Tay sát camera / xa camera | Kích hoạt được ở cả hai |
| R8 | 2 tay chụm sát (khung rất dẹt) | Không vẽ |
| R9 | Quay video 20s với các thao tác trên | Video khớp live; trong khung liền mạch với ngoài khung |
| R10 | Đổi qua lại `finger_frame` ↔ effect 1 tay nhiều lần | Không ANR, loading overlay đúng (D7/D9) |

---

## 8. Tiến độ

- [x] Duyệt kế hoạch + chốt Q1–Q5 (01/10/2026: đúng như đề xuất; Q5 thumbnail mượn `black_hole_thumbnail`)
- [x] Mốc 1 — Đưa bitmap camera tới visual (01/10/2026, đã test trên máy thật — xem Mục 9)
- [x] Mốc 2 — Khung thô (cử chỉ, effect, tứ giác) (01/10/2026, đã test trên máy thật — xem Mục 9)
- [x] Mốc 3 — Tương ứng, làm mượt, mở/khép, fade (01/10/2026, đã test trên máy thật — xem Mục 9; còn 2 việc tinh chỉnh dồn sang Mốc 5)
- [ ] Mốc 4 — Đảo màu trong khung + đánh giá live (chốt Q4)
- [ ] Mốc 4b — Phương án B cho live (chỉ khi cần)
- [ ] Mốc 5 — Hoàn thiện, đo hiệu năng, regression
- [ ] Mốc 6 — Cập nhật tài liệu

---

## 9. Nhật ký các mốc đã xong

### Mốc 1 — Đưa bitmap camera tới visual (01/10/2026)

**Kết quả test trên máy thật** (visual gỡ lỗi tạm vẽ toàn bộ ảnh camera alpha 50% đè lên preview):
- Live: ảnh phủ chồng đúng lên preview (không lệch, không ngược chiều, không sai tỉ lệ). Có dư ảnh nhẹ vì người/tay không đứng yên tuyệt đối — đúng dự đoán rủi ro R1 (bitmap luồng phân tích trễ hơn `PreviewView`); số liệu này dùng để chốt Q4 ở Mốc 4.
- Video ghi ra: không dư ảnh (nền và lớp phủ cùng một bitmap) → đúng kỳ vọng.
- 10 hiệu ứng cũ: không thấy ảnh hưởng (tham số `cameraFrame` mặc định `null`).

**File thay đổi:** `HandFrame.kt` (+`cameraFrame`, +`cameraMatrix()`), `OverlayView.kt` (+`setCameraFrame()`, tham số `cameraFrame` của `drawFrame()`), `CameraRecordFragment.kt` (2 dòng), `EffectRepository.kt`, `strings.xml` (en/vi); file mới `catalog/FingerFrameEffect.kt`, `visual/canvas/fingerframe/CameraFrameDebugVisual.kt` (TẠM, xoá ở Mốc 2).

**Doc đã cập nhật cùng commit:** `Code_Walkthrough.md` (mục 3.2, 3.9 mới, 5.2, 5.4, 5.5, 6.4), `AGENTS.md` (bản đồ docs, cây thư mục). **Chưa cập nhật** `Camera_X_Hand_Landmarker.md` — theo plan làm ở Mốc 6 khi tính năng hoàn chỉnh.

**Cách commit (đã chốt):** chỉ `git add` đúng các file liệt kê dưới đây, **không** `git add .`/`-A` — working tree đang có ~90 file hiện "modified" chỉ do khác kiểu xuống dòng (CRLF/LF) và `.idea/`, không phải thay đổi thật.

```bash
git add app/src/main/java/com/example/handar/effect/visual/HandFrame.kt \
        app/src/main/java/com/example/handar/OverlayView.kt \
        app/src/main/java/com/example/handar/ui/camera/CameraRecordFragment.kt \
        app/src/main/java/com/example/handar/effect/EffectRepository.kt \
        app/src/main/java/com/example/handar/effect/catalog/FingerFrameEffect.kt \
        app/src/main/java/com/example/handar/effect/visual/canvas/fingerframe/CameraFrameDebugVisual.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-vi/strings.xml \
        docs/Finger_Frame_Filter_Theory.md docs/Finger_Frame_Filter_Plan.md \
        docs/Code_Walkthrough.md docs/AGENTS.md
git diff --cached --stat        # kiểm tra: đúng 12 file, HandFrame.kt chỉ ~+24 dòng (nếu cả file đổi là sai kiểu xuống dòng)
git commit
```

Thông điệp commit:

```text
feat: pass camera bitmap to effect visuals (Finger Frame step 1)

- HandFrame: add cameraFrame + cameraMatrix() (mirror + center-crop, same projection as px()/py())
- OverlayView: setCameraFrame() for live, drawFrame(cameraFrame) for recording;
  ignore a bitmap whose size differs from imgWidth/imgHeight
- CameraRecordFragment: push latestCameraBitmap before setResult(); pass the same
  bitmap used as video background to drawFrame()
- add finger_frame effect (TEMP: debug visual, anyHandPresent gesture, borrowed
  thumbnail) with vi/en names
- docs: add Finger_Frame_Filter_Theory/Plan, update Code_Walkthrough and AGENTS

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01V118ZASe3XQucTozhvQGEQ
```

Sau commit: dòng "Cập nhật lần cuối tại commit" ở đầu `Code_Walkthrough.md` và `AGENTS.md` cần trỏ tới hash mới — gửi hash cho mình, mình cập nhật (theo quy ước repo thì thường thành một commit `doc: update commit hash` nhỏ riêng).

### Mốc 2 — Khung thô: cử chỉ + effect + tứ giác (01/10/2026)

**Kết quả test trên máy thật:** khung viền hiển thị đúng theo 4 đầu ngón. Quan sát: khi tay di chuyển nhanh, viền vẽ **chậm/trễ theo tay** và rung — đúng dự đoán vì đang dùng landmark thô, **để xử lý ở Mốc 3** (tracker: EMA, tương ứng điểm, hysteresis, fade).

**File thay đổi:** `Gesture.kt` (+`twoHandsFrame`), `GestureDisplay.kt`, `strings.xml` (en/vi: `gesture_two_hands_frame`), `FingerFrameEffect.kt` (đổi sang `twoHandsFrame` + `FingerFrameVisual`); file mới `visual/canvas/fingerframe/QuadMath.kt`, `FingerFrameVisual.kt`; **xoá** `CameraFrameDebugVisual.kt` (visual tạm của Mốc 1).

**Doc đã cập nhật cùng commit:** `Code_Walkthrough.md` (bảng cử chỉ, mục 3.8/3.9, hash đầu file `a70960b`), `AGENTS.md` (cây thư mục, bài học "cử chỉ lỏng", hash đầu file). Lưu ý: 2 doc `Finger_Frame_Filter_Theory.md` và `Finger_Frame_Filter_Plan.md` **chưa nằm trong commit `a70960b`** nên phải `git add` ở commit này.

**Cách commit:**

```bash
git add app/src/main/java/com/example/handar/effect/gesture/Gesture.kt \
        app/src/main/java/com/example/handar/effect/gesture/GestureDisplay.kt \
        app/src/main/java/com/example/handar/effect/catalog/FingerFrameEffect.kt \
        app/src/main/java/com/example/handar/effect/visual/canvas/fingerframe/QuadMath.kt \
        app/src/main/java/com/example/handar/effect/visual/canvas/fingerframe/FingerFrameVisual.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-vi/strings.xml \
        docs/Finger_Frame_Filter_Theory.md docs/Finger_Frame_Filter_Plan.md \
        docs/Code_Walkthrough.md docs/AGENTS.md
git rm app/src/main/java/com/example/handar/effect/visual/canvas/fingerframe/CameraFrameDebugVisual.kt
git diff --cached --stat   # kiểm tra: 12 file (11 sửa/thêm + 1 xoá), không có file lạ
git commit
```

(Nếu đã xoá `CameraFrameDebugVisual.kt` bằng tay thì `git rm` vẫn chạy được; hoặc dùng `git add -A <đường dẫn file đó>`.)

Thông điệp commit:

```text
feat: Finger Frame step 2 - two-hands frame gesture and rough quad outline

- Gestures.twoHandsFrame (loose: 2 hands present) + GestureDisplay/strings (vi/en)
- QuadMath: sortByAngle (no bow-tie) + shoelaceArea
- FingerFrameVisual: draw raw 4-fingertip quad outline only (no smoothing yet)
- fingerFrameEffect now uses twoHandsFrame + FingerFrameVisual; remove temp CameraFrameDebugVisual
- docs: Theory/Plan, Code_Walkthrough, AGENTS (commit hash a70960b, lessons)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01V118ZASe3XQucTozhvQGEQ
```

Sau commit: gửi hash để cập nhật dòng "Cập nhật lần cuối tại commit" trong `Code_Walkthrough.md` và `AGENTS.md`.

### Mốc 3 — Khung "sống" (01/10/2026)

**Thay đổi so với kế hoạch gốc:** làm mượt dùng **EMA thích nghi theo tốc độ** (kiểu 1€ Filter, Theory §5.3) thay vì EMA alpha cố định, vì Mốc 2 đã ghi nhận viền trễ khi tay nhanh — EMA cố định khử rung nhưng làm trễ nặng hơn.

**File:** `QuadMath.kt` (+`bestRotation`, +`adaptiveAlpha`), mới `FingerFrameTracker.kt`, `FingerFrameVisual.kt` (viết lại: dùng tracker, `setActive`→`reset`, `alpha = p`).

**Hằng số khởi điểm** (`FingerFrameTracker.companion`, chỉnh theo log tag `FingerFrameDbg`): `RATIO_ON 0.70`, `RATIO_OFF 0.45`, `MIN_AREA_RATIO 0.60` (tắt khi < 70% mức này), `ALPHA_MIN 0.35`, `SPEED_REF 0.08`, `FADE_IN 150ms`, `FADE_OUT 250ms`.

**Kết quả test trên máy thật:**
- Viền **mượt, hết rung** khi tay đứng yên. ✔
- **Hiện/mờ dần đúng** theo khép/mở ngón (hysteresis không nhấp nháy). ✔
- Tay **gần/xa camera** đều ổn định. ✔
- **Video ghi ra khớp live.** ✔
- ⚠️ **Vẫn còn trễ khi tay di chuyển nhanh.** Làm mượt thích nghi đã không còn là nguyên nhân chính — phần trễ còn lại đến từ **giới hạn tốc độ của đường ống** (MediaPipe + luồng analyzer ~25–30 fps, mỗi frame mới chỉ có 1 vị trí; vị trí vẽ luôn là vị trí của frame đã xử lý xong). Hướng xử lý để ở Mốc 5: dự đoán/ngoại suy 1 frame theo vận tốc (khi `dist` lớn), và đo thử `ALPHA_MIN`/`SPEED_REF`.
- ⚠️ **Ngưỡng ngừng vẽ theo diện tích còn hơi lớn** (khung nhỏ hơn mong muốn vẫn bị ẩn sớm) → hạ `MIN_AREA_RATIO` (hiện 0.60; tắt ở 0.60×0.70) theo log `FingerFrameDbg`, làm ở Mốc 5.

**File thay đổi:** `QuadMath.kt` (+`bestRotation`, +`adaptiveAlpha`), mới `FingerFrameTracker.kt`, `FingerFrameVisual.kt` (viết lại).

**Doc đã cập nhật cùng commit:** `Code_Walkthrough.md` (mục 3.9), `AGENTS.md` (cây thư mục, bài học về độ trễ làm mượt).

**Cách commit:**

```bash
git add app/src/main/java/com/example/handar/effect/visual/canvas/fingerframe/QuadMath.kt \
        app/src/main/java/com/example/handar/effect/visual/canvas/fingerframe/FingerFrameTracker.kt \
        app/src/main/java/com/example/handar/effect/visual/canvas/fingerframe/FingerFrameVisual.kt \
        docs/Finger_Frame_Filter_Plan.md docs/Code_Walkthrough.md docs/AGENTS.md
git diff --cached --stat   # kiểm tra: đúng 6 file, không có file lạ
git commit
```

Thông điệp commit:

```text
feat: Finger Frame step 3 - quad tracker with adaptive smoothing and fade

- QuadMath: bestRotation (cyclic corner matching) + adaptiveAlpha (speed-based smoothing)
- FingerFrameTracker: thumb-index ratio hysteresis, area gate, presence fade in/out,
  freeze corners while closing, immutable @Volatile snapshot for the recording thread
  (TEMP debug log, tag FingerFrameDbg)
- FingerFrameVisual: use tracker, alpha = presence, reset on deactivate
- docs: Plan, Code_Walkthrough, AGENTS

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01V118ZASe3XQucTozhvQGEQ
```

Sau commit: gửi hash để cập nhật dòng "Cập nhật lần cuối tại commit" trong `Code_Walkthrough.md` và `AGENTS.md`.
