# TÀI LIỆU LÝ THUYẾT: FINGER FRAME FILTER (FILTER TRONG KHUNG NGÓN TAY)

> **Công nghệ chủ đạo:** MediaPipe Hand Landmarker · Hình học phẳng (tứ giác, góc, diện tích) · Lọc tín hiệu (EMA, 1€ Filter) · Android Canvas (`Path`, `BitmapShader`, `ColorMatrix`) · Shader GPU (AGSL).
>
> **📝 Phạm vi:** đây là tài liệu **lý thuyết nền tảng** cho kiểu hiệu ứng "dùng 2 bàn tay dựng một khung, vùng bên trong khung bị biến đổi pixel" (tham khảo app *FingerLens: Magic Gesture* và các dự án mã nguồn mở ở Mục 12). Kế hoạch triển khai cụ thể trong HandAr nằm ở `Finger_Frame_Filter_Plan.md`.
>
> Phần đã có trong `Camera_X_Hand_Landmarker.md` (21 điểm mốc, chiếu toạ độ, mirror, kiến trúc live/recording) chỉ **dẫn chiếu**, không chép lại.
>
> ⚠️ **Độ tin cậy:** công thức ở đây là kiến thức chuẩn của hình học, xử lý ảnh và đồ hoạ (có nguồn ở Mục 12). Các **ngưỡng và hệ số** (tỉ lệ mở tay, α làm mượt, tốc độ fade...) chỉ là **giá trị khởi điểm**, phải đo lại bằng log trên máy thật (bài học lặp lại nhiều lần ở `Camera_X_Hand_Landmarker.md` mục 11.4, 11.9). Chỗ nào chưa kiểm chứng được trên máy đều ghi "cần kiểm chứng".
>
> Viết ngày 01/10/2026, đối chiếu code tại commit `6d63617`.

---

## MỤC LỤC

