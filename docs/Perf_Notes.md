# Ghi chép Hiệu năng — HandAr

> Kết quả điều tra hiện tượng "quay clip dài thì FPS tụt", thực hiện 09-10/09/2026.
> Mục đích của file này: **không phải đo lại từ đầu** khi vấn đề hiệu năng quay lại.

---

## TL;DR

1. **Không có lỗi code.** Khi máy nguội, pipeline chạy dư sức: `work` ≈ 17ms trên ngân sách 40ms, 0% frame quá hạn, GC không hề làm đứng thread ghi.
2. **Thủ phạm của mọi lần FPS thấp (17-21) là throttling nhiệt**, do test liên tục nhiều lượt. Đo trên máy nguội cho 24,3 fps ổn định suốt 90 giây.
3. **`Avg FPS` trong `VideoStatsLogger` gây hiểu nhầm** — nó là trung bình cộng dồn, nên clip càng dài con số càng thấp kể cả khi tốc độ tức thời không đổi. Đây là lý do ban đầu tưởng "clip dài thì tụt dần".
4. Đòn bẩy tối ưu lớn nhất **chưa dùng**: video đang được phóng to 2,44× từ nguồn 480×640 (xem Mục 5).

---

## 1. Công cụ đo: `RecordingPerfLogger`

`utils/RecordingPerfLogger.kt` — giữ lại trong repo, gỡ khi dự án dừng phát triển. Gỡ bằng cách xoá file + các chỗ đánh dấu `// TẠM` trong `CameraRecordFragment` (grep `TẠM`).

```powershell
adb logcat -c ; adb logcat -s RecPerf:I
```

Cứ 5 giây một dòng:

```
t= 15s  fps=24,4  work= 19,1ms  qua_han=  4%  cam=30,0fps  gc=15(734ms)  chan=0(0ms)  nhiet=0-BINH_THUONG
```

| Cột | Nghĩa | Dùng để loại trừ giả thuyết nào |
|---|---|---|
| `fps` | tốc độ ghi thực trong cửa sổ | hình dạng đường cong: giảm-rồi-phẳng = nhiệt; răng cưa = GC; phẳng = không có vấn đề |
| `work` | thời gian TB xử lý 1 frame (vẽ + encode) | ngân sách 40ms ở 25fps |
| `qua_han` | % frame vượt ngân sách | 0% = vòng lặp còn ngủ nhàn rỗi |
| `cam` | fps của analyzer camera | **cột phân biệt then chốt**: chậm toàn hệ thống (cam tụt theo) vs chậm riêng đường ghi (cam giữ nguyên) |
| `gc` | số lần GC + tổng ms | áp lực bộ nhớ; phần lớn GC của ART chạy đồng thời nên **không** đồng nghĩa mất fps |
| `chan` | số lần GC **làm đứng thread** + ms | đây mới là GC thực sự ăn vào fps |
| `nhiet` | `PowerManager.currentThermalStatus` | throttling |

Ngoài ra in một lần lúc frame camera đầu tiên: kích thước bitmap analyzer + KB mỗi frame.

## 2. Quy trình đo chuẩn

Không tuân thủ đủ các điều kiện này thì số liệu vô nghĩa — đã tự chứng minh bằng 2 lần đo hỏng.

1. **Máy nguội.** `adb shell dumpsys battery | Select-String temperature` (đơn vị 1/10 °C). Đợi về mức nền (~30-31°C với máy test). Ghi nhiệt độ trước và sau.
2. **`nhiet` phải giữ `0-BINH_THUONG` suốt cả lần đo.** Rời khỏi 0 → bỏ lần đo đó.
3. **Giữ tay trong khung hình liên tục**, cùng cử chỉ, cùng khoảng cách. Tay ra khỏi khung làm `work` tụt 3-5ms và làm hỏng phép so.
4. Cùng ánh sáng, cùng độ sáng màn hình, cùng độ dài clip.
5. **Đo ít nhất 2 lần cho mỗi cấu hình.** Xem Mục 4 — nhiễu giữa 2 lần đo cùng cấu hình có thể lớn hơn hiệu ứng đang muốn đo.

## 3. Số liệu nền (máy test, 09/2026)

