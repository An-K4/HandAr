# TÀI LIỆU HƯỚNG DẪN MÔ PHỎNG FACE AR
> **Công nghệ chủ đạo:** CameraX | Google MediaPipe Tasks Vision (Face Landmarker) | Android Canvas 2D | Hình học giải tích ứng dụng.

> **📝 Phạm vi tài liệu:** Đây là tài liệu **lý thuyết nền tảng** cho tính năng nhận diện khuôn mặt, viết song song với `Camera_X_Hand_Landmarker.md`. Phần CameraX, OverlayView, chiếu tọa độ, mirror/rotation **dùng lại nguyên tắc đã có ở tài liệu Hand** (được dẫn chiếu thay vì chép lại); phần mới hoàn toàn là: mô hình 478 điểm mốc, blendshape, ma trận biến đổi khuôn mặt, các công thức EAR/MAR/Head Pose, và chiến lược chạy song song Hand + Face.
>
> ⚠️ **Lưu ý về độ tin cậy:** Các chỉ số landmark (index) và công thức ngưỡng bên dưới là giá trị **chuẩn từ Canonical Face Model của MediaPipe** và từ tài liệu học thuật. Ngưỡng thực tế (EAR, MAR, góc) **phải được đo lại trên thiết bị mục tiêu** — xem Mục 10 (Debug Checklist). Những chỗ chưa thể khẳng định từ tài liệu đều được đánh dấu "cần kiểm chứng".

---

