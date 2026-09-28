# AGENTS.md — Ngữ cảnh nhanh cho AI agent

> **Cập nhật lần cuối tại commit `9fb0b06`**. **Note cho agent:** sau khi repo có thêm
> commit mới liên quan tới cấu trúc code, hiệu ứng, hoặc luồng ghi hình/âm thanh — hãy cập nhật lại
> nội dung file này (và dòng commit hash phía trên) cho khớp, đừng để nó lỗi thời âm thầm.

> Đọc file này trước khi làm bất kỳ việc gì trong repo. Mục tiêu: 80% ngữ cảnh chỉ trong ~2 phút đọc.
> Chi tiết đầy đủ luôn nằm ở `docs/*.md` — file này chỉ tóm tắt + trỏ đường.

## 1. App này là gì

**HandAr** — app Android quay video với hiệu ứng AR theo cử chỉ bàn tay:
camera → MediaPipe nhận diện 21 điểm mốc tay → vẽ GIF/ảnh hiệu ứng đè lên lòng bàn tay
→ phát âm thanh hiệu ứng → ghi lại tất cả (hình + tiếng hiệu ứng, **không mic**) thành file MP4.

Ngôn ngữ giao tiếp trong repo (README, docs, commit) là **tiếng Việt**.

Tên hiển thị của app là **Magic Hand** (`app_name` trong `strings.xml`, cũng là tên bản build trên GitHub Release);
**HandAr** là tên repo/package. Đừng "sửa cho thống nhất".

## 2. Stack

Kotlin · View/XML + ViewBinding (không Compose) · Fragments + Navigation Component + Safe Args ·
CameraX 1.4.2 · MediaPipe Tasks Vision 0.10.26 (`hand_landmarker.task`) · Canvas 2D (`OverlayView`) ·
`MediaCodec` + `MediaMuxer` (ghi hình) · Media3/ExoPlayer 1.8.0 (phát lại) · AGP 9.3.2, Gradle 9.5.0 ·
minSdk 28, target/compileSdk 37, Java 11.

Lệnh hay dùng:
```bash
./gradlew :app:installDebug
./gradlew :app:assembleDebug :app:lintDebug   # chạy trước khi commit
./gradlew :app:bundleRelease
```

CI: `.github/workflows/release.yml` — mỗi lần push lên `main`, GitHub Actions (JDK 17 temurin)
chạy `./gradlew bundleRelease packageReleaseUniversalApk`, ước tính dung lượng tải từ Play bằng `bundletool get-size`
(ghi vào mô tả Release) rồi đẩy APK universal release lên GitHub Release tag `latest` (tên "Magic Hand Latest Build").
Bản release ký bằng keystore riêng của CI (secrets `CI_KEYSTORE_BASE64`, `CI_KEYSTORE_PASSWORD`, `CI_KEY_ALIAS`,
`CI_KEY_PASSWORD`) — không phải upload key của Play; build local không có biến môi trường thì ký bằng debug keystore. Không chạy lint/test trong CI, vẫn phải tự chạy `assembleDebug lintDebug` trước khi commit.

## 3. Kiến trúc cốt lõi (bắt buộc hiểu trước khi sửa)

1. **Tách thread hiển thị khỏi thread ghi hình.** Thread camera chỉ đọc ảnh + chạy AI, ghi kết
   quả vào biến `@Volatile`. Thread ghi hình (`recordingFrameThread`, ~25fps) luôn lấy dữ liệu
   *mới nhất*, được phép rớt frame cũ. Đây là bài học từ `google/grafika`.
2. **Không ghi mic.** Track audio của video sinh hoàn toàn từ `EffectAudioClock` + `AudioMixer`
   (PCM tiếng hiệu ứng) — không dùng `RECORD_AUDIO`. PTS audio/video cùng neo vào một mốc
   (frame đầu tiên thực sự được encode).

Pipeline một khung hình: Camera → CameraX ImageAnalysis → MediaPipe HandLandmarker (SharedFlow)
→ chọn `EffectState` theo cử chỉ → vừa vẽ live (`OverlayView.onDraw`) vừa vẽ vào frame ghi
(`lockHardwareCanvas` → `VideoEncoderWrapper` + `EffectAudioClock`/`AudioMixer`/`AudioEncoderWrapper`
→ `MuxerCoordinator` → `.mp4`).