Cấu hình: `stranger_things`, giữ nắm tay, 720×1560, 25fps mục tiêu.

| Clip | fps | `work` | `gc` /5s | `chan` | `nhiet` | ΔNhiệt |
|---|---|---|---|---|---|---|
| 90s | 24,25 | 8-21ms | 16 lần / 530ms | 0 | 0 suốt | — |
| 60s | 24,28 | 16,5ms TB | 12,7 lần / 718ms | 0 | 0 suốt | +0,8°C |

**Vì sao 24,3 chứ không phải 25:** vòng lặp tính giấc ngủ bằng `intervalMs - elapsedMs` với `elapsedMs` bị cắt cụt phần lẻ, cộng `Thread.sleep` luôn ngủ dôi ~0,5-1ms → chu kỳ thực 41,2ms thay vì 40ms. Không tích luỹ, không ảnh hưởng chất lượng video. Muốn đúng 25 thì đổi sang lịch theo **mốc tuyệt đối** (`nextFrameNs += intervalNs`) thay vì "ngủ phần còn lại".

**Áp lực GC:** `1200 KB × 2 bitmap × 30fps = 72 MB/giây`. Hai bitmap mỗi frame camera là `imageProxy.toBitmap()` + `Bitmap.createBitmap(..., matrix, true)` để xoay. `chan=0` ở mọi lần đo → GC chỉ tốn CPU/nhiệt, không chặn vòng lặp ghi.

## 4. Thí nghiệm GIF vs Sprite sheet — **kết luận: không đáng chuyển**

Chuyển `stranger_things_clock` (289×250, 16 frame) sang sprite sheet 4×4 @256px, đo cùng điều kiện:

| Lần | Asset | `work` TB | `gc` lần/5s | fps |
|---|---|---|---|---|
| 1 | GIF | 16,48 | 12,7 | 24,28 |
| 2 | Sprite sheet | 16,68 | 13,9 | 24,37 |
| 3 | GIF | 17,23 | 13,1 | 24,33 |

**Hai lần đo cùng cấu hình GIF chênh nhau 0,75ms — lớn hơn khoảng cách giữa hai cấu hình.** Đây là ngưỡng nhiễu của phép đo này, cần nhớ khi so bất cứ thứ gì khác.

Loại các cửa sổ nghi tay ra khỏi khung (`work` < 15ms giữa chuỗi ~17ms): GIF 17,19 và 17,59; sprite 16,98 → sprite nhanh hơn **~0,4ms/frame ≈ 2,4%**. Khớp với lý thuyết (hiệu ứng chiếm ~5% khối lượng vẽ, sprite tiết kiệm khoảng một nửa phần đó).

**Vì sao lợi ích nhỏ đến vậy** — phân bổ khối lượng mỗi frame:

| Việc | Pixel đích |
|---|---|
| `drawBitmap` ảnh camera, phóng 2,44× lên canvas 720×1560 | **~1.120.000** |
| Vẽ hiệu ứng tại cỡ bàn tay | ~65.000 |

Hiệu ứng chỉ là ~5% khối lượng. Tối ưu nó là tối ưu sai chỗ.

**Cái giá:** sprite sheet 1024×1024 chiếm **4 MB RAM thường trú**, mà `OverlayView` tạo 2 bộ visual (live + recording) → **8 MB** cho một trạng thái. GIF chỉ giữ vài buffer frame.

**Khi nào sprite sheet vẫn đáng dùng:** asset vốn được xuất ra ở dạng sprite sheet (Kenney, OpenGameArt), hoặc khi cần điều khiển chính xác thời điểm bắt đầu animation (`setActive` reset về frame 0 — GIF không cho làm điều này). `effect/SpriteSheetVisual.kt` giữ lại cho các trường hợp đó.

**Công thức RAM khi cân nhắc chuyển đổi:**

```
RAM = frameCount × frameSize² × 4 byte
```

Ngân sách ~4-6 MB mỗi trạng thái → 256px: tối đa ~24 frame; 192px: ~40; 128px: ~90.

Số liệu asset hiện có (dùng để quyết định có chuyển được không):