## MỤC LỤC
1. [Kiến trúc hệ thống tổng quan](#1-kiến-trúc-hệ-thống-tổng-quan)
2. [Google MediaPipe Face Landmarker](#2-google-mediapipe-face-landmarker)
   - 2.1. Phân biệt: Face Detection vs Face Landmark vs Face Recognition
   - 2.2. Bản đồ 478 điểm mốc (Face Mesh) & các chỉ số quan trọng
   - 2.3. Hệ tọa độ chuẩn hóa & ý nghĩa trục `z`
   - 2.4. Cấu trúc dữ liệu `FaceLandmarkerResult`
   - 2.5. Blendshapes — 52 hệ số biểu cảm
   - 2.6. Facial Transformation Matrix — tư thế đầu 3D
   - 2.7. Cấu hình thực thi tối ưu
   - 2.8. Phương án nhẹ: `FaceDetector` (6 keypoint)
3. [Nền tảng toán học hình học giải tích cho khuôn mặt](#3-nền-tảng-toán-học-hình-học-giải-tích-cho-khuôn-mặt)
   - Công thức 1 & 1.5: Chiếu tọa độ, Rotation & Mirror (dẫn chiếu tài liệu Hand)
   - Công thức 2: Tâm mặt, kích thước mặt & góc nghiêng đầu (Roll)
   - Công thức 3: Eye Aspect Ratio (EAR) — nhận diện nhắm/mở mắt
   - Công thức 4: Mouth Aspect Ratio (MAR) — nhận diện há miệng
   - Công thức 5: Head Pose từ ma trận biến đổi (Yaw/Pitch/Roll)
   - Công thức 6: Head Pose xấp xỉ từ landmark (không cần ma trận)
   - Công thức 7: Hướng nhìn từ mống mắt (Iris Ratio)
   - Công thức 8: Neo & scale vật thể AR lên mặt (kính, mũ, mặt nạ)
   - Công thức 9: Làm mượt landmark (EMA & One Euro Filter)
4. [Android CameraX cho khuôn mặt](#4-android-camerax-cho-khuôn-mặt)
5. [Vẽ lên OverlayView: lưới mặt & vật thể neo mặt](#5-vẽ-lên-overlayview-lưới-mặt--vật-thể-neo-mặt)
6. [Bảng quy tắc "Sống còn" & Hậu quả kỹ thuật khi vi phạm](#6-bảng-quy-tắc-sống-còn--hậu-quả-kỹ-thuật-khi-vi-phạm)
7. [Nhận diện cử chỉ khuôn mặt (nháy mắt, há miệng, gật/lắc đầu)](#7-nhận-diện-cử-chỉ-khuôn-mặt)
8. [Chạy song song HandLandmarker + FaceLandmarker](#8-chạy-song-song-handlandmarker--facelandmarker)
9. [Giới hạn đã biết, hiệu năng & quyền riêng tư](#9-giới-hạn-đã-biết-hiệu-năng--quyền-riêng-tư)
10. [Quy trình chẩn đoán (Debug Checklist)](#10-quy-trình-chẩn-đoán-debug-checklist)

---

## 1. KIẾN TRÚC HỆ THỐNG TỔNG QUAN

Pipeline Face AR giống hệt Hand AR về khung xương, chỉ khác "bộ não" thị giác và dạng dữ liệu đầu ra:

```text
  [ CẢM BIẾN CAMERA (thường là camera TRƯỚC) ]
          │
          ▼  (Frame thô: ImageProxy)
  [ CameraX: ImageAnalysis ]
          │
          ▼  (Chuyển đổi sang MPImage + rotationDegrees)
  [ MediaPipe FaceLandmarker ]  <─── (Chạy ngầm trên Worker Thread)
          │
          ▼  (FaceLandmarkerResult: 478 landmarks + 52 blendshapes + ma trận 4x4)
  [ Toán học giải tích ]        <─── (Tâm mặt, EAR/MAR, Head Pose, Iris Ratio)
          │
          ▼  (Tọa độ Pixel + cờ cử chỉ)
  [ OverlayView: Canvas ]       <─── (Vẽ lưới mặt / kính / mũ / bộ phận rơi trên UI Thread)
```

Sự phân tách trách nhiệm:
- **MediaPipe:** Giải bài toán Computer Vision (tìm mặt, định vị 478 điểm, ước lượng biểu cảm và tư thế đầu).
- **Hình học giải tích:** Biến điểm thô thành tín hiệu logic (mắt nhắm? miệng há? đầu quay bao nhiêu độ?).
- **Canvas / Game Engine:** Hiển thị đồ họa (đặt vật thể lên mặt, xoay/scale theo đầu).

Điểm khác biệt cốt lõi so với Hand AR:

| Khía cạnh | Hand AR | Face AR |
| :--- | :--- | :--- |
| Số điểm mốc mỗi đối tượng | 21 | **478** (468 lưới mặt + 10 mống mắt) |
| Đầu ra "cao cấp" ngoài điểm | Handedness (Trái/Phải) | **Blendshapes** (52 hệ số) + **Ma trận tư thế 4x4** |
| Camera thường dùng | Trước hoặc sau | Gần như luôn là **camera trước** ⇒ mirror là mặc định |
| Đối tượng biến dạng | Khớp xương cứng | **Mô hình biến dạng mềm** (da, môi, mắt) ⇒ nhiễu (jitter) nhiều hơn, cần làm mượt |
| Chi phí tính toán | ~15–30 ms | Model Face Landmarker nặng hơn — **phải đo thực tế** (Mục 9) |

---

## 2. GOOGLE MEDIAPIPE FACE LANDMARKER

### 2.1. Phân biệt: Face Detection vs Face Landmark vs Face Recognition

Ba khái niệm hay bị nhầm lẫn, và chỉ hai trong ba có sẵn trong `tasks-vision`:

| Khái niệm | Trả lời câu hỏi | Đầu ra | Có trong MediaPipe Tasks? |
| :--- | :--- | :--- | :---: |
| **Face Detection** | "Có mặt ở đâu trong ảnh?" | Bounding box + 6 keypoint (2 mắt, mũi, miệng, 2 tai) | ✅ `FaceDetector` |
| **Face Landmark** | "Các điểm chi tiết trên mặt nằm ở đâu?" | 478 điểm 3D + blendshape + ma trận tư thế | ✅ `FaceLandmarker` |
| **Face Recognition** | "Đây là AI?" (định danh, so khớp danh tính) | Vector embedding / nhãn người | ❌ Không nằm trong Face Landmarker |

> Dự án Face AR (đặt vật thể lên mặt, bắt cử chỉ nháy mắt, đổi biểu cảm) chỉ cần **Face Landmarker**. Không cần và không nên làm nhận dạng danh tính — vừa nặng, vừa kéo theo nghĩa vụ pháp lý/quyền riêng tư (xem Mục 9.3).

`FaceLandmarker` thực chất là một đường ống 3 model nối tiếp bên trong file `face_landmarker.task`:
1. **Face detection model** — tìm vị trí mặt (BlazeFace).
2. **Face mesh model** — hồi quy 478 điểm 3D trên vùng mặt đã cắt.
3. **Blendshape model** — từ các điểm mốc suy ra 52 hệ số biểu cảm (chỉ chạy khi bật `outputFaceBlendshapes`).

Ở chế độ `LIVE_STREAM`, model detection **chỉ chạy khi mất dấu mặt**; các frame sau đó MediaPipe **theo dấu (tracking)** từ vùng mặt frame trước ⇒ nhanh hơn, nhưng cũng nghĩa là: khi mặt nhanh ra khỏi vùng dự đoán, sẽ có vài frame "trễ" rồi mới bắt lại.

---

### 2.2. Bản đồ 478 điểm mốc (Face Mesh) & các chỉ số quan trọng

Đừng cố nhớ 478 index. Chỉ cần thuộc **nhóm điểm neo** dùng cho logic; phần còn lại dùng để vẽ lưới.

```text
                        [10] Đỉnh trán
                    ___----‾‾‾‾----___
               [54]/    [67][109]     \[284]
      [127]  [21]|   [70]..[105]..[107]  [336]..[334]..[300]  |[251]  [356]
   [234]        |     Lông mày PHẢI      |   Lông mày TRÁI     |        [454]
  (Má phải      |    [33]───◉468───[133] [362]───◉473───[263] |       Má trái)
   trong ảnh    |    Mắt phải (chủ thể)   Mắt trái (chủ thể)  |
   chưa lật)    |                                             |
                |                  [168] Sống mũi trên        |
                |                   │                         |
                |                  [ 1 ] ĐẦU MŨI              |
                |                  [ 2 ]                      |
                 \    [61]──[ 0 ]─Môi trên ngoài──[291]      /
                  \   [78]──[13]───[14]───────────[308]     /
                   \       Môi dưới trong                  /
                    \_        [17]                       _/
                       ‾‾--__ [152] CẰM __--‾‾
```

> ⚠️ **Quy ước Trái/Phải của MediaPipe là theo CHỦ THỂ (người trong ảnh), không phải theo người xem.** Với ảnh gốc chưa lật, "mắt phải của chủ thể" nằm ở phía **trái** ảnh (x nhỏ). Đây là nguồn nhầm lẫn số 1 — xem thêm Mục 4.3 và Mục 10.
> Sơ đồ bên trên chỉ là **minh họa** và khó hình dung, để nhìn rõ hơn hãy tìm kiếm từ khóa **face mesh 468 points** trên google

**Bảng chỉ số neo (Canonical Face Model)**

| Vùng | Index | Dùng để làm gì |
| :--- | :--- | :--- |
| Đỉnh trán | `10` | Neo mũ/vương miện; đo chiều cao mặt |
| Cằm | `152` | Đo chiều cao mặt; neo râu/khẩu trang |
| Đầu mũi | `1` | Tâm tham chiếu tư thế; neo mũi hề |
| Sống mũi (giữa 2 mắt) | `168` | Neo cầu kính |
| Má ngoài (trái ảnh / phải ảnh) | `234` / `454` | Đo bề ngang mặt; ước lượng Yaw |
| **Mắt phải chủ thể** – 6 điểm EAR | `33` (góc ngoài), `160`, `158`, `133` (góc trong), `153`, `144` | Công thức EAR |
| **Mắt trái chủ thể** – 6 điểm EAR | `362` (góc trong), `385`, `387`, `263` (góc ngoài), `373`, `380` | Công thức EAR |
| Mống mắt phải chủ thể | `468` (tâm), `469–472` (4 điểm biên) | Hướng nhìn |
| Mống mắt trái chủ thể | `473` (tâm), `474–477` (4 điểm biên) | Hướng nhìn |
| Miệng – khóe trái / phải | `78` / `308` (mép trong), `61` / `291` (mép ngoài) | Độ rộng miệng, cười |
| Môi trên / dưới (mép trong) | `13` / `14` | MAR (há miệng) |
| Lông mày phải chủ thể | `70, 63, 105, 66, 107` | Nhướng mày |
| Lông mày trái chủ thể | `300, 293, 334, 296, 336` | Nhướng mày |

**Đường viền khuôn mặt (Face Oval)** — 36 điểm theo chu vi, dùng để vẽ khung mặt hoặc tạo mask:

```text
10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400,
377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
```

> 💡 **Mẹo kiểm chứng index:** Khi mới tích hợp, hãy vẽ **số thứ tự** cạnh mỗi điểm cho các index ở bảng trên (dùng `canvas.drawText(i.toString(), x, y, paint)`) rồi tự nhìn bằng mắt. Chỉ mất 5 phút nhưng loại bỏ hoàn toàn rủi ro nhầm index/nhầm trái–phải ở mọi công thức phía sau.

Tập kết nối chuẩn (danh sách cặp index để vẽ đường nối lưới tam giác, viền mắt, viền môi...) có trong lớp `FaceLandmarker` của MediaPipe: `FaceLandmarker.FACE_LANDMARKS_TESSELATION`, `FACE_LANDMARKS_FACE_OVAL`, `FACE_LANDMARKS_LEFT_EYE`, `FACE_LANDMARKS_RIGHT_EYE`, `FACE_LANDMARKS_LIPS`, `FACE_LANDMARKS_LEFT_IRIS`, `FACE_LANDMARKS_RIGHT_IRIS`... — nên dùng các hằng này thay vì tự chép danh sách.

---

### 2.3. Hệ tọa độ chuẩn hóa & ý nghĩa trục `z`

Mỗi điểm mốc $P(x, y, z)$:
* **$x \in [0.0, 1.0]$**, **$y \in [0.0, 1.0]$:** Tọa độ chuẩn hóa theo chiều ngang / dọc của ảnh đưa vào model (giống Hand — $0.0$ là mép trái/trên).
  - Có thể **hơi vượt** ngoài $[0,1]$ khi một phần mặt nằm ngoài khung hình.
* **$z$:** Độ sâu tương đối, gốc tại **tâm đầu** (khác Hand: gốc tại cổ tay). Đơn vị xấp xỉ **cùng thang với $x$** (tức cũng là "chuẩn hóa theo chiều rộng ảnh"). $z$ càng **âm** ⇒ điểm càng **gần camera** (ví dụ đầu mũi có $z$ âm hơn má).

> ⚠️ **Bẫy dị hướng (anisotropy):** $x$ chuẩn hóa theo **chiều rộng** ảnh, $y$ theo **chiều cao** ảnh. Với ảnh $480\times640$ thì một đơn vị $\Delta x$ và một đơn vị $\Delta y$ **không cùng độ dài vật lý**. Mọi phép đo khoảng cách (EAR, MAR, độ rộng mặt) **phải đổi sang pixel trước** ($x\cdot W$, $y\cdot H$) rồi mới tính. Đây là lỗi im lặng phổ biến nhất khi tự viết EAR — kết quả vẫn "trông có vẻ đúng" nhưng ngưỡng bị lệch tùy tỉ lệ khung hình.

---

### 2.4. Cấu trúc dữ liệu `FaceLandmarkerResult`

```kotlin
result.faceLandmarks()                    // List<List<NormalizedLandmark>>
result.faceBlendshapes()                  // Optional<List<List<Category>>>
result.facialTransformationMatrixes()     // Optional<List<float[]>>
```

* **Tầng ngoài của `faceLandmarks()`**: số khuôn mặt phát hiện được — `0` (không có mặt), `1`, `2`, ... tối đa bằng `numFaces` đã cấu hình.
* **Tầng trong**: kích thước cố định **478** phần tử (index `0…477`); index `468…477` là 10 điểm mống mắt.
* `faceBlendshapes()` và `facialTransformationMatrixes()` là `Optional` — **rỗng** trừ khi đã bật cờ trong options (Mục 2.7). Luôn kiểm `isPresent` trước khi `get()`.
* Phần tử thứ `i` của cả 3 danh sách **cùng thuộc về khuôn mặt thứ `i`**.

---

### 2.5. Blendshapes — 52 hệ số biểu cảm

Blendshape là **52 số thực trong $[0,1]$**, mỗi số mô tả mức độ của một cơ mặt (0 = không có, 1 = tối đa). Đây là cách **ổn định hơn** để bắt biểu cảm so với tự đo hình học, vì model đã được huấn luyện trên dữ liệu tư thế/ánh sáng đa dạng.

```kotlin
val shapes: List<Category> = result.faceBlendshapes().get()[0]   // mặt đầu tiên
val score: Map<String, Float> = shapes.associate { it.categoryName() to it.score() }
val blinkL = score["eyeBlinkLeft"] ?: 0f
```

Các blendshape hữu ích nhất cho ứng dụng giải trí:

| Nhóm | Tên | Ý nghĩa |
| :--- | :--- | :--- |
| Mắt | `eyeBlinkLeft`, `eyeBlinkRight` | Mức nhắm mắt (1 = nhắm hẳn) |
| Mắt | `eyeWideLeft/Right`, `eyeSquintLeft/Right` | Trợn mắt / nheo mắt |
| Mắt | `eyeLookUp/Down/In/Out` + `Left/Right` | Hướng nhìn của từng mắt |
| Miệng | `jawOpen` | Mức há hàm (há miệng) |
| Miệng | `mouthSmileLeft/Right`, `mouthFrownLeft/Right` | Cười / mếu |
| Miệng | `mouthPucker`, `mouthFunnel` | Chu môi, phồng "ô" |
| Miệng | `cheekPuff` | Phồng má |
| Mày | `browInnerUp`, `browDownLeft/Right`, `browOuterUpLeft/Right` | Nhướng / nhíu mày |
| Khác | `_neutral` | Phần tử index `0` — **không** phải biểu cảm, bỏ qua khi duyệt |

> **Trái/Phải của blendshape** theo quy ước ARKit (tức trục của chủ thể). Vì chi tiết này dễ lệch giữa các phiên bản, **hãy kiểm chứng bằng thực nghiệm**: nháy riêng mắt trái của bạn, log `eyeBlinkLeft`/`eyeBlinkRight`, xem cái nào tăng. Ghi kết quả vào code làm hằng số.

**Khi nào dùng blendshape, khi nào dùng hình học (EAR/MAR)?**

| Tiêu chí | Blendshape | Hình học (EAR/MAR) |
| :--- | :--- | :--- |
| Độ ổn định khi quay đầu / ánh sáng | Tốt hơn | Kém hơn khi Yaw/Pitch lớn |
| Chi phí | Thêm 1 model chạy mỗi frame | Gần như 0 (chỉ vài phép tính) |
| Kiểm soát/giải thích ngưỡng | Hộp đen | Minh bạch, tự chỉnh được |
| Phù hợp | Biểu cảm phức tạp, ngưỡng "mềm" | Nháy mắt, há miệng nhanh, thiết bị yếu |

Khuyến nghị: nếu chỉ cần nháy mắt/há miệng, **tắt blendshape và dùng EAR/MAR** để tiết kiệm CPU; bật blendshape khi cần nhiều biểu cảm hoặc cần độ bền cao.

---

### 2.6. Facial Transformation Matrix — tư thế đầu 3D

Khi bật `setOutputFacialTransformationMatrixes(true)`, mỗi mặt có thêm **1 ma trận 4×4** (16 số `float`) biến đổi từ **mô hình mặt chuẩn (Canonical Face Model)** sang **mặt thực đang thấy**:

$$M = \begin{bmatrix} s\,R_{3\times3} & \vec{t} \\ \vec{0}^T & 1 \end{bmatrix}$$

* $R$: ma trận xoay 3×3 ⇒ chứa **Yaw / Pitch / Roll**.
* $s$: hệ số scale đồng đều ⇒ tỉ lệ với **khoảng cách mặt tới camera** (mặt càng gần, $s$ càng lớn).
* $\vec t$: vector tịnh tiến (đơn vị của mô hình chuẩn, xấp xỉ centimet).

> ⚠️ **Cần kiểm chứng — thứ tự lưu 16 phần tử (row-major hay column-major):** Cách nhanh nhất: log mảng 16 số khi mặt nhìn thẳng, không nghiêng. Ma trận sẽ gần đường chéo $s$ ở 3 phần tử đường chéo đầu; các phần tử tịnh tiến nằm ở **index `3, 7, 11`** (row-major) hoặc **index `12, 13, 14`** (column-major). Xác định xong thì **ghi cứng** vào code, không đoán lại.

Ứng dụng: xoay/nghiêng vật thể 3D theo đầu, hiệu ứng "nhìn xuống thì mũ trượt xuống", điều khiển game bằng gật/lắc đầu — **không cần tự giải PnP**.

---

### 2.7. Cấu hình thực thi tối ưu

```kotlin
val baseOptions = BaseOptions.builder()
    .setModelAssetPath("face_landmarker.task")   // đặt trong app/src/main/assets/
    .setDelegate(delegate)                      // giống Hand: ưu tiên GPU, tự rơi về CPU khi RAM thấp/init >5s/lỗi — xem HandLandmarkerProvider.createWithFallback()
    .build()

val options = FaceLandmarker.FaceLandmarkerOptions.builder()
    .setBaseOptions(baseOptions)
    .setRunningMode(RunningMode.LIVE_STREAM)     // bắt buộc cho camera thời gian thực
    .setNumFaces(1)                              // 1 mặt là đủ cho selfie AR — giảm tải mạnh
    .setMinFaceDetectionConfidence(0.5f)
    .setMinFacePresenceConfidence(0.5f)
    .setMinTrackingConfidence(0.5f)
    .setOutputFaceBlendshapes(false)             // bật true CHỈ khi cần biểu cảm
    .setOutputFacialTransformationMatrixes(false) // bật true CHỈ khi cần Head Pose 3D
    .setResultListener { result, inputImage -> /* nhận kết quả bất đồng bộ */ }
    .setErrorListener { e -> Log.e(TAG, "FaceLandmarker error", e) }
    .build()

val faceLandmarker = FaceLandmarker.createFromOptions(context, options)
```

Ý nghĩa 3 ngưỡng tin cậy:
* **`minFaceDetectionConfidence`:** Ngưỡng để coi kết quả model tìm mặt là hợp lệ.
* **`minFacePresenceConfidence`:** Ngưỡng model mesh xác nhận "vẫn còn mặt trong vùng này". Thấp ⇒ dễ giữ dấu nhưng dễ ra điểm sai; cao ⇒ dễ rớt mặt.
* **`minTrackingConfidence`:** Ngưỡng để tiếp tục theo dấu thay vì chạy lại detection.

Cách gọi mỗi frame (giống Hand, nhớ `setRotationDegrees`):
```kotlin
val mpImage = BitmapImageBuilder(bitmap).build()
val imageProcessingOptions = ImageProcessingOptions.builder()
    .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
    .build()
faceLandmarker.detectAsync(mpImage, imageProcessingOptions, frameTimeMs)
```

> **CPU vs GPU delegate:** **Cập nhật 29/09/2026:** Hand đã đo thật và đổi mặc định sang `Delegate.GPU` kèm fallback tự động về CPU (`Camera_X_Hand_Landmarker.md` Mục 2.4 và Mục 14; số liệu ở `Perf_Notes.md` mục 9), nên Face nên **bắt chước cùng cơ chế** (`createWithFallback`: RAM < 3GB → CPU ngay; GPU init > 5s hoặc lỗi → CPU; nhớ trạng thái cả phiên; tạo model trên luồng nền, không phải main thread — tránh ANR như Mục 14.1) thay vì hard-code CPU. Vẫn nên đo lại cho Face trên thiết bị mục tiêu (`DelegatePerfLogger`) vì mô hình Face khác mô hình Hand; đừng suy số liệu của Hand sang Face. Đừng quên ràng buộc khởi tạo/gọi cùng một thread (`EGL context`) — xem ⚠️ ở `Camera_X_Hand_Landmarker.md` Mục 6 rule 5.

---

### 2.8. Phương án nhẹ: `FaceDetector` (6 keypoint)

Nếu tính năng chỉ cần **biết mặt ở đâu và lớn cỡ nào** (ví dụ khung tròn theo mặt, đặt sticker lên vùng mặt), `FaceDetector` nhẹ hơn rất nhiều so với `FaceLandmarker`:

```kotlin
FaceDetector.FaceDetectorOptions.builder()
    .setBaseOptions(BaseOptions.builder().setModelAssetPath("blaze_face_short_range.tflite").build())
    .setRunningMode(RunningMode.LIVE_STREAM)
    .setMinDetectionConfidence(0.5f)
    .setResultListener { result, _ -> /* result.detections(): bounding box + 6 keypoint */ }
    .build()
```

| Cần | Chọn |
| :--- | :--- |
| Chỉ vị trí + kích cỡ mặt | `FaceDetector` (nhẹ nhất) |
| Nháy mắt, há miệng, viền môi, mống mắt | `FaceLandmarker` |
| Đặt vật thể xoay theo đầu, bắt gật/lắc | `FaceLandmarker` + ma trận biến đổi (hoặc Công thức 6) |
| Bắt biểu cảm phong phú | `FaceLandmarker` + blendshape |

---

## 3. NỀN TẢNG TOÁN HỌC HÌNH HỌC GIẢI TÍCH CHO KHUÔN MẶT

Quy ước ký hiệu: $P_i = (x_i, y_i, z_i)$ là landmark index $i$ (chuẩn hóa). $W, H$ là kích thước ảnh mà MediaPipe trả về (`inputImage.width/height`, **sau khi đã xoay**). Với khoảng cách pixel:

$$D(i,j) = \sqrt{\big((x_i - x_j)\,W\big)^2 + \big((y_i - y_j)\,H\big)^2}$$

### Công thức 1 & 1.5: Chiếu tọa độ, Rotation & Mirror

Hoàn toàn dùng lại **Công thức 1** (ánh xạ chuẩn hóa → pixel với `scaleFactor`/`offset` cho `CENTER_CROP`) và **Công thức 1.5** (xoay theo `rotationDegrees`, lật $x$ khi camera trước) của `Camera_X_Hand_Landmarker.md`. Chỉ nhắc lại bản chất:

$$\text{Pixel}_X = (x_{\text{norm}}' \cdot W \cdot \text{scaleFactor}) + \text{offsetX}, \qquad x_{\text{norm}}' = \begin{cases} 1 - x_{\text{norm}} & \text{camera trước (mirror)} \\ x_{\text{norm}} & \text{camera sau} \end{cases}$$

> ⚠️ Với khuôn mặt, **đừng quên mirror ngay từ đầu**: Face AR gần như luôn dùng camera trước, nên "quên lật $x$" là lỗi mặc định chứ không phải ngoại lệ. Lật xảy ra ở **bước chiếu ra pixel**, còn các phép tính đo khoảng cách (EAR, MAR) **không bị ảnh hưởng** bởi lật (khoảng cách bảo toàn qua phép đối xứng) — chỉ cần lưu ý nhãn Trái/Phải (Mục 4.3).

---

### Công thức 2: Tâm mặt, kích thước mặt & góc nghiêng đầu (Roll)

**Tâm mặt** — trung điểm hai má ngoài (ổn định hơn dùng đầu mũi khi quay đầu):

$$x_c = \frac{x_{234} + x_{454}}{2}, \qquad y_c = \frac{y_{234} + y_{454}}{2}$$

**Kích thước mặt** — dùng làm cơ sở scale vật thể AR:

$$\text{faceWidth} = D(234,\,454), \qquad \text{faceHeight} = D(10,\,152)$$

**Góc nghiêng đầu (Roll)** — góc của đường nối hai góc ngoài mắt (`33` và `263`) so với phương ngang, tính trên **pixel** (nhớ trục $y$ hướng xuống):

$$\Delta X = (x_{263} - x_{33})\,W, \quad \Delta Y = (y_{263} - y_{33})\,H, \qquad \theta_{\text{roll}} = \operatorname{atan2}(\Delta Y,\ \Delta X)\cdot\frac{180^\circ}{\pi}$$

Dùng `canvas.rotate(theta_roll)` (đã dịch gốc về tâm) để vật thể nghiêng theo đầu — cùng cơ chế với Công thức 5 của tài liệu Hand.

> Khi lật gương ($x' = 1-x$), $\Delta X$ đổi dấu ⇒ $\theta_{\text{roll}}$ đổi dấu. Nếu tính roll trên tọa độ **gốc** rồi vẽ trên canvas **đã lật**, phải **đảo dấu** góc; hoặc thống nhất tính roll trên tọa độ đã lật ngay từ đầu.

---

### Công thức 3: Eye Aspect Ratio (EAR) — nhận diện nhắm/mở mắt

Ý tưởng (Soukupová & Čech, 2016): mắt mở thì chiều cao mắt so với chiều rộng lớn; mắt nhắm thì tỉ số xấp xỉ 0. Dùng **6 điểm** quanh mắt, đánh số $p_1 \dots p_6$ theo chiều kim đồng hồ bắt đầu từ góc ngoài:

```text
        p2   p3
   p1 ◉─────────◉ p4        (p1,p4: 2 góc mắt;  p2,p3: mí trên;  p5,p6: mí dưới)
        p6   p5
```

$$\text{EAR} = \frac{D(p_2,p_6) + D(p_3,p_5)}{2\,D(p_1,p_4)}$$

Ánh xạ sang index MediaPipe:

| Mắt (theo chủ thể) | $p_1$ | $p_2$ | $p_3$ | $p_4$ | $p_5$ | $p_6$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Phải | `33` | `160` | `158` | `133` | `153` | `144` |
| Trái | `362` | `385` | `387` | `263` | `373` | `380` |

```kotlin
fun ear(lm: List<NormalizedLandmark>, i: IntArray, w: Float, h: Float): Float {
    fun d(a: Int, b: Int) = hypot((lm[a].x() - lm[b].x()) * w, (lm[a].y() - lm[b].y()) * h)
    // mảng đánh số từ 0 nên p1 = i[0], ..., p6 = i[5]; đặt tên lại để return đọc giống hệt công thức
    val p1 = i[0]; val p2 = i[1]; val p3 = i[2]
    val p4 = i[3]; val p5 = i[4]; val p6 = i[5]
    return (d(p2, p6) + d(p3, p5)) / (2f * d(p1, p4))
}
```

**Ngưỡng tham khảo** (⚠️ cần hiệu chỉnh trên thiết bị + người thật): mắt mở thường $\approx 0.25 - 0.35$; mắt nhắm $< 0.15 - 0.2$. Người mắt nhỏ/mắt xếch có "EAR mở" thấp hơn ⇒ ngưỡng cố định sẽ báo nhắm sai.

> **Hiệu chỉnh cá nhân (khuyến nghị):** Trong 1–2 giây đầu, đo EAR trung bình khi người dùng mở mắt bình thường ($\text{EAR}_{\text{open}}$), rồi đặt ngưỡng nhắm $= 0.6 \times \text{EAR}_{\text{open}}$ (hệ số 0.6 là điểm khởi đầu, chỉnh theo thực nghiệm). Cách này bền hơn ngưỡng cứng.

> ⚠️ **Bẫy dị hướng:** Nếu tính EAR trực tiếp trên tọa độ chuẩn hóa (bỏ qua $\times W$, $\times H$), tử số (chủ yếu theo $y$) và mẫu số (chủ yếu theo $x$) bị co giãn khác nhau theo tỉ lệ khung hình ⇒ ngưỡng chỉ đúng với **một** độ phân giải. Luôn đổi sang pixel trước.

---

### Công thức 4: Mouth Aspect Ratio (MAR) — nhận diện há miệng

Tương tự EAR nhưng đo miệng, dùng **mép trong** của môi để không bị ảnh hưởng bởi độ dày môi:

$$\text{MAR} = \frac{D(13,\,14)}{D(78,\,308)}$$

* `13`/`14`: điểm giữa mép trong môi trên/dưới. `78`/`308`: hai khóe miệng (mép trong).
* Miệng khép $\approx 0.0 - 0.05$; há miệng rõ $> 0.3 - 0.5$ (⚠️ cần hiệu chỉnh). Cười rộng làm tăng mẫu số nên MAR giảm nhẹ — vẫn ổn với ngưỡng "há miệng".

Phương án thay thế ổn định hơn: blendshape `jawOpen` (Mục 2.5), ngưỡng khởi điểm $\approx 0.3 - 0.5$.

---

### Công thức 5: Head Pose từ ma trận biến đổi (Yaw/Pitch/Roll)

Từ $M$ (Mục 2.6), lấy $R$ bằng cách **chuẩn hóa scale**: chia 3 cột (hoặc hàng) đầu cho $s$. Với quy ước $R = R_z(\text{roll})\,R_y(\text{yaw})\,R_x(\text{pitch})$, các góc:

$$\text{yaw} = \arcsin(-r_{20}), \qquad \text{pitch} = \operatorname{atan2}(r_{21},\ r_{22}), \qquad \text{roll} = \operatorname{atan2}(r_{10},\ r_{00})$$

trong đó $r_{ij}$ là phần tử hàng $i$, cột $j$ của $R$ (đã chuẩn hóa).

> ⚠️ **Cần kiểm chứng:** (1) Thứ tự lưu mảng 16 số (Mục 2.6) quyết định cách đọc $r_{ij}$. (2) **Dấu** của yaw/pitch/roll phụ thuộc quy ước trục của mô hình chuẩn và có bị đảo khi mirror hay không. Cách làm đúng: quay đầu **sang phải**, gật đầu **xuống**, nghiêng đầu **sang phải**, in 3 góc ra log, rồi **gán dấu** cho khớp cảm nhận rồi cố định vào hằng số `YAW_SIGN`, `PITCH_SIGN`, `ROLL_SIGN`. Đừng tin dấu trong tài liệu này (hoặc bất kỳ tài liệu nào) mà không thử.

Khi mirror camera trước: **Yaw và Roll đổi dấu, Pitch giữ nguyên** (đối xứng gương qua mặt phẳng dọc).

---

### Công thức 6: Head Pose xấp xỉ từ landmark (không cần ma trận)

Khi tắt ma trận biến đổi để tiết kiệm CPU, vẫn ước lượng được xấp xỉ bằng tỉ số khoảng cách:

**Yaw** — khi quay đầu sang một bên, đầu mũi lệch về phía má bên đó:

$$\text{yawRatio} = \frac{D(1,\,234) - D(1,\,454)}{D(1,\,234) + D(1,\,454)} \in [-1,\ 1]$$

$\text{yawRatio} \approx 0$: nhìn thẳng. $|\text{yawRatio}| \to 1$: quay nghiêng gần hết. Có thể quy ra góc gần đúng bằng $\text{yaw} \approx \arcsin(\text{yawRatio}) $ nhưng **chỉ nên dùng làm ngưỡng thô** (quay trái / quay phải / thẳng).

**Pitch** — vị trí tương đối của mũi giữa trán và cằm:

$$\text{pitchRatio} = \frac{D(10,\,1)}{D(10,\,152)} \in (0,\ 1)$$

Giá trị cơ sở khi nhìn thẳng $\approx 0.5$ (⚠️ đo thực tế). Ngẩng đầu ⇒ mũi lên gần trán, tỉ số giảm; cúi ⇒ tỉ số tăng.

**Roll** — chính là $\theta_{\text{roll}}$ ở Công thức 2.

Ưu điểm: rẻ, không phụ thuộc định dạng ma trận. Nhược điểm: xấp xỉ 2D, kém chính xác ở góc lớn.

---

### Công thức 7: Hướng nhìn từ mống mắt (Iris Ratio)

Vị trí tâm mống mắt so với hai góc mắt cho biết mắt đang liếc về phía nào. Với mắt phải chủ thể (tâm `468`, góc ngoài `33`, góc trong `133`):

$$\rho_x = \frac{x_{468} - x_{33}}{x_{133} - x_{33}} \in [0, 1], \qquad \rho_x \approx 0.5 \Rightarrow \text{nhìn thẳng}$$

Tương tự cho trục dọc với mí trên/dưới (`159`, `145`):

$$\rho_y = \frac{y_{468} - y_{159}}{y_{145} - y_{159}}$$

> **Giới hạn:** Ước lượng hướng nhìn từ camera điện thoại chỉ đủ dùng cho tương tác thô (liếc trái/phải/lên/xuống). **Không** đủ chính xác để định vị điểm nhìn trên màn hình (eye tracking thực sự cần hiệu chuẩn riêng). Khi $\text{EAR}$ thấp (mắt gần nhắm) thì $\rho$ **không đáng tin** — bỏ qua frame đó.

---

### Công thức 8: Neo & scale vật thể AR lên mặt (kính, mũ, mặt nạ)

Ba đại lượng cần cho mọi vật thể neo mặt: **vị trí neo**, **kích thước**, **góc xoay**.

| Vật thể | Điểm neo | Độ rộng vật thể | Góc xoay |
| :--- | :--- | :--- | :--- |
| Kính | Trung điểm hai góc mắt trong `133` & `362` (hoặc `168`) | $\approx \text{faceWidth} \times k_{\text{kính}}$ | $\theta_{\text{roll}}$ |
| Mũ / vương miện | Đỉnh trán `10`, dịch lên trên $\approx 0.3\,\text{faceHeight}$ | $\approx \text{faceWidth} \times k_{\text{mũ}}$ | $\theta_{\text{roll}}$ |
| Mũi hề | Đầu mũi `1` | $\approx 0.25\,\text{faceWidth}$ | $\theta_{\text{roll}}$ |
| Râu / khẩu trang | Cằm `152` hoặc trung điểm `1` & `152` | $\approx \text{faceWidth}$ | $\theta_{\text{roll}}$ |
| Bộ phận mặt rơi (puzzle) | Tâm bộ phận = trung điểm các landmark của bộ phận đó | Bao quanh bởi bounding box các landmark | Không cần |

Các hệ số $k$ ($k_{\text{kính}} \approx 1.0 - 1.2$, $k_{\text{mũ}} \approx 1.1 - 1.4$...) là **điểm khởi đầu, phải chỉnh bằng mắt** theo artwork thực tế.

Bằng Matrix (cùng tinh thần Mục 7 của tài liệu Hand):

```kotlin
val scale = faceWidthPx * k / asset.width
matrix.reset()
matrix.postTranslate(-asset.width / 2f, -asset.height / 2f) // dời tâm asset về gốc
matrix.postScale(scale, scale)
matrix.postRotate(rollDeg)
matrix.postTranslate(anchorX, anchorY)                       // dời tới điểm neo
canvas.drawBitmap(asset, matrix, paint)
```

Vật thể 3D "nhìn xuống thì trượt xuống": dịch neo theo pitch và/hoặc scale ngang theo $\cos(\text{yaw})$ để giả lập nghiêng. Đây là xấp xỉ 2D; không thay thế được render 3D thực.

---

### Công thức 9: Làm mượt landmark (EMA & One Euro Filter)

Landmark khuôn mặt **rung (jitter)** rõ hơn tay, đặc biệt khi vật thể AR nhỏ và bám sát (mắt, môi). Cần lọc trước khi dùng cho vẽ; **không lọc cho quyết định nhị phân** như nháy mắt (lọc làm chậm/nuốt mất cú nháy nhanh).

**EMA (Exponential Moving Average)** — đơn giản nhất:

$$\hat{x}_t = \alpha\,x_t + (1-\alpha)\,\hat{x}_{t-1}, \quad \alpha \in (0, 1]$$

$\alpha$ nhỏ ⇒ mượt nhưng **trễ**; $\alpha$ lớn ⇒ bám sát nhưng vẫn rung. Nhược điểm: một $\alpha$ cố định không thể vừa mượt khi đứng yên vừa nhanh khi di chuyển.

**One Euro Filter** — $\alpha$ tự thích nghi theo tốc độ: đứng yên thì lọc mạnh, chuyển động nhanh thì lọc nhẹ. Hai tham số chính: `minCutoff` (giảm ⇒ bớt rung khi đứng yên) và `beta` (tăng ⇒ giảm trễ khi chuyển động nhanh). Khuyến nghị dùng One Euro cho **vị trí neo & góc xoay** của vật thể AR; EMA đơn giản là đủ cho **kích thước**.

> Áp dụng lọc trên **đại lượng dẫn xuất** (tâm, roll, scale) thay vì trên cả 478 điểm để tiết kiệm CPU — trừ khi vẽ lưới mặt.

---

## 4. ANDROID CAMERAX CHO KHUÔN MẶT

### 4.1. Tái sử dụng cấu hình đã có

`ProcessCameraProvider`, `Preview`, `ImageAnalysis`, `ImageProxy`, `STRATEGY_KEEP_ONLY_LATEST`, `imageProxy.close()`, chạy AI ở luồng phụ, timestamp đơn điệu tăng... **giữ nguyên** như `Camera_X_Hand_Landmarker.md` Mục 4 và Mục 6. Không lặp lại ở đây.

### 4.2. Khác biệt cần chú ý cho khuôn mặt

* **Camera mặc định là `CameraSelector.DEFAULT_FRONT_CAMERA`** ⇒ mirror bật thường trực.
* **Độ phân giải phân tích không cần cao.** Face Landmarker nội bộ cắt vùng mặt rồi resize về kích thước nhỏ cố định; đưa ảnh 1080p vào chỉ tốn công chuyển đổi RGBA/xoay mà không tăng chất lượng. Điểm khởi đầu hợp lý: `setTargetResolution`/resolution selector quanh **640×480** cho `ImageAnalysis` (⚠️ đo lại trên máy thật).
* **Mặt cần đủ lớn trong khung hình:** vùng mặt nhỏ hơn ~ 1/5 chiều ngắn của ảnh sẽ cho landmark rất nhiễu. Nếu UI cho phép, hướng dẫn người dùng đưa mặt vào khung oval.
* **Ánh sáng ảnh hưởng mạnh hơn Hand:** thiếu sáng hoặc ngược sáng làm mất mặt/nhiễu landmark ⇒ nên có thông báo mềm "không thấy mặt" thay vì im lặng.

### 4.3. Mirror & nhãn Trái/Phải — bài học tương đương Mục 13.10 của tài liệu Hand

Có **hai** lớp "trái/phải" độc lập, dễ nhầm:

1. **Tọa độ $x$:** MediaPipe trả theo ảnh gốc chưa lật. Camera trước ⇒ hiển thị đã lật ⇒ phải dùng $x' = 1 - x$ khi vẽ (Công thức 1.5).
2. **Nhãn "Left/Right"** (trong tên blendshape như `eyeBlinkLeft` và trong quy ước "mắt phải chủ thể"): theo **chủ thể**, không theo ảnh/preview.

Hệ quả thực tế: Trên preview đã lật (như soi gương), mắt **hiển thị bên phải màn hình** là mắt **phải** của chủ thể? — Câu trả lời đúng: mắt trái chủ thể xuất hiện **bên phải màn hình gương**, đúng như khi soi gương thật (nâng tay phải lên thì hình trong gương ở bên phải màn hình… vì gương lật, còn ảnh camera gốc thì không). Vì dễ rối, **không suy luận bằng đầu — hãy thử bằng mắt**: nháy riêng một mắt và xem log để xác định (Mục 10).

---

## 5. VẼ LÊN OVERLAYVIEW: LƯỚI MẶT & VẬT THỂ NEO MẶT

Dùng nguyên cơ chế "Tấm kính trong suốt" (`Camera_X_Hand_Landmarker.md` Mục 5): `OverlayView` trong suốt, đặt chồng lên `PreviewView`.

### 5.1. Vẽ lưới mặt hiệu quả (debug / hiệu ứng lưới)

478 điểm × mỗi frame ⇒ **không tạo đối tượng mới trong `onDraw`**:

```kotlin
private val pts = FloatArray(478 * 2)          // cấp phát 1 lần, tái dùng

fun bindFace(lm: List<NormalizedLandmark>) {   // gọi khi có kết quả mới
    for (i in lm.indices) {
        pts[i * 2]     = projectX(lm[i].x())   // đã gồm mirror + scale + offset
        pts[i * 2 + 1] = projectY(lm[i].y())
    }
}

override fun onDraw(canvas: Canvas) {
    canvas.drawPoints(pts, 0, 478 * 2, dotPaint)   // 1 lần gọi thay vì 478 lần drawCircle
}
```

* Dùng `drawPoints` / `drawLines` thay vì vòng lặp `drawCircle`. Với đường nối (viền mắt, môi...) gom vào 1 `Path` hoặc mảng `FloatArray` cho `drawLines`.
* Chỉ vẽ lưới khi **debug hoặc là hiệu ứng chủ đích**; sản phẩm thường chỉ vẽ vật thể neo (Mục 5.2).

### 5.2. Vật thể neo mặt: dùng chung nguyên lý "dữ liệu trung lập"

Áp dụng nguyên tắc Mục 13.3 của tài liệu Hand: lưu landmark ở dạng **chuẩn hóa chưa chiếu**, chỉ chiếu ra pixel **khi vẽ**, theo canvas thực tế đang vẽ (màn hình hoặc canvas ghi video có kích thước khác). Nếu đã có `Projection` dùng chung cho Hand, **dùng lại chính lớp đó cho Face** để tránh hai công thức chiếu lệch nhau.

Quy tắc vẽ:
* Chỉ vẽ khi `faceLandmarks().isNotEmpty()`; nếu mất mặt, **ẩn có kiểm soát** (fade-out ngắn hoặc giữ vị trí cuối trong ~100–200 ms) thay vì nháy tắt ngay — mất mặt 1–2 frame là chuyện bình thường.
* Cập nhật kết quả từ analyzer thread ⇒ **đẩy sang Main Thread** trước khi `invalidate()` (Quy tắc 3, Mục 6).
* Với ghi video: dùng cùng dữ liệu chuẩn hóa nhưng chiếu theo kích thước khung ghi (xem Mục 10.5 tài liệu Hand: scale theo canvas thực tế đang vẽ).

---

## 6. BẢNG QUY TẮC "SỐNG CÒN" & HẬU QUẢ KỸ THUẬT KHI VI PHẠM

Các quy tắc 1–8 của Hand (đóng `imageProxy`, luồng phụ, Main Thread, Delegate, backpressure, timestamp, Z-Index) **vẫn áp dụng nguyên vẹn**. Bảng dưới là các quy tắc **bổ sung riêng cho Face**:

| STT | Quy tắc bắt buộc | Mã nguồn liên quan | Hậu quả nếu KHÔNG tuân theo |
| :---: | :--- | :--- | :--- |
| **F1** | **Đổi khoảng cách sang pixel trước khi tính EAR/MAR/độ rộng mặt** | `ear()`, `mar()`, `faceWidth` | **NGƯỠNG LỆCH THEO TỈ LỆ KHUNG HÌNH:** Cùng 1 người, cùng 1 ngưỡng, nhưng đổi độ phân giải/xoay màn hình là báo nhắm/mở sai. Lỗi im lặng, không crash. |
| **F2** | **Chỉ bật `outputFaceBlendshapes` / `outputFacialTransformationMatrixes` khi thật sự dùng** | `FaceLandmarkerOptions` | **PHÍ CPU VÔ ÍCH:** Mỗi cờ thêm việc tính toán mỗi frame. Bật "cho chắc" trên máy yếu sẽ tụt FPS mà không có lợi ích. |
| **F3** | **Kiểm `Optional.isPresent` trước `get()` với blendshape / ma trận** | `result.faceBlendshapes()` | **CRASH `NoSuchElementException`:** Khi cờ tắt hoặc frame không có mặt, `Optional` rỗng. |
| **F4** | **Không giả định luôn có mặt: xử lý `faceLandmarks().isEmpty()`** | callback kết quả | **CRASH `IndexOutOfBounds`** khi truy cập `[0]` lúc người dùng quay đi/che mặt. |
| **F5** | **Không cấp phát đối tượng trong `onDraw` khi vẽ lưới** | `OverlayView.onDraw` | **GIẬT HÌNH (GC jank):** 478 điểm × 30 fps sinh rác liên tục, GC làm tụt fps đúng lúc cần mượt. |
| **F6** | **Đặt `setNumFaces(1)` nếu tính năng chỉ cần 1 mặt** | `FaceLandmarkerOptions` | **CHẬM VÀ NHẢY MẶT:** Nhiều mặt ⇒ tốn thêm tính toán; người đứng sau lọt khung có thể cướp neo vật thể. |
| **F7** | **Lọc (làm mượt) đại lượng để vẽ, KHÔNG lọc đại lượng dùng cho quyết định nhị phân** | Công thức 9 | **VẬT THỂ RUNG** (nếu không lọc) hoặc **MẤT/TRỄ CÚ NHÁY MẮT** (nếu lọc EAR quá mạnh). |
| **F8** | **Áp dụng hysteresis + debounce cho ngưỡng cử chỉ** | Mục 7.1 | **NHÁY MẮT GIẢ / NHÁY ĐÚP:** Giá trị dao động quanh ngưỡng làm kích hoạt sự kiện liên tục. |
| **F9** | **Xác nhận quy ước Trái/Phải bằng thực nghiệm, ghi thành hằng số** | Mục 4.3, 10 | **NHÁY MẮT TRÁI PHẢN ỨNG BẰNG MẮT PHẢI** (hoặc ngược lại) — chỉ lộ ra ở tính năng phân biệt hai mắt. |

---

## 7. NHẬN DIỆN CỬ CHỈ KHUÔN MẶT

### 7.1. Máy trạng thái cho sự kiện "nháy mắt" (hysteresis + debounce)

Không dùng `if (ear < 0.2)` trần trụi. EAR dao động quanh ngưỡng sẽ sinh hàng loạt sự kiện giả. Dùng **hai ngưỡng** (hysteresis) và **thời gian tối thiểu** (debounce):

```text
             ear < T_close
   OPEN  ───────────────────►  CLOSED
     ▲                            │
     │      ear > T_open          │
     └────────────────────────────┘        với  T_open > T_close  (ví dụ 0.25 / 0.18)
```

Quy tắc phát sự kiện `BLINK`:
1. Chuyển `OPEN → CLOSED` khi $\text{EAR} < T_{\text{close}}$ **liên tục ≥ 1–2 frame**.
2. Chuyển `CLOSED → OPEN` khi $\text{EAR} > T_{\text{open}}$.
3. Phát `BLINK` **khi quay về `OPEN`** nếu thời gian ở `CLOSED` nằm trong khoảng hợp lý (**~ 50–400 ms**): quá ngắn ⇒ nhiễu; quá dài ⇒ người dùng đang **nhắm mắt chủ ý** (có thể xử lý thành sự kiện khác, ví dụ "nhắm giữ").
4. Sau `BLINK`, chờ một khoảng **cooldown** (~ 200–300 ms) trước khi cho phép sự kiện tiếp theo.

Các mốc thời gian là điểm khởi đầu — **phải tinh chỉnh theo FPS thực tế của thiết bị**: ở 15 fps mỗi frame ≈ 66 ms, một cú nháy nhanh (~100 ms) chỉ chiếm 1–2 frame; đừng yêu cầu "≥ 3 frame" cho thiết bị chậm.

Quyết định dùng **một mắt hay trung bình hai mắt:**
* Nháy **cả hai mắt** (phổ biến): dùng $\overline{\text{EAR}} = (\text{EAR}_L + \text{EAR}_R)/2$ hoặc `max` của hai EAR ⇒ giảm báo giả khi một mắt bị che/lệch góc.
* Nháy **một mắt** (wink): so sánh hai EAR: một mắt $< T_{\text{close}}$ trong khi mắt kia $> T_{\text{open}}$.

Ví dụ ứng dụng: **dừng bộ phận mặt đang rơi khi nháy mắt** — sự kiện `BLINK` phát ra một lần cho mỗi cú nháy, callback chốt vị trí hiện tại của bộ phận đó.

### 7.2. Há miệng, cười, nhướng mày

| Cử chỉ | Tín hiệu hình học | Tín hiệu blendshape | Ghi chú |
| :--- | :--- | :--- | :--- |
| Há miệng | MAR (Công thức 4) | `jawOpen` | Dùng hysteresis, ngưỡng khác nhau cho mở/đóng |
| Cười | Khoảng cách `61`–`291` tăng so với lúc bình thường | `mouthSmileLeft/Right` | Chuẩn hóa theo `faceWidth` để không phụ thuộc khoảng cách camera |
| Nhướng mày | Khoảng cách giữa lông mày (`105`/`334`) và mắt (`159`/`386`) tăng | `browInnerUp`, `browOuterUp*` | Chuẩn hóa theo `faceHeight` |
| Chu môi | Độ rộng miệng giảm, môi nhô | `mouthPucker` | Dễ nhầm với "ô" |

> Mọi khoảng cách hình học dùng làm ngưỡng nên **chuẩn hóa theo `faceWidth`** (hoặc `faceHeight`) để bất biến với khoảng cách tới camera:
> $$\tilde{d} = \frac{D(i,j)}{\text{faceWidth}}$$

### 7.3. Gật đầu / Lắc đầu (cử chỉ động)

Đây là cử chỉ **chuỗi theo thời gian**, không phải tĩnh:

* **Lắc đầu (No):** $\text{yaw}(t)$ đổi dấu ≥ 2 lần trong ~1 giây với biên độ ≥ ~15–20° (⚠️ hiệu chỉnh).
* **Gật đầu (Yes):** $\text{pitch}(t)$ đổi chiều ≥ 2 lần trong ~1 giây với biên độ tương tự.
* Cài đặt: giữ **cửa sổ trượt** ~ 1 giây các mẫu góc (đã lọc), đếm số lần đảo chiều vượt biên độ; reset sau khi phát sự kiện để không kích hoạt lặp.

---

## 8. CHẠY SONG SONG HANDLANDMARKER + FACELANDMARKER

Ứng dụng gộp cả tay và mặt có hai cách tổ chức, tùy tính chất sản phẩm:

| Phương án | Mô tả | Khi nào chọn |
| :--- | :--- | :--- |
| **A. Tách chế độ** | Mỗi màn/chế độ chỉ chạy **1** landmarker (Hand HOẶC Face); chuyển màn ⇒ `close()` cái cũ, tạo cái mới | Hầu hết trường hợp — **rẻ nhất, ổn định nhất** |
| **B. Chạy đồng thời** | Cả hai landmarker nhận cùng 1 frame, cho hiệu ứng kết hợp tay + mặt | Chỉ khi sản phẩm **thật sự** cần (ví dụ: dùng tay điều khiển hiệu ứng lên mặt) |

Nếu chọn B:

* **Dùng chung frame:** `imageProxy → bitmap → MPImage` **chỉ chuyển đổi 1 lần**, đưa cùng `mpImage` cho cả hai `detectAsync(...)`. Đừng chuyển đổi hai lần.
* **Mỗi landmarker có luồng callback riêng** ⇒ kết quả về **không đồng thời** và **không cùng số frame**. Đừng giả định hai kết quả thuộc cùng 1 frame; nếu cần ghép, gắn `timestamp` và lấy kết quả mới nhất của mỗi bên (có độ lệch tối đa vài chục ms — chấp nhận được cho hiệu ứng, không chấp nhận cho logic chính xác từng frame).
* **Timestamp:** mỗi landmarker yêu cầu timestamp **đơn điệu tăng độc lập** nhau; dùng chung một nguồn thời gian (`imageProxy.imageInfo.timestamp` hoặc `SystemClock.uptimeMillis()`) là đủ, miễn không lùi.
* **Chi phí cộng dồn:** Tổng thời gian xử lý mỗi frame ≈ Hand + Face. Nếu vượt ngân sách frame, `STRATEGY_KEEP_ONLY_LATEST` sẽ tự bỏ frame — FPS AR giảm theo. **Đo bằng `VideoStatsLogger`-style logging** (Mục 10.10 tài liệu Hand) trước khi kết luận là chạy nổi.
* **Race condition khi cập nhật kết quả:** Áp dụng lại bài học Mục 13.11–13.12 của tài liệu Hand: hai callback từ hai thread cùng ghi vào state của `OverlayView`/effect ⇒ dùng **khóa hoặc `@Volatile` tham chiếu bất biến** (publish nguyên một object kết quả, không sửa từng trường).
* **Kích thước file model và APK:** thêm `face_landmarker.task` (vài MB) — xem `App_Size_Optimization_Plan.md` nếu APK size là ràng buộc.

---

## 9. GIỚI HẠN ĐÃ BIẾT, HIỆU NĂNG & QUYỀN RIÊNG TƯ

### 9.1. Giới hạn kỹ thuật

* **Góc quay lớn:** Khi quay mặt nghiêng quá (~ > 60–70° Yaw) model mất mặt hoặc landmark bên khuất bị "đoán" ⇒ vật thể neo có thể nhảy.
* **Che khuất:** Tay che miệng/mắt, khẩu trang, kính râm làm landmark vùng đó kém tin cậy; **EAR trên kính râm gần như vô nghĩa**.
* **Kính gọng dày / tóc mái che mắt:** EAR bị lệch, nên có bước hiệu chỉnh cá nhân (Công thức 3).
* **Ánh sáng yếu / ngược sáng:** rớt mặt, nhiễu tăng.
* **Đa dạng khuôn mặt:** hình dạng mắt, độ mở mắt tự nhiên khác nhau giữa người ⇒ **không có ngưỡng chung hoàn hảo**, hiệu chỉnh cá nhân là giải pháp bền nhất.
* **Không phải công cụ y khoa/an ninh:** EAR/blendshape phục vụ giải trí, không dùng để kết luận trạng thái buồn ngủ, cảm xúc hay danh tính.

### 9.2. Hiệu năng

* Thứ tự tối ưu: (1) `setNumFaces(1)`; (2) tắt blendshape/ma trận nếu không dùng; (3) hạ độ phân giải phân tích; (4) đo CPU vs GPU trên máy mục tiêu; (5) tránh cấp phát trong `onDraw`.
* **Đo, đừng đoán.** Ghi log thời gian `detectAsync → result` và FPS hiển thị thực tế trên **thiết bị tầm thấp** (không chỉ máy mạnh) trước khi chốt cấu hình.

### 9.3. Quyền riêng tư & tuân thủ

* Xử lý **hoàn toàn trên thiết bị** (MediaPipe chạy local) — nên giữ như vậy; **không gửi frame/landmark lên server** nếu không có lý do rõ ràng và có đồng ý của người dùng.
* Không lưu ảnh/video khuôn mặt ngoài phạm vi chức năng người dùng chủ động kích hoạt (ví dụ nút Record).
* Dữ liệu sinh trắc học (khuôn mặt) là nhóm nhạy cảm theo nhiều khung pháp lý và chính sách cửa hàng ứng dụng. Trước khi phát hành thật: đọc chính sách **Google Play (User Data / Permissions)** và điều chỉnh Privacy Policy nêu rõ camera được dùng để làm gì. Đây là gợi ý kỹ thuật chứ không phải tư vấn pháp lý.
* **Không làm nhận dạng danh tính** (face recognition) trừ khi có yêu cầu sản phẩm rõ ràng và đã rà soát pháp lý.

---

## 10. QUY TRÌNH CHẨN ĐOÁN (DEBUG CHECKLIST)

Đi theo thứ tự — mỗi bước loại trừ một tầng lỗi, không nhảy cóc:

1. **Có nhận được mặt không?** Log `faceLandmarks().size` mỗi frame. `0` liên tục ⇒ kiểm tra: `assets/face_landmarker.task` có đúng tên/đường dẫn; quyền camera; mặt có đủ lớn/sáng; `imageProxy.close()` đã được gọi.
2. **Kích thước ảnh có đúng chiều không?** Log `inputImage.width/height` và `rotationDegrees`. Máy cầm dọc mà thấy width > height ⇒ chưa xoay (Công thức 1.5a).
3. **Vẽ số thứ tự lên các điểm neo** (Mục 2.2) — nhìn bằng mắt để xác nhận `33/133/362/263`, `13/14`, `1`, `10`, `152`, `234/454` nằm đúng chỗ. Điểm nằm **đối xứng ngược** so với thực tế ⇒ quên mirror hoặc mirror hai lần.
4. **Vật thể lệch một khoảng cố định?** Thiếu `offsetX/offsetY` của `CENTER_CROP` (Công thức 1 tài liệu Hand). **Lệch đối xứng gương theo trục ngang?** Lỗi mirror.
5. **EAR luôn trả gần cùng một giá trị hoặc ngưỡng chỉ đúng ở một độ phân giải?** Đã đổi sang pixel chưa (Quy tắc F1)? Log $\text{EAR}_L$, $\text{EAR}_R$ ở trạng thái mở, nhắm hẳn, nheo — ghi lại làm căn cứ chọn ngưỡng.
6. **Xác định quy ước Trái/Phải bằng thực nghiệm:** Nháy **riêng mắt trái của bạn**, log cả hai EAR (và `eyeBlinkLeft/Right` nếu bật). Ghi lại "mắt chủ thể trái ⇒ EAR của index nhóm nào / blendshape nào" thành hằng số trong code.
7. **Xác định định dạng ma trận và dấu góc:** Log 16 số ma trận khi nhìn thẳng (tìm ra index tịnh tiến), rồi lần lượt quay phải / gật xuống / nghiêng phải và log yaw/pitch/roll để gán dấu (Công thức 5). Khi mirror, kiểm tra Yaw & Roll đã đổi dấu.
8. **Cử chỉ kích hoạt liên tục / nhiều lần?** Thiếu hysteresis hoặc cooldown (Mục 7.1). **Bỏ lỡ cú nháy nhanh?** EAR bị lọc quá mạnh hoặc yêu cầu số frame tối thiểu vượt FPS thực tế.
9. **Vật thể rung?** Chưa lọc (Công thức 9). **Vật thể trễ so với mặt?** Lọc quá mạnh (giảm `minCutoff`/tăng `beta`, hoặc tăng $\alpha$).
10. **FPS thấp?** Đo thời gian mỗi tầng: chuyển đổi bitmap, `detectAsync`, tính toán, `onDraw`. Kiểm tra lần lượt Quy tắc F2, F5, F6; nếu chạy Hand + Face, xem lại Mục 8.
11. **Chỉ lỗi khi ghi video, preview thì đúng?** Chiếu theo kích thước canvas ghi (không theo View màn hình) — Mục 10.5 tài liệu Hand; và kiểm tra mirror giữa preview và khung ghi (Mục 8.7–8.8 tài liệu Hand).
12. **Crash ngẫu nhiên khi mặt biến mất?** Kiểm tra Quy tắc F3, F4 (`Optional` rỗng, danh sách rỗng).
