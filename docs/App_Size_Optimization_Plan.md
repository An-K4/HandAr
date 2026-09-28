# Kế hoạch tối ưu dung lượng — giai đoạn 2

> Tiếp nối [App_Size_Optimization_16KB_Compliance.md](App_Size_Optimization_16KB_Compliance.md). Tài liệu cũ dừng ở mốc `.aab` ~33.8 MB; tài liệu này đo lại từ đầu và chốt các việc còn đáng làm.

## 0. Bối cảnh & quyết định đã chốt

| Câu hỏi | Quyết định |
|---|---|
| Kênh phát hành cho người dùng cuối | **Chỉ Google Play** (`.aab`) |
| Vai trò GitHub Release | Cho sếp/lead **xem dung lượng thật khi lên Play** và cài thử — không phát cho người dùng cuối |
| Ưu tiên | **Dung lượng tải về** trước; dung lượng sau cài giảm được thì tốt, không thì thôi |
| WAV → OGG | **Không làm** — ~3 MB không đáng để sửa `AudioUtils` và test lại toàn bộ âm thanh |
| PNG → WebP (`bg_*`) | **Đã làm** — 2.25 MB → 0.20 MB |

## 1. Mốc đo hiện tại (28/09/2026)

Đo bằng cách đọc trực tiếp nội dung file zip, trước khi đổi PNG → WebP:

| Thành phần | APK debug (CI hiện tại) | APK release (R8 bật) |
|---|---|---|
| **Tổng** | **56.9 MB** | **33.5 MB** |
| dex | 24.4 MB | 2.7 MB |
| `lib/arm64-v8a` (lưu không nén) | 13.7 MB | 13.7 MB |
| `res/` (WAV + ảnh) | 10.8 MB | 10.3 MB |
| `assets/hand_landmarker.task` | 5.8 MB | 5.6 MB |
| `resources.arsc` | 1.6 MB | 0.9 MB |

Phát hiện quan trọng:

- **MediaPipe `0.10.26` chỉ đóng gói `.so` cho `arm64-v8a`.** Thư mục `armeabi-v7a` chỉ có ~20 KB của CameraX → máy 32-bit cài được app nhưng gần như chắc chắn crash (`UnsatisfiedLinkError`) khi khởi tạo HandLandmarker. Tách APK theo ABI vì thế **không giảm được gì**.
- **Chênh lệch lớn nhất giữa bản CI và bản Play là dex** (24.4 MB debug vs 2.7 MB release) — tức con số trên GitHub Release hiện tại không phản ánh đúng Play.
- Trong `.aab`, `.so` được nén còn ~5.4 MB; Play cũng nén khi truyền tải, nên **dung lượng tải từ Play nhỏ hơn nhiều so với kích thước file APK**. Ước tính thô cho 1 máy arm64: ~18–20 MB (cần đo thật ở P1).

## 2. Các việc sẽ làm

### P1 — CI phát hành đúng "con số Play" (ưu tiên cao nhất)

**Mục tiêu:** mỗi lần push `main`, GitHub Release có (a) con số dung lượng tải từ Play ước tính theo cấu hình máy thật, và (b) một APK release cài được để xem thử.

**Thay đổi:**

1. `app/build.gradle.kts`
   - ~~Bỏ `armeabi-v7a`~~ → **giữ lại theo quyết định của mentor** (xem mục 5).
   - Thêm keep rule R8 cho MediaPipe, protobuf-lite và Flogger (`src/main/keepRules/rules.keep`) — thiếu chúng, bản release crash ngay khi khởi tạo HandLandmarker.
   - Thêm `signingConfig` cho `release` đọc từ biến môi trường; không có biến thì fallback sang debug keystore để build local vẫn cài được:
     ```kotlin
     signingConfigs {
         create("ci") {
             val ks = System.getenv("CI_KEYSTORE_PATH")
             if (ks != null) {
                 storeFile = file(ks)
                 storePassword = System.getenv("CI_KEYSTORE_PASSWORD")
                 keyAlias = System.getenv("CI_KEY_ALIAS")
                 keyPassword = System.getenv("CI_KEY_PASSWORD")
             }
         }
     }
     buildTypes {
         release {
             optimization { enable = true }
             signingConfig = if (System.getenv("CI_KEYSTORE_PATH") != null)
                 signingConfigs.getByName("ci") else signingConfigs.getByName("debug")
         }
     }
     ```
2. Keystore cho CI
   - Tạo **một keystore riêng cho CI** (`keytool -genkeypair ...`), mã hoá base64, lưu vào GitHub Secrets.
   - **Không dùng upload key của Play** trên GitHub — lộ secret CI không được ảnh hưởng tới việc phát hành Play.
   - Keystore cố định giúp các bản trên GitHub cài đè lên nhau được (hiện tại runner sinh debug keystore mới mỗi lần build, có thể khiến bản mới không cài đè lên bản cũ được).
3. `.github/workflows/release-debug.yml` → đổi tên thành `release.yml`
   - `./gradlew bundleRelease packageReleaseUniversalApk` (task có sẵn của AGP, sinh APK universal từ chính `.aab`, ký bằng keystore CI).
   - Tải `bundletool` từ `google/bundletool` releases (đang ghim `1.18.3`, trùng bản AGP dùng).
   - `bundletool build-apks` rồi `get-size total` hai lần: một lần với `device.json` (arm64, xxhdpi, vi-VN, SDK 34), một lần không lọc để lấy MIN/MAX mọi cấu hình.
   - Sinh bảng dung lượng vào mô tả Release (kèm commit SHA) và vào Job Summary của Actions.
   - Xoá asset cũ của release `latest` (ví dụ `magic-hand-debug.apk`) trước khi đính kèm `magic-hand-release.apk`.
   - Mô tả Release ghi rõ APK đính kèm là bản universal, nặng hơn bản Play.

