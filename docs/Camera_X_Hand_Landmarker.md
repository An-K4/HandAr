# TÀI LIỆU HƯỚNG DẪN MÔ PHỎNG HAND AR
> **Công nghệ chủ đạo:** CameraX | Google MediaPipe Tasks Vision | Android Canvas 2D | Hình học giải tích ứng dụng.

> **📝 Đã audit & cập nhật:** Bổ sung công thức Offset cho chế độ `CENTER_CROP` (Mục 3, Công thức 1–2), thêm mục về xử lý Rotation & Mirror của CameraX (Mục 3, Công thức 1.5), sửa khuyến nghị Delegate GPU/CPU theo dữ liệu thực tế (Mục 2.4, Mục 6 rule 4), làm rõ claim về `RGBA_8888` (Mục 4.2), thêm **Mục 7 — Vẽ tài nguyên động (GIF/Sprite) đúng vị trí trong OverlayView**, thêm **Mục 8 — Ghi video (MediaRecorder) tích hợp với AR Overlay**, mở rộng **Mục 8.3/8.8** với 2 cạm bẫy mới (race condition trên `GifLayer` dùng chung, mirror lật cả Canvas), thêm **Mục 9 — Kiến trúc Mix Audio/Video thực tế (MediaCodec + MediaMuxer)**, mô tả snapshot tại commit `c62037f`, và mới nhất: **Mục 10 — Refactor hiệu năng & bỏ mic (hậu commit `c62037f`)**, đúc kết từ đợt tối ưu resolution/bitrate/threading theo tiêu chuẩn mentor — bao gồm quyết định bỏ hẳn mic (thay bằng `EffectAudioClock`), 2 bug mới phát sinh ngay khi tách thread theo bài học Grafika (sleep tính sai gây tụt fps thật, thiếu 1 dấu âm gây "GIF chồng GIF" qua cơ chế buffer xoay vòng của `Surface`), race condition `MediaMuxer` cần đủ 2 track mới `start()` khi Record/Stop quá nhanh, và quyết định chặn ở tầng UX (thời lượng ghi tối thiểu) thay vì tiếp tục đuổi theo race condition ở tầng encoder.

---