| GIF | Kích thước | Frame | Delay | Vòng lặp | Chuyển sprite? |
|---|---|---|---|---|---|
| `sunny` | 480×480 | ~181 | 70ms | 12,7s | ❌ 47 MB ở 256px — phải cắt vòng lặp trước |
| `banana_cat_crying` | 250×250 | ~155 | 70ms | 10,8s | ❌ tương tự |
| `lightning` | 480×480 | ~55 | 80ms | 4,4s | ⚠️ cần giảm frame |
| `happy_happy_happy_cat` | 104×112 | ~72 | 30ms | 2,2s | ⚠️ được nếu giảm còn ~36 frame |
| `stranger_things_clock` | 289×250 | ~16 | 70ms | 1,1s | ✅ vừa khít 4×4 — đã thử, không đáng |

Lệnh chuyển đổi (đã kiểm chứng):

```bash
ffmpeg -i input.gif -vf "scale=256:256:force_original_aspect_ratio=decrease,pad=256:256:(ow-iw)/2:(oh-ih)/2:color=0x00000000,tile=4x4" -frames:v 1 output_sheet.png
```

`pad` với alpha là bắt buộc khi GIF không vuông, thiếu nó các frame sẽ xô lệch lưới.

⚠️ **Sprite sheet và PNG hiệu ứng phải nằm trong `res/drawable-nodpi/`.** `BitmapFactory.decodeResource` tự phóng ảnh theo mật độ màn hình — file trong `res/drawable/` bị coi là mdpi và trên máy 3x sẽ decode thành ảnh gấp 3 mỗi chiều, tốn **gấp 9 lần** RAM.

## 5. Phát hiện quan trọng chưa xử lý: video đang được phóng 2,44 lần

```
bitmap analyzer:  480 × 640
video ghi ra:     720 × 1560
```

Hai tỉ lệ khác hẳn nhau (0,75 so với 0,46), mà code vẽ dùng `max(recW/w, recH/h)` — cắt hai bên rồi phóng to:

```
scale = max(720/480, 1560/640) = 2,44
vùng nguồn thực dùng ≈ 295 × 640 pixel  →  kéo lên 720 × 1560
```

Nghĩa là **video 720×1560 thực chất là ảnh ~295×640 phóng to 2,44 lần**, encode ở ~2,9 Mbps — phần lớn bitrate dùng để mã hoá pixel nội suy.

Nguồn gốc: `ImageAnalysis` không đặt độ phân giải nên CameraX chọn mặc định 640×480, và **luồng analyzer kiêm hai vai**: vừa là đầu vào MediaPipe, vừa là ảnh nền của video ghi ra.

## 6. Ba đòn bẩy chưa dùng, xếp theo tỉ lệ lợi ích/công sức

| # | Việc | Sửa ở đâu | Kỳ vọng |
|---|---|---|---|
| 1 | Hạ `targetShortSide` 720 → 480 | `CameraRecordFragment.computeRecordingSize` | Giảm ~55% pixel đích → nhẹ cả `drawBitmap` lẫn encoder, file nhỏ hơn. **Chất lượng nhìn không đổi** vì nguồn vốn không có chi tiết đó |
| 2 | `setOutputImageRotationEnabled(true)` cho `ImageAnalysis`, bỏ khối `Bitmap.createBitmap(..., matrix, true)` | `CameraRecordFragment.startCamera` | Rác giảm 72 → 36 MB/s. **Không tăng fps** (`chan=0`), nhưng giảm CPU nền → chậm sinh nhiệt. ⚠️ Nhớ bỏ hẳn phần xoay thủ công, nếu không ảnh bị xoay hai lần |
| 3 | `KEY_I_FRAME_INTERVAL` 1 → 3 | `wrapper/VideoEncoderWrapper` | Bớt keyframe đắt (hiện mỗi giây một cái), file nhỏ hơn |

Ngoài ra: 3 file PNG hiệu ứng (`egg`, `egg_cracked`, `stranger_things_monster`) hiện vẫn nằm trong `res/drawable/` nên đang bị phóng theo mật độ màn hình — chuyển sang `res/drawable-nodpi/` là việc 1 phút.

Cả ba đòn bẩy trên đều tác động vào phần chiếm ~95% khối lượng, khác với sprite sheet chỉ chạm vào 5%.

