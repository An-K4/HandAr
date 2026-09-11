# Tối ưu dung lượng App & Tuân thủ 16 KB Page Size — HandAr

> Đúc kết từ quá trình điều tra + xử lý thực tế: app từ **90 MB** (build với tối ưu hoá bị tắt) xuống còn **~33.8 MB** (raw `.aab`, ~60.8 MB APK universal test), đồng thời xử lý dứt điểm cảnh báo bắt buộc **"Does not support 16 KB devices"** trước khi có thể phát hành lên Google Play.

---

## MỤC LỤC

1. [Bối cảnh: vì sao phải điều tra dung lượng](#1-bối-cảnh-vì-sao-phải-điều-tra-dung-lượng)
2. [Bước 1 — Kiểm kê resource để loại trừ nghi ngờ ban đầu](#2-bước-1--kiểm-kê-resource-để-loại-trừ-nghi-ngờ-ban-đầu)
3. [Bước 2 — Thủ phạm chính: `optimization { enable = false }`](#3-bước-2--thủ-phạm-chính-optimization--enable--false-)
4. [Bước 3 — Nén lại asset nặng (GIF/WAV)](#4-bước-3--nén-lại-asset-nặng-gifwav)
5. [Bước 4 — Yêu cầu bắt buộc: 16 KB Page Size Compliance](#5-bước-4--yêu-cầu-bắt-buộc-16-kb-page-size-compliance)
6. [Bước 5 — Thủ phạm 16 KB #1: MediaPipe `tasks-vision`](#6-bước-5--thủ-phạm-16-kb-1-mediapipe-tasks-vision)
7. [Bước 6 — Thủ phạm 16 KB #2: CameraX `camera-core`](#7-bước-6--thủ-phạm-16-kb-2-camerax-camera-core)
8. [Hiểu đúng "APK size" vs "Download Size"](#8-hiểu-đúng-apk-size-vs-download-size)
9. [Bảng tổng kết tiến trình](#9-bảng-tổng-kết-tiến-trình)
10. [Các hướng tối ưu còn lại (lợi ích giảm dần)](#10-các-hướng-tối-ưu-còn-lại-lợi-ích-giảm-dần)
11. [Checklist rút gọn cho lần audit dung lượng tiếp theo](#11-checklist-rút-gọn-cho-lần-audit-dung-lượng-tiếp-theo)

---

## 1. Bối cảnh: vì sao phải điều tra dung lượng

App build ra **90 MB** ở lần commit gần nhất — con số này bất thường đối với 1 app chỉ có: camera + nhận diện tay (MediaPipe) + vài hiệu ứng GIF/PNG + ghi video (MediaCodec/MediaMuxer) + phát video (ExoPlayer). Không có lý do rõ ràng để 1 app quy mô này nặng tới vậy — cần điều tra thay vì đoán.

**Nguyên tắc xuyên suốt quá trình điều tra:** luôn **đo trước khi kết luận**, đúng tinh thần đã áp dụng cho FPS/audio trước đây trong dự án — không sửa mù dựa trên cảm tính "chắc là do GIF nặng" hay "chắc do thư viện quá to".

---

## 2. Bước 1 — Kiểm kê resource để loại trừ nghi ngờ ban đầu

Liệt kê trực tiếp kích thước từng nhóm resource trong project:

| Nhóm | Kích thước |
|---|---|
| `res/drawable/` (GIF + PNG hiệu ứng) | 9.27 MB |
| `res/raw/` (file `.wav` âm thanh hiệu ứng) | 5.24 MB |
| `assets/hand_landmarker.task` (model MediaPipe) | 7.46 MB |
| **Tổng resource đã biết** | **~22 MB** |

**Kết luận bước này:** 90 MB − 22 MB ≈ **68 MB không giải thích được bằng resource** — nghĩa là nghi ngờ ban đầu ("chắc do GIF nặng") **sai hướng**. Vấn đề nằm ở tầng build, không nằm ở asset.

> 💡 **Bài học:** khi thấy dung lượng bất thường, kiểm kê resource trước để **loại trừ** nhanh giả thuyết dễ đoán nhất — nếu resource chỉ chiếm 1 phần nhỏ trong tổng, đừng lãng phí thời gian tối ưu asset trước khi tìm ra khối lớn thật sự.

---

## 3. Bước 2 — Thủ phạm chính: `optimization { enable = false }`

Trong `app/build.gradle.kts`, block `release` đang có:
```kotlin
buildTypes {
    release {
        optimization {
            enable = false   // ❌ tắt hẳn tối ưu hoá
        }
    }
}
```

`optimization { enable = ... }` là **DSL mới của AGP 9.3+**, thay thế hoàn toàn cặp `isMinifyEnabled`/`isShrinkResources` của DSL cũ — `enable = true` bật **đồng thời cả 2**: rút gọn code (R8/ProGuard) **và** cắt bỏ resource không dùng tới.

Với `enable = false`:
- **Không rút gọn code** — giữ nguyên 100% class từ mọi thư viện (CameraX, MediaPipe, Media3/ExoPlayer, Navigation, Coroutines...), kể cả phần không hề dùng tới.
- **Không cắt resource thừa.**
- **Không lọc bớt native lib theo ABI** — MediaPipe `tasks-vision` đóng gói sẵn file `.so` cho **cả 4 kiến trúc CPU** (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), mỗi bản native nặng hàng chục MB; không bật tối ưu hoá đồng nghĩa cả 4 bản đều bị nhét chung vào build dù máy thật chỉ cần đúng 1.

### Fix
```kotlin
buildTypes {
    release {
        optimization {
            enable = true
        }
    }
}
```

### Kết quả
**90 MB → ~53.7 MB** (giảm ~40%) — xác nhận đúng giả thuyết, không cần điều tra thêm giả thuyết nào khác.

> ⚠️ **Rủi ro cần lưu ý khi bật lại:** AGP 9 có mặc định nghiêm ngặt hơn — có thể yêu cầu khai báo rõ file keep-rule (`src/main/keepRules/*.keep`) cho các thư viện dùng reflection/JNI nặng (MediaPipe...). Hầu hết thư viện hiện đại tự đóng gói sẵn `consumer-rules.pro` nên R8 thường tự giữ đúng phần cần thiết — nhưng **bắt buộc phải test kỹ bản release sau khi bật** (không chỉ build thành công), vì lỗi R8 xoá nhầm class chỉ lộ ra lúc chạy thật, không lộ lúc biên dịch.

---

## 4. Bước 3 — Nén lại asset nặng (GIF/WAV)

Sau khi xử lý xong khối 68 MB "vô hình", quay lại đúng 22 MB resource đã kiểm kê ở Bước 1 — giờ đây **mới thực sự là khối lớn nhất còn lại** (`res/` = 21.4 MB trong bản build đã bật R8, lớn hơn cả `lib/`).

3 file nặng nhất bị nhắm tới:
```
sunny.gif               4.30 MB
banana_cat_crying.gif   2.86 MB
lightning.gif           1.49 MB
```

**Công cụ:** [ezgif.com/optimize](https://ezgif.com/optimize) — re-encode lại GIF, thường giảm 30-50% dung lượng mà không ảnh hưởng rõ rệt tới chất lượng nhìn trên màn hình điện thoại.

### Kết quả
- `res/`: 21.4 MB → **15.1 MB**
- Tổng `.aab` raw: ~53.7 MB → **~33.8 MB**

> 💡 **Thứ tự ưu tiên đúng:** xử lý cấu hình build (Bước 2) **trước** khi tối ưu asset (Bước 3) — vì lợi ích Bước 2 lớn hơn nhiều (giảm 40% tổng) so với Bước 3 (giảm thêm ~1/3 của phần còn lại). Tối ưu asset trước khi fix cấu hình build sẽ lãng phí công sức nén file trong khi phần lớn dung lượng thực ra đến từ chỗ khác.

---

## 5. Bước 4 — Yêu cầu bắt buộc: 16 KB Page Size Compliance

Trong lúc phân tích `.aab`/`.apk` bằng **Build → Analyze APK/Bundle...** của Android Studio, thấy cảnh báo:
```
⚠️ Does not support 16 KB devices
```
kèm chi tiết trên từng file `.so`: `4 KB LOAD section alignment, but 16 KB is required`.

### Đây không phải cảnh báo phụ — là yêu cầu bắt buộc của Google Play

Theo tài liệu chính thức Android: từ **1/11/2025**, mọi app mới/bản cập nhật nhắm **Android 15+ (`targetSdk` ≥ 35)** **bắt buộc** hỗ trợ page size 16 KB — nếu không, **Google Play sẽ từ chối cho phát hành**. Thời hạn gia hạn (tới 31/5/2026) cũng đã hết hiệu lực.

**Nguyên nhân kỹ thuật:** phần cứng Android hiện đại đang chuyển từ quản lý bộ nhớ theo trang (page) 4 KB sang 16 KB để tăng hiệu năng. Thư viện native (`.so`) biên dịch với giả định cứng "trang luôn 4 KB" sẽ không tương thích — đây là lỗi ở **tầng biên dịch của chính thư viện**, không phải thứ tự sửa được bằng code Kotlin/Java của app.

### Cách kiểm tra
**Build → Analyze APK/Bundle...** → mở file `.aab`/`.apk` vừa build → cột **Alignment** sẽ hiện cảnh báo trên đúng file `.so` không đạt chuẩn.

---

## 6. Bước 5 — Thủ phạm 16 KB #1: MediaPipe `tasks-vision`

File bị cảnh báo: `libmediapipe_tasks_vision_jni.so`.

**Nguyên nhân:** MediaPipe `tasks-vision` bản `0.10.14` (đang dùng) được biên dịch **trước khi** Google vá lỗi alignment.

**Bằng chứng:** release notes chính thức của MediaPipe (bản `v0.10.26`) ghi rõ:
> *"All the latest Android packages from Google Maven are now supporting the Android 16kb page size."*

### Fix
Trong `gradle/libs.versions.toml`:
```toml
mediapipeTasksVision = "0.10.26"   # hoặc bản mới hơn tại thời điểm làm
```

### Kết quả
Cảnh báo trên `libmediapipe_tasks_vision_jni.so` biến mất — cột **Alignment** đổi từ tam giác cảnh báo sang hiện `16 KB`.

> ⚠️ Sau khi nâng version bất kỳ thư viện lõi nào (đặc biệt MediaPipe — ảnh hưởng trực tiếp tới độ chính xác/hành vi model), **luôn chạy lại bộ test hiện có** (ở đây là Checklist B — Live preview) để xác nhận hành vi nhận diện tay không đổi, không chỉ tin vào việc build thành công.

---

## 7. Bước 6 — Thủ phạm 16 KB #2: CameraX `camera-core`

Sau khi fix MediaPipe, cảnh báo tổng **vẫn còn** — vì thủ phạm đã chuyển sang 1 thư viện khác: `libimage_processing_util_jni.so`.

**Đây không phải MediaPipe** — file này thuộc `androidx.camera:camera-core` (CameraX). Theo nhiều nguồn xác nhận (bao gồm báo cáo từ các dự án khác gặp đúng lỗi này): **CameraX bản `1.2.x`/`1.3.x` chưa hỗ trợ 16 KB, đã fix từ bản `1.4.0` trở lên**.

### Fix
```toml
camerax = "1.4.2"   # hoặc bản ổn định mới hơn, miễn ≥ 1.4.0
```

> ⚠️ **Lưu ý đi kèm khi nâng CameraX lên ≥1.4.0:** bản này bổ sung kiểm tra chặt chẽ hơn về khả năng truy cập camera — nếu code gọi `cameraProviderFuture.get()` mà không bọc `try/catch`, app có thể crash khi camera tạm thời không khả dụng (máy khác đang chiếm camera...). Dự án này **đã có sẵn** `try/catch` đúng chỗ quanh `cameraProvider.bindToLifecycle(...)` từ trước, nên rủi ro thấp — nhưng đây là điểm **bắt buộc kiểm tra** nếu project khác gặp lại tình huống tương tự mà chưa có try/catch.

### Kết quả
Sạch hoàn toàn — cả `libmediapipe_tasks_vision_jni.so` lẫn `libimage_processing_util_jni.so` đều hiện `16 KB`, dòng cảnh báo tổng `Does not support 16 KB devices` biến mất. Checklist B chạy lại vẫn Pass.

> 💡 **Bài học chung cho lỗi 16 KB (áp dụng cho mọi project khác, không riêng HandAr):** lỗi này hầu như luôn đến từ **thư viện bên thứ 3 đóng gói sẵn `.so`** (MediaPipe, CameraX, ML Kit, SQLite native, PDF renderer...), không phải code tự viết. Cách xử lý luôn giống nhau: xác định đúng thư viện sở hữu file `.so` bị cảnh báo (tên file thường gợi ý rõ, ví dụ `libimage_processing_util_jni.so` → tìm theo tên hàm/package liên quan) → tra changelog/GitHub issues của thư viện đó xem đã có bản fix chưa → nâng version. Nếu thư viện **chưa** có bản fix, không có cách nào tự vá được từ phía app — chỉ có thể chờ, tìm thư viện thay thế, hoặc xin gia hạn (nếu vẫn còn hiệu lực).

> ⚠️ **Cảnh báo bổ sung tìm được khi tra cứu (chưa gặp phải, nhưng cần biết):** có báo cáo cho thấy đôi khi **Google Play Console báo lỗi 16 KB dù APK Analyzer local báo sạch** — do khác biệt giữa cơ chế kiểm tra local và server (ví dụ kiểm tra thêm cả PT_GNU_RELRO alignment, không chỉ PT_LOAD). Vì vậy: Android Studio báo sạch là **điều kiện cần, không phải đủ** — nên xác nhận thật sự "sạch" bằng cách upload thử lên kênh Internal Testing của Play Console trước khi coi đây là đã xong hẳn.

---

## 8. Hiểu đúng "APK size" vs "Download Size"

Bảng trong Analyze APK/Bundle hiện 2 con số dễ gây nhầm lẫn, ví dụ: `APK size: 60.8 MB, Download Size: 33.7 MB`.

| | **APK size** | **Download Size** |
|---|---|---|
| Đo cái gì | Kích thước byte thật của chính file đang phân tích | Ước tính của Android Studio về lưu lượng mạng nếu phân phối qua Google Play |
| Phản ánh dung lượng cài trên máy? | **Có** — gần đúng nhất | **Không** |

**Trả lời câu hỏi "cài file này thì máy tốn bao nhiêu?"** — phụ thuộc cách cài:
- Cài trực tiếp đúng file `.apk` đang phân tích (adb install, Android Studio Run...) → dung lượng máy tốn **gần với con số "APK size"**, vì đây là file đầy đủ (cả 4 kiến trúc CPU), không qua bước tách nào.
- Phát hành qua Google Play bằng `.aab` → người dùng thật tải về **nhẹ hơn cả 2 con số này** — Play tự sinh 1 bản APK **chỉ chứa đúng 1 kiến trúc CPU** phù hợp với máy đó, loại bỏ hẳn 3 kiến trúc còn lại.

> 💡 Đây là lý do luôn build/phân phối bằng `.aab` cho bản release thật, chỉ dùng `.apk` universal để tự test cục bộ — số liệu 2 loại file này không thể so sánh trực tiếp như "cùng 1 thứ đo 2 lần".

---

## 9. Bảng tổng kết tiến trình

| Mốc | Dung lượng | Thay đổi |
|---|---|---|
| Ban đầu (phát hiện vấn đề) | 90 MB | — |
| Sau khi bật `optimization { enable = true }` | ~53.7 MB | ↓ ~40% |
| Sau khi nén lại GIF nặng (`sunny`, `banana_cat_crying`, `lightning`) | ~33.8 MB (`.aab` raw) | ↓ thêm ~37% |
| 16 KB compliance (MediaPipe `0.10.26` + CameraX `1.4.2`) | Không đổi dung lượng đáng kể | Chuyển từ **không thể phát hành** → **đủ điều kiện phát hành** |

---

## 10. Các hướng tối ưu còn lại (lợi ích giảm dần)

Đã xử lý 2 đòn bẩy lớn nhất (R8 + `.aab`). Các hướng còn lại, đánh đổi tăng dần:

1. **Giới hạn ABI hỗ trợ** (đánh đổi tương thích thiết bị):
```kotlin
ndk {
    abiFilters += "arm64-v8a"
}
```
Loại hẳn `x86`/`armeabi-v7a`/`x86_64` khỏi build — giảm thêm `lib/`, nhưng mất khả năng chạy trên máy dùng CPU khác (chủ yếu ảnh hưởng máy rất cũ hoặc emulator x86 — thường chấp nhận được với ứng dụng mới).
2. **`isDebuggable = false`** + dọn log không cần thiết ở bản release — tác động nhỏ, chỉ giảm nhẹ `dex/`.
3. Ngoài 2 điểm trên, phần dung lượng còn lại nằm ở **bản chất chức năng** (MediaPipe model AI + CameraX + ExoPlayer) — muốn giảm sâu hơn đồng nghĩa phải đánh đổi tính năng, không còn là "tối ưu cấu hình build" nữa.

**Kết luận:** mức ~33.8 MB (`.aab` raw, thực tế người dùng tải về còn nhẹ hơn nữa sau khi Play tách ABI) là mức hợp lý cho 1 app tích hợp AI nhận diện tay + camera + ghi/phát video. Tiếp tục ép xuống thêm nên cân nhắc kỹ đánh đổi trước khi làm.

---

## 11. Checklist rút gọn cho lần audit dung lượng tiếp theo

Khi dung lượng app bất thường trở lại (thêm thư viện mới, thêm hiệu ứng mới...), làm theo đúng thứ tự này thay vì đoán:

1. [ ] Kiểm kê resource (`res/`, `assets/`) — loại trừ nhanh giả thuyết "chắc do asset".
2. [ ] Kiểm tra `optimization { enable = ... }` (hoặc `isMinifyEnabled`/`isShrinkResources` ở DSL cũ) đang bật hay tắt cho `release`.
3. [ ] Build `.aab`, mở **Analyze Bundle**, đọc breakdown `res/lib/dex/assets` để biết khối nào đang lớn nhất **thật sự**, không đoán.
4. [ ] Nếu `lib/` lớn bất thường → kiểm tra có native lib nào bị cảnh báo 16 KB không (luôn tiện thể kiểm tra, không tốn thêm công).
5. [ ] Nếu asset (`res/raw`, `res/drawable`) lớn → nén lại bằng công cụ phù hợp từng loại (ezgif cho GIF, tương tự cho ảnh/âm thanh).
6. [ ] Sau mọi thay đổi cấu hình build (R8, version thư viện) → **luôn chạy lại bộ test hiện có** trước khi coi là xong, vì lỗi loại này thường chỉ lộ ra lúc chạy thật.