## MỤC LỤC
1. [Kiến trúc hệ thống tổng quan (System Architecture)](#1-kiến-trúc-hệ-thống-tổng-quan)
2. [Google MediaPipe Hand Landmarker](#2-google-mediapipe-hand-landmarker)
   - Bản đồ 21 điểm mốc giải phẫu
   - Hệ tọa độ chuẩn hóa (Normalized Coordinates)
   - Cấu trúc dữ liệu `List<List<NormalizedLandmark>>`
   - Cấu hình thực thi tối ưu
3. [Nền tảng toán học hình học giải tích trong AR](#3-nền-tảng-toán-học-hình-học-giải-tích-trong-ar)
   - Công thức 1: Ánh xạ chuẩn hóa sang Pixel màn hình (kèm Offset cho chế độ CENTER_CROP)
   - Công thức 1.5: Xử lý Rotation & Mirror trước khi vào Pipeline toán học
   - Công thức 2: Tìm tâm lòng bàn tay (Tọa độ trung điểm)
   - Công thức 3: Đo độ sâu & Tỉ lệ khiên phép (Khoảng cách Euclid)
   - Công thức 4: Nhận diện Nắm / Xòe tay (Bất đẳng thức khoảng cách hướng tâm)
   - Công thức 5: Ma trận xoay 2D & Phép biến đổi Affine
   - Công thức 6: Phân bố góc đều $360^\circ / N$
   - Công thức 7 (Nâng cao): Vector pháp tuyến lòng bàn tay (3D Cross Product)
4. [Android CameraX - Quản lý phần cứng & Bơm dữ liệu](#4-android-camerax---quản-lý-phần-cứng--bơm-dữ-liệu)
   - Các đối tượng cốt lõi (`ProcessCameraProvider`, `Preview`, `ImageAnalysis`, `ImageProxy`)
   - Chiến lược Backpressure & Định dạng màu RGBA
5. [Đồ họa Android Canvas & OverlayView](#5-đồ-họa-android-canvas--overlayview)
   - Cơ chế "Tấm kính trong suốt"
   - Cọ vẽ (`Paint`) và Bề mặt vẽ (`Canvas`)
   - Vòng lặp hoạt ảnh (`invalidate` vs `postInvalidateOnAnimation`)
   - Cơ chế Choreographer & Z-Index
6. [Bảng quy tắc "Sống còn" & Hậu quả kỹ thuật khi vi phạm](#6-bảng-quy-tắc-sống-còn--hậu-quả-kỹ-thuật-khi-vi-phạm)
7. [Vẽ tài nguyên động (GIF/Sprite) đúng vị trí trong OverlayView](#7-vẽ-tài-nguyên-động-gifsprite-đúng-vị-trí-trong-overlayview)
   - 7.1. Vì sao không thể `drawBitmap` trực tiếp cho GIF
   - 7.2. Pipeline chuẩn: Buffer trung gian + Matrix
   - 7.3. Cạm bẫy 1: Hardware Bitmap không tương thích Software Canvas
   - 7.4. Cạm bẫy 2: `AnimatedImageDrawable` không tự scale theo `setBounds()`
   - 7.5. Cạm bẫy 3: Quên `eraseColor` gây bóng ma (ghosting)
   - 7.6. Fit (`min`) vs Crop (`max`) khi chuẩn hoá GIF về khung vuông
   - 7.7. Nguyên tắc thống nhất: PNG tĩnh vs GIF động khác nhau ở đâu
   - 7.8. Quy trình chẩn đoán lệch vị trí (Debug Checklist)
8. [Ghi video (MediaRecorder) tích hợp với AR Overlay](#8-ghi-video-mediarecorder-tích-hợp-với-ar-overlay)
   - 8.1. Kiến trúc: 2 Surface độc lập, 2 mục đích khác nhau
   - 8.2. Cạm bẫy 1: Guard-clause bị đảo ngược logic
   - 8.3. Cạm bẫy 2: Race Condition giữa Analyzer Thread và UI Thread
   - 8.4. Cạm bẫy 3: `@RequiresApi` không hề chặn runtime
   - 8.5. Cạm bẫy 4: File thiếu phần mở rộng khiến hệ thống "không nhận diện"
   - 8.6. Cạm bẫy 5: Scoped Storage chặn duyệt `Android/data/<package>` dù file tồn tại thật
   - 8.7. Cạm bẫy 6: Preview bị Mirror nhưng Frame ghi hình thì không
   - 8.8. Cạm bẫy 7: Video quay ra bị ngược chiều so với Preview lúc quay — vì sao "lật cả Canvas" là cách sai
   - 8.9. Quy trình chẩn đoán lỗi quay video AR (Debug Checklist)
9. [Kiến trúc Mix Audio/Video thực tế (MediaCodec + MediaMuxer)](#9-kiến-trúc-mix-audiovideo-thực-tế-mediacodec--mediamuxer)
   - 9.1. Vì sao phải bỏ `MediaRecorder`, chuyển sang `MediaCodec` + `MediaMuxer`
   - 9.2. Sơ đồ luồng dữ liệu tổng quan
   - 9.3. Vai trò từng class (bảng tra cứu nhanh)
   - 9.4. `VideoEncoderWrapper` & `AudioEncoderWrapper` — bọc `MediaCodec` thô
   - 9.5. `MuxerCoordinator` — chờ đủ 2 track mới `start()`
   - 9.6. `MicReader` — đọc PCM thô từ mic trên luồng riêng *(⚠️ class này đã bị xóa ở Mục 10 — xem 10.2)*
   - 9.7. `AudioMixer` — trộn PCM mic với PCM hiệu ứng, cơ chế lặp vô hạn
   - 9.8. `SoundEffectPlayer` — phát trực tiếp ra loa, độc lập với pipeline ghi hình
   - 9.9. `VideoRecorder` — class điều phối trung tâm
   - 9.10. Cơ chế `ActiveEffect` + `startPos` — đồng bộ pha âm thanh khi bắt đầu ghi giữa chừng hiệu ứng
   - 9.11. Timestamp video vs audio — 2 cách tính khác nhau, vì sao
10. [Refactor hiệu năng & bỏ mic (hậu commit `c62037f`)](#10-refactor-hiệu-năng--bỏ-mic-hậu-commit-c62037f)
    - 10.1. Vì sao bỏ mic — quyết định sản phẩm, không chỉ kỹ thuật
    - 10.2. `EffectAudioClock` — đồng hồ âm thanh ảo thay cho `AudioRecord`
    - 10.3. Cạm bẫy: tham số tính đúng nhưng quên dùng (bitrate "ma")
    - 10.4. VBR vs CBR — đo đạc trước khi tối ưu, đừng đoán
    - 10.5. `OverlayView` phải tính scale theo canvas thực tế đang vẽ, không theo View màn hình
    - 10.6. Tách thread theo Grafika — và 2 bug mới xuất hiện ngay khi tách
    - 10.7. PTS baseline lazy-init + đồng bộ audio/video tại đúng 1 mốc
    - 10.8. `MediaMuxer` cần đủ 2 track mới `start()` — race condition khi Record/Stop quá nhanh
    - 10.9. Quyết định cuối: chặn ở tầng UX thay vì đuổi race condition ở tầng encoder
    - 10.10. `VideoStatsLogger` — công cụ đo thật, đừng tin số fps khai báo
    - 10.11. Cạm bẫy phụ: `BuildConfig` trùng tên từ nhiều thư viện
    - 10.12. Quy trình chẩn đoán mở rộng (Debug Checklist)
11. [Nhận diện cử chỉ 2 tay](#11-nhận-diện-cử-chỉ-2-tay)
    - 11.1. Vì sao phải đổi kiến trúc: từ "mỗi tay tự xét" sang "xét đồng thời tất cả các tay"
    - 11.2. Công thức 8: Tâm & bán kính khi có nhiều tay (trung điểm, và vì sao mirror áp sau vẫn đúng)
    - 11.3. Cạm bẫy: gõ nhầm biến khi mở rộng công thức — `normMidY` thay vì `normMidX`
    - 11.4. Công thức 9: Độ cong ngón tay không phụ thuộc hướng cong (Finger Curl Ratio)
    - 11.5. Công thức 10: Giao cắt 2 đoạn thẳng (Segment Intersection / Orientation Test)
    - 11.6. Công thức 11: Góc giữa 2 vector qua Dot Product — đã gỡ khỏi app, giữ làm tài liệu tham khảo
    - 11.7. Hai cử chỉ tĩnh 2 tay cụ thể: trái tim, dấu X
    - 11.8. Giới hạn đã biết & hướng mở rộng tiếp theo (cử chỉ động)
    - 11.9. Quy trình chẩn đoán cử chỉ 2 tay (Debug Checklist)
12. [Vẽ lớp Nền (Background) thay thế Camera](#12-vẽ-lớp-nền-background-thay-thế-camera)
    - 12.1. Kiến trúc: `OverlayView` đảm nhiệm 2 trách nhiệm độc lập, không chia sẻ trạng thái
    - 12.2. Cạm bẫy 1: Quên bước B — tính `Matrix` xong nhưng không gọi `drawBitmap`
    - 12.3. Cạm bẫy 2: Tái phạm lỗi `AnimatedImageDrawable` không tự scale theo `setBounds()` (biến thể của Mục 7.4)
    - 12.4. Cạm bẫy 3: Lớp nền phải phủ kín tuyệt đối — vì sao `drawBitmap` phủ kín kích thước vẫn chưa đủ
    - 12.5. Quy tắc asset: không dùng chung file giữa `EffectAsset` và `EffectBackground`
    - 12.6. Quy trình chẩn đoán lớp Nền (Debug Checklist)

---

## 1. KIẾN TRÚC HỆ THỐNG TỔNG QUAN

Hệ thống Magic Hand AR hoạt động theo mô hình đường ống tuần hoàn (Continuous Pipeline) với tần số 30–60 FPS:

```text
  [ CẢM BIẾN CAMERA ]
          │
          ▼  (Frame thô: ImageProxy)
  [ CameraX: ImageAnalysis ]
          │
          ▼  (Chuyển đổi sang MPImage)
  [ MediaPipe HandLandmarker ]  <─── (Chạy ngầm trên GPU Worker Thread)
          │
          ▼  (HandLandmarkerResult: 21 Landmarks x, y, z)
  [ Toán học giải tích ]        <─── (Xác định Tâm, Bán kính, Cử chỉ Nắm/Xòe)
          │
          ▼  (Tọa độ Pixel thực tế)
  [ OverlayView: Canvas ]       <─── (Render Khiên / Quả cầu lửa trên UI Thread)
```

Sự phân tách trách nhiệm:
- **MediaPipe:** Giải bài toán Computer Vision (Định vị 21 khớp xương bàn tay).
- **Hình học giải tích:** Giải bài toán logic tương tác (Biến 21 điểm thô thành cơ chế phép thuật).
- **Canvas / Game Engine:** Giải bài toán hiển thị đồ họa (Vẽ khiên, hạt sáng, xoay góc).

---

## 2. GOOGLE MEDIAPIPE HAND LANDMARKER

### 2.1. Bản đồ 21 điểm mốc giải phẫu (Hand Landmark Map)

```text
                           [12] TIP (Đầu ngón giữa)
                            │
                           [11] DIP
                            │
       [8] TIP             [10] PIP                   [16] TIP
       (Đầu ngón trỏ)       │                         (Áp út)
         │                  │                           │
        [7] DIP             │                          [15] DIP
         │                 [9] MCP                      │                 [20] TIP
        [6] PIP           /   │   \                    [14] PIP           (Đầu ngón út)
         │               /    │    \                    │                   │
   [4]   │              /     │     \                   │                  [19] DIP
  (Cái) [5] MCP ───────       │      ───────────────  [13] MCP              │
    │    │ \                  │                     /   │              [18] PIP
   [3]   │   \                │                   /     │               │
    │    │     \              │                 /       │              [17] MCP
   [2]   │       \       [TÂM LÒNG BÀN TAY]   /         │             /
     \   │         \       (VÙNG LÀM PHÉP)  /           │            /
      [1]            \        │           /             │           /
        \              \      │         /               │          /
          \              \    │       /                 │         /
            \              \  │     /                   │        /
              ───────────────[0]────────────────────────────────
                              WRIST (Cổ tay)
```

* **Cổ tay:** `[0]`
* **Ngón cái:** `[1]` CMC $\rightarrow$ `[2]` MCP $\rightarrow$ `[3]` IP $\rightarrow$ **`[4]` TIP**
* **Ngón trỏ:** `[5]` MCP $\rightarrow$ `[6]` PIP $\rightarrow$ `[7]` DIP $\rightarrow$ **`[8]` TIP**
* **Ngón giữa:** `[9]` MCP $\rightarrow$ `[10]` PIP $\rightarrow$ `[11]` DIP $\rightarrow$ **`[12]` TIP**
* **Ngón áp út:** `[13]` MCP $\rightarrow$ `[14]` PIP $\rightarrow$ `[15]` DIP $\rightarrow$ **`[16]` TIP**
* **Ngón út:** `[17]` MCP $\rightarrow$ `[18]` PIP $\rightarrow$ `[19]` DIP $\rightarrow$ **`[20]` TIP**

### 2.2. Hệ tọa độ chuẩn hóa (Normalized Coordinates)
Mỗi điểm mốc $P$ được biểu diễn dưới dạng $P(x, y, z)$:
* **$x \in [0.0, 1.0]$:** Vị trí tương đối theo chiều ngang của ảnh camera ($0.0$ mép trái, $1.0$ mép phải).
* **$y \in [0.0, 1.0]$:** Vị trí tương đối theo chiều dọc của ảnh camera ($0.0$ mép trên, $1.0$ mép dưới).
* **$z$:** Độ sâu tương đối so với khớp cổ tay `[0]`. Trục $z$ hướng về phía camera; giá trị $z$ càng âm thì điểm đó càng gần camera hơn cổ tay.

### 2.3. Cấu trúc dữ liệu `List<List<NormalizedLandmark>>`
Là danh sách 2 chiều:
* **Tầng ngoài (`List<...>`)**: Đại diện cho số bàn tay được phát hiện trong khung hình.
    * Không có tay: `size = 0`
    * 1 tay: `size = 1`
    * 2 tay: `size = 2`
* **Tầng trong (`List<NormalizedLandmark>`)**: Luôn có kích thước cố định là **đúng 21 phần tử** tương ứng từ index `0` đến `20`.

### 2.4. Cấu hình thực thi tối ưu
```kotlin
val baseOptions = BaseOptions.builder()
    .setModelAssetPath("hand_landmarker.task")
    .setDelegate(Delegate.CPU) // Mặc định & ổn định nhất trên đa số thiết bị (xem lưu ý bên dưới)
    .build()

val options = HandLandmarker.HandLandmarkerOptions.builder()
    .setBaseOptions(baseOptions)
    .setRunningMode(RunningMode.LIVE_STREAM) // Bắt buộc cho Camera thời gian thực
    .setNumHands(1) // Giảm tải xử lý nếu chỉ làm phép 1 tay
    .setMinHandDetectionConfidence(0.5f)
    .setMinTrackingConfidence(0.5f)
    .setResultListener { result, inputImage -> /* Nhận kết quả bất đồng bộ */ }
    .build()
```

> **Về lựa chọn CPU vs GPU delegate:** Khác với hiểu lầm phổ biến rằng "GPU luôn nhanh hơn và bắt buộc phải dùng", trên thực tế:
> - Nếu không chỉ định delegate, MediaPipe mặc định dùng **CPU**.
> - GPU delegate có thể khiến thời gian **khởi tạo model chậm hơn đáng kể** (có báo cáo lên tới ~30 giây so với vài giây của CPU), do phải build shader/GL context.
> - Ở một số phiên bản MediaPipe gần đây, chênh lệch tốc độ xử lý thực tế giữa CPU và GPU đã **không còn đáng kể** như trước.
> - GPU delegate bắt buộc phải được **khởi tạo và gọi trên cùng một thread** (ràng buộc `EGL context`), phức tạp hơn CPU delegate (CPU có thể khởi tạo ở main thread rồi gọi ở background thread bình thường — xem Bảng quy tắc, mục 5).
>
> → Nên coi `Delegate.CPU` là lựa chọn mặc định an toàn; chỉ cân nhắc `Delegate.GPU` khi đã đo đạc thực tế trên thiết bị mục tiêu và xác nhận có lợi ích rõ ràng, đồng thời sẵn sàng xử lý đúng ràng buộc về thread.

---

## 3. NỀN TẢNG TOÁN HỌC HÌNH HỌC GIẢI TÍCH TRONG AR

### Công thức 1: Ánh xạ tọa độ chuẩn hóa sang Pixel màn hình

**Bước 1 — Chọn `scaleFactor` theo kiểu hiển thị.** Khi `OverlayView` cần phủ kín toàn bộ View (kiểu `CENTER_CROP`, giống `PreviewView` mặc định của CameraX), `scaleFactor` được tính theo tỉ lệ **lớn hơn** giữa 2 trục:

$$\text{scaleFactor} = \max\left(\frac{W_{\text{view}}}{W_{\text{image}}}, \frac{H_{\text{view}}}{H_{\text{image}}}\right)$$

Vì chỉ dùng **một** hệ số scale chung cho cả 2 trục (để không làm méo hình), sẽ luôn có đúng 1 trục mà `image × scaleFactor` **lớn hơn** kích thước View thật — phần dư đó bị "crop" ra ngoài 2 bên và bị chia đều. Đây là lý do **bắt buộc** phải có bước Offset ở dưới, nếu thiếu, tọa độ vẽ sẽ lệch tâm một khoảng cố định theo đúng trục bị crop:

$$\text{offsetX} = \frac{W_{\text{view}} - W_{\text{image}} \times \text{scaleFactor}}{2}, \quad \text{offsetY} = \frac{H_{\text{view}} - H_{\text{image}} \times \text{scaleFactor}}{2}$$

**Bước 2 — Ánh xạ điểm mốc sang tọa độ Pixel màn hình:**

$$\text{Pixel}_X = (x_{\text{norm}} \times W_{\text{image}} \times \text{scaleFactor}) + \text{offsetX}$$

$$\text{Pixel}_Y = (y_{\text{norm}} \times H_{\text{image}} \times \text{scaleFactor}) + \text{offsetY}$$

> ⚠️ **Lỗi thường gặp:** Rất dễ nhầm tưởng chỉ cần nhân `scaleFactor` là đủ và bỏ qua `offsetX/offsetY`. Với kiểu `CENTER_CROP` (dùng `max`), thiếu bước cộng offset sẽ khiến toàn bộ hiệu ứng AR lệch tâm một khoảng **cố định** theo đúng trục bị crop — không dao động, không phụ thuộc vị trí tay, nên rất dễ gây nhầm lẫn khi debug (trông giống lỗi ở landmark hoặc ở phép xoay, nhưng thực chất chỉ là thiếu 1 phép cộng).

*Biến thể đơn giản hơn — chế độ `FIT_CENTER`/full-view không crop (`scaleFactor = min(...)` thay vì `max(...)`)* cũng cần offset tương tự, chỉ khác dấu: phần dư khi đó nằm ở viền (letterbox) chứ không bị cắt.

---

### Công thức 1.5: Xử lý Rotation & Mirror trước khi vào Pipeline toán học

Trước khi mọi công thức toán học ở trên có ý nghĩa, ảnh đưa vào `HandLandmarker` phải đã **đúng chiều hiển thị**. Có 2 bước xử lý bắt buộc, độc lập với nhau, dễ bị bỏ sót:

**a) Xoay ảnh theo `rotationDegrees` của cảm biến**

`ImageProxy.toBitmap()` **không** tự xoay ảnh theo hướng cầm máy — nó trả về bitmap theo đúng chiều vật lý của cảm biến (thường là ngang/landscape, ví dụ `640×480`), bất kể máy đang cầm dọc hay ngang. Nếu không xoay trước khi đưa vào MediaPipe, `inputImage.width/height` mà `HandLandmarkerResult` trả về sẽ **bị đảo trục X/Y** so với `OverlayView` (đang hiển thị dọc) — khiến `scaleFactor`/`offsetX`/`offsetY` tính sai trục, gây lệch tâm nặng và có xu hướng di chuyển "chéo/ngược hướng" khi đưa tay di chuyển.

```kotlin
val rotationDegrees = imageProxy.imageInfo.rotationDegrees
val bitmap = imageProxy.toBitmap()

val rotatedBitmap = if (rotationDegrees != 0) {
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
} else bitmap

val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()
```

> Cách tối ưu hơn (tránh cấp phát bitmap mới mỗi frame): dùng `ImageProcessingOptions.builder().setRotationDegrees(rotationDegrees).build()` truyền vào `detectAsync(mpImage, imageProcessingOptions, timestamp)` thay vì tự xoay bằng `Matrix`.

**b) Lật gương (mirror) tọa độ X khi dùng camera trước**

`PreviewView` mặc định tự lật ảnh theo chiều ngang khi dùng `CameraSelector.DEFAULT_FRONT_CAMERA` để người dùng nhìn thấy giống soi gương. Nhưng toạ độ landmark từ MediaPipe được tính trên ảnh **gốc, chưa lật**. Nếu không xử lý, tâm hiệu ứng AR sẽ đối xứng ngược so với những gì mắt thấy trên preview:

```kotlin
val normCenterX = 1f - (wrist.x() + middleMcp.x()) / 2f   // chỉ áp dụng khi dùng camera trước
```

> Hai lỗi (a) và (b) hoàn toàn độc lập và **có thể cộng dồn**: nếu chỉ sửa một trong hai, hiện tượng lệch tâm có thể trông giống bị sửa một phần nhưng vẫn "đi ngược" khi di chuyển tay theo hướng chéo — cần kiểm tra cả hai cùng lúc khi debug.

---

### Công thức 2: Tìm tâm lòng bàn tay (Tọa độ trung điểm)
Lòng bàn tay nằm giữa Cổ tay (`[0]`) và Gốc ngón giữa (`[9]`). Tọa độ tâm $C(c_x, c_y)$ là trung điểm chuẩn hóa của 2 khớp này, sau đó áp dụng đúng phép ánh xạ ở Công thức 1 (kèm offset):

$$x_{\text{mid}} = \frac{x_0 + x_9}{2}, \quad y_{\text{mid}} = \frac{y_0 + y_9}{2}$$

$$c_x = (x_{\text{mid}} \times W_{\text{image}} \times \text{scaleFactor}) + \text{offsetX}$$

$$c_y = (y_{\text{mid}} \times H_{\text{image}} \times \text{scaleFactor}) + \text{offsetY}$$

> Lưu ý: $W_{\text{image}}/H_{\text{image}}$ ở đây là kích thước ảnh mà MediaPipe trả về (`inputImage.width/height`) — **không phải** $W_{\text{view}}/H_{\text{view}}$ của `OverlayView`. Nhầm lẫn 2 đại lượng này (hoặc bỏ qua `scaleFactor`/offset) là nguyên nhân phổ biến nhất gây lệch tâm.

---

### Công thức 3: Đo độ to bàn tay & Bán kính khiên (Khoảng cách Euclid 2D)
Tính độ dài bàn tay $d$ trên màn hình để phóng to/thu nhỏ khiên khi đưa tay lại gần hoặc ra xa camera:

$$\Delta x = (x_9 - x_0) \times W_{\text{view}}, \quad \Delta y = (y_9 - y_0) \times H_{\text{view}}$$

$$d = \text{hypot}(\Delta x, \Delta y) = \sqrt{\Delta x^2 + \Delta y^2}$$

$$R_{\text{shield}} = d \times 1.4$$

---

### Công thức 4: Nhận diện Nắm / Xòe tay (Bất đẳng thức khoảng cách hướng tâm)
Để nhận diện không bị phụ thuộc vào góc quay của bàn tay (dù tay úp, ngửa, nghiêng hay lộn ngược), ta tính khoảng cách từ Cổ tay (`[0]`) tới Đầu ngón tay (`Tip`) và Khớp gập giữa (`PIP`):

$$d(\text{Tip}, \text{Wrist}) = \sqrt{(x_{\text{Tip}} - x_0)^2 + (y_{\text{Tip}} - y_0)^2}$$

$$d(\text{PIP}, \text{Wrist}) = \sqrt{(x_{\text{PIP}} - x_0)^2 + (y_{\text{PIP}} - y_0)^2}$$

**Quy tắc logic:**
* Ngón tay $i$ mở rộng (DUỖI) khi: $d(\text{Tip}_i, \text{Wrist}) > d(\text{PIP}_i, \text{Wrist})$
* Ngón tay $i$ co lại (NẮM) khi: $d(\text{Tip}_i, \text{Wrist}) \le d(\text{PIP}_i, \text{Wrist})$

Xét 4 ngón chính: Trỏ (`8` vs `6`), Giữa (`12` vs `10`), Áp út (`16` vs `14`), Út (`20` vs `18`):
$$\text{State} = \begin{cases} \text{OPEN\_PALM (Xòe tay)}, & \text{nếu số ngón duỗi} \ge 3 \\ \text{FIST (Nắm đấm)}, & \text{nếu số ngón duỗi} \le 1 \end{cases}$$

---

### Công thức 5: Ma trận xoay 2D & Phép biến đổi Affine (Spinning Shield)
Để toàn bộ vòng tròn ma thuật tự xoay quanh tâm lòng bàn tay $C(c_x, c_y)$ một góc $\theta$:

$$\begin{bmatrix} x' \\ y' \\ 1 \end{bmatrix} =
\begin{bmatrix} 1 & 0 & c_x \\ 0 & 1 & c_y \\ 0 & 0 & 1 \end{bmatrix}
\begin{bmatrix} \cos\theta & -\sin\theta & 0 \\ \sin\theta & \cos\theta & 0 \\ 0 & 0 & 1 \end{bmatrix}
\begin{bmatrix} x \\ y \\ 1 \end{bmatrix}$$

Trong Android Canvas, thao tác này được tối ưu bằng phần cứng:
```kotlin
canvas.save()
canvas.translate(cx, cy)       // Dời gốc tọa độ về tâm bàn tay
canvas.rotate(rotationAngle)   // Xoay toàn bộ mặt phẳng vẽ một góc θ
// Thực hiện vẽ vòng tròn, hình vuông ma thuật tại gốc (0, 0)
canvas.restore()
```

---

### Công thức 6: Phân bố góc đều $360^\circ / N$ (Vẽ các tia lửa ma thuật)
Vẽ $N$ họa tiết hoặc tia năng lượng phân bố đối xứng qua tâm:

$$\Delta\theta = \frac{360^\circ}{N}$$

Với mỗi bước lặp $i \in [0, N-1]$, xoay Canvas một góc $\Delta\theta$ và vẽ tia thẳng đứng từ bán kính trong $R_1$ tới bán kính ngoài $R_2$:
```kotlin
for (i in 0 until N) {
    canvas.rotate(360f / N)
    canvas.drawLine(0f, rInner, 0f, rOuter, paint)
}
```

---

### Công thức 7 (Nâng cao): Vector pháp tuyến lòng bàn tay (3D Palm Normal)
Dùng để xác định bàn tay đang hướng về camera hay đang nghiêng một góc 3D, từ đó áp dụng hiệu ứng nghiêng khiên phép:
* Vector dọc: $\vec{u} = P_9 - P_0 = (x_9 - x_0, y_9 - y_0, z_9 - z_0)$
* Vector ngang: $\vec{v} = P_5 - P_{17} = (x_5 - x_{17}, y_5 - y_{17}, z_5 - z_{17})$
* Vector pháp tuyến $\vec{n}$ là tích có hướng (Cross Product):

$$\vec{n} = \vec{u} \times \vec{v} = \begin{pmatrix} u_y v_z - u_z v_y \\ u_z v_x - u_x v_z \\ u_x v_y - u_y v_x \end{pmatrix}$$

---

## 4. ANDROID CAMERAX - QUẢN LÝ PHẦN CỨNG & BƠM DỮ LIỆU

### 4.1. Các đối tượng cốt lõi

| Đối tượng | Bản chất | Nhiệm vụ trong app AR |
| :--- | :--- | :--- |
| **`ProcessCameraProvider`** | Singleton | Quản lý vòng đời phần cứng camera của thiết bị. |
| **`Preview`** | Use Case | Dẫn luồng video thời gian thực đổ vào `PreviewView` để người dùng nhìn thấy không gian thực. |
| **`ImageAnalysis`** | Use Case | Cắt từng khung hình camera ra bộ đệm RAM để chuyển giao cho AI phân tích. |
| **`ImageProxy`** | Wrapper Data | Đại diện cho **duy nhất 1 khung hình đơn lẻ** tại thời điểm mili-giây cụ thể. |

### 4.2. Cấu hình ImageAnalysis quan trọng

```kotlin
val imageAnalyzer = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .build()
```

* **`STRATEGY_KEEP_ONLY_LATEST`:** Nếu AI xử lý không kịp tốc độ của cảm biến camera, CameraX sẽ tự động hủy bỏ các khung hình trung gian, chỉ giữ lại khung hình mới nhất. Tránh tình trạng tích lũy độ trễ (Zero latency accumulation).
* **`OUTPUT_IMAGE_FORMAT_RGBA_8888`:** Yêu cầu CameraX tự động convert khung hình sang định dạng RGBA 4 kênh trước khi đưa vào `Analyzer`, để MediaPipe đọc trực tiếp mà bạn **không cần tự viết code chuyển đổi YUV → RGBA thủ công**. Lưu ý: cảm biến camera vẫn xuất dữ liệu thô dạng YUV như bình thường — bước convert sang RGBA vẫn tốn một phần xử lý, chỉ là CameraX đã lo phần này giúp bạn, chứ không phải "bỏ qua" hoàn toàn việc giải mã.

---

## 5. ĐỒ HỌA ANDROID CANVAS & OVERLAYVIEW

### 5.1. Cơ chế "Tấm kính trong suốt"
Trong layout, `OverlayView` được đặt chồng khít (Overlay) lên `PreviewView` thông qua `FrameLayout`:
- `PreviewView` nằm ở lớp dưới (Background): Hiển thị thế giới thực.
- `OverlayView` nằm ở lớp trên (Foreground): Nền trong suốt (`Color.TRANSPARENT`), chỉ vẽ hiệu ứng AR.

### 5.2. Chu trình vẽ và vòng lặp hoạt ảnh
1. **`invalidate()`**: Đánh dấu View bị "bẩn" (Dirty Flag) $\rightarrow$ Yêu cầu hệ điều hành vẽ lại trong chu kỳ quét màn hình tiếp theo.
2. **`postInvalidateOnAnimation()`**: Kích hoạt lệnh vẽ lại đồng bộ với tốc độ làm tươi của phần cứng (V-Sync, 60Hz/120Hz), giúp vòng tròn ma thuật tự xoay mượt mà ngay cả khi bàn tay đứng yên.

### 5.3. Cơ chế Choreographer
Android gom tất cả các lệnh gọi `invalidate()` trong khoảng thời gian $\approx 16.6\text{ms}$ (chuẩn 60 FPS) thành **duy nhất 1 lần gọi hàm `onDraw(canvas)`**. Do đó việc gọi `invalidate()` nhiều lần trong cùng 1 frame không làm suy giảm hiệu năng.

---

## 6. BẢNG QUY TẮC "SỐNG CÒN" & HẬU QUẢ KỸ THUẬT KHI VI PHẠM

| STT | Quy tắc bắt buộc | Mã nguồn liên quan | Hậu quả nếu KHÔNG tuân theo |
| :---: | :--- | :--- | :--- |
| **1** | **BẮT BUỘC gọi `imageProxy.close()`** | `imageAnalyzer.setAnalyzer { ... imageProxy.close() }` | **TREO CỨNG CAMERA:** Bộ đệm khung hình (Buffer queue) của CameraX bị đầy sau 3-4 frame đầu tiên. Camera sẽ vĩnh viễn không gửi thêm frame mới. |
| **2** | **Chạy AI ở luồng phụ (Background Thread)** | `imageAnalyzer.setAnalyzer(backgroundExecutor)` | **ỨNG DỤNG BỊ TREO (ANR):** Model AI tính toán mất 15–30ms. Nếu chạy trên Main Thread, giao diện sẽ tụt về 10–15 FPS hoặc văng app (Application Not Responding). |
| **3** | **Cập nhật tọa độ sang View trên Main Thread** | `runOnUiThread { overlayView.setResults(result) }` | **VĂNG APP NGAY LẬP TỨC:** Android cấm tuyệt đối việc gọi `invalidate()` hoặc sửa đổi View từ một Thread không phải Main Thread (`CalledFromWrongThreadException`). |
| **4** | **Đo đạc trước khi chọn Delegate GPU/CPU** | `baseOptions.setDelegate(Delegate.CPU / GPU)` | **CHỌN SAI ĐÁNH ĐỔI:** Ép dùng GPU trên thiết bị/model không hưởng lợi có thể khiến thời gian khởi tạo chậm hẳn (tới hàng chục giây) mà FPS runtime không cải thiện rõ rệt so với CPU. Nếu máy yếu và không dùng delegate nào tăng tốc, CPU vẫn phải gánh cả phân tích hình ảnh lẫn logic toán, có thể tụt FPS — nhưng hướng khắc phục là đo đạc thực tế trên thiết bị mục tiêu, không phải mặc định bật GPU. |
| **5** | **Khởi tạo và gọi GPU trên cùng một luồng** | `setupHandLandmarker()` | **VĂNG APP ĐỒ HỌA:** OpenGL context gắn liền với thread tạo ra nó. Khởi tạo GPU delegate ở thread này nhưng gọi ở thread khác sẽ gây lỗi crash driver đồ họa (`EGL_BAD_ACCESS`). |
| **6** | **Dùng `STRATEGY_KEEP_ONLY_LATEST`** | `setBackpressureStrategy(...)` | **HIỆU ỨNG BỊ TRỄ (AR LAG):** Các frame bị xếp hàng đợi (Queue). Khi bạn vẫy tay sang trái, 1 giây sau khiên ma thuật mới bay sang trái. |
| **7** | **Đồng bộ Timestamp đơn điệu tăng** | `handLandmarker.detectAsync(mpImage, frameTime)` | **AI TỪ CHỐI TÍNH TOÁN:** Chế độ `LIVE_STREAM` yêu cầu `frameTime` phải luôn lớn hơn `frameTime` của frame trước. Nếu dùng sai hàm lấy giờ khiến timestamp bị lùi, MediaPipe sẽ ném ngoại lệ và dừng tracking. |
| **8** | **Cân bằng Z-Index của Layout** | `activity_main.xml` | **HIỆU ỨNG BỊ CHE KHUẤT:** Nếu đặt View Camera hoặc Menu đè lên trên `OverlayView`, toàn bộ khiên phép và khung xương AR sẽ bị vẽ chìm bên dưới nền và biến mất khỏi mắt người dùng. |

---

## 7. VẼ TÀI NGUYÊN ĐỘNG (GIF/SPRITE) ĐÚNG VỊ TRÍ TRONG OVERLAYVIEW

> Mục này đúc kết từ một chuỗi debug thực tế khi chuyển hiệu ứng từ hình học thuần (`drawCircle`/`drawRect`) sang ảnh động (GIF quái vật) gắn theo tay — nhiều lỗi trong số này **không xuất hiện trong tài liệu chính thức của Android** và chỉ lộ ra khi thực chiến.

### 7.1. Vì sao không thể `drawBitmap` trực tiếp cho GIF

`Bitmap` là dữ liệu pixel tĩnh, bất biến — `canvas.drawBitmap(bitmap, matrix, paint)` luôn tôn trọng `Matrix` 100%. Nhưng GIF động phải được biểu diễn bằng `Drawable` (cụ thể là `AnimatedImageDrawable`, API 28+, tạo qua `ImageDecoder.decodeDrawable()`), vì nó cần tự quản lý việc chuyển frame theo thời gian. Một `Drawable` không phải là dữ liệu pixel có sẵn — nó chỉ "biết cách tự vẽ" khi được gọi `draw(canvas)`, và như mục 7.4 sẽ chỉ ra, nó **không hành xử giống `Bitmap`** khi kết hợp với các phép biến đổi thủ công (`translate`, `rotate`, `scale`) mà kỹ thuật AR theo tay bắt buộc phải dùng.

### 7.2. Pipeline chuẩn: Buffer trung gian + Matrix

Giải pháp ổn định nhất được rút ra: **không bao giờ gọi `animatedDrawable.draw()` trực tiếp lên canvas chính đã bị `translate`/`rotate` theo tay.** Thay vào đó, tách thành 2 bước độc lập:

```text
Bước A (Chuẩn hoá):  AnimatedImageDrawable.draw()  ──▶  Buffer Bitmap cố định, vuông, KHÔNG transform
Bước B (Định vị):    Buffer Bitmap (giờ coi như PNG tĩnh)  ──▶  canvas.drawBitmap(buffer, matrix, null)
```

```kotlin
private class GifLayer(context: Context, resId: Int) {
    val drawable: AnimatedImageDrawable = (ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(context.resources, resId)
    ) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE   // xem mục 7.3
    } as AnimatedImageDrawable).apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
    }

    val buffer: Bitmap = createBitmap(GIF_BUFFER_SIZE, GIF_BUFFER_SIZE)
    val bufferCanvas = Canvas(buffer)

    fun renderToBuffer() {
        buffer.eraseColor(Color.TRANSPARENT)                          // mục 7.5
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

        val uniformScale = min(                                       // mục 7.6 — Fit, không méo
            GIF_BUFFER_SIZE / drawable.intrinsicWidth.toFloat(),
            GIF_BUFFER_SIZE / drawable.intrinsicHeight.toFloat()
        )
        val dx = (GIF_BUFFER_SIZE - drawable.intrinsicWidth * uniformScale) / 2f
        val dy = (GIF_BUFFER_SIZE - drawable.intrinsicHeight * uniformScale) / 2f

        bufferCanvas.withSave {
            translate(dx, dy)
            scale(uniformScale, uniformScale)
            drawable.draw(this)                                       // mục 7.4 — KHÔNG dùng setBounds để scale
        }
    }
}
```

Sau `renderToBuffer()`, `buffer` được coi như 1 tấm PNG tĩnh bình thường, dùng lại đúng công thức `Matrix` đã kiểm chứng ở Công thức 2 (Mục 3):

```kotlin
val drawScale = (r * 2f) / GIF_BUFFER_SIZE
val matrix = Matrix().apply {
    postTranslate(-GIF_BUFFER_SIZE / 2f, -GIF_BUFFER_SIZE / 2f)
    postScale(drawScale, drawScale)
    postTranslate(cx, cy)
}
canvas.drawBitmap(buffer, matrix, null)
```

> ⚠️ Lỗi hay gặp nhất khi refactor pipeline 2 bước này: **quên mất bước B** (đoạn `drawBitmap(buffer, matrix, null)`) sau khi sửa bước A — kết quả là chỉ thấy hiệu ứng debug (nếu có) mà không thấy GIF đâu cả, dù buffer đã render đúng nội dung.

### 7.3. Cạm bẫy 1: Hardware Bitmap không tương thích Software Canvas

Từ Android 8.0, `ImageDecoder` mặc định giải mã ảnh thành `Bitmap.Config.HARDWARE` — pixel nằm trên VRAM (GPU) để tiết kiệm RAM. Nhưng `bufferCanvas = Canvas(buffer)` (tạo từ `Bitmap.createBitmap()` thông thường) là **software canvas** (chạy trên CPU). Vẽ hardware bitmap lên software canvas gây lỗi kinh điển của Android:
```
java.lang.IllegalArgumentException: Software rendering doesn't support hardware bitmaps
```
Một số thiết bị/driver không crash thẳng mà **âm thầm render sai/vỡ hình** — dễ nhầm sang lỗi Matrix hoặc lỗi tọa độ.

**Fix bắt buộc:** ép `ImageDecoder` giải mã bằng bộ nhớ phần mềm ngay từ đầu:
```kotlin
ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
}
```
Đánh đổi: tốn RAM hơn một chút so với hardware bitmap, nhưng là bắt buộc nếu muốn vẽ vào buffer tự quản lý.

### 7.4. Cạm bẫy 2: `AnimatedImageDrawable` không tự scale theo `setBounds()`

Đây là cạm bẫy khó phát hiện nhất. Theo lý thuyết, mọi `Drawable` phải tự scale nội dung để vừa khít vùng `setBounds()` được truyền vào. Nhưng `AnimatedImageDrawable` là **ngoại lệ** — tài liệu của thư viện Coil xác nhận rõ điều này khi giới thiệu class `ScaleDrawable` của họ: mục đích của nó là giúp các drawable "chỉ vẽ trong đúng kích thước nội tại" (và họ nêu thẳng `AnimatedImageDrawable` làm ví dụ) có thể lấp đầy bounds mong muốn.

Hậu quả nếu gọi `setBounds(0, 0, GIF_BUFFER_SIZE, GIF_BUFFER_SIZE)` với 1 GIF có kích thước gốc khác `GIF_BUFFER_SIZE` (ví dụ `750×630`): ảnh vẫn được vẽ **đúng bằng kích thước gốc**, bắt đầu từ góc `(0,0)` — nếu kích thước gốc lớn hơn buffer, bạn chỉ thấy đúng phần góc trên-trái bị "cắt" bởi kích thước buffer, trông y hệt như ảnh bị lệch vị trí dù toạ độ tính toán hoàn toàn chính xác.

**Fix:** không dựa vào `setBounds()` để scale. Thay vào đó, tự `canvas.scale()` trên buffer canvas *trước khi* gọi `draw()` (xem code mẫu Mục 7.2) — set bounds về đúng kích thước gốc (`intrinsicWidth/Height`), rồi để phép `scale()` của canvas lo việc co giãn.

### 7.5. Cạm bẫy 3: Quên `eraseColor` gây bóng ma (ghosting)

`Bitmap` buffer là vùng nhớ **tái sử dụng** qua từng frame — nó không tự làm sạch. Nếu thiếu `buffer.eraseColor(Color.TRANSPARENT)` trước khi vẽ frame mới, pixel của frame cũ còn sót lại sẽ bị frame mới vẽ chồng lên (nếu frame mới có vùng trong suốt), tạo hiệu ứng "bóng ma" — nhìn giống hình ảnh cũ không được xoá kịp. Luôn `eraseColor` ngay đầu mỗi lần `renderToBuffer()`.

### 7.6. Fit (`min`) vs Crop (`max`) khi chuẩn hoá GIF về khung vuông

Cùng 1 công thức chọn `scaleFactor`, khác nhau ở việc dùng `min` hay `max`:

| Hàm chọn scale | Tên gọi | Hành vi | Khi nào dùng |
|---|---|---|---|
| `max(sx, sy)` | **CENTER_CROP** | Ảnh lấp đầy 100% khung, phần dư theo 1 trục bị **cắt mất** | Khi asset đã được thiết kế/crop sẵn theo đúng tỉ lệ khung hiển thị |
| `min(sx, sy)` | **CENTER_INSIDE / FIT** | Toàn bộ ảnh nằm gọn trong khung, không mất chi tiết, nhưng có thể dư viền trong suốt ở 1 trục | Khi asset có tỉ lệ khung khác khung hiển thị (ví dụ GIF `250×210` không vuông) và không muốn mất chi tiết (ví dụ đầu nhân vật bị crop) |

**Kết hợp đường dài:** dùng `min` (Fit) để không mất chi tiết ngay từ code, đồng thời **crop/resize lại chính file GIF gốc cho vuông sẵn** bằng công cụ như ezgif.com/crop hoặc ezgif.com/resize (hỗ trợ giữ nguyên animation) — khi asset đã đúng tỉ lệ khung, `min` và `max` cho kết quả gần như nhau, không còn viền trống.

### 7.7. Nguyên tắc thống nhất: PNG tĩnh vs GIF động khác nhau ở đâu

Fit/Crop là **cùng 1 công thức toán** cho mọi loại tài nguyên. Khác biệt duy nhất nằm ở **số bước** cần làm, do đặc tính kỹ thuật của từng loại:

| | PNG tĩnh (`Bitmap`) | GIF động (`AnimatedImageDrawable`) |
|---|---|---|
| Ai giữ dữ liệu pixel? | Chính `Bitmap` — có sẵn, bất biến | `Drawable` — tự vẽ mỗi lần gọi `draw()`, không "cầm" được pixel trực tiếp |
| Áp dụng Fit/Crop khi nào? | 1 lần duy nhất, ngay khi vẽ ra màn hình | 2 lần: 1 lần chuẩn hoá vào buffer vuông (Mục 7.6), 1 lần vẽ buffer đó ra màn hình theo tay |
| Cần buffer trung gian? | Không cần | Bắt buộc — vì `Drawable` không tôn trọng `setBounds()` để tự scale (Mục 7.4) |

**Quy tắc chung khi thêm loại tài nguyên mới** (Lottie, WebP động, sprite sheet...): bất kỳ thứ gì **không phải `Bitmap` thuần** đều nên đi qua bước "vật chất hoá vào buffer vuông cố định" trước, sau đó luôn tái sử dụng đúng 1 hàm `Matrix` chung để định vị theo tay — tách biệt rõ "chuẩn hoá asset" khỏi "định vị trên màn hình".

### 7.8. Quy trình chẩn đoán lệch vị trí (Debug Checklist)

Khi hiệu ứng AR (dù hình học, PNG, hay GIF) hiển thị sai vị trí/kích thước, làm theo đúng thứ tự sau để khoanh vùng nhanh, tránh đoán mò:

1. **Test bằng 1 khối màu đặc thay cho nội dung thật** (ví dụ `bufferCanvas.drawColor(Color.RED)` thay vì `drawable.draw(bufferCanvas)`). Nếu khối màu hiện đúng khít trong khung debug (`drawRect` viền đen quanh `cx, cy, r`) → xác nhận `Matrix`/toạ độ/buffer hoàn toàn đúng, lỗi nằm ở bước vẽ nội dung vào buffer, không phải ở toán học định vị.
2. **Log các giá trị trung gian** ngay trước và sau bước nghi vấn: `intrinsicWidth/Height`, `bounds`, `isRunning`, và lấy mẫu `buffer.getPixel(x, y)` tại vài điểm để biết buffer có thực sự chứa nội dung hay không.
3. **Log liên tục qua nhiều frame**, không chỉ 1 lần — vì decode/render có thể là bất đồng bộ (frame đầu tiên có thể chưa sẵn sàng ngay khi `draw()` được gọi lần đầu).
4. **Grep toàn bộ project** tìm các chỗ khác có thể đang vẽ/setBounds cùng 1 `Drawable`/biến dùng chung — dễ xảy ra khi code cũ (thử nghiệm ban đầu) chưa được dọn sạch, gây hiệu ứng vẽ chồng từ 2 nguồn.
5. Nếu nội dung **có** vẽ được nhưng bị cắt/lệch một phần cố định → nghi ngờ trước tiên là `Drawable` không tôn trọng `setBounds()` (Mục 7.4) hoặc tỉ lệ khung nguồn khác tỉ lệ buffer (Mục 7.6), trước khi nghi ngờ tới `Matrix` định vị theo tay.
---

## 8. GHI VIDEO (MEDIARECORDER) TÍCH HỢP VỚI AR OVERLAY

> Mục này đúc kết từ chuỗi debug thực tế khi thêm tính năng **quay lại video có hiệu ứng AR đè trực tiếp lên khung hình** (composite camera frame + hiệu ứng landmark vào 1 `Surface` ghi hình duy nhất) — khác về bản chất so với việc chỉ hiển thị AR trên `OverlayView` (Mục 5–7), vì giờ đây có **thêm một Surface thứ hai**, **thêm một luồng ghi**, và **thêm một pipeline hiển thị song song** với pipeline preview. Phần lớn lỗi ở mục này không nằm ở toán học hay đồ họa, mà nằm ở **vòng đời luồng (threading)**, **vòng đời phần cứng (Surface)**, và một cạm bẫy tưởng vô hại: sự bất đối xứng giữa **những gì mắt thấy trên preview** và **những gì thực sự được ghi vào file**.

### 8.1. Kiến trúc: 2 Surface độc lập, 2 mục đích khác nhau

Khi vừa preview vừa ghi hình, ứng dụng đang vận hành **2 Surface hoàn toàn tách biệt**, dễ nhầm lẫn khi đọc log vì cả hai đều có thể ném lỗi dạng `BufferQueue`/`SurfaceView`:

| | Surface Preview (`PreviewView`) | Surface Ghi hình (`mediaRecorder.surface`) |
| :--- | :--- | :--- |
| Ai tạo ra? | CameraX, qua use case `Preview` | `MediaRecorder`, sau khi `prepare()` |
| Ai hiển thị? | Hệ thống (SurfaceFlinger) vẽ thẳng lên màn hình | Không ai "xem" trực tiếp — chỉ là input cho bộ mã hoá H.264 |
| Vòng đời gắn với gì? | Vòng đời của **View** (`surfaceCreated`/`surfaceDestroyed`, xoay màn hình, activity pause) | Vòng đời của **đối tượng `MediaRecorder`** (từ lúc `prepare()` tới lúc `release()`) |
| Ai được phép vẽ vào? | Chỉ CameraX (bạn không tự `lockCanvas()` surface này) | Code của bạn, qua `surface.lockHardwareCanvas()` / `unlockCanvasAndPost()` |
| Mirror với camera trước? | **Có**, CameraX tự động lật ngang | **Không**, nhận đúng frame thô (xem 8.7) |

> ⚠️ Quy tắc đọc log số 1: khi thấy lỗi `BufferQueue has been abandoned`, **đọc kỹ tag trong dòng log trước khi đoán nguyên nhân** — tag `SurfaceView - com.package/...MainActivity` chỉ ra đây là surface của **preview**, còn nếu không thấy tag `SurfaceView` mà chỉ có địa chỉ buffer trần trụi, nhiều khả năng đó là surface **ghi hình** của `MediaRecorder`. Hai loại lỗi này có nguyên nhân và hướng sửa hoàn toàn khác nhau, gộp chung sẽ dẫn tới sửa sai chỗ.

### 8.2. Cạm bẫy 1: Guard-clause bị đảo ngược logic

Lỗi không hề gây crash, không hề bị compiler hay lint cảnh báo — nó là một **lỗi ngữ nghĩa thầm lặng**, chỉ lộ ra khi quan sát hành vi runtime:

```kotlin
// SAI — điều kiện bị đảo ngược
fun stop() {
    if (isRecording) return   // đang recording thì... thoát ngay?!
    isRecording = false
    mediaRecorder?.stop()
    mediaRecorder?.release()
}
```

Trong luồng sử dụng thực tế (bấm nút khi đang quay, `isRecording == true`), hàm thoát ngay ở dòng đầu tiên — toàn bộ phần dọn dẹp tài nguyên **không bao giờ chạy**. `MediaRecorder` native vẫn sống, chỉ chờ GC/finalizer dọn một cách "cưỡng ép", gián tiếp gây ra các lỗi Surface abandoned ở nơi khác trong app.

```kotlin
// ĐÚNG
fun stop() {
    if (!isRecording) return   // chỉ thoát khi ĐANG KHÔNG recording
    isRecording = false
    mediaRecorder?.stop()
    mediaRecorder?.release()
}
```

**Quy tắc chung:** với mọi guard-clause dạng `if (state) return` trong hàm toggle (start/stop, open/close, play/pause, connect/disconnect), hãy **đọc thành lời** ý nghĩa của điều kiện trước khi tin tưởng nó — ví dụ đọc to "nếu đang recording thì return" và tự hỏi "return khỏi hàm `stop()` khi đang recording — có hợp lý không?". Lỗi kiểu đảo dấu `!` rất dễ gõ nhầm khi copy-paste giữa các hàm đối xứng nhau (`start()`/`stop()`) và cực khó phát hiện qua code review lướt nhanh.

### 8.3. Cạm bẫy 2: Race Condition giữa Analyzer Thread và UI Thread

Trong kiến trúc chuẩn (Mục 4), `ImageAnalysis.Analyzer` chạy trên `backgroundExecutor` — một luồng nền riêng, tách biệt hoàn toàn khỏi Main/UI Thread. Nhưng nút bấm "Dừng quay" lại chạy trên UI Thread. Cả hai cùng đọc/ghi chung các biến `recordingSurface`, `isRecording`, `mediaRecorder` mà **không có bất kỳ cơ chế đồng bộ nào**:

| Hàm | Chạy trên thread nào? | Truy cập gì? |
| :--- | :--- | :--- |
| `pushFrame()` | `backgroundExecutor` (callback của `ImageAnalysis.Analyzer`) | Đọc `recordingSurface`, `isRecording`; gọi `lockHardwareCanvas()`/`unlockCanvasAndPost()` |
| `start()` / `stop()` | UI Thread (`View.OnClickListener`) | Ghi `recordingSurface`, `isRecording`; gọi `mediaRecorder.release()` |

Đây là race condition kinh điển dạng TOCTOU (Time-Of-Check to Time-Of-Use): giữa lúc `pushFrame()` kiểm tra `recordingSurface != null` và lúc nó thực sự gọi `lockHardwareCanvas()`, luồng UI có thể đã chạy `stop()` → `release()` → surface bị abandoned ngay giữa chừng, gây đúng log `queueBuffer`/`cancelBuffer: BufferQueue has been abandoned`.

**Fix:** bọc mọi thao tác đọc/ghi các biến dùng chung bằng cùng **một** `synchronized(lock)`, áp dụng cho cả `pushFrame()` lẫn `stop()` (và cả `start()` nếu có thể bị gọi chồng):

```kotlin
private val lock = Any()

fun pushFrame(draw: (Canvas) -> Unit) {
    synchronized(lock) {
        val surface = recordingSurface ?: return
        if (!isRecording) return
        val canvas = try { surface.lockHardwareCanvas() } catch (e: Exception) { return }
        try { draw(canvas) } finally {
            try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
        }
    }
}

fun stop() {
    synchronized(lock) {
        if (!isRecording) return
        isRecording = false
        try { mediaRecorder?.stop() } catch (e: Exception) { /* log */ }
        mediaRecorder?.release()
        mediaRecorder = null
        recordingSurface = null
    }
}
```

> ⚠️ Chỉ đồng bộ hoá `pushFrame()` **hoặc** chỉ `stop()` không đủ — TOCTOU chỉ được giải quyết khi **tất cả** các bên cùng tranh chấp tài nguyên đều đi qua cùng một lock.

**Biến thể 2 của cùng một lớp lỗi — tài nguyên bị tranh chấp không phải lúc nào cũng là `MediaRecorder`/`Surface`.** Sau khi Mục 7 giới thiệu `GifLayer` (gói gọn `AnimatedImageDrawable` + `Bitmap` buffer + `Canvas` buffer để render GIF), một sai lầm rất dễ mắc là **dùng chung 1 instance `GifLayer` cho cả preview lẫn recording**:

```kotlin
// SAI — 1 instance dùng chung cho 2 luồng khác nhau
private val mooLayer by lazy { GifLayer(context!!, R.drawable.moo) }

// onDraw() (UI Thread) và pushFrame() (Analyzer Thread) CÙNG gọi:
mooLayer.renderToBuffer()   // cùng ghi/đọc chung 1 Bitmap + cùng gọi draw() trên chung 1 AnimatedImageDrawable
```

Vì `Bitmap`/`Canvas` không thread-safe, và bản thân `AnimatedImageDrawable` giữ trạng thái nội bộ (frame hiện tại, buffer giải mã native) được thiết kế cho luồng đơn, gọi đồng thời từ 2 luồng gây ra đúng 2 triệu chứng đặc trưng: **hình ảnh lệch dần theo thời gian** (2 luồng liên tục "giẫm" lên buffer của nhau giữa chừng một chu kỳ render) rồi cuối cùng **mất hẳn không vẽ được gì** (trạng thái giải mã native bị hỏng do truy cập đồng thời) — không hề có crash hay exception nào được ném ra để dễ dàng lần ra nguyên nhân.

**Fix — tách hẳn 2 instance độc lập, không share giữa 2 luồng:**

```kotlin
// Bộ dùng cho PREVIEW (UI thread)
private val mooLayer by lazy { GifLayer(context!!, R.drawable.moo) }
private val rollingLayer by lazy { GifLayer(context!!, R.drawable.rolling) }

// Bộ RIÊNG dùng cho RECORDING (background executor) — KHÔNG share với preview
private val mooLayerForRecording by lazy { GifLayer(context!!, R.drawable.moo) }
private val rollingLayerForRecording by lazy { GifLayer(context!!, R.drawable.rolling) }
```

`drawHandEffects()` nhận thêm tham số `forRecording: Boolean` để chọn đúng bộ layer tương ứng với luồng đang gọi nó. Đánh đổi là tốn thêm bộ nhớ decode GIF lần 2 (không đáng kể với GIF nhỏ), đổi lại tránh hoàn toàn việc phải `synchronized` — vốn sẽ buộc 1 luồng chờ luồng kia mỗi frame, dễ ảnh hưởng ngược lại framerate preview khi đang ghi hình.

**Quy tắc chung mở rộng cho Mục 8.3:** bất kỳ object nào giữ trạng thái khả biến (mutable state) — `Bitmap`, `Canvas`, `Drawable` có animation, hay đơn giản là 1 biến đếm — nếu bị truy cập từ **cả UI Thread lẫn Analyzer/Background Thread**, đều là ứng viên của race condition, không chỉ riêng `MediaRecorder`/`Surface`. Khi thêm bất kỳ tính năng nào chạy song song 2 pipeline hiển thị (preview + recording), hãy tự hỏi: "danh sách các object khả biến nào đang bị 2 luồng này cùng chạm vào?" trước khi cho rằng code đã an toàn.

### 8.4. Cạm bẫy 3: `@RequiresApi` không hề chặn runtime

`MediaRecorder(context: Context)` (constructor nhận `Context`) chỉ tồn tại từ **API 31 (Android 12)** trở lên. Rất dễ tưởng nhầm rằng gắn annotation là đã "an toàn":

```kotlin
@RequiresApi(Build.VERSION_CODES.S)
fun start(): File {
    mediaRecorder = MediaRecorder(context).apply { ... }   // 💥 NoSuchMethodError trên API < 31
    ...
}
```

`@RequiresApi` **chỉ là annotation cho Lint/Android Studio** — nó giúp IDE gạch đỏ cảnh báo lúc code, nhưng **không sinh ra bất kỳ đoạn kiểm tra nào ở runtime**. Nếu `minSdkVersion` của app thấp hơn mức yêu cầu (hoặc code gọi hàm này không nằm sau một điều kiện `SDK_INT` thực sự), app cài trên máy có API thấp hơn sẽ **crash thẳng với `NoSuchMethodError`** ngay khi chạm vào dòng đó — không phải do model điện thoại không hỗ trợ camera, mà đơn giản là constructor đó không tồn tại trong `framework.jar` của phiên bản Android đó.

**Fix — luôn kèm nhánh `Build.VERSION.SDK_INT` thực sự, không dựa vào annotation:**

```kotlin
fun start(): File {   // bỏ @RequiresApi — hàm giờ tự xử lý được mọi API level
    mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }.apply { ... }
    ...
}
```

**Quy tắc chung:** `@RequiresApi` chỉ nên dùng để tự nhắc nhở lúc viết code, không bao giờ được coi là cơ chế bảo vệ runtime. Bất kỳ constructor/hàm nào bị gate bởi annotation này, nếu `minSdk` của app thấp hơn, **bắt buộc** phải có nhánh `if (Build.VERSION.SDK_INT >= ...)` bao quanh với fallback tương thích ngược.

### 8.5. Cạm bẫy 4: File thiếu phần mở rộng khiến hệ thống "không nhận diện"

```kotlin
val outputFile = File(outDir, "ar_record_${System.currentTimeMillis()}")   // thiếu ".mp4"
```

File này **hoàn toàn hợp lệ về mặt nội dung** (đúng định dạng MP4 do `MediaRecorder` ghi ra), nhưng vì tên không có đuôi `.mp4`, nhiều app quản lý file, trình phát video, và cả Gallery **dựa vào extension để nhận diện loại nội dung trước khi thử mở** — dẫn tới hiện tượng dễ gây hoang mang: icon file lạ, không tự mở được bằng trình phát nào, tưởng nhầm là "video bị hỏng" hoặc "không ghi được frame nào", trong khi thực chất file hoàn toàn ổn.

**Fix:** luôn gắn đúng extension khi tạo `File`:
```kotlin
val outputFile = File(outDir, "ar_record_${System.currentTimeMillis()}.mp4")
```

> ⚠️ Đây là một "false lead" điển hình khi debug: triệu chứng ("không xem được video") trông giống hệt lỗi ghi hình (frame trắng, Surface abandoned, ghi thiếu dữ liệu...), nhưng nguyên nhân thực ra nằm ở tầng đặt tên file, không liên quan gì tới pipeline camera/AR. Luôn loại trừ khả năng này sớm bằng cách `adb pull` file về máy tính và thử mở bằng VLC trước khi đào sâu vào code ghi hình.

### 8.6. Cạm bẫy 5: Scoped Storage chặn duyệt `Android/data/<package>` dù file tồn tại thật

Từ Android 10/11 trở đi (Scoped Storage), **các app quản lý file thông thường — kể cả Files by Google chính chủ — không được phép liệt kê nội dung bên trong `Android/data/<package của app khác>`**, dù thư mục đó (do `getExternalFilesDir()` trỏ tới) thực sự chứa file. Biểu hiện: mở đúng đường dẫn nhưng báo "Không có tệp nào ở đây" — đây **không phải bug của app bạn**, mà là giới hạn ở cấp hệ điều hành.

Các cách xác minh file thực sự tồn tại và xem được nội dung, xếp theo độ tin cậy:

| Cách | Điều kiện | Ghi chú |
| :--- | :--- | :--- |
| `adb pull "<đường-dẫn-tuyệt-đối-tới-file>" .` | Cần biết chính xác tên file | Đáng tin cậy nhất — pull thẳng vào 1 file cụ thể vẫn hoạt động dù *duyệt* thư mục bị chặn |
| `adb shell run-as <package> ...` | App phải là **debug build** | Cho phép `ls`/`cp` bên trong sandbox riêng của app |
| File manager có quyền cao (MT Manager, X-plore, Solid Explorer...) | Người dùng tự cấp quyền "Toàn quyền truy cập bộ nhớ"/root | Bỏ qua được giới hạn Scoped Storage, nhưng không phải máy nào cũng cài sẵn |
| Files by Google / File manager mặc định | — | **Không dùng được** để duyệt `Android/data/<package>`, kể cả khi file tồn tại thật |

**Mẹo cho vòng lặp debug nhanh:** trong giai đoạn phát triển, có thể tạm ghi file ra `context.cacheDir` hoặc `context.filesDir` thay vì `getExternalFilesDir(DIRECTORY_MOVIES)` — cả hai đều truy cập được dễ dàng qua `adb shell run-as`, giúp tách bạch việc kiểm tra "code có ghi ra file đúng không" khỏi việc "vướng quyền truy cập storage" — hai vấn đề rất dễ bị nhầm lẫn làm một khi mới thấy "không tìm thấy file".

### 8.7. Cạm bẫy 6 (khó phát hiện nhất): Preview bị Mirror nhưng Frame ghi hình thì không

Đây là lỗi tinh vi nhất trong toàn bộ pipeline AR + Recording: **hiệu ứng đi đúng theo tay khi xem trực tiếp, nhưng lại đi ngược hướng khi xem lại video đã quay** — dù không có gì "crash" hay báo lỗi.

**Cơ chế:**

1. Với **camera trước**, CameraX **tự động lật ngang (mirror)** output của use case `Preview` để hiển thị giống cảm giác soi gương — đây là hành vi built-in, không cần code thêm.
2. Output của use case `ImageAnalysis` (nơi lấy `imageProxy.toBitmap()` để đưa vào MediaPipe và cũng là nguồn ảnh nền để ghi video) **không hề bị mirror** — chỉ xoay theo `rotationDegrees`, giữ nguyên đúng frame thô từ cảm biến.
3. Do đó, toạ độ landmark tay từ MediaPipe được tính trên **hệ toạ độ không mirror**, khớp với `ImageAnalysis`/frame ghi video, nhưng **lệch** so với những gì mắt nhìn thấy trên `PreviewView` (đang mirror).
4. Để bù trừ, code overlay hiển thị live thường có một phép lật thủ công dạng `(1f - x)` khi tính toạ độ vẽ — bù đúng cho việc preview bị mirror, nên **xem trực tiếp thấy đúng hướng**.
5. Cạm bẫy: nếu tái sử dụng **nguyên hàm vẽ overlay đó** (kèm phép lật `1f - x`) để composite hiệu ứng vào frame ghi video — nơi ảnh nền (`rotatedBitmap`) lại **không** bị mirror — thì chỉ riêng phần hiệu ứng bị lật trong khi nền ảnh thì không, khiến hiệu ứng chạy **ngược hướng tay** trong file video xuất ra.

| Nơi vẽ | Ảnh nền lấy từ đâu | Ảnh nền có mirror không? | Landmark cần lật (`1f - x`) không? |
| :--- | :--- | :---: | :---: |
| Overlay đè lên `PreviewView` (xem trực tiếp) | CameraX `Preview` (tự động mirror) | ✅ Có | ✅ Có |
| Composite vào frame ghi video (`pushFrame`) | `ImageAnalysis` / `rotatedBitmap` (không mirror) | ❌ Không | ❌ Không |

**Fix — tách rõ 2 đường gọi bằng 1 tham số, không dùng chung mặc định:**

```kotlin
fun drawHandEffects(canvas: Canvas, handResult: HandLandmarkerResult, mirrorX: Boolean) {
    for (landmark in handResult.landmarks()) {
        val middleMcp = landmark[9]
        val normalizedX = if (mirrorX) (1f - middleMcp.x()) else middleMcp.x()
        val cx = (normalizedX * imgWidth * scaleFactor) + offsetX
        // ...phần còn lại giữ nguyên
    }
}

// Vẽ live lên PreviewView:
drawHandEffects(canvas, result, mirrorX = true)

// Composite vào frame ghi video (trong pushFrame):
overlayView.drawHandEffects(canvas, result, mirrorX = false)
```

**Quy tắc chung (áp dụng cho mọi hiệu ứng AR, không riêng gì bàn tay):** bất kỳ phép biến đổi toạ độ nào được thêm vào **chỉ để bù trừ cho mirror ở tầng hiển thị** (display-layer) đều **không được rò rỉ** sang bất kỳ pipeline nào composite lên dữ liệu pixel **không mirror**. Khi có từ 2 "đích vẽ" trở lên dùng chung 1 tập toạ độ landmark (ví dụ: preview trực tiếp + frame ghi video + có thể sau này là chụp ảnh tĩnh), hãy tham số hoá rõ ràng đặc tính mirror của từng đích, thay vì giả định "vẽ ở đâu cũng giống nhau".

### 8.8. Cạm bẫy 7: Video quay ra bị ngược chiều so với Preview lúc quay — và vì sao "lật cả Canvas" là cách sai

Sau khi tách đúng `mirrorX` theo từng pipeline (Mục 8.7), một vấn đề khác dễ xuất hiện: **video xuất ra bị ngược chiều so với những gì thấy trên preview lúc quay** (ví dụ đưa tay sang phải nhưng video lại cho thấy tay đưa sang trái). Nguyên nhân: `rotatedBitmap` (nguồn ảnh nền để ghi video, lấy từ `ImageAnalysis`) vốn **không** được CameraX tự mirror như `PreviewView` — nên nếu ghi thẳng `rotatedBitmap` vào video mà không xử lý gì thêm, video sẽ đúng theo thực tế vật lý (giống video quay bằng camera sau) nhưng lại **khác** với cảm giác "soi gương" mà người dùng đã quen mắt khi xem preview.

**Cách sai (trực giác đầu tiên): lật thẳng toàn bộ `Canvas`**

```kotlin
// SAI — lật cả canvas, kéo theo lật luôn NỘI DUNG hiệu ứng
canvas.save()
canvas.scale(-1f, 1f, recW / 2f, recH / 2f)
canvas.drawBitmap(rotatedBitmap, bmpMatrix, null)
overlayView.drawHandEffects(canvas, result, mirrorX = false, forRecording = true)
canvas.restore()
```

`canvas.scale(-1f, 1f, ...)` lật **toàn bộ hệ toạ độ** của canvas — mọi lệnh vẽ sau đó, kể cả `drawBitmap(activeLayer.buffer, ...)` bên trong `drawHandEffects()`, đều bị lật theo. Hậu quả: vị trí của hiệu ứng thì đúng, nhưng **hình dạng con quái vật bị lật ngược/lộn trái** so với thiết kế gốc của file GIF — một lỗi tinh vi vì rất dễ chỉ nhìn thấy "vị trí đúng rồi" mà bỏ qua việc bản thân hình ảnh đã bị đảo.

**Cách đúng: chỉ mirror `Matrix` của riêng lớp nền, giữ nguyên `Matrix` của lớp hiệu ứng — thống nhất bằng toạ độ, không phải bằng phép biến đổi canvas dùng chung**

```kotlin
val s = max(recW / w, recH / h)
val offsetX = (recW - w * s) / 2f
val offsetY = (recH - h * s) / 2f

// Chỉ lật MATRIX CỦA RIÊNG BITMAP NỀN — không đụng gì tới canvas chung
val bmpMatrix = Matrix().apply {
    setScale(-s, s)                       // scaleX âm = lật ngang riêng lớp này
    postTranslate(w * s + offsetX, offsetY)
}
canvas.drawBitmap(rotatedBitmap, bmpMatrix, null)

// Nền đã mirror giống preview -> tính toạ độ tay theo ĐÚNG mirrorX = true (giống hệt logic live)
overlayView.drawHandEffects(canvas, result, mirrorX = true, forRecording = true)
```

Cơ chế: lớp nền (`rotatedBitmap`) được lật bằng `Matrix` **riêng của chính nó**, không ảnh hưởng gì tới `Matrix` mà `drawHandEffects()` dùng để vẽ `activeLayer.buffer` (GIF) — nội dung pixel của GIF giữ nguyên hình dạng thiết kế gốc, chỉ có **toạ độ tâm `(cx, cy)`** của nó thay đổi (do tính lại theo `mirrorX = true`) để khớp đúng vị trí tay đã mirror ở lớp nền bên dưới.

> **Nguyên tắc chung khi ghép nhiều lớp (nền + hiệu ứng + có thể thêm chữ/logo sau này) vào cùng 1 frame ghi hình:** không bao giờ áp dụng 1 phép biến đổi hình học (mirror, rotate...) lên **toàn bộ `Canvas` dùng chung**, vì nó sẽ lật/xoay theo cả nội dung pixel của mọi lớp, kể cả những lớp vốn không cần bị lật (asset thiết kế sẵn như logo, GIF, text). Thay vào đó, áp phép biến đổi **riêng cho `Matrix` của từng lớp cần lật vị trí**, và đảm bảo mọi lớp cùng thống nhất theo **1 hệ quy chiếu toạ độ chung** (ở đây là "toạ độ đã mirror, giống hệt cách preview hiển thị") bằng cách truyền đúng tham số (`mirrorX`) vào từng hàm vẽ toạ độ, thay vì để canvas tự động lật hộ mọi thứ.

### 8.9. Quy trình chẩn đoán lỗi quay video AR (Debug Checklist)



Khi gặp lỗi liên quan tới quay video AR (crash, Surface abandoned, không thấy frame, hiệu ứng sai hướng...), làm theo đúng thứ tự sau để khoanh vùng nhanh, tránh sửa nhầm chỗ:

1. **Nếu app crash thật (force-close):** luôn lấy full `FATAL EXCEPTION` (lọc theo tag `AndroidRuntime`) trước khi phân tích. Các dòng log cấp E rời rạc kiểu `BufferQueue`/`FrameEvents` thường chỉ là **triệu chứng phụ**, không phải nguyên nhân gốc — đừng vội kết luận từ chúng.
2. **Nếu không crash nhưng hành vi sai:** xác định trước tiên đây là lỗi ở Surface nào — đọc kỹ tag trong log (`SurfaceView - ...` = preview; không có tag đó = khả năng cao là surface ghi hình), tránh gộp chung 2 loại lỗi có nguyên nhân khác nhau (Mục 8.1).
3. **Đọc to từng guard-clause** (`if (x) return`) trong các hàm toggle (`start`/`stop`) để xác nhận đúng ý đồ trước khi nghi ngờ các nguyên nhân phức tạp hơn (Mục 8.2).
4. **Liệt kê rõ mọi entry point chạy trên thread nào** (click listener = UI thread; analyzer callback = background executor...) trước khi nghi ngờ race condition hay đi thẳng vào thêm `synchronized` (Mục 8.3).
5. **Nếu crash là `NoSuchMethodError`/`NoSuchFieldError`:** gần như chắc chắn là do gọi API mới hơn `minSdk` mà thiếu kiểm tra `Build.VERSION.SDK_INT` thực sự — annotation `@RequiresApi` không tự bảo vệ (Mục 8.4).
6. **Trước khi kết luận "không ghi được gì":** luôn xác minh file có tồn tại bằng `adb pull` đường dẫn tuyệt đối. Loại trừ 2 khả năng dễ gây hiểu nhầm: thiếu extension (Mục 8.5) và Scoped Storage chặn duyệt thư mục dù file tồn tại thật (Mục 8.6).
7. **Nếu hiệu ứng "đúng lúc xem live nhưng sai lúc xem lại video"** (hoặc ngược lại) → nghi ngay tới sự khác biệt mirror/orientation giữa 2 pipeline hiển thị (Mục 8.7), **trước khi** nghi ngờ tới công thức toán học định vị landmark (vốn đã đúng, chỉ là bị áp sai lên sai hệ toạ độ).
8. **Nếu hiệu ứng bị lệch dần theo thời gian rồi mất hẳn khi vừa xem preview vừa ghi hình cùng lúc** (không có crash) → liệt kê mọi object khả biến (`Bitmap`, `Canvas`, `Drawable` động...) đang bị cả UI Thread lẫn Analyzer Thread cùng chạm vào, không chỉ riêng `MediaRecorder`/`Surface` (Mục 8.3, biến thể 2) — hướng fix ưu tiên là tách instance riêng cho từng luồng, không phải luôn luôn thêm `synchronized`.
9. **Nếu video quay ra đúng vị trí hiệu ứng nhưng bản thân hình ảnh (logo/GIF) bị lật ngược/lộn trái** → dấu hiệu của việc lật `Canvas` dùng chung thay vì lật riêng `Matrix` của lớp nền (Mục 8.8). Không bao giờ áp phép mirror lên toàn `Canvas` khi có nhiều lớp asset thiết kế sẵn cùng vẽ chung.

---

## 9. KIẾN TRÚC MIX AUDIO/VIDEO THỰC TẾ (MEDIACODEC + MEDIAMUXER)

> Mục này mô tả đúng kiến trúc thực tế đang chạy tại commit `c62037f` ("basic video recording with micro and sound effects") — **thay thế hoàn toàn** cách tiếp cận `MediaRecorder` đơn giản ở Mục 8.
>
> ⚠️ **Cập nhật quan trọng:** sau đợt refactor hiệu năng được mô tả ở **Mục 10**, kiến trúc ở Mục 9 này đã thay đổi thêm một bước nữa: **`MicReader` đã bị xóa hoàn toàn**, thay bằng `EffectAudioClock` (Mục 10.2), và quyền `RECORD_AUDIO` không còn được dùng. Phần còn lại của Mục 9 (`VideoEncoderWrapper`, `AudioEncoderWrapper`, `AudioMixer`, `MuxerCoordinator`, `SoundEffectPlayer`, cơ chế `ActiveEffect`/`startPos`) **vẫn còn nguyên giá trị** — đọc Mục 9 để hiểu *nền tảng*, rồi đọc Mục 10 để biết phần nào đã thay đổi tiếp và vì sao. Vì có khá nhiều class nhỏ phối hợp với nhau, mục này ưu tiên trả lời câu hỏi "class này để làm gì, tại sao cần nó" trước khi đi vào chi tiết code.

### 9.1. Vì sao phải bỏ `MediaRecorder`, chuyển sang `MediaCodec` + `MediaMuxer`

`MediaRecorder` (Mục 8) rất tiện nhưng có **đúng 1 giới hạn chí mạng** cho tính năng hiệu ứng âm thanh theo cử chỉ: nó chỉ chấp nhận **1 nguồn âm thanh cố định** (`AudioSource.MIC`, `CAMCORDER`...) — không có cách nào "tiêm" thêm dữ liệu PCM tự tạo (tiếng mèo kêu) vào track audio đang ghi. Để **trộn** (mix) tiếng mic thật với tiếng hiệu ứng do app tự phát, bắt buộc phải tự làm mọi khâu mà `MediaRecorder` từng làm hộ:

```
MediaRecorder (hộp đen, không can thiệp được vào bên trong)
        ▼ thay bằng
MediaCodec (encode video)  +  MediaCodec (encode audio)  +  MediaMuxer (ghép 2 track)
        + tự đọc mic (AudioRecord) + tự trộn PCM (AudioMixer) thủ công
```

Đánh đổi: code phức tạp hơn hẳn (nhiều class nhỏ), nhưng đổi lại có toàn quyền kiểm soát nội dung audio trước khi ghi vào file.

### 9.2. Sơ đồ luồng dữ liệu tổng quan

```
┌─────────────┐     ┌──────────────┐     ┌───────────────────┐     ┌──────────────────┐
│  MicReader   │────▶│  AudioMixer  │────▶│ AudioEncoderWrapper│────▶│                  │
│ (đọc mic,    │ PCM │ (trộn PCM    │ PCM │ (encode AAC,       │ AAC │  MuxerCoordinator │──▶ file.mp4
│  thread riêng)│     │  mic+hiệu ứng)│     │  add audio track)  │     │  (ghép 2 track,   │
└─────────────┘     └──────────────┘     └───────────────────┘     │   chờ đủ cả 2)    │
                                                                     │                   │
┌─────────────┐     ┌───────────────────┐                          │                   │
│ pushFrame()  │────▶│ VideoEncoderWrapper│─────────────────────────▶│                  │
│ (vẽ camera+AR│Canvas│ (encode H.264,    │ H.264                    └──────────────────┘
│  qua Surface)│     │  add video track)  │
└─────────────┘     └───────────────────┘

┌───────────────────┐
│ SoundEffectPlayer   │  ── phát THẲNG ra loa qua SoundPool, HOÀN TOÀN TÁCH BIỆT khỏi luồng ghi file ở trên
│ (live, không ghi)   │
└───────────────────┘
```

**Điểm quan trọng nhất cần nắm trước khi đọc tiếp:** có **2 pipeline âm thanh độc lập, chạy song song, không phụ thuộc nhau**:
1. **`SoundEffectPlayer`** — để người dùng **nghe trực tiếp** ngay khi xoè/nắm tay, không quan tâm có đang ghi hình hay không.
2. **`AudioMixer` → `AudioEncoderWrapper` → `MuxerCoordinator`** — để **ghi hiệu ứng vào file video**, chỉ hoạt động khi `VideoRecorder` đang `isRecording = true`.

Tắt 1 trong 2 không ảnh hưởng đến cái còn lại — đây là lý do kiến trúc có vẻ "nhiều class trùng lặp" nhưng thực chất mỗi bên phục vụ 1 mục đích khác hẳn nhau.

### 9.3. Vai trò từng class (bảng tra cứu nhanh)

| Class | Vai trò 1 câu | Chạy trên thread nào? |
|---|---|---|
| `VideoRecorder` | **Nhạc trưởng** — điều phối tất cả các class bên dưới, expose `start()`/`stop()`/`pushFrame()` cho `MainActivity` gọi | Được gọi từ cả UI Thread (`start`/`stop`) lẫn Analyzer Thread (`pushFrame`) |
| `VideoEncoderWrapper` | Bọc 1 `MediaCodec` chuyên encode hình ảnh (H.264), cấp ra 1 `Surface` để vẽ vào | Analyzer Thread (qua `pushFrame`) |
| `AudioEncoderWrapper` | Bọc 1 `MediaCodec` chuyên encode âm thanh (AAC) | Thread riêng của `MicReader` |
| `MicReader` | Mở mic vật lý (`AudioRecord`), đọc liên tục thành từng chunk PCM thô | Tự tạo 1 `Thread` riêng bên trong |
| `AudioMixer` | "Bàn trộn" — cộng PCM mic với PCM hiệu ứng đang lặp, chống vỡ tiếng (clamp) | Được gọi từ thread của `MicReader` |
| `MuxerCoordinator` | Ghép track video (H.264) + track audio (AAC) đã encode thành 1 file `.mp4` duy nhất | Được gọi từ cả Analyzer Thread (video) lẫn thread `MicReader` (audio) — có `synchronized` bảo vệ |
| `SoundEffectPlayer` | Phát hiệu ứng âm thanh **thẳng ra loa** để người dùng nghe ngay, không liên quan gì đến việc ghi file | UI Thread |

### 9.4. `VideoEncoderWrapper` & `AudioEncoderWrapper` — bọc `MediaCodec` thô

Cả 2 đều theo cùng 1 khuôn: `prepare()` cấu hình + khởi động `MediaCodec`, `release()` dọn dẹp. Khác biệt chính:

- **`VideoEncoderWrapper`** cấp ra 1 `Surface` (`createInputSurface()`) — bạn **vẽ trực tiếp** (`lockHardwareCanvas`) vào đó, không cần tự đưa dữ liệu thô vào; `MediaCodec` tự "chụp" nội dung Surface làm input.
- **`AudioEncoderWrapper`** không có Surface — bạn phải **tự tay** đẩy từng mảng `ShortArray` (PCM) vào qua `encodeAndWrite()`, đồng thời hàm này tự rút output đã encode và gọi thẳng `muxer.writeAudio(...)` / `muxer.addAudioTrack(...)` khi cần, không tách thành 2 bước riêng như phần video.

> ⚠️ Điểm dễ nhầm: `MediaFormat` thật của track (dùng để `addTrack()` vào `MuxerCoordinator`) **chỉ có sau khi** `MediaCodec` xử lý xong ít nhất 1 lần và trả về mã đặc biệt `INFO_OUTPUT_FORMAT_CHANGED` — không có ngay sau `configure()`. Đây là lý do cả `drainVideoEncoder()` (trong `VideoRecorder`) lẫn `encodeAndWrite()` (trong `AudioEncoderWrapper`) đều phải xử lý riêng nhánh `outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED` để gọi `muxer.addVideoTrack(...)`/`addAudioTrack(...)` đúng lúc.

### 9.5. `MuxerCoordinator` — chờ đủ 2 track mới `start()`

`MediaMuxer` có 1 ràng buộc quan trọng: **phải add đủ tất cả track cần dùng rồi mới được gọi `start()`**, và sau khi `start()` thì **không thể add thêm track nào nữa**. Vì video track và audio track được add từ **2 luồng khác nhau** (video từ Analyzer Thread, audio từ thread của `MicReader`) tại 2 thời điểm không đoán trước được (tuỳ luồng nào encode xong khung `INFO_OUTPUT_FORMAT_CHANGED` trước), `MuxerCoordinator` cần:
- `synchronized(lock)` bọc quanh mọi thao tác (đã áp dụng đúng bài học Mục 8.3)
- `maybeStart()` chỉ thực sự gọi `muxer.start()` khi **cả `videoTrack >= 0` lẫn `audioTrack >= 0`** — bất kể track nào tới trước, track kia tới sau cũng an toàn

Nếu không có `hasAudio` (người dùng từ chối quyền mic — xem 9.9), audio track **sẽ không bao giờ được add**, `maybeStart()` sẽ đợi mãi mãi → video ghi được nhưng **không có track audio nào cả** trong file — đây là hành vi **cố ý**, không phải bug, để app vẫn hoạt động được khi thiếu quyền mic.

### 9.6. `MicReader` — đọc PCM thô từ mic trên luồng riêng

Đơn giản nhất trong nhóm: mở `AudioRecord`, chạy 1 vòng lặp `while` trên `Thread` riêng, mỗi lần `read()` xong thì gọi callback `onPcmChunk(buffer, len)` — nơi `VideoRecorder.start()` đã nối sẵn để đẩy tiếp vào `AudioMixer.mix()`. Class này **không biết gì** về việc trộn hay encode — đúng tinh thần tách trách nhiệm (mỗi class chỉ làm đúng 1 việc).

### 9.7. `AudioMixer` — trộn PCM mic với PCM hiệu ứng, cơ chế lặp vô hạn

Đây là "bộ não" của việc mix âm thanh, nhưng logic khá ngắn gọn: chỉ giữ **đúng 1 slot** (`effectPcm`) cho hiệu ứng đang hoạt động.

- `triggerEffect(pcm, startPos)`: đặt hiệu ứng mới (ghi đè hiệu ứng cũ nếu có — đây là lý do "phát cái mới tự động cắt cái cũ" mà không cần logic gì thêm, chỉ vì cấu trúc dữ liệu có đúng 1 chỗ chứa).
- `mix()`: mỗi mẫu (`sample`) của mic được **cộng thẳng** với mẫu tương ứng của hiệu ứng (`result[i] + pcm[effectPos]`), sau đó `coerceIn(...)` để **chống vỡ tiếng** (clipping) nếu tổng vượt quá giới hạn số nguyên 16-bit.
- **Lặp vô hạn:** khi `effectPos` chạm hết độ dài mảng PCM, thay vì dừng hẳn (`effectPcm = null`), code quay `effectPos = 0` để phát lại từ đầu — hiệu ứng cứ thế lặp mãi cho tới khi có `triggerEffect()` mới (đổi cử chỉ) hoặc `triggerEffect(null)` (tay biến mất khỏi khung hình, xem 9.9).

### 9.8. `SoundEffectPlayer` — phát trực tiếp ra loa, độc lập với pipeline ghi hình

D�ng `SoundPool` — API dành riêng cho hiệu ứng ngắn, độ trễ thấp (khác `MediaPlayer` vốn hợp cho nhạc/video dài). 3 tham số cấu hình quan trọng, đều đã được chọn có chủ đích:

| Tham số | Giá trị | Vì sao |
|---|---|---|
| `setMaxStreams(1)` | 1 | Đảm bảo hiệu ứng mới luôn cắt ngang hiệu ứng cũ đang phát — khớp đúng ý muốn "chỉ nghe đúng 1 tiếng ứng với cử chỉ hiện tại", không bị chồng 2 tiếng |
| `loop` trong `play(..., -1, ...)` | `-1` | Lặp vô hạn khi giữ nguyên cử chỉ, dừng bằng `stop(streamId)` tường minh (qua `stopEffect()`) thay vì tự hết |
| `currentStreamId` | lưu lại ID | Bắt buộc phải có để gọi đúng `soundPool.stop(id)` khi tay biến mất khỏi khung hình — không lưu lại sẽ không có cách nào dừng 1 stream đang lặp vô hạn |

**Vì sao tách hẳn khỏi `AudioMixer`/pipeline ghi hình?** Vì 2 mục đích hoàn toàn khác nhau: người dùng cần **nghe ngay lập tức** dù có bấm nút quay hay không, còn `AudioMixer` chỉ có ý nghĩa khi đang ghi file. Gộp chung 2 việc này vào 1 class sẽ khiến class đó vừa phải biết cách phát loa vừa phải biết cách encode — vi phạm nguyên tắc mỗi class 1 trách nhiệm.

### 9.9. `VideoRecorder` — class điều phối trung tâm

Là nơi duy nhất `MainActivity` cần biết tới — sở hữu và khởi tạo toàn bộ 5 class ở trên (`VideoEncoderWrapper`, `AudioEncoderWrapper`, `MicReader`, `MuxerCoordinator`, và `audioMixer` được expose `public` để `MainActivity` gọi `triggerEffect()` từ bên ngoài).

Điểm đáng chú ý trong `start()`:
```kotlin
hasAudio = ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PERMISSION_GRANTED
```
Kiểm tra lại quyền **ngay tại thời điểm bắt đầu ghi**, không dựa vào kết quả xin quyền lúc mở app — vì người dùng có thể thu hồi quyền mic trong Settings hệ thống bất kỳ lúc nào giữa 2 lần ghi. Nếu `hasAudio = false`, toàn bộ khối audio (`audioEncoder.prepare()`, `micReader.start()`) bị bỏ qua — video vẫn ghi được, chỉ thiếu tiếng, đúng tinh thần "audio là tính năng mềm" đã thống nhất ở phần xin quyền.

### 9.10. Cơ chế `ActiveEffect` + `startPos` — đồng bộ pha âm thanh khi bắt đầu ghi giữa chừng hiệu ứng

Đây là phần tinh vi nhất, dễ gây khó hiểu nhất trong toàn bộ Mục 9 — giải quyết 1 tình huống cụ thể: **người dùng đang giữ tay xoè (nghe tiếng mèo kêu lặp qua loa) rồi mới bấm nút "Bắt đầu quay"** — lúc này hiệu ứng đã phát được 1 đoạn (ví dụ giữa chừng tiếng kêu thứ 2), nhưng track audio của video vừa mới `start()` thì phải bắt đầu từ đâu?

```kotlin
private data class ActiveEffect(val pcm: ShortArray, val startedAtMs: Long)

@Volatile private var activeEffect: ActiveEffect? = null

// Mỗi khi trigger hiệu ứng (đổi cử chỉ), LUÔN ghi lại luôn cả thời điểm bắt đầu:
activeEffect = ActiveEffect(pcm, SystemClock.elapsedRealtime())
```

Khi `toggleRecording()` bắt đầu ghi mới, nếu đang có 1 `activeEffect` tồn tại (tay vẫn đang giữ nguyên cử chỉ từ trước lúc bấm quay), code tính **hiệu ứng đã trôi qua bao lâu** rồi quy đổi ra "đã phát tới sample thứ mấy":
```kotlin
activeEffect?.let { effect ->
    val elapsedMs = SystemClock.elapsedRealtime() - effect.startedAtMs
    val elapsedSamples = (elapsedMs * 44_100L / 1000L).toInt()
    audioMixer.triggerEffect(effect.pcm, startPos = elapsedSamples)
}
```
`AudioMixer.triggerEffect()` nhận thêm `startPos`, dùng phép chia dư (`startPos % pcm.size`) để đưa `effectPos` vào **đúng vị trí tương ứng trong vòng lặp hiện tại** — nhờ vậy, tiếng ghi vào video sẽ **khớp pha** với tiếng người dùng đang nghe trực tiếp qua loa tại đúng thời điểm bấm quay, thay vì bị "giật lùi về đầu" một cách khó chịu.

> Đây là ví dụ điển hình của việc 2 pipeline độc lập (9.2) vẫn cần **1 điểm đồng bộ** khi chúng vô tình chồng lấn thời điểm với nhau — dù không dùng chung tài nguyên (không vi phạm Mục 8.3), chúng vẫn cần "nói chuyện" với nhau đúng 1 lần tại thời điểm `start()`.

### 9.11. Timestamp video vs audio — 2 cách tính khác nhau, vì sao

| | Video (`drainVideoEncoder`) | Audio (`VideoRecorder.start` callback) |
|---|---|---|
| Công thức | `bufferInfo.presentationTimeUs -= (recordStartTimeNs / 1000L)` | `ptsUs = totalAudioSamples * 1_000_000L / sampleRate` |
| Cơ sở tính | Đồng hồ hệ thống thật (wall-clock), trừ lùi về mốc `0` tại thời điểm `start()` | Đếm **số sample** đã xử lý, suy ngược ra thời gian theo `sampleRate` cố định |
| Vì sao khác nhau? | `Surface`/`MediaCodec` video tự gắn timestamp theo đồng hồ hệ thống tại thời điểm `lockHardwareCanvas`/`unlockCanvasAndPost` — cần trừ lùi vì đồng hồ hệ thống không bắt đầu từ `0` | `AudioRecord` đọc theo từng chunk có độ dài (`len`) xác định trước tại `sampleRate` cố định — đếm sample chính xác hơn dùng đồng hồ hệ thống (tránh sai số do thời điểm gọi callback không đều) |

Đây chính là kiểu kiến trúc "hybrid" để giảm thiểu (nhưng **không loại bỏ hoàn toàn**) vấn đề trôi lệch A/V đã cảnh báo trước đó: audio dùng cách tính **tất định** (deterministic, chỉ phụ thuộc số sample, không phụ thuộc tốc độ xử lý), trong khi video vẫn phụ thuộc vào **tần suất thực tế** gọi `pushFrame()` — nếu pipeline AI xử lý chậm/không đều, số lượng frame video trong 1 khoảng thời gian thực vẫn có thể ít hơn kỳ vọng, gây lệch tích luỹ giống đã phân tích ở Mục 8. Việc tính `ptsUs` theo sample cho audio giúp **bản thân track audio nghe mượt, không giật** (vì nội tại nó luôn đều), nhưng không tự động đồng bộ hoàn hảo với tốc độ thực tế của track video — đây vẫn là hạn chế đã biết, để ngỏ cho lần cải tiến tiếp theo (ví dụ nếu cần, có thể tham chiếu ngược PTS audio theo đúng tốc độ video thực tế thay vì giả định `sampleRate` không đổi).

---

## 10. REFACTOR HIỆU NĂNG & BỎ MIC (HẬU COMMIT `c62037f`)

> Mục này đúc kết từ đợt refactor theo đúng 4 tiêu chí mentor yêu cầu: **dung lượng file, độ dài, fps, tốc độ lưu**. Khác với Mục 7-9 (thiên về "làm sao cho đúng ngay từ đầu"), mục này có tính chất "nhật ký gỡ lỗi thực chiến" nhiều hơn.

### 10.1. Vì sao bỏ mic — quyết định sản phẩm, không chỉ kỹ thuật

Kiến trúc ở Mục 9 gặp bug: **hiệu ứng âm thanh bị ghi vào video 2 lần, lệch nhịp nhẹ, nghe như chạy song song**. Xác nhận bằng thực nghiệm đơn giản: tắt loa ngoài rồi quay lại — hiện tượng biến mất hoàn toàn. Kết luận: mic thu lại chính tiếng loa ngoài đang phát, cộng dồn lên trên bản mix phần mềm vốn đã có sẵn.

Hướng sửa "đúng kỹ thuật" (giảm âm lượng loa khi đang ghi, `AcousticEchoCanceler`...) đều khả thi nhưng giải quyết đúng triệu chứng, không giải quyết đúng câu hỏi gốc: ứng dụng dạng "magic hand effect" ngắn (giống thể loại gag/sticker để chia sẻ TikTok/Reels) — nhiều app cùng thể loại trên store **vốn đã không ghi mic**, chỉ bake sẵn hiệu ứng, vì 3 lý do độc lập với bug trên: (1) tránh xung đột với tính năng thêm nhạc nền của nền tảng đích sau khi đăng, (2) nội dung dạng phản ứng ngắn không cần thoại thật, (3) giảm friction xin quyền `RECORD_AUDIO`.

**Quyết định:** bỏ hẳn mic. Hệ quả tích cực ngoài dự kiến — không chỉ tự động triệt tiêu bug rò rỉ loa→mic, mà còn đơn giản hoá đáng kể toàn bộ phần còn lại của refactor (Mục 10.7 sẽ cho thấy việc đồng bộ PTS audio/video trở nên gần như tầm thường khi không còn áp lực "phải ghi mic thật càng sớm càng tốt").

> **Quy tắc chung:** khi 1 bug kỹ thuật có vẻ "khó vá triệt để", luôn tự hỏi thêm 1 bước: tính năng đang cố bảo vệ có thực sự cần thiết cho sản phẩm không, hay chỉ đang được giữ vì "đã có sẵn từ đầu"? Đôi khi hướng sửa tốt nhất là xoá bớt 1 yêu cầu, không phải viết thêm code để đáp ứng nó.

### 10.2. `EffectAudioClock` — đồng hồ âm thanh ảo thay cho `AudioRecord`

Bỏ mic không có nghĩa bỏ luôn track audio — `AudioMixer.mix()` vẫn cần 1 "nguồn nhịp" để biết lúc nào cần đẩy bao nhiêu sample vào encoder. Giải pháp: thay `MicReader` bằng 1 class phần mềm thuần tuý, tự sinh ra các đoạn PCM **im lặng** đúng nhịp thời gian thực:

```kotlin
class EffectAudioClock(private val sampleRate: Int) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(chunkMs: Int = 20, onChunk: (pcmChunk: ShortArray, len: Int) -> Unit) {
        running = true
        val chunkSamples = sampleRate * chunkMs / 1000
        val silence = ShortArray(chunkSamples)

        thread = Thread {
            val startNs = System.nanoTime()
            var producedSamples = 0L

            while (running) {
                val elapsedNs = System.nanoTime() - startNs
                val expectedSamples = elapsedNs * sampleRate / 1_000_000_000L
                val pending = (expectedSamples - producedSamples).toInt()

                if (pending >= chunkSamples) {
                    onChunk(silence, chunkSamples)
                    producedSamples += chunkSamples
                } else {
                    Thread.sleep(2)
                }
            }
        }.apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(200)
        thread = null
    }
}
```

**Điểm mấu chốt dễ bị làm sai:** không dùng `Thread.sleep(chunkMs)` cố định mỗi vòng lặp — sai số cộng dồn sẽ khiến đồng hồ ảo trôi dần so với thời gian thực. Thay vào đó, mỗi vòng lặp tính `expectedSamples` dựa trên **elapsed time thật** kể từ `startNs` — cùng nguyên lý PTS "neo theo elapsed time thật" đã học từ Grafika ở Mục 9 gốc.

### 10.3. Cạm bẫy: tham số tính đúng nhưng quên dùng (bitrate "ma")

```kotlin
class VideoEncoderWrapper(
    private val width: Int, private val height: Int, private val fps: Int,
    private val bitRate: Int = computeBitrate(width, height, fps)   // (1) tính đúng...
) {
    fun prepare() {
        val format = MediaFormat.createVideoFormat(...).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)   // (2) ...nhưng KHÔNG dùng bitRate!
        }
    }
}
```

Tham số `bitRate` được tính đúng nhưng chưa từng được đọc tới ở nơi thực sự cần dùng. Sửa: `setInteger(MediaFormat.KEY_BIT_RATE, bitRate)`.

> **Quy tắc chung:** khi 1 giá trị được "tính ra" nhưng số liệu đo thực tế không đổi sau khi sửa, nghi ngờ đầu tiên nên là "giá trị tính ra có thực sự được dùng ở nơi cần dùng không", trước khi nghi ngờ công thức tính sai.

### 10.4. VBR vs CBR — đo đạc trước khi tối ưu, đừng đoán

Sau khi sửa xong 10.3, ban đầu nghi ngờ bitrate thực tế vẫn cao hơn target vì `MediaCodec` mặc định chạy **VBR**. Thử ép `KEY_BITRATE_MODE = BITRATE_MODE_CBR` — nhưng đo lại: bật hay tắt CBR cho ra kết quả gần như giống hệt nhau. Tức là giả thuyết VBR-là-nguyên-nhân *sai* (nguyên nhân thật là 10.3).

**Quyết định cuối cùng: bỏ hẳn CBR, giữ VBR mặc định.** VBR về lý thuyết phù hợp hơn với nội dung có độ phức tạp cảnh thay đổi liên tục (camera + GIF hoạt hình).

> **Quy tắc chung:** khi 1 giả thuyết tối ưu "nghe có lý về mặt lý thuyết" nhưng chưa được đo đạc, đo trước khi commit vào code.

### 10.5. `OverlayView` phải tính scale theo canvas thực tế đang vẽ, không theo View màn hình

Khi thu nhỏ resolution ghi hình xuống HD (khác kích thước màn hình thật), phát hiện thêm 1 biến thể mới của nguyên tắc đã nêu ở Mục 8.7:

```kotlin
// SAI (dù đã chạy "đúng" suốt thời gian resolution ghi hình == kích thước màn hình)
fun setResult(handResult: HandLandmarkerResult, imgWidth: Int, imgHeight: Int) {
    scaleFactor = max(width * 1f / imgWidth, height * 1f / imgHeight)   // width/height = View màn hình
}
```

`scaleFactor`/`offsetX`/`offsetY` được tính 1 lần theo kích thước View màn hình, rồi dùng chung cho cả vẽ màn hình lẫn vẽ canvas ghi hình. Bug này im lặng do trùng hợp: canvas ghi hình luôn bằng đúng kích thước View màn hình — chỉ lộ ra khi 2 kích thước này khác nhau lần đầu.

**Fix — tính cục bộ theo canvas thật đang vẽ, ngay tại thời điểm vẽ:**

```kotlin
fun drawHandEffects(canvas: Canvas, handResult: HandLandmarkerResult, mirrorX: Boolean, forRecording: Boolean) {
    val targetW = if (forRecording) canvas.width.toFloat() else width.toFloat()
    val targetH = if (forRecording) canvas.height.toFloat() else height.toFloat()
    val localScale = max(targetW / imgWidth, targetH / imgHeight)
    val localOffsetX = (targetW - imgWidth * localScale) / 2f
    val localOffsetY = (targetH - imgHeight * localScale) / 2f
    // ...dùng localScale/localOffsetX/localOffsetY thay cho field cấp class
}
```

> **Quy tắc chung (mở rộng của Mục 8.7):** giá trị hình học nào phụ thuộc kích thước đích vẽ cụ thể đều là ứng viên của bug "rò rỉ" khi có đích vẽ thứ 2 kích thước khác — luôn tính theo tham số truyền vào tại thời điểm vẽ, không tính sẵn 1 lần.

### 10.6. Tách thread theo Grafika — và 2 bug mới xuất hiện ngay khi tách

Số liệu đo cho thấy fps thực tế (13.5–18.7fps) thấp hơn nhiều so với 25fps khai báo. Áp dụng bài học Grafika: tách hẳn **thread camera/AI** (chỉ đọc ảnh, xoay, chạy `detectAsync`, ghi kết quả mới nhất vào biến `@Volatile`) khỏi **thread ghi hình riêng** (loop độc lập theo nhịp ~25fps, tự lấy dữ liệu mới nhất để vẽ+encode).

Thay đổi này **ngay lập tức tạo ra 2 bug mới**:

#### 10.6.1. Sleep cố định sau khi làm việc → tụt fps thật

```kotlin
// SAI
while (videoRecorder?.isRecording == true) {
    if (bitmap != null) { videoRecorder?.pushFrame { canvas -> ... } }   // (a) tốn X ms
    Thread.sleep(intervalMs)   // (b) LUÔN ngủ đủ 40ms nữa, bất kể (a) đã tốn bao lâu
}
```

Chu kỳ thật của mỗi vòng lặp = thời gian xử lý (a) + 40ms cố định (b), không phải chỉ 40ms như mục tiêu.

**Fix — trừ hao thời gian đã xử lý:**

```kotlin
val frameStartNs = System.nanoTime()
if (bitmap != null) { videoRecorder?.pushFrame { canvas -> ... } }
val elapsedMs = (System.nanoTime() - frameStartNs) / 1_000_000
val sleepMs = (intervalMs - elapsedMs).coerceAtLeast(0)
if (sleepMs > 0) Thread.sleep(sleepMs)
```

#### 10.6.2. Thiếu 1 dấu âm khi copy code giữa 2 nơi → "GIF chồng GIF" qua cơ chế buffer xoay vòng của `Surface`

Triệu chứng: video ghi ra hiện tượng nhiều lớp GIF chồng lên nhau kiểu "vệt bóng ma", trong khi preview hoàn toàn bình thường.

```kotlin
// Code gốc (đúng)
val bmpMatrix = Matrix().apply {
    setScale(-s, s)                          // dấu ÂM để mirror
    postTranslate(w * s + offsetX, offsetY)
}
```
```kotlin
// Code copy sang thread mới — MẤT dấu âm
val bmpMatrix = Matrix().apply {
    setScale(s, s)                           // thiếu dấu âm!
    postTranslate(w * s + offsetX, offsetY)  // công thức này CHỈ đúng khi scale ÂM
}
```

Khi scale đổi dấu thành dương mà giữ nguyên công thức dịch, hình camera bị dịch lố sang phải đúng `w*s` pixel — để hở 1 dải bên trái canvas không được vẽ đè lên. `Surface.lockHardwareCanvas()` xoay vòng nhiều buffer vật lý, mỗi buffer có thể còn giữ nội dung của vài frame trước — vì phần bên trái không được vẽ đè, nội dung GIF của frame cũ lộ ra, cộng dồn qua nhiều frame thành "vệt bóng ma".

**Fix:** thêm lại đúng 1 dấu `-` (`setScale(-s, s)`).

> **Quy tắc chung:** khi copy-paste code hình học (matrix, scale, rotate) sang ngữ cảnh mới, phải kiểm tra bằng mắt kết quả hình ảnh thực tế — bug sai dấu/sai hệ số hình học không hề crash, chỉ âm thầm cho ra hình sai, và triệu chứng có thể trông rất khác nguyên nhân gốc.

### 10.7. PTS baseline lazy-init + đồng bộ audio/video tại đúng 1 mốc

Hiện tượng: vài frame đầu tiên của video bị đơ/đứng hình khi phát lại. Nguyên nhân (cùng họ với bài học Grafika ở Mục 9 gốc — "PTS phải neo vào frame đầu tiên thực sự được encode"): `recordStartTimeNs` được lấy ngay trong `start()`, trong khi frame camera thật đầu tiên luôn đến trễ hơn.

**Fix — dời baseline sang đúng lúc frame đầu tiên thực sự được đẩy vào (lazy-init trong `pushFrame()`):**

```kotlin
fun pushFrame(draw: (Canvas) -> Unit) {
    synchronized(lock) {
        if (!isRecording) return
        if (recordStartTimeNs < 0) {
            recordStartTimeNs = System.nanoTime()
            effectClock.start { pcmChunk, len -> ... }
        }
        // ...vẽ + drain như cũ
    }
}
```

**Điểm hay nhất của việc đã bỏ mic lộ ra ở đây:** vì `EffectAudioClock` không đại diện cho âm thanh thật (không sợ "lỡ mất tiếng" nếu bắt đầu trễ), có thể dời luôn cả việc khởi động audio clock sang đúng thời điểm này. Kết quả: cả audio track lẫn video track đều có PTS = 0 tại đúng cùng 1 thời điểm thật — đồng bộ hoàn hảo ngay từ đầu, không cần tính offset bù trừ phức tạp như Mục 9.11 từng phải làm khi còn mic thật.

> **Quy tắc chung:** khi bỏ 1 ràng buộc kỹ thuật cũ, luôn rà lại xem còn phần code nào khác đang thiết kế quanh ràng buộc đó không.

### 10.8. `MediaMuxer` cần đủ 2 track mới `start()` — race condition khi Record/Stop quá nhanh

Test "bấm Record/Stop liên tục thật nhanh" phát hiện: thỉnh thoảng file mp4 tạo ra bị lỗi, đọc bằng `MediaMetadataRetriever` báo `status = 0xFFFFFFEA` (đọc dạng số có dấu là `-22`, tức `-EINVAL`).

Gốc rễ: `MuxerCoordinator.maybeStart()` chỉ gọi `muxer.start()` khi **cả video track lẫn audio track đều đã add**. Video track được add gần như ngay lập tức. Nhưng sau khi tách thread (Mục 10.6), audio track lại phụ thuộc vào việc Thread của `EffectAudioClock` được hệ điều hành cấp lịch — không tức thời. Nếu bấm Stop cực nhanh, video đã có 1 frame nhưng audio track chưa từng được add → `muxer.start()` không bao giờ được gọi → file rỗng/hỏng.

Lớp vá đầu tiên (check `recordStartTimeNs >= 0`) **giảm xác suất nhưng chưa triệt để**. Lớp vá thứ 2 (ép `encodeAndWrite()` với silence chunk trong `stop()`) giảm thêm nữa — nhưng vẫn còn sót ở những lần bấm Stop cực đoan (dưới 100-200ms), vì **cả video encoder cũng có độ trễ pipeline nội tại vài frame** trước khi thật sự trả ra output buffer đầu tiên (đặc tính phần cứng/driver).

> **Quy tắc chung:** khi bug là race condition giữa tốc độ thao tác người dùng và độ trễ pipeline phần cứng, mỗi lớp vá thêm ở tầng encoder chỉ thu hẹp cửa sổ race chứ không đóng hẳn được.

### 10.9. Quyết định cuối: chặn ở tầng UX thay vì đuổi race condition ở tầng encoder

Sau 2 lượt vá ở tầng encoder vẫn còn sót, chuyển hẳn sang giải pháp tầng sản phẩm — giới hạn thời lượng ghi tối thiểu (1 giây):

```kotlin
private const val MIN_RECORD_DURATION_MS = 1000L
private var recordStartUiTimeMs = 0L

val elapsed = SystemClock.elapsedRealtime() - recordStartUiTimeMs
if (elapsed < MIN_RECORD_DURATION_MS) {
    Toast.makeText(this, "Giữ máy quay thêm chút nữa nhé", Toast.LENGTH_SHORT).show()
    return
}
```

1 giây (~25 frame) dư sức vượt qua độ trễ khởi động nội tại, loại bỏ toàn bộ họ race condition này thay vì thu hẹp thêm; đồng thời 1 clip 70-230ms không có giá trị sử dụng thực tế với bất kỳ ai.

> **Quy tắc chung:** không phải mọi race condition đều đáng để tiếp tục vá ở đúng tầng nó xuất hiện. Khi chi phí đóng triệt để 1 race condition ở tầng thấp tăng dần mà lợi ích thực tế giảm dần, cân nhắc chặn từ tầng cao hơn (UX).

### 10.10. `VideoStatsLogger` — công cụ đo thật, đừng tin số fps khai báo

Bài học lớn nhất xuyên suốt Mục 10: số liệu `KEY_FRAME_RATE`/`KEY_BIT_RATE` khai báo cho `MediaCodec` chỉ là con số mục tiêu, không phải cam kết cứng. Công cụ đơn giản dựa trên `MediaMetadataRetriever` (chỉ chạy ở bản Debug qua `BuildConfig.DEBUG`):

```kotlin
fun logRecordingStats(context: Context, file: File) {
    if (!BuildConfig.DEBUG) return
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
        // avgFps = frameCount / (durationMs / 1000.0) — fps THẬT, không phải fps khai báo
    } finally {
        retriever.release()
    }
}
```

> ⚠️ `METADATA_KEY_VIDEO_FRAME_COUNT` chỉ có từ API 28 trở lên.

Số liệu đo được nhiều lần đảo ngược trực giác ban đầu: giảm resolution không cải thiện fps, thêm CBR không thay đổi gì, tách thread lần đầu còn làm **giảm** fps thay vì tăng. **Quy tắc chung:** luôn đo lại bằng công cụ trung lập trước khi kết luận đã tối ưu xong.

### 10.11. Cạm bẫy phụ: `BuildConfig` trùng tên từ nhiều thư viện

Khi thêm `if (!BuildConfig.DEBUG) return`, IDE auto-import chọn nhầm `com.google.android.datatransport.BuildConfig` — một class public nhưng không liên quan, lộ ra qua auto-import vì thư viện `datatransport` (kéo theo qua CameraX/MediaPipe) có sẵn 1 class `BuildConfig` nội bộ. `DEBUG` của class đó luôn `false`, khiến điều kiện luôn early-return — không lỗi biên dịch, chỉ sai logic âm thầm.

Gốc rễ sâu hơn: từ AGP 8+, tính năng sinh class `BuildConfig` cho chính app **mặc định bị tắt**, phải khai báo tường minh:

```kotlin
android {
    buildFeatures {
        buildConfig = true
    }
}
```

> **Quy tắc chung:** khi 1 dòng code compile được nhưng hành vi runtime hoàn toàn không đúng kỳ vọng — đặc biệt với các class tên rất chung chung (`BuildConfig`, `Config`...) — luôn xác minh lại chính xác package của import đó trước khi nghi ngờ logic.

### 10.12. Quy trình chẩn đoán mở rộng (Debug Checklist)

Bổ sung nối tiếp Debug Checklist ở Mục 8.9:

1. **Nếu 1 giá trị cấu hình đã sửa công thức nhưng đo lại không đổi** → nghi ngờ đầu tiên là "giá trị tính ra có thực sự được dùng đúng chỗ không" (Mục 10.3).
2. **Nếu tối ưu hiệu năng theo 1 giả thuyết lý thuyết nhưng chưa đo bằng công cụ trung lập** → luôn đo trước khi kết luận (Mục 10.4, 10.6, 10.10).
3. **Nếu hiệu ứng/hình ảnh đúng khi resolution ghi hình == kích thước màn hình, nhưng sai ngay khi 2 kích thước này khác nhau lần đầu** → nghi ngay tới giá trị hình học tính sẵn theo 1 đích vẽ cụ thể (Mục 10.5).
4. **Nếu vừa tách 1 pipeline tuần tự thành nhiều thread mà xuất hiện "chồng hình"/"tụt tốc độ"** → soát lại từng đoạn code hình học/timing bị copy sang thread mới có bị đổi/mất dấu không (Mục 10.6).
5. **Nếu file media thỉnh thoảng đọc lỗi "invalid argument"/`-EINVAL`** → nghi ngờ race condition giữa các track được add vào muxer từ nhiều thread, đặc biệt khi Start/Stop rất nhanh (Mục 10.8) — cân nhắc chặn ở tầng UX (Mục 10.9) nếu vá tầng encoder không triệt để.
6. **Nếu 1 dòng code dùng class tên chung chung compile được nhưng sai logic hoàn toàn** → kiểm tra lại chính xác package của import (Mục 10.11).

---

## 11. NHẬN DIỆN CỪ CHỈ 2 TAY

> Mục này đúc kết từ đợt mở rộng `Gestures` từ chỉ hỗ trợ 1 tay sang hỗ trợ cả 2 tay đồng thời.

### 11.1. Vì sao phải đổi kiến trúc

Trước đợt mở rộng, `OverlayView.drawHandEffects()` lặp qua từng phần tử của `handResult.landmarks()`, mỗi tay lại gọi riêng `gesture.recognize(listOf(landmark))` — luôn đóng gói thành danh sách chỉ 1 phần tử, dù `GestureRecognizer.recognize()` được thiết kế sẵn để nhận `List<List<NormalizedLandmark>>` (nhiều tay) ngay từ đầu. Tương tự, `CameraRecordFragment.handleGesture()` chỉ lấy `result.landmarks().firstOrNull()` — tay thứ 2 bị bỏ hoàn toàn khỏi đường âm thanh/debounce.

Fix: sửa 2 điểm gọi này để gọi `recognize()` đúng 1 lần, truyền toàn bộ danh sách tay. Phần debounce/audio phía sau không cần đổi gì vì chỉ quan tâm `matchedState.id`. Vì `GestureRecognizer` vẫn giữ tính vô trạng thái, dùng chung 1 `object Gestures` vẫn an toàn dù gọi từ nhiều luồng.

Quan trọng: `EffectDefinition.requiredNumHands` phải khớp với số tay lớn nhất mà các gesture trong `states` cần. Nếu 1 state dùng gesture 2 tay nhưng `requiredNumHands = 1`, `hands.size < 2` sẽ luôn đúng, gesture đó không bao giờ chạy qua khỏi dòng guard đầu tiên — kể cả log debug thêm vào cũng không in được dòng nào vì hàm return trước khi tới `Log.d`.

### 11.2. Công thức 8: Tâm & bán kính khi có nhiều tay

Dùng trung bình cộng toạ độ `middleMcp` (landmark 9) của mọi tay đang có để tính điểm neo — với 1 tay, trung bình của 1 giá trị chính là giá trị đó, tự động tương thích ngược. Bán kính cũng lấy trung bình cộng bán kính từng tay theo công thức cũ. Vì phép lật gương `1 - x` là tuyến tính, áp mirror sau khi lấy trung bình cho kết quả giống hệt áp trước rồi mới trung bình.

### 11.3. Cạm bẫy: gõ nhầm biến khi mở rộng công thức

Sau khi viết lại công thức neo, xuất hiện triệu chứng: hiệu ứng chỉ neo đúng khi tay ở bên trái màn hình, không bao giờ bám sang phải dù tay di chuyển thật. Nguyên nhân: gõ nhầm `normMidY` thay vì `normMidX` trong dòng tính mirror — khiến `cx` phụ thuộc vào vị trí tay theo chiều cao thay vì chiều ngang. Quy tắc chung: khi 2 biến tên gần giống nhau (X/Y) cùng xuất hiện trong 1 biểu thức sau khi vừa refactor, triệu chứng "chỉ đúng ở 1 vùng cố định, không phản ứng theo trục di chuyển" là dấu hiệu đặc trưng của việc dùng nhầm trục.

### 11.4. Công thức 9: Độ cong ngón tay không phụ thuộc hướng cong (Finger Curl Ratio)

Công thức 4 gốc (so khoảng cách tip/pip tới cổ tay) chỉ đúng khi ngón cong theo trục gần trùng hướng cổ tay. Khi ngón cong theo hướng khác (vào trong như OK sign, hoặc sang ngang như trái tim 2 tay), công thức đó báo sai. Giải pháp: so khoảng cách thẳng từ gốc tới đầu ngón (mcp→tip) với tổng độ dài các đốt ngón (mcp→pip→dip→tip) — cả 2 đại lượng chỉ dùng điểm trên chính ngón đó, không phụ thuộc điểm neo ngoài nên không bị sai theo hướng cong.

```kotlin
fun fingerCurlRatio(landmark: List<NormalizedLandmark>, mcp: Int, pip: Int, dip: Int, tip: Int): Double {
    val straight = distance(landmark[mcp], landmark[tip])
    val boneLength = distance(landmark[mcp], landmark[pip]) +
            distance(landmark[pip], landmark[dip]) +
            distance(landmark[dip], landmark[tip])
    if (boneLength == 0.0) return 1.0
    return straight / boneLength
}
```

Ngón thẳng → tỉ lệ gần 1.0. Ngón cong (dù hướng nào) → tỉ lệ tụt rõ dưới 1.0. Ngưỡng thực tế đo được: khởi điểm đoán `0.8` gây chập chờn vì trùng đúng dải nhiễu tự nhiên (`0.72–0.82`) đo được qua log thật; nâng lên `0.88` giải quyết được. Bài học lặp lại: ngưỡng đoán trước luôn cần xác nhận bằng log thật, không tin trực giác.

### 11.5. Công thức 10: Giao cắt 2 đoạn thẳng (Segment Intersection)

Dùng cho cử chỉ bắt chéo (dấu X) — khác các công thức khác ở chỗ hỏi "2 đoạn có thực sự cắt qua nhau" thay vì "2 điểm có đủ gần", có công thức chính xác tuyệt đối không cần dò ngưỡng, dựa trên orientation test (tích có hướng):

```kotlin
fun segmentsCross(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark, d: NormalizedLandmark): Boolean {
    val d1 = crossSign(c, d, a); val d2 = crossSign(c, d, b)
    val d3 = crossSign(a, b, c); val d4 = crossSign(a, b, d)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}
```

Đây là hiếm hoi trường hợp bỏ trục z không phải đánh đổi mà đúng bản chất cần đo: "bắt chéo hiển thị" là kiểu dáng nhìn từ camera, không phải sự thật vật lý 3D cần 2 ngón thực sự chạm nhau.

### 11.6. Công thức 11: Góc giữa 2 vector qua Dot Product — ĐÃ GỠ KHỎI APP

> **Cập nhật:** cử chỉ khung máy ảnh (và công thức `vectorAngleCos` riêng cho nó) đã bị xoá
> hoàn toàn khỏi `Gestures.kt`/`GestureUtils.kt` — không còn trong danh sách hiệu ứng thực tế.
> Lý do (quan sát thực tế, chưa đo bằng số liệu cụ thể): công thức đúng về toán, nhưng tay người
> **rất khó tự nhiên giữ đúng góc vuông hoàn hảo** giữa ngón cái và ngón trỏ — muốn ép đúng hình
> chữ nhật thường phải cố gập/cong ngón theo cách không tự nhiên, làm chính góc đang đo bị lệch đi.
> Cộng thêm hiện tượng **che khuất đầu ngón** (đầu ngón này nằm trước/sau đầu ngón kia theo
> hướng nhìn camera) — landmark 2D của MediaPipe lấy từ hình chiếu phẳng, nên khi bị che khước
> như vậy, toạ độ đo được không còn phản ánh đúng góc thật giữa 2 ngón nữa. Cộng dồn 2 yếu tố này
> với đòi hỏi phải đúng **đồng thời** cả 2 tầng điều kiện (Mục 11.7) khiến cử chỉ này khó giữ đúng
> để được nhận diện hơn hẳn trái tim/dấu X trong thực tế sử dụng, dù công thức tự nó không sai.
> Giữ lại công thức dưới đây làm tài liệu tham khảo — toán đúng, chỉ là bài toán ứng dụng nó không
> đáng công duy trì.

Dùng cho cử chỉ khung máy ảnh cũ (hình L: cái + trỏ vuông góc) — công thức duy nhất phải đo góc thật vì độ vuông góc là thuộc tính góc thuần tuý, không có đại lượng khoảng cách nào thay thế được:

```kotlin
fun vectorAngleCos(o1: NormalizedLandmark, tip1: NormalizedLandmark, o2: NormalizedLandmark, tip2: NormalizedLandmark): Double {
    val v1x = tip1.x() - o1.x(); val v1y = tip1.y() - o1.y()
    val v2x = tip2.x() - o2.x(); val v2y = tip2.y() - o2.y()
    val dot = v1x * v2x + v1y * v2y
    val mag1 = hypot(v1x.toDouble(), v1y.toDouble())
    val mag2 = hypot(v2x.toDouble(), v2y.toDouble())
    if (mag1 == 0.0 || mag2 == 0.0) return 1.0
    return dot / (mag1 * mag2)
}
```

Vuông góc thật → cos = 0. Ngưỡng thực tế chấp nhận: `|cos| < 0.5`, tương đương chấp nhận góc lệch 60°–120° quanh vuông góc.

### 11.7. Hai cử chỉ tĩnh 2 tay cụ thể

**🫶 Trái tim 2 tay:** 2 đầu ngón trỏ chạm nhau ở đỉnh, 2 đầu ngón cái chạm nhau ở dưới, đỉnh trỏ nằm cao hơn (y nhỏ hơn) điểm chạm cái. Chuẩn hoá khoảng cách bằng trung bình cộng `palmLength` 2 tay (không dùng khoảng cách 2 cổ tay vì nó tự thay đổi theo đúng chuyển động cần đo, gây nhiễu vòng lặp). Ban đầu dùng `!isIndexExtended` để chặn hình tam giác giả — nhưng log thật cho thấy công thức đó luôn sai vì ngón trỏ cong sang ngang (đúng giới hạn Mục 11.4). Đổi sang `fingerCurlRatio` thì lộ ra vấn đề khác: ngón trỏ trong tư thế tim vươn ra để chạm tay kia (không co sâu), trong khi 3 ngón giữa/áp út/út co hẳn vào lòng bàn tay — ngón út là tín hiệu "co" đáng tin hơn chính ngón trỏ. Giải pháp cuối: chấp nhận cùng 1 loại ngón bất kỳ (trỏ hoặc út) co ở cả 2 tay đồng thời — phản ánh đúng tính đối xứng qua gương vốn có của cử chỉ 2 tay, chặt hơn hẳn cách chấp nhận 1-trong-4-ngón độc lập từng tay. Giới hạn chấp nhận không sửa: cần giơ tay ở góc tương đối đối diện camera mới ổn định, vì đo trên hình chiếu 2D.

**❌ Dấu X:** tổng quát hoá thành "1 trong 4 loại ngón bất kỳ, miễn cùng loại ở cả 2 tay và tự nó thoả cả 2 điều kiện riêng (thẳng và cắt nhau)" — khác trái tim ở chỗ không nới lỏng OR giữa các loại ngón vì `segmentsCross` đã tự đảm bảo tính nhất quán hình học.

> **Cử chỉ khung máy ảnh đã bị gỡ** (xem Mục 11.6) — từng dùng 2 tầng điều kiện (góc vuông
> mỗi tay + chạm chéo 2 tay), phức tạp hơn hẳn 2 cử chỉ còn lại ở đây, đúng nguyên nhân bị
> gỡ đã ghi ở đầu Mục 11.6.

### 11.8. Giới hạn đã biết & hướng mở rộng (cử chỉ động)

Cả 2 cử chỉ trên đều là cử chỉ tĩnh — chỉ cần 1 frame để quyết định, không cần nhớ frame trước. Đây là lý do toàn bộ `object Gestures` dùng chung 1 instance vô trạng thái an toàn từ nhiều luồng. Cử chỉ động (ví dụ vỗ tay — cần biết khoảng cách 2 tay đang giảm dần rồi chạm) sẽ phá vỡ tính vô trạng thái đó, cần buffer riêng theo từng instance và áp lại bài học race condition ở Mục 8.3 — quyết định hoãn lại có chủ ý, chỉ làm khi có nhu cầu thật.

Về việc tìm "công thức chuẩn" cho cử chỉ hình dạng phức tạp: khảo sát thực tế cho thấy không có giải pháp thuần công thức hình học nào được cộng đồng dùng phổ biến cho loại bài toán này. Thư viện `fingerpose` chỉ định nghĩa gesture 1 tay độc lập. Bộ dữ liệu HaGRID có sẵn lớp `hand_heart` nhưng giải bằng huấn luyện classifier trên hàng nghìn ảnh gán nhãn, không viết công thức hình học tay. Với quy mô app giải trí, chấp nhận giới hạn heuristic đã ghi chú rõ là quyết định hợp lý hơn đầu tư huấn luyện model chỉ cho 1 cử chỉ.

### 11.9. Quy trình chẩn đoán cử chỉ 2 tay

1. Log không in được dòng nào cả (kể cả nhánh guard sớm nhất) → kiểm tra `requiredNumHands` trước khi nghi logic bên trong.
2. Hiệu ứng chỉ đúng ở 1 vùng cố định, không phản ứng theo chuyển động → nghi dùng nhầm biến X/Y.
3. 1 điều kiện con luôn `false` dù tay đúng tư thế theo mắt thường → xét lại công thức có giả định sai hướng cong/trục không.
4. Ngưỡng đoán trước chập chờn dù tay đứng yên → log giá trị thật qua nhiều frame, xác nhận ngưỡng có rơi giữa dải nhiễu không, rồi nới ra xa dải đó — không đoán số khác mà không có số liệu.
5. Muốn phân biệt nhiều biến thể hình dạng gần giống nhau → tự hỏi có đang vá vô hạn không, nếu có thì dừng ở mức heuristic đủ dùng, ghi chú giới hạn.
6. Log debug nhiều điều kiện con → luôn log từng biến con trước khi kết hợp bằng `&&`/`||`, không chỉ log kết quả cuối.
7. Debug xong → luôn xoá sạch `Log.d`, nhất là gesture gọi mỗi frame (25-30 lần/giây).

---

## 12. Vẽ lớp Nền (Background) thay thế Camera

### 12.1. Kiến trúc: `OverlayView` đảm nhiệm 2 trách nhiệm độc lập

Kể từ khi `EffectDefinition` có thêm `background: EffectBackground?`, `OverlayView` vẫn chỉ là 1 class, nhưng gánh 2 việc không phụ thuộc nhau: vẽ nền (`liveBackground`/`recordingBackground`, chạy vô điều kiện mỗi frame nếu có nền) và vẽ hiệu ứng tay (`liveVisuals`/`recordingVisuals`, chỉ chạy khi có kết quả nhận diện). Cả 2 đều tuân thủ đúng nguyên tắc đã đúc kết ở Mục 8.3 biến thể 2: **2 instance riêng cho live và recording**, không chia sẻ trạng thái nội bộ (`Bitmap`/`AnimatedImageDrawable` của riêng từng instance) — nên bản thân cơ chế này không phải nguồn gây race condition, dù trông giống "một class làm quá nhiều việc".

**Điểm khác biệt quan trọng so với hiệu ứng tay:** `EffectAsset` (Mục 7) được thiết kế để **đè lên** camera/tay — bắt buộc có alpha xung quanh chủ thể (Mục 1 `Asset_Format_Guidelines.md`). `EffectBackground` ngược lại hoàn toàn: nó **thay thế** camera (khi có nền, `PreviewView` bị ẩn — `binding.preview.visibility = INVISIBLE`), nên bắt buộc phải **opaque tuyệt đối**. Nhầm lẫn 2 yêu cầu đối nghịch này là gốc rễ của Cạm bẫy 3 (Mục 12.4).

### 12.2. Cạm bẫy 1: Quên bước B — tính `Matrix` xong nhưng không gọi `drawBitmap`

Đúng tái hiện lỗi đã cảnh báo ở Mục 7.2 ("quy tắc 2 bước") — nhưng lần này xảy ra thật, không phải giả định lý thuyết:

```kotlin
// SAI — tính matrix xong, không dùng đến
override fun draw(canvas: Canvas) {
    val scale = max(cw / bw, ch / bh)
    val matrix = Matrix().apply { setScale(scale, scale); postTranslate(offsetX, offsetY) }
    // thiếu: canvas.drawBitmap(bitmap, matrix, paint)
}
```

Hậu quả: hàm chạy không lỗi, không crash, **không vẽ 1 pixel nào**. Live preview hiện màu nền mặc định của `FrameLayout` phía sau (vì camera đã ẩn) — trông giống "trắng bóc". Video ghi ra hiện nguyên nội dung buffer cũ đang xoay vòng của `Surface` (Mục 10.6.2) — trông giống "đen + ảnh chồng chéo". Cả 2 triệu chứng rất dễ bị quy nhầm cho alpha/asset sai (đã từng xảy ra khi debug lần này) — luôn đọc lại đúng hàm `draw()` thật trước, trước khi suy luận xa hơn về asset/thread.

### 12.3. Cạm bẫy 2: Tái phạm lỗi `AnimatedImageDrawable` không tự scale theo `setBounds()`

Biến thể của cạm bẫy đã ghi ở Mục 7.4, nhưng lần này xảy ra khi viết `AnimatedBackgroundRenderer` dù đã biết trước — chứng tỏ biết lý thuyết không thay thế được việc đối chiếu trực tiếp với pattern đã dùng đúng ở `AnimatedGifVisual`:

```kotlin
// SAI — setBounds() KHÔNG scale nội dung, chỉ định vị vùng vẽ đúng kích thước gốc
drawable.setBounds(offsetX, offsetY, offsetX + dw, offsetY + dh)
drawable.draw(canvas)
```
Triệu chứng: GIF/WebP động "dính đét" ở góc trên trái, đúng kích thước gốc, không phủ kín màn hình dù `dw`/`dh` đã tính đúng.

**Fix — quay lại đúng pipeline "Buffer trung gian + Matrix" Mục 7.2, chỉ đổi kích thước buffer:**
```kotlin
private val buffer: Bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
private val bufferCanvas = Canvas(buffer)

override fun draw(canvas: Canvas) {
    buffer.eraseColor(Color.TRANSPARENT)      // Mục 7.5
    drawable.draw(bufferCanvas)                // Bước A — đúng kích thước gốc, vào buffer riêng
    canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)   // Mục 12.4
    val matrix = Matrix().apply { setScale(scale, scale); postTranslate(offsetX, offsetY) }
    canvas.drawBitmap(buffer, matrix, null)    // Bước B — drawBitmap TÔN TRỌNG Matrix, khác Drawable.draw()
}
```
Cốt lõi: `Canvas.drawBitmap(bitmap, matrix, paint)` luôn tôn trọng `Matrix` 100% (Mục 7.1 — Bitmap là dữ liệu pixel tĩnh, bất biến); `Drawable.draw(canvas)` thì không — nó tự quyết định cách vẽ dựa trên `setBounds()` của riêng loại `Drawable` đó.

### 12.4. Cạm bẫy 3: Lớp nền phải phủ kín tuyệt đối — vì sao `drawBitmap` phủ kín kích thước vẫn chưa đủ

Sau khi sửa 2 cạm bẫy trên, nền đã hiện đúng hình, đúng kích thước — nhưng vẫn có thể còn "bóng ma" (frame cũ loáng thoáng qua nền mới). Nguyên nhân: `scale = max(cw/bw, ch/bh)` (CENTER_CROP) chỉ đảm bảo phủ kín **về kích thước**, không đảm bảo phủ kín **về nội dung** — nếu file nguồn (ảnh tĩnh hoặc từng frame GIF/WebP) có vùng alpha, `drawBitmap` không ghi đè gì ở đúng vùng đó, để lộ nguyên buffer cũ của `Surface` đang xoay vòng (Mục 10.6.2) — khớp đúng triệu chứng "phủ kín màn nhưng vẫn không rõ đã hết bóng ma chưa" — nguy hiểm hơn Cạm bẫy 1 vì **không dễ nhận ra bằng mắt thường**, đặc biệt với nền động (bản thân nội dung liên tục thay đổi, khó phân biệt "bóng ma" với "khung hình thật").

**Fix — `PorterDuff.Mode.SRC` fill cưỡng bức ngay trước khi vẽ nội dung thật, ở cả `ImageBackgroundRenderer` lẫn `AnimatedBackgroundRenderer`:**
```kotlin
canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)   // ghi đè tuyệt đối cả kênh alpha, không blend với nội dung cũ
```
Khác `canvas.drawColor(color)` mặc định (`SRC_OVER` — blend theo alpha, vẫn có thể lọt sai số nếu buffer cũ còn alpha khác 0), `SRC` xoá sạch bất kể nội dung cũ là gì — áp dụng đúng tinh thần `eraseColor` (Mục 7.5) lên canvas thật thay vì buffer trung gian.

**Cách kiểm chứng nhanh, không cần đoán:** đổi tạm `Color.BLACK` thành `Color.RED`, chạy lại — nếu trước đó có bóng ma ở vùng nào, vùng đó phải hiện đỏ chói thay vì bóng ma — xác nhận đúng asset có alpha thay vì đoán mò.

### 12.5. Quy tắc asset: không dùng chung file giữa `EffectAsset` và `EffectBackground`

Yêu cầu alpha của 2 loại asset **ngược hẳn nhau** (Mục 12.1): `EffectAsset` bắt buộc có alpha để đè lên camera/tay; `EffectBackground` bắt buộc opaque để thay thế camera. Dùng chung 1 `R.drawable.xxx` cho cả 2 vai trò — dù compile đúng, không báo lỗi gì — gần như chắc chắn tạo ra triệu chứng Mục 12.4 vì asset hiệu ứng tay luôn có vùng trong suốt xung quanh chủ thể. Khai `EffectBackground` cần asset riêng, thiết kế opaque ngay từ đầu — xem quy chuẩn cụ thể ở `Asset_Format_Guidelines.md` mục 5.

### 12.6. Quy trình chẩn đoán lớp Nền (Debug Checklist)

1. Nền không hiện gì (live trắng/video đen) → đọc lại đúng hàm `draw()` thật, xác nhận có dòng `canvas.drawBitmap(...)`/`drawable.draw(...)` thật sự được gọi — đừng suy luận từ triệu chứng trước.
2. Nền động dính 1 góc, không phủ kín màn → đang dùng `Drawable.draw()` + `setBounds()` thay vì `Canvas.drawBitmap()` + `Matrix` (Mục 12.3).
3. Nền đúng hình, đúng kích thước, nhưng vẫn nghờ thấy bóng ma mờ → đổi tạm màu fill sang `Color.RED` theo Mục 12.4 để xác nhận trực tiếp thay vì đoán.
4. Nghi ngờ race condition giữa live/recording → grep lại `setEffect()`, xác nhận có đúng 2 lần gọi `createBackgroundRenderer` riêng biệt (2 instance) — nếu đúng, race condition kiểu Mục 8.3 không thể xảy ra ở đây về mặt thiết kế, đừng tiếp tục tìm theo hướng này.
5. Muốn tái sử dụng asset hiệu ứng tay cũ làm nền để test nhanh → đừng — asset đó có alpha theo đúng thiết kế (Mục 12.5), sẽ tự gây ra triệu chứng Mục 12.4 dù code hoàn toàn đúng.