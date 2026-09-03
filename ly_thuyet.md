# TÀI LIỆU HƯỚNG DẪN MÔ PHỎNG HAND AR
> **Công nghệ chủ đạo:** CameraX | Google MediaPipe Tasks Vision | Android Canvas 2D | Hình học giải tích ứng dụng.

> **📝 Đã audit & cập nhật:** Bổ sung công thức Offset cho chế độ `CENTER_CROP` (Mục 3, Công thức 1–2), thêm mục mới về xử lý Rotation & Mirror của CameraX (Mục 3, Công thức 1.5), sửa khuyến nghị Delegate GPU/CPU theo dữ liệu thực tế mới nhất (Mục 2.4, Mục 6 rule 4), làm rõ lại claim về `RGBA_8888` (Mục 4.2).

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