## 7. Nếu vấn đề hiệu năng quay lại: đo cái gì

`work` **không** phải đại lượng cần tối ưu — ngân sách 40ms mới dùng 17ms, và fps không đổi dù `work` giảm.

Vì thủ phạm thật là nhiệt, đại lượng phản ánh đúng vấn đề là **bao lâu thì máy bắt đầu throttle**:

> Quay liên tục 5-10 phút, ghi lại mốc thời gian `nhiet` nhảy lên `1-NHE` rồi `2-VUA`, và fps tại từng mốc. So hai cấu hình bằng "phút thứ mấy bắt đầu throttle".

## 8. Bài học phương pháp

- **Trung bình cộng dồn che mất diễn biến.** Phải đo tốc độ tức thời theo cửa sổ thời gian mới thấy được hình dạng đường cong.
- **Đừng tin thứ tự mình nhớ — tìm một trường trong log làm nhãn phân biệt tự động.** Khi so hai bản build, `Resolution` trong `VideoStats` đã tự tố cáo bản nào là bản nào (720×1400 = bản còn insets padding, 720×1560 = bản edge-to-edge), trong khi nhãn ghi tay thì sai.
- **Biết ngưỡng nhiễu trước khi kết luận.** Đo 2 lần cùng một cấu hình để biết sai số, rồi mới so giữa các cấu hình.
- **Kiểm soát biến, mỗi lần đổi đúng một thứ.** Phép so đầu tiên (GIF `happy_cat` với sprite `weather`) là sai thiết kế: hai asset khác nhau thì so vô nghĩa.
- **Tính trước khi tối ưu.** Một phép nhân đơn giản (1.120.000 pixel so với 65.000) đã đủ để biết sprite sheet chỉ chạm được 5% khối lượng — lẽ ra phải làm phép tính đó trước khi bỏ một buổi ra chuyển đổi asset.

## 9. Delegate CPU vs GPU (HandLandmarker) — đo real-time, 29/09/2026

> Công cụ: `utils/DelegatePerfLogger.kt` (xem `docs/DelegatePerf_Plan.md`). Khác `RecordingPerfLogger` (đo work time của vòng lặp ghi/encode), file này đo **latency suy luận MediaPipe** (`SystemClock.uptimeMillis() - result.timestampMs()`) và **fps kết quả thực nhận** — chạy suốt lúc camera + MediaPipe hoạt động (không chỉ lúc quay), log tag `DelegatePerf` mỗi 3 giây, kèm Toast. Gắn ở `CameraRecordFragment` (trước `detectAsync` và trong `results.collect`), đổi cùng lúc với `Delegate.CPU/GPU` ở `HandLandmarkerProvider` qua hằng `DELEGATE_PERF_LABEL`.

**Kết quả (cùng máy test, 3 lần đo — 2 lần CPU, 2 lần GPU — số liệu lặp lại nhất quán giữa các lần cùng cấu hình):**

| | `Delegate.CPU` | `Delegate.GPU` |
|---|---|---|
| fps ổn định (bỏ qua lúc khởi động) | ~22,0 | ~29,5–30,1 |
| latency trung bình | ~67–70ms | ~37–49ms |
| latency min–max | 41–166ms | 26–112ms |
| frame rớt / cửa sổ ~3s (~91 gửi) | ~22–24 (**~25–27%**) | 0–4, phần lớn 0–2 (**~0–4%**) |
| tổng phiên (gửi/nhận, fps TB cả phiên) | 553/404 → 21,3 fps | 574/539 → 27,5 fps · 951/880 → 27,2 fps |

**Kết luận:** `Delegate.GPU` mượt hơn rõ rệt trên máy test này — nhanh hơn ~35-40%, độ trễ thấp hơn ~40%, gần như không rớt frame so với CPU rớt tới 1/4 số frame. Camera đầu vào luôn giữ ổn định ~30fps (`gui` ≈ 90-92/3s) ở cả hai case — khác biệt hoàn toàn nằm ở tốc độ xử lý delegate, không phải do camera.