0. [Bức tranh tổng thể: hiệu ứng này khác gì các hiệu ứng hiện có](#0-bức-tranh-tổng-thể)
1. [Landmark & hệ toạ độ](#1-landmark--hệ-toạ-độ)
2. [Điều kiện "khung đang mở" — chuẩn hoá theo kích thước tay](#2-điều-kiện-khung-đang-mở)
3. [Dựng tứ giác từ 4 điểm — trọng tâm, `atan2`, diện tích](#3-dựng-tứ-giác-từ-4-điểm)
4. [Giữ đúng "góc nào là góc nào" giữa các frame (bài toán tương ứng)](#4-bài-toán-tương-ứng-giữa-các-frame)
5. [Làm mượt: EMA và 1€ Filter](#5-làm-mượt-ema-và-1-filter)
6. [Presence — hiện/ẩn dần thay vì bật/tắt](#6-presence--hiệnẩn-dần)
7. [Vẽ chỉ trong vùng: clip, mask, blend](#7-vẽ-chỉ-trong-vùng-clip-mask-blend)
8. [Phép biến đổi phối cảnh (Homography) — khi nào cần](#8-phép-biến-đổi-phối-cảnh-homography)
9. [Lý thuyết các filter](#9-lý-thuyết-các-filter)
10. [GPU, shader và AGSL](#10-gpu-shader-và-agsl)
11. [Bảng tổng hợp: filter → kỹ thuật → API tối thiểu](#11-bảng-tổng-hợp)
12. [Tài liệu & video tham khảo](#12-tài-liệu--video-tham-khảo)

---

## 0. BỨC TRANH TỔNG THỂ

### 0.1. Hai họ hiệu ứng

| | Hiệu ứng hiện có của HandAr | Finger Frame Filter |
|---|---|---|
| Bản chất | **Vẽ thêm** (GIF, sprite, canvas procedural) đè lên ảnh camera | **Biến đổi chính ảnh camera** trong một vùng |
| Tay dùng để | Chọn **vị trí + kích thước** vật thể (`AnchorSource`/`SizeSource`) | Chọn **vùng** (một tứ giác) |
| Ảnh camera | Không bị đụng tới | Là **nguyên liệu** để vẽ |
| Dữ liệu cần thêm | Không | Bitmap khung hình camera của chính frame đó |

### 0.2. Pipeline một frame

```text
[Bitmap camera 480×640 (analyzer)] ─────────────────────────────┐
        │                                                        │
        ▼                                                        │
[MediaPipe → 2 tay × 21 landmark]                                │
        │                                                        │
        ▼  (Mục 1–2) lấy 4 đầu ngón, kiểm tra "khung đang mở"    │
        ▼  (Mục 3)   sắp 4 điểm theo góc → tứ giác               │
        ▼  (Mục 4)   giữ đúng thứ tự góc so với frame trước      │
        ▼  (Mục 5)   làm mượt toạ độ góc                         │
        ▼  (Mục 6)   tính độ hiện p ∈ [0,1]                      │
        ▼                                                        ▼
[Vẽ] ── (Mục 7) vẽ bitmap camera ĐÃ FILTER (Mục 9–10), chỉ trong tứ giác, độ đậm p
     └─ vẽ viền + chấm góc
```

### 0.3. Mỗi mảng lý thuyết đóng vai trò gì

| Mục | Mảng lý thuyết | Vai trò trong tính năng | Nếu làm sai thì thấy gì |
|---|---|---|---|
| 1 | Hệ toạ độ chuẩn hoá, mirror, center-crop | Đặt khung **đúng chỗ** trên màn hình và trong video | Khung lệch khỏi ngón tay, lệch ngang, ngược chiều |
| 2 | Chuẩn hoá theo kích thước (scale-invariant) | Quyết định **khi nào** coi là đang dựng khung | Tay xa camera không bao giờ kích hoạt, hoặc tay gần kích hoạt nhầm |
| 3 | Hình học đa giác (trọng tâm, `atan2`, shoelace) | Biến 4 điểm rời rạc thành **một hình hợp lệ** | Khung hình "cái nơ", khung dẹt vẫn vẽ |
| 4 | Bài toán tương ứng (correspondence) | Giữ góc A của frame này nối đúng với góc A frame trước | Khung "xoắn"/nhảy khi xoay tay |
| 5 | Lọc tín hiệu (low-pass) | Khung **không rung** | Mép khung run liên tục |
| 6 | Nội suy theo thời gian | Hiện/ẩn **mượt** | Khung bật tắt giật cục |
| 7 | Alpha compositing, clip/mask | Chỉ vùng trong khung bị đổi | Filter tràn ra ngoài, mép răng cưa |
| 8 | Homography | Dán **nội dung khác** méo theo khung (không bắt buộc cho filter) | — |
| 9 | Xử lý ảnh (point op, convolution, colormap) | **Nội dung** của từng filter | Màu sai, filter không giống mẫu |
| 10 | Lập trình GPU (shader) | Chạy kịp 25–30 fps | Giật, nóng máy |

---

## 1. LANDMARK & HỆ TOẠ ĐỘ

**Vai trò:** mọi bước sau đều cần toạ độ pixel chính xác của 4 đầu ngón trên **đúng canvas đang vẽ** (màn hình live hoặc frame video — hai canvas khác kích thước).

### 1.1. 4 điểm được dùng

| Điểm | Landmark | Ý nghĩa |
|---|---|---|
| Đầu ngón cái | 4 (`THUMB_TIP`) | góc khung |
| Đầu ngón trỏ | 8 (`INDEX_FINGER_TIP`) | góc khung |
| Cổ tay | 0 (`WRIST`) | dùng đo kích thước tay (Mục 2) |
| Khớp gốc ngón giữa | 9 (`MIDDLE_FINGER_MCP`) | dùng đo kích thước tay (Mục 2) |

Hai tay × (4, 8) = **4 góc khung**. Thứ tự `hands[0]`/`hands[1]` do MediaPipe trả về **không cố định** giữa các frame — xem Mục 4.

### 1.2. Chuẩn hoá → pixel (dẫn chiếu)

Landmark có `x, y ∈ [0, 1]` theo ảnh đưa vào MediaPipe (bitmap đã xoay đứng, `imgWidth × imgHeight`). Chiếu ra canvas theo kiểu **center-crop** (giống `PreviewView` mặc định `FILL_CENTER`):

```
s       = max(W_canvas / imgWidth, H_canvas / imgHeight)          ← localScale
offX    = (W_canvas − imgWidth  × s) / 2
offY    = (H_canvas − imgHeight × s) / 2
x_pixel = (1 − x) × imgWidth × s + offX      (camera trước → mirror)
y_pixel =      y  × imgHeight × s + offY
```

Đây chính là `HandFrame.px()/py()` + `OverlayView.drawFrame()` hiện tại. Lý thuyết đầy đủ: `Camera_X_Hand_Landmarker.md` Công thức 1 và 1.5; các bẫy đã gặp: mục 10.5, 13.5, 13.6.

### 1.3. Điểm mới với hiệu ứng này: vẽ **chính bitmap camera** cho khớp landmark

Muốn vùng trong khung là ảnh camera đã filter, bitmap camera phải được vẽ bằng **đúng cùng phép chiếu** với landmark — nếu không, nội dung trong khung sẽ lệch so với phần ảnh bên ngoài. Viết phép chiếu trên dưới dạng ma trận áp cho **pixel** `(u, v)` của bitmap (`u ∈ [0, imgWidth]`):

```
X = −s·u + (imgWidth·s + offX)          ← mirror + scale + dịch
Y =  s·v + offY

Android: matrix.setScale(−s, s)
         matrix.postTranslate(imgWidth·s + offX, offY)
```

Kiểm tra lại với landmark: `u = x·imgWidth` → `X = (1 − x)·imgWidth·s + offX` ✔ trùng `px()`. Đây cũng đúng là `bmpMatrix` mà `CameraRecordFragment.startRecordingFrameLoop()` đang dùng để vẽ nền camera vào video — nên hai bên sẽ khớp nhau từng pixel nếu dùng chung `s/offX/offY`.

---

## 2. ĐIỀU KIỆN "KHUNG ĐANG MỞ"

**Vai trò:** quyết định frame nào được coi là "người dùng đang dựng khung". Đây là chỗ dễ làm hỏng trải nghiệm nhất.

### 2.1. Công thức

Với mỗi tay:

```
d     = ‖P4 − P8‖           ← độ mở giữa đầu ngón cái và đầu ngón trỏ
L     = ‖P0 − P9‖           ← "chiều dài lòng bàn tay" (palmLength trong GestureUtils.kt)
ratio = d / L
tay "mở" khi ratio > NGƯỠNG_MỞ
```

`‖A − B‖ = √((xA − xB)² + (yA − yB)²)` là khoảng cách Euclid 2D (Công thức 3 trong tài liệu Hand).

**Vì sao chia cho `L`:** tay gần camera to, tay xa nhỏ. `d` đứng một mình thay đổi theo khoảng cách tới camera; `d / L` thì không (cả tử và mẫu cùng co giãn theo một hệ số) — gọi là **bất biến theo tỉ lệ (scale-invariant)**. Repo đã có đúng công thức này: `thumbIndexPinchRatio()` (dùng cho 👌 với ngưỡng `< 0.35`). Khung cần chiều ngược lại: `ratio` **lớn**.

### 2.2. Bài học lịch sử của chính repo: đừng đo góc vuông

HandAr **từng có** cử chỉ "khung máy ảnh" (hình chữ L: cái ⟂ trỏ, đo bằng `cos` góc qua tích vô hướng) và **đã gỡ** (`Camera_X_Hand_Landmarker.md` mục 11.6): tay người khó giữ đúng góc vuông một cách tự nhiên, cộng thêm hiện tượng che khuất đầu ngón làm landmark 2D sai góc, và đòi hỏi đúng đồng thời 2 tầng điều kiện → rất khó kích hoạt.

Hệ quả cho thiết kế mới:

- **Không** yêu cầu góc vuông. Chỉ cần "cái và trỏ đang tách nhau" (`ratio` đủ lớn) — nhẹ hơn nhiều.
- Việc "khung có hợp lệ không" chuyển sang kiểm tra **hình học của chính tứ giác** (diện tích đủ lớn, Mục 3.4) thay vì kiểm tra tư thế từng ngón.
- Ngưỡng phải lấy từ log thật (in `ratio` của từng tay qua nhiều frame khi giữ tay "mở tự nhiên" và "khép tự nhiên", chọn ngưỡng nằm xa cả hai dải nhiễu — đúng quy trình mục 11.9).

### 2.3. Hysteresis (ngưỡng kép) — chống chập chờn

Nếu chỉ dùng 1 ngưỡng, khi `ratio` dao động quanh ngưỡng, khung sẽ bật/tắt liên tục. Dùng 2 ngưỡng:

```
đang TẮT  → BẬT khi ratio > NGƯỠNG_BẬT
đang BẬT  → TẮT khi ratio < NGƯỠNG_TẮT        (NGƯỠNG_TẮT < NGƯỠNG_BẬT)
```

Vùng giữa hai ngưỡng giữ nguyên trạng thái cũ. Đây là kỹ thuật chuẩn (giống công tắc Schmitt trigger trong điện tử). Lưu ý: hysteresis cần **nhớ trạng thái** → không đặt được trong `GestureRecognizer` (vốn vô trạng thái, dùng chung giữa nhiều luồng — mục 11.8 tài liệu Hand), phải đặt trong visual (Mục 6 kết hợp làm luôn).

---

## 3. DỰNG TỨ GIÁC TỪ 4 ĐIỂM

**Vai trò:** biến 4 điểm không có thứ tự thành một đa giác không tự cắt để vẽ được.

### 3.1. Vấn đề

Nối 4 điểm theo thứ tự tuỳ ý có thể ra hình **"cái nơ"** (2 cạnh cắt chéo nhau). `Path` vẫn vẽ được nhưng vùng tô bị chia thành 2 tam giác đối đỉnh — sai hoàn toàn.

### 3.2. Trọng tâm

```
cx = (x1 + x2 + x3 + x4) / 4
cy = (y1 + y2 + y3 + y4) / 4
```

(Đây là trọng tâm của **4 đỉnh**, không phải trọng tâm diện tích của đa giác — với mục đích sắp xếp thì đủ.)

### 3.3. Sắp theo góc cực bằng `atan2`

```
θ_i = atan2(y_i − cy, x_i − cx)      i = 1..4,   θ_i ∈ (−π, π]
sắp 4 điểm theo θ_i tăng dần rồi nối theo thứ tự đó (và nối điểm cuối về điểm đầu)
```

- `atan2(dy, dx)` trả về góc của vector `(dx, dy)` so với trục x, **đúng cả 4 góc phần tư** (khác `atan(dy/dx)` chỉ cho (−π/2, π/2) và lỗi chia 0 khi `dx = 0`).
- Trên màn hình trục y hướng **xuống**, nên "góc tăng dần" nhìn bằng mắt là chiều **kim đồng hồ** — không quan trọng, chỉ cần nhất quán.
- **Vì sao không bao giờ ra cái nơ:** các điểm sắp theo góc quanh một điểm nằm bên trong tạo thành đa giác "hình sao" (star-shaped) đối với điểm đó — mọi tia từ trọng tâm chỉ cắt biên đúng 1 lần, nên các cạnh không thể cắt nhau. Đa giác có thể **lõm** (một góc thụt vào trong) nhưng vẫn là đa giác đơn, tô màu đúng.
- Sắp trong không gian chuẩn hoá `[0,1]` hay pixel đều cho cùng thứ tự vòng (phép chiếu ở Mục 1 là affine; mirror chỉ đảo chiều vòng, vẫn hợp lệ). Vì vậy có thể sắp ngay trên dữ liệu thô.

### 3.4. Diện tích — kiểm tra khung có "đáng vẽ" không (công thức Shoelace)

```
A = ½ · | Σ_{i=1..4} (x_i · y_{i+1} − x_{i+1} · y_i) |        (với điểm 5 ≡ điểm 1)
```

Áp dụng cho các điểm **đã sắp** ở 3.3. Dùng để loại khung suy biến (4 điểm gần thẳng hàng, 2 tay chụm sát nhau): nếu `A` nhỏ hơn một tỉ lệ của diện tích khung hình (ví dụ < 2–3% — **cần đo**) thì coi như không có khung.

Muốn bất biến theo khoảng cách camera giống Mục 2, có thể so `A` với `L²` (bình phương chiều dài lòng bàn tay trung bình 2 tay) thay vì với diện tích màn hình.

### 3.5. (Tuỳ chọn) Kiểm tra lồi bằng tích có hướng

Đa giác lồi ⇔ mọi tích có hướng của 2 cạnh liên tiếp **cùng dấu**:

```
cross_i = (x_{i+1} − x_i)·(y_{i+2} − y_{i+1}) − (y_{i+1} − y_i)·(x_{i+2} − x_{i+1})
```

Repo đã có cùng phép tính này (`crossSign()` trong `GestureUtils.kt`, dùng cho `segmentsCross`). Với filter, khung lõm vẫn vẽ đúng nên **không bắt buộc**; chỉ cần nếu sau này muốn dán ảnh bằng homography (Mục 8 — homography của tứ giác lõm cho kết quả méo kỳ dị).

---

## 4. BÀI TOÁN TƯƠNG ỨNG GIỮA CÁC FRAME

**Vai trò:** điều kiện cần để làm mượt (Mục 5) — chỉ trộn được "góc A frame trước" với "góc A frame này" nếu biết điểm nào là góc A.

### 4.1. Vì sao không dùng thẳng thứ tự sau khi sắp

Sắp theo `atan2` cho thứ tự **vòng** đúng, nhưng **điểm bắt đầu** là điểm có `θ` nhỏ nhất. Khi người dùng xoay khung, một góc đi qua ranh giới `θ = ±π` → nó nhảy từ cuối danh sách lên đầu → mọi chỉ số bị dịch 1. Nếu làm mượt theo chỉ số, góc 1 mới sẽ bị trộn với góc 1 cũ (thực ra là góc khác) → khung "xoắn" một nhịp.

Cũng không thể bám theo `hands[0]/hands[1]` vì MediaPipe có thể đổi thứ tự 2 tay giữa các frame; `handedness` cũng có lúc lật.

### 4.2. Cách giải: chọn phép quay vòng gần nhất

Với 4 điểm đã sắp `Q[0..3]` và 4 góc đã làm mượt của frame trước `S[0..3]`, thử 4 cách quay vòng `k = 0..3`:

```
cost(k) = Σ_{i=0..3} ‖Q[(i + k) mod 4] − S[i]‖²
k*      = argmin cost(k)
góc mới thứ i = Q[(i + k*) mod 4]
```

Chỉ 4 phép thử × 4 khoảng cách — chi phí không đáng kể. Vì đã sắp theo vòng nên chỉ cần thử các phép **quay**, không cần thử cả 24 hoán vị (đây là phiên bản rút gọn của bài toán gán cặp — *assignment problem*).

Nếu thứ tự vòng bị **đảo chiều** (hiếm, chỉ khi phép chiếu đổi mirror giữa chừng) thì thử thêm 4 phép quay của danh sách đảo ngược — 8 phép thử.

---

## 5. LÀM MƯỢT: EMA VÀ 1€ FILTER

**Vai trò:** landmark rung vài pixel mỗi frame dù tay đứng yên. Với hiệu ứng điểm (quả cầu ở lòng bàn tay) rung nhỏ khó thấy; với **cạnh thẳng dài** của khung, rung nhỏ ở góc lộ rõ thành cả cạnh rung — nên hiệu ứng này cần làm mượt hơn hẳn các hiệu ứng hiện có.

### 5.1. EMA — trung bình động hàm mũ (Exponential Moving Average)

```
ŷ_n = α · x_n + (1 − α) · ŷ_{n−1}          α ∈ (0, 1]
```

- `x_n`: toạ độ đo được ở frame n; `ŷ_n`: toạ độ đã làm mượt.
- `α = 1`: không làm mượt. `α` nhỏ: mượt nhưng **trễ** (khung đuổi theo tay chậm).
- Áp riêng cho từng toạ độ `x`, `y` của từng góc (8 số).
- Gọi là "hàm mũ" vì khai triển ra, mẫu cách đây `k` frame có trọng số `α(1 − α)^k` — giảm theo hàm mũ.

**Đánh đổi mượt ↔ trễ là không tránh được** với bộ lọc thông thấp cố định — đây là lý do có 1€ Filter (5.3).

### 5.2. α phụ thuộc tốc độ frame — dạng độc lập thời gian

EMA với `α` cố định cho kết quả **khác nhau** khi nhịp cập nhật khác nhau (30 fps mượt khác 20 fps). Nếu cập nhật theo thời gian thực `Δt` (giây) giữa 2 lần đo:

```
α = 1 − exp(−Δt / τ)          τ: "hằng số thời gian" (giây), ví dụ 0.05–0.1 s
```

Trong HandAr, làm mượt chạy ở `onHandFrame()` (đúng 1 lần mỗi kết quả MediaPipe — quy tắc mục 13.2 tài liệu Hand) cho cả live lẫn recording **với cùng dãy dữ liệu**, nên dù dùng `α` cố định thì 2 bản vẫn giống nhau. Dạng `Δt` chỉ cần khi muốn độ mượt không đổi giữa GPU (~30 fps) và CPU fallback (~22 fps) — xem `Perf_Notes.md` mục 9. Video giải thích rất trực quan: Freya Holmér, *Lerp smoothing is broken* (Mục 12).

### 5.3. 1€ Filter (One Euro Filter) — mượt khi đứng yên, nhanh khi di chuyển

Ý tưởng (Casiez, Roussel, Vogel — CHI 2012): rung chỉ khó chịu khi tay **đứng yên**; khi tay **di chuyển nhanh**, trễ mới khó chịu. Vậy cho `α` tự thay đổi theo tốc độ:

```
Te          = Δt                                    (chu kỳ lấy mẫu)
α(fc)       = 1 / (1 + τ/Te),     τ = 1 / (2π·fc)   (EMA với tần số cắt fc)

dx_n        = (x_n − ŷ_{n−1}) / Te                  (tốc độ thô)
dx̂_n        = EMA của dx_n với tần số cắt cố định dcutoff (thường 1 Hz)
fc          = fcmin + β · |dx̂_n|                    (tay càng nhanh, fc càng cao → α càng lớn → càng ít trễ)
ŷ_n         = α(fc) · x_n + (1 − α(fc)) · ŷ_{n−1}
```

Hai tham số cần chỉnh: `fcmin` (giảm để bớt rung khi đứng yên) và `β` (tăng để bớt trễ khi di chuyển). Quy trình chỉnh tác giả khuyên: đặt `β = 0`, chỉnh `fcmin` đến khi hết rung lúc đứng yên; sau đó tăng `β` đến khi hết trễ lúc di chuyển nhanh.

`Camera_X_Face_Landmarker.md` (Công thức 9) cũng đã nhắc tới bộ lọc này — có thể viết 1 lần dùng chung cho cả tay và mặt.

**Khuyến nghị:** bắt đầu bằng EMA (5.1) vì dễ kiểm chứng; chỉ chuyển sang 1€ nếu log/quan sát cho thấy không chọn được `α` vừa hết rung vừa đủ nhanh.

---

## 6. PRESENCE — HIỆN/ẨN DẦN

**Vai trò:** khung mờ dần vào/ra thay vì bật/tắt đột ngột; đồng thời che bớt những frame chập chờn ở biên điều kiện.

```
mục_tiêu = 1 nếu khung hợp lệ (Mục 2 + 3.4), ngược lại 0
p_n      = p_{n−1} + (mục_tiêu − p_{n−1}) · k        k ∈ (0, 1], ví dụ 0.2–0.3 mỗi lần cập nhật
```

Thực chất đây là **EMA của một tín hiệu 0/1** (cùng công thức Mục 5.1 với `α = k`). `p` dùng làm độ đậm (alpha) khi vẽ ở Mục 7. Khi `p` dưới một ngưỡng rất nhỏ (ví dụ 0.01) thì bỏ qua không vẽ.

⚠️ **Ràng buộc kiến trúc HandAr (quan trọng):** `OverlayView.drawFrame()` **không gọi `draw()` của visual** khi không có tay hoặc khi không state nào khớp (`if (hands.isEmpty() || matchedIndex == -1) return`). Nghĩa là nếu điều kiện "khung mở" nằm trong `GestureRecognizer`, ngay khi tay khép lại, visual mất quyền vẽ → **không có fade-out**. Muốn có fade-out, điều kiện "mở" phải nằm **trong visual**, còn gesture chỉ kiểm tra lỏng "có đủ 2 tay". Chi tiết lựa chọn: `Finger_Frame_Filter_Plan.md`.

---

## 7. VẼ CHỈ TRONG VÙNG: CLIP, MASK, BLEND

**Vai trò:** đây là lõi của "filter trong khung" — phần bên trong lấy pixel đã biến đổi, phần bên ngoài giữ nguyên.

### 7.1. Công thức tổng quát — alpha compositing

```
kết_quả = F(ảnh) · m + ảnh · (1 − m)          m ∈ [0, 1] mỗi pixel
m       = mask · p
```

- `F(ảnh)`: ảnh đã qua filter (Mục 9).
- `mask`: 1 bên trong tứ giác, 0 bên ngoài (ở mép có thể là giá trị trung gian nếu khử răng cưa).
- `p`: presence (Mục 6).

Đây chính là phép **"source over"** của Porter–Duff khi vẽ lớp `F(ảnh)` có alpha `m` đè lên ảnh gốc. Thuật ngữ:
- **Mask (mặt nạ):** ảnh đơn kênh quyết định "lấy bao nhiêu" pixel lớp trên.
- **ROI (Region of Interest):** vùng được chọn — ở đây là tứ giác.
- **Blend:** trộn 2 lớp theo một công thức (ở đây là nội suy tuyến tính theo `m`).

### 7.2. Ba cách hiện thực trên Android Canvas

**Cách A — `clipPath`:**
```kotlin
canvas.withSave {
    clipPath(quadPath)                       // từ đây chỉ vẽ được trong tứ giác
    drawBitmap(cameraBitmap, cameraMatrix, filterPaint)   // filterPaint có ColorFilter + alpha = p
}
```
Đơn giản, nhưng tài liệu Android ghi rõ clip phức tạp (`clipPath`, `clipRect` bị xoay…) **tốn hơn và không khử răng cưa** → mép khung nghiêng sẽ bị răng cưa. Khuyến nghị chính thức: *"vẽ đúng hình dạng thay vì clip"*.

**Cách B — vẽ thẳng hình dạng bằng `BitmapShader` (khuyến nghị):**
```kotlin
val shader = BitmapShader(cameraBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
shader.setLocalMatrix(cameraMatrix)          // ma trận Mục 1.3 → pixel bitmap nằm đúng chỗ trên canvas
filterPaint.shader = shader
filterPaint.colorFilter = ColorMatrixColorFilter(...)   // filter Mục 9.1
filterPaint.alpha = (p * 255).toInt()
filterPaint.isAntiAlias = true               // mép tứ giác được khử răng cưa
canvas.drawPath(quadPath, filterPaint)
```
"Tô tứ giác bằng một tấm ảnh" — chỉ 1 lệnh vẽ, có khử răng cưa, không cần clip. `BitmapShader` gắn với 1 `Bitmap` cụ thể; bitmap camera đổi mỗi frame nên phải tạo shader mới khi bitmap đổi (xem rào cản trong file Plan).

**Cách C — `saveLayer` + `PorterDuff` (dùng khi lớp trên phức tạp):** vẽ `F(ảnh)` vào 1 layer, vẽ mask bằng `PorterDuff.Mode.DST_IN` để chỉ giữ phần trong mask, rồi hợp layer xuống. Linh hoạt nhất (mask mềm, mask nhiều hình), nhưng `saveLayer` tốn một vùng đệm off-screen — chỉ dùng khi A/B không đủ (ví dụ filter Matrix vẽ chữ chồng lên ảnh, Mục 9.6).

### 7.3. Viền và chấm góc

Vẽ sau cùng, đè lên: `Paint.Style.STROKE` + `DashPathEffect(floatArrayOf(dài, hở), phase)` cho viền nét đứt (tăng `phase` theo thời gian → viền "chạy"), `drawCircle` ở 4 góc. Độ dày nét nên tỉ lệ theo kích thước canvas (live và video khác kích thước — cùng tinh thần mục 10.5 tài liệu Hand).

---

## 8. PHÉP BIẾN ĐỔI PHỐI CẢNH (HOMOGRAPHY)

**Vai trò:** chỉ cần khi nội dung trong khung **không phải** ảnh camera tại chỗ, mà là một ảnh chữ nhật khác (video, ảnh, sticker) cần "dán" méo theo tứ giác. **Với filter, không cần** — vì pixel bên trong vẫn là ảnh camera đúng vị trí, chỉ đổi màu.

### 8.1. Định nghĩa

Homography là ma trận `H` cỡ 3×3 biến điểm `(x, y)` thành `(x', y')` qua toạ độ đồng nhất:

```
[x'·w]       [h11 h12 h13] [x]
[y'·w]  =    [h21 h22 h23] [y]          x' = (h11·x + h12·y + h13) / (h31·x + h32·y + h33)
[  w ]       [h31 h32 h33] [1]          y' = (h21·x + h22·y + h23) / (h31·x + h32·y + h33)
```

`H` có 8 bậc tự do (chia tỉ lệ không đổi kết quả, thường cố định `h33 = 1`). Mỗi cặp điểm tương ứng cho 2 phương trình → **cần đúng 4 cặp điểm** (4 góc chữ nhật nguồn ↔ 4 góc tứ giác đích) để giải ra `H`. Đường thẳng vẫn là đường thẳng, nhưng song song không còn song song (giống nhìn nghiêng một tờ giấy).

### 8.2. Trên Android / OpenCV

| Việc | OpenCV | Android |
|---|---|---|
| Tính `H` từ 4 cặp điểm | `cv2.getPerspectiveTransform(src, dst)` | `Matrix.setPolyToPoly(src, 0, dst, 0, 4)` (tối đa 4 điểm) |
| Áp `H` lên ảnh | `cv2.warpPerspective(img, H, size)` | `canvas.drawBitmap(bmp, matrix, paint)` hoặc `BitmapShader.setLocalMatrix(matrix)` |

Lưu ý: tứ giác đích phải **lồi** và cùng chiều vòng với nguồn, nếu không ảnh bị lật/méo kỳ dị (kiểm tra bằng Mục 3.5).

---

## 9. LÝ THUYẾT CÁC FILTER

**Vai trò:** quyết định **nội dung** bên trong khung. Mọi filter đều là một hàm `F` biến ảnh thành ảnh. Chia 3 nhóm theo việc mỗi pixel kết quả cần đọc **bao nhiêu** pixel nguồn — đây là thứ quyết định cài bằng gì và tốn bao nhiêu:

| Nhóm | Pixel ra phụ thuộc | Ví dụ | Cài đặt rẻ nhất |
|---|---|---|---|
| **Point operation** (phép toán điểm) | đúng 1 pixel vào cùng vị trí | đảo màu, đen trắng, sepia, ảnh nhiệt | `ColorMatrix` (tuyến tính) / LUT (phi tuyến) |
| **Neighborhood operation** (lân cận) | một vùng pixel xung quanh | làm mờ, dò cạnh (neon) | convolution — cần shader |
| **Geometric / sinh thêm** | pixel ở **vị trí khác** hoặc nội dung mới | pixelate, glitch, mưa chữ Matrix | đổi toạ độ lấy mẫu / vẽ thêm |

Ký hiệu: mỗi pixel `(R, G, B)` có giá trị trong `[0, 1]` (lý thuyết) hoặc `[0, 255]` (Android `ColorMatrix` — xem 9.1).

### 9.1. Phép toán điểm tuyến tính — `ColorMatrix`

Android `ColorMatrix` là ma trận **4×5** áp lên `[R, G, B, A]` của từng pixel:

```
[ a b c d e ]          R' = a·R + b·G + c·B + d·A + e
[ f g h i j ]          G' = f·R + g·G + h·B + i·A + j
[ k l m n o ]          B' = k·R + l·G + m·B + n·A + o
[ p q r s t ]          A' = p·R + q·G + r·B + s·A + t
```

⚠️ Trong `ColorMatrix` của Android, màu nằm trong **[0, 255]**, nên **cột hằng số cuối (`e, j, o, t`) cũng tính theo thang 0–255**. Đây là chỗ dễ sai nhất khi chép công thức từ shader (thang 0–1) sang.

**Đảo màu (Negative / Invert):** `c' = 1 − c` (thang 0–1) ⇔ `c' = 255 − c`:
```
[ -1  0  0  0  255 ]
[  0 -1  0  0  255 ]
[  0  0 -1  0  255 ]
[  0  0  0  1    0 ]
```

**Đen trắng (Mono / Noir)** — độ sáng cảm nhận (luma, chuẩn Rec. 601):
```
Y = 0.299·R + 0.587·G + 0.114·B          (mắt nhạy với xanh lá nhất, kém nhất với xanh dương)
[ .299 .587 .114 0 0 ]
[ .299 .587 .114 0 0 ]
[ .299 .587 .114 0 0 ]
[    0    0    0 1 0 ]
```
(`ColorMatrix.setSaturation(0f)` cho kết quả tương tự với hệ số hơi khác — Android dùng bộ hệ số luma riêng.)

**Sepia:**
```
R' = 0.393R + 0.769G + 0.189B
G' = 0.349R + 0.686G + 0.168B
B' = 0.272R + 0.534G + 0.131B
```

**Ghép nhiều phép:** `ColorMatrix.postConcat()` nhân 2 ma trận → 1 ma trận (ví dụ đen trắng rồi nhuộm xanh lá cho kiểu "Matrix" giản lược) — vẫn chỉ 1 lần duyệt pixel. Tăng tương phản quanh mức xám giữa: `c' = k·(c − 128) + 128` ⇔ hệ số `k` trên đường chéo, hằng số `128·(1 − k)`.

### 9.2. Phép toán điểm phi tuyến — bảng màu (colormap / LUT): ảnh nhiệt (Thermal)

Ảnh "nhiệt" của app giải trí **không phải nhiệt độ thật** (camera điện thoại không đo hồng ngoại) — nó là **tô màu giả theo độ sáng**:

1. Tính `Y` (luma, 9.1), chuẩn hoá về `[0, 1]`.
2. Tra `Y` vào một **dải màu (gradient)** gồm các mốc `(s_i, C_i)`, ví dụ `0.0` đen-xanh thẫm → `0.25` tím → `0.5` đỏ → `0.75` cam-vàng → `1.0` trắng.
3. Nội suy tuyến tính giữa 2 mốc kẹp `Y`:
```
tìm i sao cho s_i ≤ Y ≤ s_{i+1}
t     = (Y − s_i) / (s_{i+1} − s_i)
màu   = C_i · (1 − t) + C_{i+1} · t
```

Vì đây là hàm **phi tuyến** của `Y`, `ColorMatrix` (tuyến tính) **không làm được**. Cách cài: shader (AGSL, Mục 10) — hoặc bảng tra 256 phần tử trên CPU (chậm, chỉ để thử). Dải màu đẹp, đều về cảm nhận: *Turbo* của Google, *Inferno* của matplotlib (Mục 12).

### 9.3. Phép toán lân cận — tích chập (convolution): làm mờ, Neon

**Tích chập** với một **kernel** (ma trận nhỏ, thường 3×3): pixel ra = tổng các pixel lân cận nhân với hệ số tương ứng của kernel.

```
O(x, y) = Σ_{i=−1..1} Σ_{j=−1..1}  K(i, j) · I(x + i, y + j)
```

- **Box blur 3×3:** mọi `K = 1/9` → trung bình 9 pixel → mờ.
- **Dò cạnh Sobel** — 2 kernel đo độ chênh sáng theo ngang và dọc (áp trên ảnh luma `Y`):
```
      [ -1  0  +1 ]            [ -1  -2  -1 ]
Gx =  [ -2  0  +2 ] * Y   Gy = [  0   0   0 ] * Y
      [ -1  0  +1 ]            [ +1  +2  +1 ]

G = √(Gx² + Gy²)       ← độ mạnh cạnh (lớn ở đường viền vật thể)
```
- **Neon** = dò cạnh + tô màu + phát sáng:
```
e     = clamp(G · độ_khuếch, 0, 1)
neon  = màu_neon · e                   (nền tối, chỉ cạnh sáng)
glow  = làm_mờ(neon)                   (lan sáng)
F     = neon + glow · cường_độ         (phép cộng — "Additive blending")
```
Mỗi pixel đọc 9 pixel (Sobel) + thêm cho glow → chỉ chạy kịp trên GPU (Mục 10). Glow trên mobile thường làm rẻ bằng cách thu nhỏ ảnh cạnh rồi phóng to có lọc (bilinear), thay vì blur kernel lớn.

### 9.4. Pixelate (vỡ hạt) — đổi toạ độ lấy mẫu

Chia ảnh thành ô vuông cạnh `N` pixel; mọi pixel trong ô lấy màu của tâm ô:

```
u' = (⌊u / N⌋ + 0.5) · N
v' = (⌊v / N⌋ + 0.5) · N
F(u, v) = I(u', v')
```

**Cách rẻ không cần shader:** vẽ bitmap camera thu nhỏ `N` lần vào 1 bitmap nhỏ, rồi phóng to lại với `Paint.isFilterBitmap = false` (lấy mẫu "láng giềng gần nhất" → ra ô vuông sắc cạnh). Bật lọc thì ra hiệu ứng mờ (cũng là cách làm blur rẻ).

### 9.5. Glitch — tách kênh RGB + xé dải ngang

**Tách kênh (chromatic aberration):** 3 kênh màu lấy mẫu ở 3 vị trí lệch nhau:
```
R' = I(u + δ, v).R
G' = I(u,     v).G
B' = I(u − δ, v).B
```
Viền vật thể xuất hiện bóng đỏ/xanh — giống lỗi quang học của ống kính rẻ.

**Xé dải ngang (slice displacement):** chia ảnh thành dải cao `h`; mỗi dải dịch ngang một đoạn "ngẫu nhiên nhưng tái lập được":
```
b      = ⌊v / h⌋                                (chỉ số dải)
n      = ⌊t · tần_suất⌋                          (đổi kiểu nhiễu theo thời gian)
r      = hash(b, n) ∈ [0, 1)
dịch   = (r > ngưỡng) ? (hash(b, n + 17) − 0.5) · biên_độ : 0
F(u,v) = I(u + dịch, v)
```
`hash` là hàm băm số → số giả ngẫu nhiên (ví dụ kinh điển trong shader: `fract(sin(b·12.9898 + n·78.233) · 43758.5453)`). Dùng hash thay `Random()` để **live và video ra cùng một kiểu nhiễu** khi cùng thời điểm — cùng tinh thần "dữ liệu trung lập" (mục 13.3 tài liệu Hand).

**Cách không cần shader (API 28):** tách kênh = vẽ bitmap 3 lần, mỗi lần một `ColorMatrix` chỉ giữ 1 kênh, lệch `δ`, trộn bằng `PorterDuff.Mode.ADD` trong `saveLayer`; xé dải = `drawBitmap(src: Rect, dst: Rect)` từng dải với `dst` dịch ngang. Tốn nhiều lệnh vẽ hơn shader nhưng chạy được trên mọi máy.

### 9.6. "Matrix" — mưa chữ xanh (nội dung sinh thêm)

1. Lưới ô `cột × hàng`; mỗi cột có tốc độ `v_c` và pha `φ_c` (từ hash của chỉ số cột).
2. Vị trí "đầu giọt" của cột c tại thời điểm t: `y_c(t) = (φ_c + v_c·t) mod chiều_cao`.
3. Độ sáng ô ở khoảng cách `k` ô phía trên đầu giọt: `b = exp(−k / độ_dài_đuôi)` (đầu sáng nhất, đuôi mờ dần).
4. Ký tự trong ô đổi theo `hash(cột, hàng, ⌊t·tốc_độ_nháy⌋)`.
5. Trộn với ảnh camera: ảnh camera → đen trắng → nhuộm xanh tối (9.1, `postConcat`), rồi vẽ ký tự đè lên; có thể cho độ sáng ký tự nhân với `Y` của pixel bên dưới để hình người vẫn "hiện" qua lớp chữ.

Cài bằng Canvas thuần (`drawText` lưới ký tự) được, nhưng số lệnh vẽ lớn → cần đo; bản shader cần một texture font (atlas) — phức tạp hơn các filter khác.

---

## 10. GPU, SHADER VÀ AGSL

**Vai trò:** điều kiện để các filter nhóm "lân cận" và "phi tuyến" chạy kịp thời gian thực.

### 10.1. Vì sao không làm bằng vòng lặp Kotlin

Frame video của app trên máy test là 720×1560 ≈ 1,1 triệu pixel (`computeRecordingSize`, cạnh ngắn 720 — `Perf_Notes.md` mục 5); màn hình live còn lớn hơn. Ở 25–30 fps là ~30 triệu pixel/giây cho **một** phép toán điểm; Sobel đọc 9 pixel mỗi pixel → ~270 triệu lần đọc/giây. Vòng lặp `for` trên CPU không kịp — và ngân sách ghi hình 40ms/frame đã dùng ~17ms (`Perf_Notes.md`).

**Shader** (fragment shader) là chương trình nhỏ chạy trên GPU **cho từng pixel, song song** trên hàng trăm–nghìn lõi. Bạn chỉ viết "một pixel tính thế nào"; GPU lo phần chạy cho tất cả.

### 10.2. Những gì Canvas đã chạy trên GPU sẵn (không cần viết shader)

Canvas có tăng tốc phần cứng (live `OverlayView` và `lockHardwareCanvas()` của bộ ghi hình) **đã tự sinh shader** cho các thao tác: `drawBitmap`, `BitmapShader`, `ColorMatrixColorFilter`, `PorterDuff`, `drawPath` có khử răng cưa. Vì vậy filter **nhóm điểm tuyến tính** (9.1) và các mẹo "thu nhỏ – phóng to" (9.4), "vẽ nhiều lần lệch" (9.5) **không cần AGSL** và chạy được từ API 28 (minSdk của app).

### 10.3. AGSL (Android Graphics Shading Language)

- Ngôn ngữ shader của Android, cú pháp gần **GLSL** (fragment shader), gắn vào pipeline vẽ của Skia qua `android.graphics.RuntimeShader`.
- **Chỉ có từ Android 13 (API 33)** và cần tăng tốc phần cứng. App có minSdk 28 → máy 28–32 phải có **phương án dự phòng** (filter ColorMatrix, hoặc ẩn filter đó).
- Dùng như mọi `Shader` khác: gắn vào `Paint.shader` rồi `drawPath/drawRect`.

Khái niệm:
- **`main(float2 coord)`**: hàm chạy cho từng pixel, `coord` là toạ độ pixel trên canvas (đã qua ma trận cục bộ), trả `half4` (RGBA).
- **Uniform**: biến truyền từ Kotlin vào, giống nhau cho mọi pixel trong một lần vẽ — ví dụ thời gian, độ lệch `δ`, kích thước ô. Đặt bằng `shader.setFloatUniform("tên", giá_trị)`.
- **`uniform shader image`**: một shader con làm nguồn ảnh — truyền `BitmapShader` của bitmap camera vào bằng `RuntimeShader.setInputShader("image", bitmapShader)`; trong AGSL đọc pixel bằng `image.eval(coord)`. Đây là cách một shader "đọc ảnh camera".

Ví dụ — Glitch tách kênh (9.5):
```glsl
uniform shader image;   // BitmapShader của bitmap camera (đã có local matrix Mục 1.3)
uniform float delta;    // độ lệch, pixel

half4 main(float2 p) {
    half r = image.eval(p + float2(delta, 0)).r;
    half g = image.eval(p).g;
    half b = image.eval(p - float2(delta, 0)).b;
    return half4(r, g, b, 1);
}
```

Ví dụ — Ảnh nhiệt (9.2), dải 3 mốc cho gọn:
```glsl
uniform shader image;

half3 ramp(half y) {
    half3 c0 = half3(0.05, 0.0, 0.25);   // tối: xanh tím thẫm
    half3 c1 = half3(0.9, 0.1, 0.1);     // giữa: đỏ
    half3 c2 = half3(1.0, 1.0, 0.6);     // sáng: vàng trắng
    return y < 0.5 ? mix(c0, c1, y / 0.5) : mix(c1, c2, (y - 0.5) / 0.5);
}

half4 main(float2 p) {
    half3 c = image.eval(p).rgb;
    half y = dot(c, half3(0.299, 0.587, 0.114));   // luma Rec.601
    return half4(ramp(y), 1);
}
```

⚠️ **Cần kiểm chứng trên máy:** `RuntimeShader` vẽ lên canvas của `Surface.lockHardwareCanvas()` (Surface đầu vào của `MediaCodec`) — về lý thuyết đây vẫn là canvas tăng tốc phần cứng nên phải chạy, nhưng chưa có tài liệu khẳng định riêng cho trường hợp này; thử trên máy thật trước khi dựa vào.

---

## 11. BẢNG TỔNG HỢP

| Filter | Nhóm (Mục 9) | Cách cài khuyến nghị | API tối thiểu | Chi phí tương đối |
|---|---|---|---|---|
| Negative (đảo màu) | điểm tuyến tính | `ColorMatrixColorFilter` + `BitmapShader` | 28 | rất thấp |
| Mono / Sepia / nhuộm màu | điểm tuyến tính | `ColorMatrixColorFilter` | 28 | rất thấp |
| Pixelate | hình học | thu nhỏ → phóng to không lọc / AGSL | 28 / 33 | thấp |
| Glitch | hình học | vẽ nhiều lần + `PorterDuff.ADD` / AGSL | 28 / 33 | trung bình / thấp |
| Thermal | điểm phi tuyến | AGSL (colormap) | 33 | thấp |
| Neon | lân cận | AGSL (Sobel + glow) | 33 | cao |
| Matrix | sinh thêm | `drawText` lưới + ColorMatrix / AGSL + atlas font | 28 / 33 | cao |

→ Filter **đầu tiên** hợp lý nhất để chứng minh toàn bộ pipeline (khung, làm mượt, vẽ trong vùng, ghi hình) là **Negative**: ít rủi ro nhất ở phần filter, nên mọi lỗi lộ ra đều thuộc về phần khung/hạ tầng — dễ khoanh vùng.

---

## 12. TÀI LIỆU & VIDEO THAM KHẢO

### 12.1. Dự án tham khảo cùng kiểu hiệu ứng (mã nguồn mở)

- [sophiamyang/finger-frame-effect](https://github.com/sophiamyang/finger-frame-effect) — mô tả rõ nhất: 4 đầu ngón (cái + trỏ 2 tay), điều kiện "L mở chuẩn hoá theo kích thước tay", sắp theo góc quanh trọng tâm, làm mượt hàm mũ, presence fade, vẽ webcam → filter clip theo quad → viền. (Mục 2, 3, 5, 6, 7)
- [Nothing-dot-exe/HandFrame-Gesture-Controlled-AR-Camera-Studio](https://github.com/Nothing-dot-exe/HandFrame-Gesture-Controlled-AR-Camera-Studio) — ROI mask + `warpPerspective`, công thức blend `filter·α + input·(1−α)`. (Mục 7, 8)
- [b0urbonn/python-handtrack (Retrolens FX)](https://github.com/b0urbonn/python-handtrack) — danh sách filter gần trùng FingerLens (Thermal, Glitch, Neon, Invert, Pixelate…), kích hoạt bằng 4 đầu ngón 2 tay.
- [FingerLens: Magic Gesture (Google Play)](https://play.google.com/store/apps/details?id=com.camera.filter.finger.magic.lens) — app sản phẩm tham khảo.

### 12.2. Hand landmark

- [MediaPipe Hand Landmarker — tổng quan](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker) và [hướng dẫn Android](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker/android) — bản đồ 21 điểm, `numHands`, LIVE_STREAM. (Mục 1)
- [google-ai-edge/mediapipe-samples — hand_landmarker/android](https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/hand_landmarker/android)
- Nội bộ: `Camera_X_Hand_Landmarker.md` (Công thức 1, 1.5, 3; mục 11.6 cử chỉ khung đã gỡ; mục 13 HandFrame/EffectScope).

### 12.3. Hình học & làm mượt

- [Atan2 — Wikipedia](https://en.wikipedia.org/wiki/Atan2) · Video: [Every Mathematician Should Learn This: atan2 Function](https://www.youtube.com/watch?v=VMYk9fqXz_4) (Mục 3.3)
- [Shoelace formula — Wikipedia](https://en.wikipedia.org/wiki/Shoelace_formula) (Mục 3.4)
- [Exponential smoothing — Wikipedia](https://en.wikipedia.org/wiki/Exponential_smoothing) (Mục 5.1)
- Video: [Freya Holmér — Lerp smoothing is broken](https://www.youtube.com/watch?v=LSNQuFEDOyQ) — vì sao `α` cố định phụ thuộc fps và dạng `1 − exp(−Δt/τ)`. (Mục 5.2)
- [1€ Filter — trang chính của Géry Casiez](https://gery.casiez.net/1euro/) · [mã nguồn tham chiếu casiez/OneEuroFilter](https://github.com/casiez/OneEuroFilter) · [bài báo (CHI 2012)](https://www.semanticscholar.org/paper/1-%E2%82%AC-filter:-a-simple-speed-based-low-pass-filter-in-Casiez-Roussel/9dc90eec1eaeee77e17c21642597b871e8e7a6c8) · [giải thích dễ đọc của Jaan Tollander](https://jaantollander.com/post/noise-filtering-using-one-euro-filter/) (Mục 5.3)

### 12.4. Vẽ trong vùng & phối cảnh

- [Android Developers — Draw what you see! … and clip the rest](https://medium.com/androiddevelopers/draw-what-you-see-and-clip-the-e11-out-of-the-rest-6df58c47873e) — `clipPath` tốn và không khử răng cưa; nên vẽ đúng hình dạng. (Mục 7.2)
- [Android — Hardware acceleration (bảng thao tác Canvas được hỗ trợ)](https://developer.android.com/develop/ui/views/graphics/hardware-accel) (Mục 7, 10.2)
- [Android `ColorMatrix`](https://developer.android.com/reference/android/graphics/ColorMatrix) (Mục 9.1)
- [Alpha compositing — Wikipedia](https://en.wikipedia.org/wiki/Alpha_compositing) (Mục 7.1)
- Video: [Computing Homography — First Principles of Computer Vision (Shree Nayar)](https://www.youtube.com/watch?v=l_qjO4cM74o) (Mục 8)

### 12.5. Xử lý ảnh

- Video: [How Blurs & Filters Work — Computerphile](https://www.youtube.com/watch?v=C_zFhWdM4ic) — tích chập, kernel. (Mục 9.3)
- Video: [Finding the Edges (Sobel Operator) — Computerphile](https://www.youtube.com/watch?v=uihBwtPIBxM) · [Sobel operator — Wikipedia](https://en.wikipedia.org/wiki/Sobel_operator) (Mục 9.3)
- [Luma (video) — Wikipedia](https://en.wikipedia.org/wiki/Luma_(video)) — hệ số 0.299/0.587/0.114. (Mục 9.1)
- [Turbo, An Improved Rainbow Colormap — Google Research](https://research.google/blog/turbo-an-improved-rainbow-colormap-for-visualization/) (Mục 9.2)
- [Glitch effect with RGB split](https://agatedragon.blog/2023/12/24/glitch-effect-with-rgb-split/) · Video: [Godot Shader Effect Breakdown — Glitch Effect](https://www.youtube.com/watch?v=mtyWgEURxNY) (Mục 9.5)

### 12.6. Shader & AGSL

- [AGSL — tổng quan](https://developer.android.com/develop/ui/views/graphics/agsl) · [Using AGSL in your app](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl) · [`RuntimeShader`](https://developer.android.com/reference/android/graphics/RuntimeShader) (Mục 10.3)
- [AGSL: Made in the Shade(r) — Android Developers (Chet Haase)](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a)
- [The Book of Shaders](https://thebookofshaders.com/) — học fragment shader từ đầu, cú pháp GLSL gần AGSL.
- Video: [Freya Holmér — Shader Basics, Blending & Textures (Shaders for Game Devs, phần 1)](https://www.youtube.com/watch?v=kfM-yu0iQBk) — giải thích uniform, sampling texture, blending.
- Video: [Having fun with AGSL Shaders in Jetpack Compose](https://www.youtube.com/watch?v=aHzFRPSo2s0)
- Podcast: [Android Developers Backstage #184 — Skia and AGSL](https://adbackstage.libsyn.com/episode-184-skia-and-agsl-shaders-of-things-to-come)
- Mẫu AGSL tham khảo: [drinkthestars/shady](https://github.com/drinkthestars/shady), [mejdi14/Android-AGSL-Shader-Playground](https://github.com/mejdi14/Android-AGSL-Shader-Playground)