**Kiểm thử bắt buộc:** cài bản release (R8 bật) lên máy thật, chạy lại Checklist B (live preview) + ghi hình + phát lại video. Lỗi R8 xoá nhầm class JNI/reflection chỉ lộ lúc chạy.

**Kết quả kỳ vọng:** con số trên GitHub khớp với con số Play Console hiển thị (±vài %), thay vì 56.9 MB như hiện tại.

### P2 — Dọn nhỏ (tuỳ chọn, làm kèm P1 nếu tiện)

| Việc | Lợi ích | Ghi chú |
|---|---|---|
| Bỏ `navigation-ui-ktx` nếu xác nhận không dùng `NavigationUI` | < 0.2 MB | Build + chạy qua mọi màn để chắc chắn |
| `androidResources { localeFilters += listOf("vi", "en") }` | Gần như 0 trên Play (Play đã tách theo ngôn ngữ); giảm nhẹ `resources.arsc` của APK universal | Khớp với `locales_config` hiện có |

### P3 — Dung lượng sau khi cài (không bắt buộc)

Phần "phình to sau thời gian dùng" ở các app khác gần như luôn đến từ **dữ liệu/cache**, không phải từ APK. Với HandAr, nguồn tiềm năng là video ghi hình và file tạm của `recording/`. Việc cần làm chỉ là rà soát:

- File tạm trong quá trình ghi/ghép có bị xoá sau khi xong/huỷ không.
- Video đã lưu vào thư viện (MediaStore) thì bản trong thư mục app có còn bị giữ lại không.
- Kiểm tra bằng *Settings → Apps → Magic Hand → Storage* sau ~10 lần ghi hình: mục "Data"/"Cache" có tăng liên tục không.

## 3. Các việc đã cân nhắc và loại bỏ

| Việc | Lý do loại |
|---|---|
| Tách APK theo ABI (`splits { abi }`) | Chỉ có 1 ABI thật sự có native lib → không giảm gì |
| `useLegacyPackaging = true` (nén `.so` trong APK) | Chỉ làm **file APK** nhỏ đi; Play đã nén khi truyền tải nên **dung lượng tải từ Play không đổi**, còn dung lượng cài trên máy tăng thêm ~13 MB. Ngoài ra sẽ làm con số trên GitHub lệch khỏi Play |
| WAV → OGG | ~3 MB, phải sửa `AudioUtils` + test lại toàn bộ âm thanh — không đáng |
| ReDex | Lợi ích thêm so với R8 rất ít, tăng độ phức tạp build |
| Play Asset Delivery | Model 7.5 MB + WAV ~4 MB quá nhỏ để đổi lấy độ phức tạp |
| Build lại MediaPipe chỉ với Hand Landmarker (Bazel) | Công sức rất lớn |
| Nén lại ảnh hiệu ứng | Đã là WebP, file lớn nhất 531 KB |

## 4. Tiến độ

| Mục | Trạng thái |
|---|---|
| Đo lại mốc bằng bản release | ✅ Xong (33.5 MB APK release) |
| PNG → WebP cho 6 ảnh `bg_*` | ✅ Xong, chờ kiểm tra hiển thị trên máy |
| P1 — CI + bundletool + keystore CI + keep rule R8 | 🔧 Code xong, chờ tạo keystore + secrets và chạy thử CI lần đầu |
| P2 — Dọn thư viện / `localeFilters` | ⏳ Tuỳ chọn |
| P3 — Rà soát cache/recording | ⏳ Tuỳ chọn |

### Kết quả đo sau P1 + WebP (local, bundletool 1.18.3)

| | Dung lượng |
|---|---|
| **Tải từ Play — máy arm64, xxhdpi, tiếng Việt** | **20.4 MB** |
| Tải từ Play — mọi cấu hình máy | 20.3 – 20.4 MB |
| APK universal (đính kèm GitHub Release) | 30.5 MB |
| `.aab` upload lên Play | 23.4 MB |

## 5. Về hỗ trợ máy 32-bit (`armeabi-v7a`)

**Quyết định:** giữ `armeabi-v7a` trong `abiFilters` theo ý mentor. Nếu phát sinh crash thật trên máy 32-bit (dự kiến `UnsatisfiedLinkError` khi mở camera), sẽ báo cáo kèm log và xem xét lại.

**Bối cảnh kỹ thuật:**

MediaPipe `tasks-vision` 0.10.14 còn đóng gói `armeabi-v7a`, nhưng 0.10.26 — bản bắt buộc phải lên để đạt chuẩn 16 KB của Play — chỉ còn `arm64-v8a`. Không có bản MediaPipe dựng sẵn nào vừa đạt 16 KB vừa chạy trên 32-bit. Tự build lại bằng Bazel hoặc viết lại pipeline trên TFLite là khả thi về kỹ thuật nhưng chi phí không tương xứng với nhóm máy (chủ yếu Android Go RAM ≤ 2 GB) vốn nhỏ, đang thu hẹp, và cũng khó chạy mượt camera + nhận diện tay thời gian thực. Danh sách model cụ thể bị loại: xem **Play Console → Device catalog** sau khi upload.