**Lưu ý khi đọc số liệu GPU:** cần **~6-10 giây khởi động** trước khi đạt tốc độ ổn định (build shader/EGL context) — 2-3 cửa sổ đầu (mỗi cửa sổ 3s) thường thấp hơn hẳn (fps từ 0 → ~17-26 → mới lên ~30), rồi ổn định luôn sau đó. Bỏ qua các cửa sổ này khi so sánh, đừng lấy trung bình cả phiên nếu phiên đo ngắn. Không thấy hiện tượng GPU init chậm tới hàng chục giây như cảnh báo cũ trong `Camera_X_Hand_Landmarker.md` mục 2.4 — nhưng đó là cảnh báo dựa trên máy khác, **nên đo lại trên các máy target khác** (đặc biệt máy cấu hình thấp) trước khi coi GPU là mặc định an toàn cho mọi thiết bị.

**Quyết định:** đã đổi `HandLandmarkerProvider.kt` sang `Delegate.GPU` làm mặc định dựa trên số liệu trên (29/09/2026).

**Ngưỡng fallback GPU→CPU đã chọn** (`HandLandmarkerProvider.createWithFallback`, xem `CameraLoading_Fallback_Plan.md`):
RAM máy `< 3GB` (`ActivityManager.isLowRamDevice()` hoặc `MemoryInfo().totalMem`) → dùng thẳng CPU, không thử GPU;
GPU init quá **5 giây** (`Executors.newSingleThreadExecutor().submit(...).get(5, TimeUnit.SECONDS)`) hoặc ném lỗi →
fallback CPU. Cả 2 ngưỡng chọn theo cảm tính (chưa đo trên máy RAM thấp thật để tinh chỉnh) — **đã xác nhận nhánh
timeout 5s hoạt động đúng trên máy test thật** (log `HandLandmarkerProvider W GPU init qua 5s, chuyen ve CPU cho
phan con lai cua phien`, 29/09/2026, xảy ra tự nhiên chứ không phải ép hạ ngưỡng để test) — nhánh RAM thấp vẫn
chỉ được xác nhận qua đọc code, chưa có máy thật để trigger.

### 9.1. `requiredNumHands = 2` tốn gần gấp đôi `requiredNumHands = 1` — trên cả CPU lẫn GPU

> Đo cùng máy, cùng ngày, so `fire_ball` (1 tay) với `black_hole` (2 tay), lặp lại trên cả 2 delegate:

| | `fire_ball` (1 tay) | `black_hole` (2 tay) | Tỉ lệ chậm đi |
|---|---|---|---|
| **CPU** — fps / latency TB / rớt | ~19,8 / ~76ms / ~34% | ~10,9 / ~119ms / ~60% | ~1,8x |
| **GPU** — fps / latency TB / rớt | ~26,8 / ~57ms / ~11% | ~12,85 / ~105ms / ~57% | ~2,1x |

**Kết luận:** tỉ lệ chậm đi (~1,8-2,1 lần) gần như giống nhau dù dùng CPU hay GPU — đây là chi phí tính
toán vốn có khi MediaPipe phải theo dõi 2 tay (chạy lại model landmark cho từng tay + palm-detector kém
ổn định hơn khi có 2 tay), **không phải do pin yếu/nhiệt** (nếu là nhiệt thì tỉ lệ sẽ trôi dần theo thời
gian test chứ không giữ nguyên qua nhiều lần đo tách biệt). Ứng dụng thực tế: mọi hiệu ứng 2 tay
(`black_hole`, `dragon_ball`, `gojo`, `lightning`) sẽ luôn phản hồi chậm/rớt frame nhiều hơn rõ rệt so với
hiệu ứng 1 tay trên cùng máy, kể cả sau khi đã tối ưu delegate — cần cân nhắc khi thiết kế/thêm effect 2 tay
mới (tránh gesture 2 tay yêu cầu phản xạ quá nhanh).

**Đã thử CPU cho hố đen với kỳ vọng "ổn định hơn" — sai:** CPU (~10,9 fps / ~119ms) còn chậm hơn GPU
(~12,85 fps / ~105ms) ở đúng effect 2 tay này. GPU vẫn là lựa chọn tốt hơn cho mọi trường hợp đã đo được
tới nay, kể cả effect 2 tay — quyết định giữ `Delegate.GPU` làm mặc định ở mục 9 vẫn đúng.


