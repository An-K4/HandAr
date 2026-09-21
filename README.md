# HandAr

Ứng dụng Android quay video với **hiệu ứng AR theo cử chỉ bàn tay**: camera nhận diện 21 điểm mốc bàn tay bằng MediaPipe, vẽ GIF/ảnh hiệu ứng ngay trên lòng bàn tay, phát tiếng hiệu ứng tương ứng, và ghi lại toàn bộ (hình + tiếng hiệu ứng) thành file MP4 xem/chia sẻ được ngay trong app.

---

## Mục lục

- [Tính năng](#tính-năng)
- [Công nghệ](#công-nghệ)
- [Yêu cầu & cách chạy](#yêu-cầu--cách-chạy)
- [Kiến trúc](#kiến-trúc)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Danh sách hiệu ứng](#danh-sách-hiệu-ứng)
- [Thêm một hiệu ứng mới](#thêm-một-hiệu-ứng-mới)
- [Hiệu năng](#hiệu-năng)
- [Dung lượng & tuân thủ 16 KB page size](#dung-lượng--tuân-thủ-16-kb-page-size)
- [Test](#test)
- [Tài liệu trong `docs/`](#tài-liệu-trong-docs)
- [Quy ước & bài học quan trọng](#quy-ước--bài-học-quan-trọng)

---

## Tính năng

- **Nhận diện cử chỉ tay thời gian thực** (1 tay hoặc 2 tay) bằng MediaPipe Hand Landmarker.
- **Hiệu ứng AR** vẽ đè lên preview camera: GIF động, ảnh PNG tĩnh, sprite sheet, và hiệu ứng vẽ bằng code (canvas thuần, ghi nét theo ngón trỏ, ghép 2 quả cầu khi 2 tay chạm nhau...) — bám theo tâm lòng bàn tay hoặc điểm mốc khác (đầu ngón trỏ, điểm nhón, trung điểm 2 tay), tự co giãn theo khoảng cách tương ứng.
- **Nền & nhạc nền riêng cho từng hiệu ứng** (màu/ảnh/GIF nền thay cho camera thật, nhạc nền được trộn thẳng vào video) — dùng cho các hiệu ứng như vẽ canvas, đổi nền theo cử chỉ.
- **Âm thanh hiệu ứng** phát ra loa khi live, đồng thời được trộn thẳng vào track audio của video ghi ra (không dùng mic).
- **Ghi video MP4** bằng `MediaCodec` + `MediaMuxer`, độ phân giải và bitrate tính động theo khung hình.
- **Xem lại ngay sau khi quay**: Xong / Xoá / Chia sẻ (qua `FileProvider`).
- **Thư viện video**: danh sách video đã quay kèm thumbnail, thời lượng, ngày quay; phát lại bằng ExoPlayer (Media3).
- **Da ngôn ngữ** (vi/en, chuyển bằng `AppCompatDelegate.setApplicationLocales`) + màn onboarding/khảo sát khi mở app lần đầu.

## Công nghệ

| Hạng mục | Lựa chọn |
|---|---|
| Ngôn ngữ | Kotlin |
| UI | View/XML + View Binding (không dùng Compose) |
| Điều hướng | Fragments + Navigation Component (`nav_graph.xml`) + **Safe Args** (`androidx.navigation.safeargs.kotlin`) |
| Camera | CameraX 1.4.2 (`Preview` + `ImageAnalysis`) |
| Nhận diện tay | MediaPipe Tasks Vision 0.10.26 (`hand_landmarker.task`, `Delegate.CPU`, `LIVE_STREAM`) |
| Vẽ hiệu ứng | Android Canvas 2D (`OverlayView`) |
| Ghi hình | `MediaCodec` (H.264 + AAC) + `MediaMuxer` |
| Phát video | Media3 / ExoPlayer 1.8.0 |
| Build | AGP 9.3.2, Gradle 9.5.0 |

## Yêu cầu & cách chạy

- **minSdk 28** (Android 9) · **targetSdk / compileSdk 37** · Java 11
- Quyền cần cấp: **CAMERA** (app **không** dùng mic — xem [Kiến trúc](#kiến-trúc))
- Video được lưu tại `getExternalFilesDir(Environment.DIRECTORY_MOVIES)` của app

```bash
# Build & cài bản debug
./gradlew :app:installDebug

# Build kiểm tra nhanh trước khi commit
./gradlew :app:assembleDebug :app:lintDebug

# Build bản phát hành (đã bật R8 + shrinkResources)
./gradlew :app:bundleRelease
```

> `local.properties` (đường dẫn SDK) là file máy cá nhân, không commit.

## Kiến trúc

### Pipeline xử lý mỗi khung hình

```text
[Camera sensor]
      │  ImageProxy
[CameraX ImageAnalysis]  ── thread camera ──> MediaPipe HandLandmarker (detectAsync)
      │                                              │ SharedFlow<result>
      │  @Volatile latestCameraBitmap                ▼
      └──────────────────────────────>  [Chọn EffectState theo cử chỉ]
                                                     │
                    ┌────────────────────────────────┴───────────────────┐
                    ▼                                                    ▼
        [OverlayView.onDraw]  (live preview)         [recordingFrameThread ~25fps]
                                                        lockHardwareCanvas
                                                        → vẽ camera + hiệu ứng
                                                        → drainVideoEncoder
                                                              │
                                              [VideoEncoderWrapper] ─┐
                                              [EffectAudioClock]     ├─> MuxerCoordinator ─> .mp4
                                              [AudioMixer → AudioEncoderWrapper] ─┘
```

Hai điểm kiến trúc quan trọng (đã chốt qua nhiều vòng debug, xem `docs/`):

1. **Tách thread hiển thị khỏi thread ghi hình** (theo bài học từ `google/grafika`): thread camera chỉ đọc ảnh + chạy AI và ghi vào biến `@Volatile`; thread ghi hình chạy theo nhịp cố định, luôn lấy dữ liệu *mới nhất* và cho phép rớt frame cũ.
2. **Không ghi mic.** Track audio của video được sinh hoàn toàn từ `EffectAudioClock` + `AudioMixer` (PCM của tiếng hiệu ứng). Nhờ đó: hết bug mic thu lại tiếng loa, không cần quyền `RECORD_AUDIO`, và PTS của audio/video cùng neo vào đúng một mốc (frame đầu tiên thực sự được encode).

### Mô hình dữ liệu hiệu ứng

```text
EffectDefinition            1 hiệu ứng người dùng chọn được ở màn danh sách
├── id / displayName / thumbnailRes
├── requiredNumHands        1 hoặc 2 — quyết định cấu hình HandLandmarker
├── states: List<EffectState>   KHÔNG giới hạn số trạng thái
│   └── EffectState = gesture + asset? + soundRes? + background? riêng + sizeSource + anchorSource
├── background?             nền mặc định (Solid/Image/Animated) khi không state nào có nền riêng
├── bgm?                    nhạc nền (resId + gainPercent) được TRỘN VÀO VIDEO khi ghi
└── stateMode               Momentary (mặc định, mất tay là tắt) | Latched (giữ nguyên state)

EffectAsset (sealed class)  StaticImage | AnimatedGif | SpriteSheet | Procedural (vẽ bằng code)
AnchorSource / SizeSource   quyết định tâm/kích thước vẽ: PalmCenter+PalmRadius (mặc định),
                            PinchMidpoint+PinchDistance, IndexFingertip, TwoHandMidpoint+TwoHandDistance
EffectVisual (interface)    3 nhóm: ảnh có sẵn (Static/AnimatedGif/SpriteSheet)Visual,
                            vẽ canvas thủ công (StrokeVisual, GojoVisual...), nền riêng (BackgroundRenderer)
GestureRecognizer (fun interface)  Gestures.singleHandPalmOpen, twoHandsHeart, ...
```

`OverlayView` chỉ làm việc với interface `EffectVisual`, chọn trạng thái bằng
`states.indexOfFirst { it.gesture.recognize(hands) }` — **thứ tự khai báo trong `states` chính là độ ưu tiên**.

⚠️ `CameraRecordFragment` tự chạy **một bộ nhận diện cử chỉ thứ hai, độc lập** với `OverlayView`
để quyết định phát/trộn âm thanh nào (có debounce 200ms) — sửa gesture logic ở 1 nơi không tự ảnh
hưởng nơi còn lại, xem `docs/Code_Walkthrough.md` mục 12.

`HandLandmarkerProvider` là singleton cache `HandLandmarker` theo `requiredNumHands` và phân phối kết quả
qua `SharedFlow` (không dùng `setResultListener` trỏ thẳng vào Fragment — tránh leak khi vào/ra màn camera).

### Luồng màn hình

```text
splashFragment (start, delay ~1s)
      │ popUpTo+inclusive
      ▼
languageFragment (chọn vi/en)
      │ popUpTo+inclusive
      ▼
onboarding1Fragment ──> onboarding2Fragment ──> onboarding3Fragment
                                                        │ popUpTo+inclusive
                                                        ▼
                                                  surveyFragment
                                                        │ popUpTo+inclusive
                                                        ▼
effectListFragment  ──chọn effectId──>  cameraRecordFragment
        │                                              │ Stop
        │ nút Thư viện                                 ▼
        ▼                                       recordedPreviewFragment  (Xong / Xoá / Chia sẻ)
videoListFragment ──chọn video──> videoPlayerFragment
```

Cụm màn mở app lần đầu (splash/language/onboarding/survey) chạy **một chiều**, mỗi bước đều
`popUpTo` + `popUpToInclusive="true"` về điểm đầu cụm đó — không back ngược lại được. Tương tự,
action `cameraRecord → recordedPreview` khai `popUpTo="@id/cameraRecordFragment"` +
`popUpToInclusive="true"` để giải phóng camera ngay lúc điều hướng và không back ngược lại màn quay.

Tham số giữa các màn (`effectId`, `videoPath`) được truyền bằng **Safe Args**, ví dụ:

```kotlin
val action = EffectListFragmentDirections.actionEffectListToCameraRecord(effect.id)
findNavController().navigate(action)
```

Mọi tham số phải được khai `<argument>` trong `nav_graph.xml` thì các class `*Directions` / `*Args` mới được sinh ra.

## Cấu trúc thư mục

```text
app/src/main/java/com/example/handar/
├── MainActivity.kt              NavHost thuần; release HandLandmarkerProvider ở onDestroy
├── effect/
│   ├── EffectRepository.kt      List<EffectDefinition> phẳng, lắp từ khai trực tiếp + catalog/
│   ├── HandLandmarkerProvider.kt
│   ├── model/                   EffectDefinition / EffectState / EffectAsset / EffectBackground /
│   │                            EffectBgm / AnchorSource / SizeSource / StateMode
│   ├── gesture/                 Gesture.kt (object Gestures) · GestureRecognizer · GestureUtils
│   ├── visual/                  EffectVisual + factory createEffectVisual() · HandFrame · EffectScope · ProceduralVisual
│   │   ├── image/                StaticImageVisual · AnimatedGifVisual · SpriteSheetVisual
│   │   └── canvas/
│   │       ├── drawcanvas/       StrokeModel/StrokeVisual/ClearOnActivate/SkeletonOnlyVisual/HandSkeleton
│   │       └── gojo/             GojoModel/GojoVisual
│   ├── background/              BackgroundRenderer + Solid/Image/AnimatedBackgroundRenderer
│   └── catalog/                 factory cho hiệu ứng phức tạp (CameraShutter, TestEffectBackground,
│                                CanvasDraw, Earth, BlackHole, Gojo)
├── recording/                    toàn bộ pipeline ghi hình, không đụng vào nếu không bắt buộc (xem cuối file)
│   ├── VideoRecorder.kt         điều phối ghi hình (nhạc trưởng)
│   ├── MuxerCoordinator.kt      chờ đủ 2 track mới muxer.start()
│   ├── AudioMixer.kt · EffectAudioClock.kt
│   └── VideoEncoderWrapper.kt · AudioEncoderWrapper.kt   bọc MediaCodec
├── audio/                        BgmPlayer (MediaPlayer) · SoundEffectPlayer (SoundPool) — cả hai chỉ
│                                phát ra loa, TÁCH RIÊNG khỏi track ghi hình (xem `AudioMixer`)
├── ui/
│   ├── splash/ · language/ · onboarding/ · survey/   luồng mở app lần đầu, 1 chiều
│   ├── effectlist/   EffectListFragment, EffectAdapter
│   ├── camera/       CameraRecordFragment          (camera + AI + ghi hình)
│   ├── preview/      RecordedPreviewFragment       (xem lại ngay sau khi quay)
│   ├── videolist/    VideoListFragment, VideoAdapter, VideoRepository
│   └── player/       VideoPlayerFragment           (ExoPlayer)
├── OverlayView.kt                canvas vẽ hiệu ứng cho cả live lẫn frame ghi hình — file trung tâm
└── utils/                        AudioUtils (đọc PCM từ .wav), FormatUtils, ViewInsetsUtils (edge-to-edge),
                                RecordingPerfLogger, VideoStatsLogger

app/src/main/res/
├── drawable/  ảnh & GIF hiệu ứng      raw/  file .wav tiếng hiệu ứng
├── navigation/ nav_graph.xml    values/  · values-vi/   xml/file_paths.xml · xml/locales_config.xml
app/src/main/assets/hand_landmarker.task   model MediaPipe (~7.5 MB)
docs/                                      tài liệu thiết kế & vận hành
```

## Danh sách hiệu ứng

Khai báo trong `effect/EffectRepository.kt` (một phần lấy từ `effect/catalog/`) — hiện có **16 hiệu ứng**:

| id | Tên hiển thị | Số tay | Các trạng thái (cử chỉ → asset) |
|---|---|---|---|
| `cat_meme_1` | Meme mèo 1 | 1 | xoè tay → mèo cười · nắm tay → chuối khóc |
| `egg` | Trứng | 1 | xoè tay → trứng · nắm tay → trứng nứt |
| `weather` | Thời tiết | 1 | xoè tay → nắng · nắm tay → sét |
| `stranger_things` | Stranger things | 1 | xoè tay → quái vật · nắm tay → đồng hồ |
| `black_background_with_monster` | Quái vật bóng đêm với tiếng đồng hồ kêu | 1 | giống `stranger_things` nhưng có **nền GIF riêng** (`background`) + **nhạc nền** (`bgm`) trộn vào video |
| `rock_on_ily` | Rock on / I love you | 1 | rock on · I-love-you |
| `camera_shutter` | Chụp ảnh | 2 | 5 cử chỉ (OK, peace, like, rock on, call) đều ra cùng 1 GIF chụp ảnh |
| `cat_meme_2` | Meme mèo 2 | 1 | peace → hello · chỉ tay → you · call → call |
| `mood_meter` | Đo tâm trạng | 1 | like · nắm tay → sad · xoè tay → neutral |
| `absolute_cinema_two_hand` | Absolute cinema | 2 | 2 tay xoè · 2 tay nắm |
| `heart_or_cross` | Trái tim và dấu X | 2 | trái tim · dấu X |
| `test_effect_background` | Thay đổi nền | 1 | 4 cử chỉ đổi nền (Solid/Image/Animated) khác nhau, dùng `StateMode.Latched` — giữ nền khi mất tay |
| `canvas_draw` | Vẽ canvas | 1 | chỉ tay → vẽ nét theo đầu ngón trỏ · nắm tay → xoá nét · khác → chỉ hiện khung xương tay (nền đen, dùng `EffectAsset.Procedural` + `EffectScope`) |
| `earth` | Trái đất | 1 | có tay → quả đất to/nhỏ theo khoảng nhón ngón tay cái-trỏ (`AnchorSource.PinchMidpoint`) |
| `black_hole` | Hố đen | 2 | có tay → hố đen to/nhỏ theo khoảng cách 2 tay (`AnchorSource.TwoHandMidpoint`) |
| `gojo` | Gojo | 2 | chỉ tay → quả cầu ở đầu ngón trỏ mỗi tay, chạm 2 đầu ngón trỏ → phát animation sáp nhập rồi hiện quả cầu tím |

Giải thích chi tiết cách từng loại hoạt động (Procedural, AnchorSource/SizeSource, EffectScope...):
xem `docs/Code_Walkthrough.md` mục 1 và 3.

## Thêm một hiệu ứng mới

1. Chuẩn bị asset theo đúng `docs/Asset_Format_Guidelines.md`:
   - **PNG**: có alpha, cạnh dài 512–768px, vuông/gần vuông.
   - **GIF**: ≤ 256×256, vuông, ≤ 20 frame, ≤ 500 KB, nền trong suốt, loop mượt.
   - **Sprite sheet**: lưới chia hết, ghi rõ `columns` / `rows` / `frameCount` / `frameDurationMs`.
   - **WAV**: **PCM 16-bit / Mono / 44100 Hz**, dài 0.3–3s (sai thông số không crash, chỉ ra tiếng rè/lệch tốc độ).
   - Đặt sprite sheet và PNG hiệu ứng trong `res/drawable-nodpi/` để tránh bị phóng theo mật độ màn hình.
2. Thêm cử chỉ mới vào `object Gestures` nếu cần (thiết kế các công thức loại trừ lẫn nhau rõ ràng).
3. Thêm một `EffectDefinition` vào `EffectRepository.all`, khai đúng `requiredNumHands`.
4. Chạy lại Checklist **B** (live preview) và **D** (chống regression) trong `docs/Test_Checklist.md`.

## Hiệu năng

Số liệu đo trên máy test (09/2026, máy nguội, clip 60–90s, 720×1560 @ mục tiêu 25fps):

| Chỉ số | Giá trị |
|---|---|
| FPS ghi hình thực | ~24.3 (ổn định suốt 90s) |
| Thời gian xử lý mỗi frame (`work`) | ~17 ms / ngân sách 40 ms |
| Frame quá hạn | 0% |
| GC làm đứng thread ghi | 0 lần |
| Bitrate | ~2.9 Mbps |

- **Nguyên nhân duy nhất khiến FPS tụt xuống 17–21 là throttling nhiệt**, không phải lỗi code. Muốn so sánh hiệu năng thì phải để máy nguội giữa các lần đo.
- `Avg FPS` trong `VideoStatsLogger` là **trung bình cộng dồn** — clip càng dài con số càng thấp, đừng đọc nó như tốc độ tức thời.
- Công cụ đo: `utils/RecordingPerfLogger.kt` → `adb logcat -s RecPerf:I` (in 5s/dòng), và `utils/VideoStatsLogger.kt` (chỉ chạy bản Debug).
- Đòn bẩy tối ưu **còn chưa dùng** (xem `docs/Perf_Notes.md` mục 5–6): video đang được phóng 2.44× từ nguồn analyzer 480×640; hạ `targetShortSide` 720 → 480, bật `setOutputImageRotationEnabled(true)`, tăng `KEY_I_FRAME_INTERVAL` 1 → 3.

## Dung lượng & tuân thủ 16 KB page size

| Mốc | Dung lượng |
|---|---|
| Ban đầu (tắt tối ưu hoá) | 90 MB |
| Bật `optimization { enable = true }` (R8 + shrinkResources) | ~53.7 MB |
| Sau khi thay/nén asset (`res/drawable` 15.1 → 3.74 MB, dù số hiệu ứng tăng 2 → 10) | **~33.8 MB** (`.aab` raw) |

App đã đạt **16 KB page size compliance** (bắt buộc để phát hành lên Google Play với `targetSdk ≥ 35`)
nhờ nâng MediaPipe lên `0.10.26` và CameraX lên `1.4.2`. Kiểm tra bằng **Build → Analyze APK/Bundle** → cột *Alignment*.

## Test

App được kiểm thử chủ yếu bằng **checklist thủ công** — `docs/Test_Checklist.md`:

| Nhóm | Nội dung |
|---|---|
| A | Quyền & khởi động (chỉ hỏi quyền Camera, không hỏi mic) |
| B | Live preview: hiệu ứng đúng vị trí, đúng tiếng, co giãn theo khoảng cách |
| C | Ghi hình luồng cơ bản |
| D | **Chống regression** cho 6 bug đã fix (mất trigger, UI đơ khi Stop, frame đầu đơ, tiếng chèn đôi, GIF chồng GIF, mất mirror) |
| E | Số liệu & hiệu năng qua `VideoStatsLogger` |
| F | Edge case & độ bền |
| G | Điều hướng giữa các màn (kể cả 3 trạng thái của quyền camera) |
| H | **Vòng đời Fragment & rò rỉ tài nguyên** — nhóm bug chỉ lộ ra sau nhiều lần vào/ra màn |

Chạy đầy đủ A–H sau mỗi phase, trên ít nhất 2 máy (1 máy mới + 1 máy yếu).
Khuyến nghị bật LeakCanary ở bản debug cho nhóm H.

## Tài liệu trong `docs/`

| File | Nội dung |
|---|---|
| `Camera_X_Hand_Landmarker.md` | Tài liệu gốc & đầy đủ nhất: 21 điểm mốc bàn tay, toàn bộ công thức hình học, CameraX, Canvas, kiến trúc mix audio/video, refactor hiệu năng, nhận diện cử chỉ 2 tay — kèm danh sách **cạm bẫy** đã gặp thật |
| `HandAr_Refactor_Plan.md` | Kế hoạch Phase 0–5 của pipeline ghi hình (resolution, bitrate, bỏ mic, tách thread, PTS baseline) |
| `HandAr_Plan.md` | Kế hoạch Phase A–N gộp thành một mạch: **Phần I** (A–F) đưa app từ 1 màn thành 5 màn + thiết kế `EffectDefinition` & nav graph; **Phần II** (G–N) nền theo hiệu ứng/state, nhạc nền, an toàn dữ liệu khi quay, hiệu ứng vẽ bằng canvas, và đợt cấu trúc lại package sắp tới |
| `Design_App_HandAr.md` | Tài liệu thiết kế sản phẩm: app tham khảo, danh sách màn hình còn thiếu, 10 hiệu ứng đề xuất |
| `Fragment_Review_Checklist.md` | Checklist tự soát mỗi khi thêm/sửa Fragment: bảng cấp phát ↔ giải phóng, 3 câu hỏi cho mỗi khối code, thứ tự trong `onDestroyView` |
| `Asset_Format_Guidelines.md` | Quy chuẩn PNG / GIF / sprite sheet / WAV, mỗi quy tắc gắn với dòng code sinh ra nó |
| `Perf_Notes.md` | Kết quả điều tra hiệu năng, quy trình đo chuẩn, thí nghiệm GIF vs sprite sheet |
| `App_Size_Optimization_16KB_Compliance.md` | Hành trình 90 MB → 33.8 MB và cách xử lý cảnh báo 16 KB |
| `Test_Checklist.md` | Kịch bản test thủ công A–H |
| `Code_Walkthrough.md` | Giải thích code chi tiết từng file/hàm + sơ đồ quan hệ giữa các file trong package `effect/`, `recording/`, `ui/camera/` — đọc khi cần hiểu đoạn code cụ thể làm gì thay vì chỉ biết kiến trúc tổng quát |

## Quy ước & bài học quan trọng

- **Không đụng vào logic bên trong pipeline ghi hình** (toàn bộ package `recording/`: `VideoRecorder`, `AudioMixer`, `EffectAudioClock`, `MuxerCoordinator`, `VideoEncoderWrapper`, `AudioEncoderWrapper`) — đã ổn định qua nhiều vòng debug. Nếu buộc phải sửa, đối chiếu `HandAr_Refactor_Plan.md` + `Camera_X_Hand_Landmarker.md` trước.
- **Đo trước khi kết luận.** Số `KEY_FRAME_RATE` / `KEY_BIT_RATE` khai cho `MediaCodec` chỉ là mục tiêu, không phải cam kết. Biết ngưỡng nhiễu của phép đo (đo 2 lần cùng cấu hình) trước khi so hai cấu hình.
- **Fragment có hai vòng đời.** Nơi tạo và nơi huỷ tài nguyên phải đối xứng; dùng `viewLifecycleOwner` chứ không dùng `this`/`lifecycleScope`; mọi callback đến muộn phải chốt cửa `_binding ?: return`.
- **Nav graph quyết định vòng đời**, không phải bản thân code Fragment — xem `popUpTo` trước khi suy luận.
- **Đặt tên trong nav graph**: destination camelCase khớp tên class; action `action_<từ>_to_<đến>`; id View snake_case; `app:argType` **luôn viết thường** (`string`, không phải `String`).
- **Khi refactor: đừng đọc, hãy diff.** Ba bug ở Phase B đều build xanh và chạy được, chỉ lộ ra khi so từng dòng với bản gốc.
- `RecordingPerfLogger` và các đoạn đánh dấu `// TẠM` trong `CameraRecordFragment` là công cụ tạm — gỡ khi dự án dừng phát triển.