Mô hình dữ liệu hiệu ứng (nằm trong `effect/model/`): `EffectDefinition` (id, requiredNumHands,
danh sách `EffectState`, `background?`, `bgm?`, `stateMode`) · `EffectState` (gesture + asset? +
soundRes? + `background?` riêng + `sizeSource`/`anchorSource`) · `EffectAsset` (sealed:
StaticImage/AnimatedGif/SpriteSheet/**Procedural**) · `EffectBackground` (Solid/Image/Animated) ·
`EffectBgm` (resId + gainPercent) · `AnchorSource`/`SizeSource` (quyết định tâm/kích thước vẽ —
mặc định PalmCenter/PalmRadius, có thêm PinchMidpoint/IndexFingertip/TwoHandMidpoint/TwoWristMidpoint và
PinchDistance/TwoHandDistance cho hiệu ứng đặc biệt) · `StateMode` (Momentary mặc định, hoặc
Latched — giữ nguyên state khi tay biến mất, dùng cho hiệu ứng đổi nền). `EffectVisual` (interface
trong `effect/visual/`, factory `createEffectVisual()`) tách theo 3 nhóm impl: ảnh có sẵn
(`effect/visual/image/`), vẽ canvas thủ công (`effect/visual/canvas/{drawcanvas,gojo,fireball,magicshield,lightning,dragonball}/`), và nền
riêng (`effect/background/`). `GestureRecognizer` (fun interface trong `effect/gesture/`, công
thức nằm trong `object Gestures`).

⚠️ **Hai bộ nhận diện gesture chạy độc lập trên cùng 1 kết quả MediaPipe**: `OverlayView`
(`resolveMatchedIndex`, chọn VẼ GÌ, tôn trọng `StateMode`, không debounce) và
`CameraRecordFragment.handleGesture()` (chọn PHÁT TIẾNG GÌ, có debounce 200ms). Sửa 1 bên không
tự sửa bên kia — xem `Code_Walkthrough.md` mục 12 để biết chi tiết + các bẫy tương tự khác.

Luồng màn hình đầy đủ (đổi thứ tự từ commit `24d7626`/`b1a3cf9`, xem lại nếu nhớ nhầm thứ tự cũ):
`splashFragment` (delay 5s, thanh loading chạy song song) → `welcomeFragment` → `onboarding1Fragment`
(có nút Skip nhảy thẳng sang `survey1Fragment`) → `onboarding2Fragment` → `onboarding3Fragment` →
`survey1Fragment` → `survey2Fragment` (khảo sát 2 bước, đã có nội dung thật, chưa lưu lựa chọn — xem mục 5) →
`permissionFragment` (2 switch xin quyền Camera / Thông báo + nút bắt đầu) →
`effectListFragment` → (chọn effect) → `effectPreviewFragment` (xem trước + nút Create; Create → camera
dùng `popUpTo` chính màn preview inclusive nên back ở camera vẫn về `effectListFragment`) →
`cameraRecordFragment` → (Stop, popUpTo inclusive) →
`recordedPreviewFragment` (nút Save duy nhất; Save không còn thoát thẳng ra ngoài nữa — Save
điều hướng sang `shareFragment`, `popUpTo` chính màn này inclusive; back/nút Thoát qua `ConfirmDialog`:
nút Thoát trong dialog xoá file rồi pop, nút Save trong dialog thì chạy đúng hàm Save) →
`shareFragment` (xem lại video dạng card thu gọn/toàn màn hình + icon chia sẻ MXH — 4 nút gọi `shareVideoToSocialApp()`,
xem đoạn `ShareFragment` bên dưới; nút Trang chủ → `popBackStack(effectListFragment,
inclusive=false)`; nút Thử lại → `actionShareToCameraRecord(effectId)` với `popUpTo` chính
`shareFragment` inclusive — tạo camera **mới**, không quay lại camera cũ; back hệ thống: đang
fullscreen thì thu nhỏ trước, đang ở card thì về thẳng Trang chủ); từ `cameraRecordFragment`, nút Effect mở
`effectPickerFragment` (lưới chọn mẫu, top bar riêng, không có bottom nav) → tick: effect khác →
`effectPreviewFragment` mới (`popUpTo` camera inclusive; camera mới chỉ được tạo khi bấm Create), cùng effect →
chỉ pop về camera cũ; back = huỷ ; `effectListFragment` ↔ `videoListFragment` →
`videoPlayerFragment`. Mỗi cụm màn one-shot (splash/welcome/onboarding/survey/permission) đều `popUpTo` +
`popUpToInclusive=true` để không back ngược lại được — `languageFragment` KHÔNG còn nằm trong cụm
one-shot này nữa (xem đoạn `settingsFragment` bên dưới). Riêng `effectListFragment` ↔
`videoListFragment` đi qua bottom nav (tab Home/Collection, `MainActivity.setupBottomNavTabs`)
dùng `popUpTo(effectListFragment, saveState=true)` + `launchSingleTop`/`restoreState` — KHÁC
với pattern popUpTo-inclusive one-shot ở trên, xem mục 5. Tham số giữa các màn dùng **Safe
Args** (`*Directions`/`*Args` sinh ra từ `<argument>` trong `nav_graph.xml`).

Ngoài các lối vào camera ở trên, nút camera giữa bottom nav (`badge_container`, gắn ở `MainActivity`)
navigate thẳng `cameraRecordFragment` với `effectId=""` (không qua action nào, không popUpTo) — camera
**không effect**: `currentEffect == null`, chỉ quay video thường, cột nút Action bị ẩn. Muốn có effect thì
bấm nút Effect → `effectPickerFragment` như bình thường. `CameraRecordFragment` dùng
`EffectRepository.findByIdOrNull` (không phải `findById`) chính vì trường hợp này.

⚠️ **Bất biến “camera luôn nằm ngay trên `effectListFragment` trong back stack”** được giữ xuyên
suốt mọi lối vào camera (Create, tick effect khác ở picker, Thử lại ở `shareFragment`) — mỗi lối đều
`popUpTo` gỡ sạch mọi thứ giữa `effectListFragment` và camera mới trước khi tạo. Đừng thêm đường
vào camera nào không tuân theo quy tắc này mà không kiểm tra lại `EffectPickerFragment`'s
`action_effectPicker_to_effectPreview` (comment trong code cảnh báo popUpTo này sẽ “im lặng không
làm gì” nếu giả định camera nằm dưới bị phá vỡ).

`ShareFragment` có nút Facebook/Instagram/TikTok/YouTube, mỗi nút gọi `shareVideoToSocialApp()`
(utils/SocialShare.kt) — ACTION_SEND đi kèm FileProvider URI, không dùng SDK/App ID riêng
của Meta/TikTok. Giới hạn đã biết trước, không phải bug: không app nào prefill được caption qua
Intent; Instagram không mở được Story composer qua đường này. App đích chưa cài thì tự mở Play Store.
Note từ user: "mặc dù lý thuyết là vậy nhưng trên thực tế nó đã hoạt động chính xác những gì tôi cần.
facebook tự mở bài đăng với video, instagram ở chplay, tiktok mở bottom sheet cho share dạng tin nhắn/video,
youtube mở trình edit video của nó trước khi đăng."

`settingsFragment` mở từ icon hamburger ở `view_top_bar.xml` (top bar chung của `MainActivity`,
`btn_open_settings`) — có sẵn ở mọi màn nằm trong `destinationsWithMainChrome`, không phải 1 bước
trong luồng mở app lần đầu. Hiện chỉ mục **Ngôn ngữ** có logic điều hướng thật (`action_settings_to_language`
→ `languageFragment`, layout 2 dòng chọn cờ vi/en dạng radio, KHÔNG còn ở trong luồng onboarding một chiều —
vào/ra bằng back thường, không `popUpTo`); các mục còn lại (Đánh giá, Chia sẻ app, Góp ý, Giới thiệu, Chính
sách riêng tư) mới chỉ có UI, chưa gắn logic — xem docstring trong `SettingsFragment.kt`.

Màn camera có nút Action (`btn_action`) mở `GestureGuideDialog` (dialog lưới 2 cột liệt kê từng cử chỉ
+ tên hiển thị của effect đang chọn, dùng `GestureAdapter`/`item_gesture.xml`); map cử chỉ → tên/icon nằm ở
`effect/gesture/GestureDisplay.kt` (`gestureDisplayMap`). **Icon hiện dùng tạm `ic_action` cho mọi cử chỉ**
(chưa có bộ icon riêng từng cử chỉ) — không phải bug.

## 4. Cấu trúc thư mục

```text
app/src/main/java/com/example/handar/
├── MainActivity.kt        NavHost + quản lý chrome (top bar/bottom nav): ẩn/hiện theo destination,
│                          chuyển tab Home↔Collection (popUpTo+saveState, xem mục 5), nút camera giữa bottom nav mở camera không effect (effectId=""),
│                          đổi icon theo tab đang ở; release HandLandmarkerProvider ở onDestroy
├── effect/
│   ├── EffectRepository.kt      danh sách phẳng List<EffectDefinition>, lắp hoàn toàn từ catalog/ (10 effect);
│   │                            có findByName(query) lọc theo tên (contains, ignoreCase) cho search real-time;
│   │                            findById (crash nếu sai id) và findByIdOrNull (dùng cho camera không effect)
│   ├── HandLandmarkerProvider.kt  singleton cache HandLandmarker theo numHands, phát SharedFlow
│   ├── model/                   EffectDefinition/EffectState/EffectAsset/EffectBackground/EffectBgm/
│   │                            AnchorSource/SizeSource/StateMode — kiểu dữ liệu thuần, không logic vẽ
│   ├── gesture/                 Gesture.kt (object Gestures), GestureRecognizer, GestureUtils (công thức hình học)
│   ├── visual/                  EffectVisual, HandFrame (hệ toạ độ dùng chung), EffectScope (share state
│   │   │                        giữa các state cùng effect), ProceduralVisual
│   │   ├── image/                StaticImageVisual, AnimatedGifVisual, SpriteSheetVisual, OneShotGifVisual
│   │   │                         (ảnh động chạy 1 lần rồi ẩn hẳn, dùng cho sóng âm của Quái vật)
│   │   └── canvas/
│   │       ├── drawcanvas/       StrokeModel/StrokeVisual/ClearOnActivate/SkeletonOnlyVisual/HandSkeleton (vẽ tay)
│   │       ├── gojo/             GojoModel/GojoVisual (composition: chứa 1 AnimatedGifVisual con + gojo_merge oneShot)
│   │       ├── fireball/         FireBallBurstVisual (burst oneShot rồi tự chuyển sang big loop)
│   │       ├── magicshield/      ShieldHideVisual (thu nhỏ theo thời gian bằng code, dùng chung asset lúc hiện)
│   │       ├── lightning/        LightningVisual (buffer-render 1 lần, vẽ nhiều bản xoay theo từng ngón đang duỗi)
│   │       └── dragonball/       KamehamehaVisual (buffer-render, to hơn + xoáy nhanh hơn state 1 tay)
│   ├── background/              BackgroundRenderer + Solid/Image/AnimatedBackgroundRenderer
│   └── catalog/                 mỗi file 1 hàm factory trả EffectDefinition, đủ 10/10 hiệu ứng
│                                (FireBallEffect, MagicShieldEffect, LightningEffect, DragonBallEffect,
│                                 GojoEffect, MonsterEffect, RoomTeleportEffect, CanvasDrawEffect,
│                                 EarthEffect, BlackHoleEffect)
├── recording/             ⚠️ KHÔNG ĐỤNG nếu không bắt buộc (xem mục 5)
│                          VideoRecorder, MuxerCoordinator, AudioMixer, EffectAudioClock,
│                          VideoEncoderWrapper, AudioEncoderWrapper
├── audio/                 BgmPlayer (MediaPlayer, phát loa), SoundEffectPlayer (SoundPool, phát loa) —
│                          CẢ HAI đều chỉ phát ra loa, KHÔNG liên quan track ghi hình (xem AudioMixer)
├── ui/
│   ├── splash|onboarding|survey|welcome|permission/   luồng mở app 1 lần (thứ tự: splash → welcome →
│   │                    onboarding1-3 → survey → permission), mỗi cụm tự popUpTo+inclusive
│   │                    (permission/: PermissionFragment, 2 switch xin quyền Camera + Thông báo;
│   │                     onboarding1/: có nút Skip nhảy thẳng sang survey)
│   ├── language/        LanguageFragment — KHÔNG còn trong luồng mở app 1 lần, chỉ mở từ settings/
│                        (2 dòng chọn cờ vi/en dạng radio, `AppCompatDelegate.setApplicationLocales`)
│   ├── settings/        SettingsFragment — mở từ icon hamburger ở view_top_bar.xml, mục Ngôn ngữ là
│                        mục duy nhất có logic (nav sang language/), còn lại mới đổ UI (xem mục 3)
│   ├── effectlist|effectpreview|camera|recordedpreview|share|videolist|player/   luồng chính, 1 package / màn hình
│   │                    (effectpreview/: màn xem trước effect + nút Create, cửa vào camera duy nhất;
│   │                     share/: ShareFragment, mở từ recordedpreview.save() HOẶC từ menu ⋮ của
│   │                     videoPlayer — xem trước video + 4 icon MXH gọi shareVideoToSocialApp(); nút
│   │                     Trang chủ luôn có, nút Thử lại chỉ hiện khi fromRecordedPreview=true;
│   │                     player/: VideoPlayerFragment, menu ⋮ ở top bar: Đổi tên (RenameDialog),
│   │                     Xoá (ConfirmDialog), Chia sẻ (mở shareFragment, fromRecordedPreview=false))
│   ├── camera/          CameraRecordFragment + GestureGuideDialog/GestureAdapter (dialog hướng dẫn cử
│   │                    chỉ mở từ btn_action, xem mục 3)
│   ├── effectpicker/    màn chọn effect mở từ nút Effect của camera (EffectPickerFragment + EffectPickerAdapter)
│   └── widget/          view/decoration dùng chung: GridSpacingItemDecoration, CurvedNavBackgroundView, RoundedOutline,
│                        ConfirmDialog (dialog xác nhận dùng chung, xem `dialog_confirm.xml`),
│                        VideoSeekBarController (đồng bộ 1 SeekBar + 2 nhãn thời gian với 1 ExoPlayer, tách
│                        từ recordedpreview để dùng lại ở share/), VideoThumbnailView (ảnh thumbnail +
│                        nút expand tùy chọn, dùng bởi `item_video.xml`), RenameDialog (dialog đổi tên
│                        video, mở từ menu ⋮ của videoPlayerFragment, xem `dialog_rename.xml`)
├── OverlayView.kt         canvas vẽ hiệu ứng, dùng cho cả live lẫn frame ghi hình — file trung tâm
└── utils/                 AudioUtils (đọc PCM từ .wav), FormatUtils, ViewInsetsUtils (edge-to-edge),
                           RecordingPerfLogger (TẠM), VideoStatsLogger
.github/workflows/release.yml   CI: push main → bundleRelease + ước tính dung lượng Play → APK release lên GitHub Release tag `latest`
app/src/main/res/drawable/  ảnh UI, thumbnail, và các ảnh hiệu ứng cũ (.webp) | drawable-nodpi/  ảnh + nền của các hiệu ứng
                             đã chuẩn bị lại (Trái Đất, Hố đen, Quái vật, Dịch chuyển phòng) | raw/  .wav tiếng hiệu ứng + nhạc nền
app/src/main/res/font/      Noto Serif (Regular 400 + Bold 700, gom trong family `noto_serif.xml`)
app/src/main/res/values/type.xml   15 `TextAppearance.HandAr.*` (slot chữ M3) — gắn vào theme ở `values/themes.xml`
app/src/main/res/values-vi/, res/xml/locales_config.xml   đa ngôn ngữ (vi mặc định, có en) — chọn ở
                             languageFragment (mở từ settingsFragment, không còn trong luồng mở app lần đầu)
app/src/main/res/navigation/nav_graph.xml
app/src/main/assets/hand_landmarker.task   (~7.5 MB, model MediaPipe)
app/src/main/assets/licenses/OFL-NotoSerif.txt   giấy phép font (SIL OFL 1.1)
docs/                        tài liệu thiết kế & vận hành — xem mục 6
.github/workflows/release.yml   CI: push main → bundleRelease + ước tính dung lượng Play → APK release lên GitHub Release tag `latest`
```

**10 hiệu ứng** hiện có trong `EffectRepository.all`, khớp HOÀN TOÀN `Design_App_HandAr.md` mục 4
(10/10 — xem mục 5 tài liệu đó để biết asset/ghi chú code từng hiệu ứng), tất cả nằm trong `effect/catalog/`
(không còn hiệu ứng nào khai trực tiếp trong `EffectRepository.kt`). 4 hiệu ứng thuần test cũ
(`rock_on_ily`, `camera_shutter`, `absolute_cinema_two_hand`, `heart_or_cross`) đã bị gỡ hẳn, thay
bằng Cầu lửa/Vòng khiên/Tia sét/Dragon Ball — 8 cử chỉ từng gắn với 4 hiệu ứng đó (`singleHandOkSign`,
`singleHandThumbsUp`, `singleHandCall`, `singleHandRockOn`, `singleHandILoveYou`, `bothHandsFist`,
`twoHandsHeart`, `twoHandsCrossedFingers`) vẫn còn nguyên trong `Gestures` nhưng "mồ côi" — không
`EffectState` nào gán tới nữa, xem docstring đầu `EffectRepository.kt` và cách test tạm ở
`Test_Checklist.md` mục I.10. Danh sách đầy đủ + cử chỉ từng hiệu ứng: xem README mục "Danh sách
hiệu ứng", giải thích cách hoạt động từng loại: xem `Code_Walkthrough.md` mục 1 và 3.

## 5. Quy ước & bẫy đã biết — đọc kỹ trước khi sửa code

- **Không đụng logic bên trong `recording/`** (VideoRecorder, AudioMixer, EffectAudioClock,
  MuxerCoordinator, VideoEncoderWrapper, AudioEncoderWrapper) — đã ổn định qua nhiều vòng debug.
  Bắt buộc sửa thì đối chiếu `HandAr_Refactor_Plan.md` + `Camera_X_Hand_Landmarker.md` trước.
  Thay đổi hợp lệ vào `AudioMixer` (nếu có) chỉ nên là cộng thêm nguồn PCM vào `mix()`, không
  đổi chữ ký hàm cũ, không đụng `effectClock`/`totalAudioSamples`/thời điểm gọi liên quan PTS.
- **Khi refactor: đừng đọc, hãy diff.** Nhiều bug từng build xanh, chạy được, chỉ lộ ra khi so
  từng dòng với bản gốc — đặc biệt khi đổi hợp đồng (interface) mà nhiều nơi implement.
- **Đo trước khi kết luận về hiệu năng.** `KEY_FRAME_RATE`/`KEY_BIT_RATE` chỉ là mục tiêu, không
  phải cam kết. Nguyên nhân phổ biến nhất khiến FPS tụt là **throttling nhiệt**, không phải lỗi
  code — để máy nguội giữa các lần đo, và biết `Avg FPS` trong `VideoStatsLogger` là trung bình
  cộng dồn (không phải tốc độ tức thời).
- **Fragment có hai vòng đời.** Dùng `viewLifecycleOwner`, không dùng `this`/`lifecycleScope`
  trực tiếp; nơi tạo và nơi huỷ tài nguyên phải đối xứng; callback đến muộn phải chốt
  `_binding ?: return`. Xem `Fragment_Review_Checklist.md` mỗi khi thêm/sửa Fragment.
- **Nav graph quyết định vòng đời**, không phải bản thân code Fragment — kiểm tra `popUpTo`
  trước khi suy luận về vì sao 1 màn bị huỷ/giữ.
- **2 pattern popUpTo khác nhau trong app, đừng nhầm**: các cụm màn one-shot
  (splash/language/onboarding/survey/welcome/permission) dùng `popUpTo` + `popUpToInclusive=true` để không thể
  back lại được (mục 3). Riêng chuyển tab Home↔Collection ở bottom nav
  (`MainActivity.setupBottomNavTabs`) dùng `popUpTo(effectListFragment, saveState=true)` +
  `launchSingleTop`/`restoreState`, KHÔNG inclusive — coi `effectListFragment` là gốc của cụm
  2 tab, giữ trạng thái cuộn/tab khi qua lại thay vì xóa sạch back stack.
- **Bottom nav đè nổi lên `nav_host`, không xếp dọc**: `activity_main.xml` là `FrameLayout`
  — `nav_host` tràn hết xuống đáy màn hình thật (đằng sau `bottom_nav`), còn `bottom_nav` +
  `bottom_system_bar_scrim` (che inset thanh nav hệ thống) đè nổi lên trên. Bất kỳ
  RecyclerView/nội dung nào nằm dưới `bottom_nav` (ví dụ `recycler_effect`) PHẢI tự thêm
  `paddingBottom = @dimen/bottom_nav_curve_height` + gọi
  `applySystemBarsInsetsPadding(bottom = true)` (xem `ViewInsetsUtils.kt`), nếu không hàng cuối
  sẽ bị che/khó bấm khi đã cuộn đến đáy. Cả `recycler_effect` (EffectListFragment) lẫn
  `recycler_videos` (VideoListFragment) đều đã áp dụng; thêm RecyclerView mới nằm dưới
  bottom_nav thì làm tương tự.
- **Màn camera (`fragment_camera_record.xml`) — 3 điểm dễ sai**: (1) khi ghi hình, 2 cột Effect/Action
  phải `INVISIBLE` chứ không `GONE` (chúng là 2 ô weight 1 kẹp nút record, `GONE` làm nút record lệch tâm);
  (2) mỗi cột có 1 **nhãn ma** `invisible` phía trên nút để tâm nút viền trùng tâm nút record — đổi chữ/cỡ
  nhãn thì phải đổi cả 2 nhãn; (3) inset áp lên 2 khối bọc ngoài (`layoutCameraTopBar`,
  `layoutCameraBottomContainer`), không áp lên từng nút. Chi tiết: `Code_Walkthrough.md` mục 6.1.
- **Màn chọn effect (`effectPickerFragment`) — camera nằm lại back stack**: action `cameraRecord → effectPicker`
  cố ý **không** `popUpTo`, nên camera ở trạng thái "instance sống, view chết" (xem `Fragment_Review_Checklist.md`
  mục 0): `CameraRecordFragment.resetGestureState()` (gọi ở `onViewCreated`) phải reset state debounce + `activeEffect`
  mỗi lần quay lại. Action `effectPicker → effectPreview` thì `popUpTo` camera inclusive (gỡ camera cũ + màn chọn);
  camera mới chỉ được tạo khi bấm Create ở `effectPreviewFragment` (`effectPreview → cameraRecord`, `popUpTo` chính màn
  preview inclusive) nên “đổi effect” = tạo camera mới (không đổi effect tại chỗ). `currentEffectId` truyền `""` nếu chưa có effect nào.
- **Camera không effect (`effectId=""`)**: vào từ nút camera bottom nav, `currentEffect` null. Mọi chỗ dùng
  `currentEffect` trong `CameraRecordFragment` phải chịu được null. Lưu ý bất biến "camera nằm ngay trên
  `effectListFragment`": nút này KHÔNG popUpTo, nên nếu bấm khi đang ở tab Collection thì back stack sẽ là
  effectList → videoList → camera, và `popUpTo` camera của `action_effectPicker_to_effectPreview` có thể
  không như kỳ vọng — cần kiểm tra khi động vào luồng này.
- **Màn xem trước (`effectPreviewFragment`)** là cửa vào camera duy nhất (từ `effectListFragment` hoặc
  `effectPickerFragment`). `DEMO_PREVIEW_RES` trong `EffectPreviewFragment` là media **TẠM** dùng chung cho mọi effect —
  không phải bug, đừng "dọn dẹp" khi chưa có media thật. `previewDrawable` phải null hoá ở `onDestroyView` (drawable giữ
  callback về ImageView, không null hoá là leak cả cây view).
- **Khảo sát (`survey1Fragment`/`survey2Fragment`, package `ui/survey/`) đã có nội dung thật** —
  câu hỏi + 4 đáp án mỗi bước lấy từ `strings.xml` (`survey_1_question`/`survey_2_question` +
  `survey_<bước>_answer_<1..4>`), mỗi đáp án có ảnh minh hoạ riêng khai trong `ANSWERS` (list
  `drawableRes to stringRes`) của từng Fragment — không còn dùng chung 1 ảnh TẠM nữa. Radio chọn
  1 đáp án (mặc định đáp án 1) đã có logic UI (tham khảo đúng pattern
  `item_language_option.xml`/`LanguageFragment`) nhưng **vẫn chưa lưu lựa chọn đi đâu cả** — nút
  Skip/Continue/Finish chỉ điều hướng thuần (`action_survey1_to_survey2`,
  `action_survey1_to_permission`, `action_survey2_to_permission` — Skip ở bước 2 dùng chung action
  với Finish vì cùng đích).
- **Top bar dùng chung `view_effect_top_bar.xml`** (màn camera + màn xem trước) qua `<include android:id=...>`: field
  `binding.<id>` có kiểu là binding của layout con → dùng `.root` (áp inset), `.btnBack`, `.textEffectName`.
  **Đừng tạo bản sao layout chứa `<include>` này trong thư mục qualifier** (`layout-v31/`, `layout-land/`...) mà để top bar
  viết thẳng: ViewBinding gộp các biến thể, id lệch kiểu (include ↔ view thường) nên field tụt xuống `LinearLayout`
  và mọi `binding.<id>.btnBack` báo *Unresolved reference* (đã gặp thật; thư mục `layout-v31/` nay đã xoá).
  `android:clipToOutline` trong XML chỉ có từ API 31 nhưng máy API < 31 chỉ bỏ qua (lint `UnusedAttribute`), không crash —
  cần bo góc chắc chắn trên máy cũ thì gọi `clipRoundedCorners()` (`ui/widget/RoundedOutline.kt`) trong code.
- **Font Noto Serif áp cho toàn app ở 2 tầng**: `android:fontFamily` trong `Base.Theme.HandAr` **và** 15 slot
  `textAppearance*` của Material 3 (`values/type.xml`) — thiếu tầng sau thì nút/dialog/bottom nav/TextInputLayout M3 vẫn
  rơi về font mặc định. Family chỉ có weight 400 + 700: `textStyle="bold"` dùng đúng file Bold, còn slot M3 đòi weight
  500/600 (Label, Title) sẽ hiển thị như Regular. **Đừng khai lại `Base.Theme.HandAr` trong `values-night/`** (style trong
  thư mục qualifier thay thế HOÀN TOÀN style gốc → mất font ở máy bật night mode); `values-night/themes.xml` cố ý để
  trống. Tên file trong `res/` chỉ được chữ thường/số/gạch dưới (`OFL.txt` để trong `res/font/` sẽ làm hỏng build →
  giấy phép nằm ở `assets/licenses/`). Toast dùng font hệ thống; nếu sau này vẽ chữ bằng `Paint`/Canvas thì phải gán
  `ResourcesCompat.getFont(context, R.font.noto_serif)` thủ công.
- **Đặt tên nav graph**: destination camelCase khớp tên class; action
  `action_<từ>_to_<đến>`; id View snake_case; `app:argType` luôn viết thường (`string`, không
  phải `String`).
- App xin quyền **CAMERA** (dùng thật; xin qua switch ở `PermissionFragment`, `CameraRecordFragment` vẫn có nhánh xin lại
  khi bị từ chối) và **POST_NOTIFICATIONS** (Android 13+, switch ở `PermissionFragment`), không xin `RECORD_AUDIO` —
  nếu thấy yêu cầu thêm mic, đó là
  dấu hiệu sai kiến trúc (xem mục 3.2).
- Asset hiệu ứng phải theo đúng `Asset_Format_Guidelines.md` (mọi ảnh dùng `.webp`: ảnh tĩnh có alpha 512–768px; ảnh động
  ≤256×256, ≤20 frame, ≤500KB; sprite sheet chia lưới hết; WAV PCM16/Mono/44100Hz, 0.3–3s; tên theo mẫu `<effect_id>_<trạng thái>`, xem `Design_App_HandAr.md` mục 5.1) và
  đặt trong `res/drawable-nodpi/` để tránh bị phóng theo mật độ màn hình.
- **Tiếng hiệu ứng luôn lặp vòng**: `SoundEffectPlayer` gọi `SoundPool.play(..., loop = -1)` và `AudioMixer` tự lặp PCM khi
  hết, nên mọi WAV tiếng hiệu ứng phải là file lặp mượt (đầu nối đuôi). Không có "tiếng phát 1 lần" — muốn có phải sửa
  `AudioMixer` trong `recording/` (cần yêu cầu rõ). Hiệu ứng có `background` riêng thì camera bị ẩn cả lúc xem lẫn trong video.
- `local.properties` là file máy cá nhân, không commit (đã trong `.gitignore`).
- `RecordingPerfLogger` và các đoạn đánh dấu `// TẠM` trong `CameraRecordFragment` là công cụ
  tạm thời — không phải bug, đừng "dọn dẹp" trừ khi được yêu cầu.

## 6. Bản đồ `docs/` — đọc đúng file khi cần đào sâu

| File | Khi nào đọc |
|---|---|
| `Camera_X_Hand_Landmarker.md` | Tài liệu gốc, đầy đủ nhất: hình học 21 điểm mốc, CameraX, Canvas, mix audio/video, refactor hiệu năng, cử chỉ 2 tay, danh sách cạm bẫy thực tế |
| `HandAr_Refactor_Plan.md` | Trước khi đụng vào pipeline ghi hình (resolution, bitrate, bỏ mic, tách thread, PTS baseline) |
| `HandAr_Plan.md` | Lịch sử/kế hoạch Phase A–N: từ 1 màn → 5 màn + nav graph (I), đến hiệu ứng/state/nhạc nền/canvas (II) — có mục "việc đã bàn nhưng cố ý hoãn", đừng đề xuất lại |
| `Design_App_HandAr.md` | Ý tưởng sản phẩm, app tham khảo, hiệu ứng đề xuất; mục 5 là bảng asset cần chuẩn bị cho 10 hiệu ứng |
| `Fragment_Review_Checklist.md` | Mỗi khi thêm/sửa Fragment |
| `Asset_Format_Guidelines.md` | Mỗi khi thêm hiệu ứng/asset mới |
| `Perf_Notes.md` | Trước khi đổi cấu hình liên quan hiệu năng (resolution, bitrate, GIF vs sprite sheet) |
| `App_Size_Optimization_16KB_Compliance.md` | Trước khi đụng cấu hình build/R8/shrinkResources hoặc bản 16KB page size |
| `Test_Checklist.md` | Sau mỗi thay đổi — chạy checklist A–H phù hợp (đặc biệt D: chống regression 6 bug đã fix) |
| `Code_Walkthrough.md` | **Đọc trước khi sửa bất kỳ file .kt nào** — giải thích code từng file/từng hàm, sơ đồ quan hệ import giữa các file, và mục 12 liệt kê các liên kết chéo dễ nhầm khi debug (ví dụ 2 bộ nhận diện gesture độc lập nói ở mục 3 trên) |

## 7. Thêm một hiệu ứng mới (việc thường gặp nhất)

1. Chuẩn bị asset đúng `Asset_Format_Guidelines.md`, đặt tên theo mẫu `<effect_id>_<trạng thái>` (xem `Design_App_HandAr.md` mục 5.1).
2. Thêm cử chỉ mới vào `object Gestures` nếu cần (đảm bảo loại trừ lẫn nhau rõ ràng).
3. Tạo 1 file factory mới trong `effect/catalog/` (hàm trả `EffectDefinition`, khai đúng `requiredNumHands`) rồi thêm lời gọi vào `EffectRepository.all`.
4. Chạy lại Checklist **B** (live preview) và **D** (chống regression) trong `Test_Checklist.md`.

## 8. Việc KHÔNG nên tự ý làm

Xem mục 10 của `HandAr_Plan.md` — các việc đã bàn và cố ý hoãn/không làm (đổi camera
trước/sau, zoom, tap-to-focus, pause/resume, giới hạn thời lượng, xuất video ra Gallery/MediaStore).
Đừng đề xuất lại trừ khi người dùng chủ động hỏi.

⚠️ **2 mục từng nằm trong danh sách hoãn ở trên đã được làm — đừng liệt kê lại vào danh sách hoãn
nếu thấy nhắc tới trong `HandAr_Plan.md`/`Design_App_HandAr.md` cũ**: (1) xoá/chia sẻ/đổi tên ngay
trong màn thư viện — nay là menu ⋮ của `VideoPlayerFragment` (mục 4); (2) hướng dẫn cử chỉ cho người
dùng — nay là `GestureGuideDialog` mở từ `btn_action` ở màn camera (mục 3), icon vẫn tạm dùng chung
`ic_action` cho mọi cử chỉ.