### 9.2. Đo tổng thể lúc quay video (29/09/2026) — ghi hình OK, nhưng detect tay bị tranh tài nguyên rõ rệt

> Gắn cùng lúc cả 3 công cụ (`DelegatePerfLogger`, `RecordingPerfLogger`, `VideoStatsLogger`/`logRecordingStats`,
> tắt hết Toast, chỉ giữ Log) để so trong 1 lần quay ~30s, `Delegate.GPU`, effect 1 tay. Sau khi đo xong đã
> gỡ hết các chỗ gọi trong `CameraRecordFragment` (xem `AGENTS.md` mục 5) — file utils vẫn còn, đo lại theo
> đúng vị trí gắn cũ nếu cần.

**Vòng lặp ghi hình (`RecPerf`) dư sức, không phải điểm nghẽn:** `work` (vẽ overlay + encode) chỉ 11-16ms
trên ngân sách 40ms (mục tiêu 25fps), `qua_han` gần như 0% sau ~5s khởi động. Nhiệt độ giữ nguyên
`0-BINH_THUONG` suốt clip — không throttling.

**fps ghi hình thật KHÔNG đạt đúng 25fps mục tiêu, dù dư tài nguyên:** `RecPerf` tổng kết 744 khung/30,8s =
24,15fps; `VideoStatsLogger` đọc thẳng từ file mp4 ra cũng khớp: 24,18fps (743 khung/30,73s, đọc bằng
`MediaMetadataRetriever`). Vì bản thân `work` luôn thấp hơn ngân sách rất nhiều, nguyên nhân không phải xử
lý nặng mà là **`Thread.sleep(sleepMs)` trong `startRecordingFrameLoop` luôn trễ hơn 1 chút so với yêu cầu**
(hành vi bình thường của scheduler Android, không phải real-time OS) — cộng dồn qua ~750 lần ngủ lệch ra
đúng khoảng 1 giây trên 30 giây clip. GC không chặn thread (`chan=0` suốt clip) nhưng có áp lực liên tục
(12-15 lần GC non-blocking/cửa sổ 5s, ~700ms GC time/cửa sổ) — có thể góp phần vào độ trễ sleep. **Nếu cần
đúng ≥25fps thật sự**, phải sửa cách hẹn giờ (bù trừ drift tích luỹ thay vì sleep cố định mỗi vòng, hoặc đặt
target cao hơn 25 một chút để bù hao hụt) — chưa làm, vì 24,15-24,5fps hiện tại đã được coi là đạt yêu cầu.

**Phát hiện quan trọng hơn — detect tay (GPU) bị chậm hẳn khi quay video, không liên quan tới fps video:**
`DelegatePerf` trong lúc quay chỉ đạt trung bình **14-15fps** (so với ~27-30fps đo lúc chỉ xem preview không
quay ở mục 9), latency trung bình 94-100ms (đỉnh 330ms), và tỉ lệ rớt khung (`rot`) xấp xỉ **50%**
(gửi ~90, nhận ~45/cửa sổ 3s) — trong khi `cam` (fps camera analyzer thô, độc lập với detect) vẫn ổn định
25-30fps. Nguyên nhân hợp lý nhất: khi ghi hình có thêm 1 thread riêng vừa vẽ overlay lên Surface vừa encode
H.264 vừa mix audio, tranh chấp CPU/GPU với pipeline `HandLandmarker` chạy song song. Vì vòng ghi dùng giá
trị `latestHandResult`/`latestCameraBitmap` mới nhất có sẵn (không đợi detect mới, xem kiến trúc mục 1
`AGENTS.md`) nên số khung video vẫn ra đều, nhưng **hiệu ứng AR trong video có thể lặp lại cùng 1 vị trí tay
qua nhiều khung liên tiếp rồi mới nhảy** — dễ nhận ra khi tay di chuyển nhanh trong lúc quay. Chưa có hướng
xử lý cụ thể — cần cân nhắc riêng (ví dụ ưu tiên CPU core cho detect thread, hoặc giảm tải bước vẽ/encode)
nếu sau này người dùng phàn nàn hiệu ứng giật trong video xuất ra.
