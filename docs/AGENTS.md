# AGENTS.md — Ngữ cảnh nhanh cho AI agent

> **Cập nhật lần cuối tại commit `898989c`**. **Note cho agent:** sau khi repo có thêm
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
`MediaCodec` + `MediaMuxer` (ghi hình) · Media3/ExoPlayer 1.8.0 (phát lại) ·
Lifecycle 2.11.0 (ViewModel + runtime-ktx + viewmodel-savedstate), Fragment 1.9.1 (`by viewModels()`) ·
AGP 9.3.2, Gradle 9.5.0 ·
minSdk 28, target/compileSdk 37, Java 17 (`compileOptions` trong `app/build.gradle.kts`; CI cũng dùng JDK 17).

Lệnh hay dùng:
```bash
./gradlew :app:installDebug
./gradlew :app:assembleDebug :app:lintDebug   # chạy trước khi commit
./gradlew :app:bundleRelease
```

CI: `.github/workflows/release.yml` — mỗi lần push lên `main`, GitHub Actions (JDK 17 temurin)
chạy `./gradlew bundleRelease packageReleaseUniversalApk`, ước tính dung lượng tải từ Play bằng `bundletool get-size`
(ghi vào mô tả Release) rồi đẩy APK universal release lên GitHub Release tag `latest` (tên "Magic Hand Latest Build"); sau đó `git tag -f latest $GITHUB_SHA` + push force để tag `latest` luôn trỏ đúng commit vừa build (source zip/tar.gz của Release sinh từ tag, `action-gh-release` không tự dời tag đã có).
Bản release ký bằng keystore riêng của CI (secrets `CI_KEYSTORE_BASE64`, `CI_KEYSTORE_PASSWORD`, `CI_KEY_ALIAS`,
`CI_KEY_PASSWORD`) — không phải upload key của Play; build local không có biến môi trường thì ký bằng debug keystore. Không chạy lint/test trong CI, vẫn phải tự chạy `assembleDebug lintDebug` trước khi commit.

**Bản release bật R8 nên phải giữ keep rule** (`app/src/main/keepRules/rules.keep`, từ commit `7f43107`): MediaPipe
tra class/method qua JNI theo tên, protobuf-lite đọc field theo tên (`GeneratedMessageLite`), Flogger dò stack tìm
class của chính nó. Thiếu 3 nhóm rule này thì bản debug vẫn chạy còn bản release crash ngay khi khởi tạo
`HandLandmarker` — lỗi kiểu này KHÔNG lộ ra khi chỉ test bản debug, nên sau mỗi lần đổi thư viện native/reflection
phải cài thử bản release trên máy thật.

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
soundRes? + `background?` riêng + `sizeSource`/`anchorSource`/`sizeScale`) · `EffectAsset` (sealed:
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
xem đoạn `ShareFragment` bên dưới; tiêu đề top bar là "Lưu thành công" khi vào từ màn xem lại, "Chia sẻ" khi vào từ menu ⋮ của player — cùng cờ `fromRecordedPreview` với nút Thử lại; nút Trang chủ → `popBackStack(effectListFragment,
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
`effect/gesture/GestureDisplay.kt` (`gestureDisplayMap`). **Mỗi cử chỉ có icon riêng** (`res/drawable/ic_action_<tên>.xml`, vector 36dp,
màu cố định không tint). Cử chỉ chưa có trong map rơi về `unknownGestureDisplay` (icon `ic_action_open`). Icon chưa dùng ở
đâu: `ic_action_frame_2hands` (cử chỉ khung máy ảnh đã gỡ), `ic_action_index_touch_2hands` (chạm 2 ngón trỏ của Gojo, xem giới hạn ở mục 5).

## 4. Cấu trúc thư mục

```text
app/src/main/java/com/example/handar/
├── MainActivity.kt        NavHost + quản lý chrome (top bar/bottom nav): ẩn/hiện theo destination,
│                          chuyển tab Home↔Collection (popUpTo+saveState, xem mục 5), nút camera giữa bottom nav mở camera không effect (effectId=""),
│                          đổi icon theo tab đang ở; release HandLandmarkerProvider ở onDestroy
├── effect/
│   ├── EffectRepository.kt      danh sách phẳng List<EffectDefinition>, lắp hoàn toàn từ catalog/ (11 effect);
│   │                            có findByName(context, query) lọc theo tên ĐÃ DỊCH (getString(nameRes), contains, ignoreCase) cho search real-time;
│   │                            findById (crash nếu sai id) và findByIdOrNull (dùng cho camera không effect)
│   ├── HandLandmarkerProvider.kt  singleton cache HandLandmarker theo numHands, phát SharedFlow; tạo model bằng
│   │                            `createWithFallback()` — thử GPU trước, tự rơi về CPU (RAM thấp / init > 5s / lỗi), xem mục 5
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
│   │       ├── lightning/        LightningVisual (buffer-render 1 lần, vẽ nhiều bản xoay theo từng ngón đang duỗi —
│   │       │                      cả 5 ngón của cả 2 tay, bán kính tính riêng mỗi tay)
│   │       ├── dragonball/       KamehamehaVisual (buffer-render, to hơn + xoáy nhanh hơn state 1 tay)
│   │       └── fingerframe/      Finger Frame (khung 4 đầu ngón, filter ảnh camera trong khung) — ĐANG LÀM DỞ, xong Mốc 2–4 (khung 4 đầu ngón + đảo màu bằng BitmapShader), còn Mốc 5 tinh chỉnh + Mốc 6 docs;
│   │                             xem Finger_Frame_Filter_Plan.md. Visual đọc ảnh camera qua HandFrame.cameraFrame/cameraMatrix() (chỉ đọc, không recycle)
│   ├── background/              BackgroundRenderer + Solid/Image/AnimatedBackgroundRenderer
│   └── catalog/                 mỗi file 1 hàm factory trả EffectDefinition, đủ 10/10 hiệu ứng + FingerFrameEffect (hiệu ứng thứ 11, đã xong Mốc 1–5, thumbnail mượn `black_hole_thumbnail`)
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
│   │                    (permission/: PermissionFragment, switch xin quyền Camera + Thông báo — card
│   │                     Thông báo bị ẩn hẳn trên Android < 13 vì không có POST_NOTIFICATIONS để xin;
│   │                     onboarding1/: có nút Skip nhảy thẳng sang survey)
│   ├── language/        LanguageFragment + LanguageViewModel + LanguageRepository — KHÔNG còn trong
│                        luồng mở app 1 lần, chỉ mở từ settings/ (2 dòng chọn cờ vi/en dạng radio).
│                        LanguageRepository bọc `AppCompatDelegate.setApplicationLocales`, KHÔNG cần Context
│   ├── settings/        SettingsFragment + SettingsViewModel — mở từ icon hamburger ở view_top_bar.xml,
│                        mục Ngôn ngữ là mục duy nhất có logic (nav sang language/, gọi thẳng không qua
│                        VM); 5 mục còn lại đã có sự kiện trong VM nhưng nhánh xử lý còn TODO (mục 3)
│   ├── effectlist|effectpreview|camera|recordedpreview|share|videolist|player/   luồng chính, 1 package / màn hình
│   │                    (effectpreview/: màn xem trước effect + nút Create, cửa vào camera duy nhất;
│   │                     share/: ShareFragment, mở từ recordedpreview.save() HOẶC từ menu ⋮ của
│   │                     videoPlayer — xem trước video + 4 icon MXH gọi shareVideoToSocialApp(); nút
│   │                     Trang chủ luôn có, nút Thử lại chỉ hiện khi fromRecordedPreview=true;
│   │                     player/: VideoPlayerFragment, menu ⋮ ở top bar: Đổi tên (RenameDialog),
│   │                     Xoá (ConfirmDialog), Chia sẻ (mở shareFragment, fromRecordedPreview=false))
│   ├── camera/          CameraRecordFragment + GestureGuideDialog/GestureAdapter (dialog hướng dẫn cử
│   │                    chỉ mở từ btn_action, xem mục 3; CameraRecordFragment còn có loading overlay
│   │                    `layout_camera_loading` che khoảng chờ tạo HandLandmarker, xem mục 5)
│   ├── effectpicker/    màn chọn effect mở từ nút Effect của camera (EffectPickerFragment +
│   │                    EffectPickerAdapter + EffectPickerViewModel; Adapter KHÔNG tự giữ lựa chọn,
│   │                    nhận qua setSelectedId() và vẫn cập nhật từng item bằng PAYLOAD_SELECTION)
│   └── widget/          view/decoration dùng chung: GridSpacingItemDecoration, CurvedNavBackgroundView, RoundedOutline,
│                        ConfirmDialog (dialog xác nhận dùng chung, xem `dialog_confirm.xml`),
│                        VideoSeekBarController (đồng bộ 1 SeekBar + 2 nhãn thời gian với 1 ExoPlayer, tách
│                        từ recordedpreview để dùng lại ở share/), VideoThumbnailView (ảnh thumbnail +
│                        nút expand tùy chọn, dùng bởi `item_video.xml`), RenameDialog (dialog đổi tên
│                        video, mở từ menu ⋮ của videoPlayerFragment, xem `dialog_rename.xml`),
                        PermissionDeniedDialog (dialog 2 nút Thoát/Cài đặt khi quyền bị từ chối vĩnh viễn,
                        xem `dialog_permission_denied.xml`)
│   │
│   │  ── ViewModel / Repository theo package (thêm ở MVVM_Migration_Plan.md) ─────────────────
│   │  videolist/     VideoListViewModel, VideoRepository (class, Context), VideoFileRepository (không Context)
│   │  player/        VideoPlayerViewModel (SavedStateHandle cho playbackPosition)
│   │  recordedpreview/ RecordedPreviewViewModel        share/  ShareViewModel (chỉ isFullscreen + 2 sự kiện)
│   │  effectlist/    EffectListViewModel (query + favouriteIds)   effectpreview/ EffectPreviewViewModel
│   │  effectpicker/  EffectPickerViewModel             splash/ SplashViewModel (timer gắn VM, có remainingMs)
│   │  survey/        SurveyViewModel + SurveyRepository — VM dùng CHUNG 2 màn qua navGraphViewModels(nav_graph)
│   │  language/      LanguageViewModel + LanguageRepository       settings/ SettingsViewModel (5 sự kiện, TODO)
│   │  permission/    PermissionViewModel (mỏng: đọc quyền vẫn ở Fragment mỗi onResume)
│   │  camera/        CameraRecordViewModel (CHỈ currentEffect/statePcmMap/bgmPcm/gestureStateMachine)
│   │                 + EffectAudioRepository + GestureStateMachine + computeRecordingSize()
│   │  welcome/, onboarding/  KHÔNG có VM — màn tĩnh, xem mục 5
│   │
├── OverlayView.kt         canvas vẽ hiệu ứng, dùng cho cả live lẫn frame ghi hình — file trung tâm
└── utils/                 AudioUtils (đọc PCM từ .wav), FormatUtils, ViewInsetsUtils (edge-to-edge),
                           PermissionUtils (Context.openAppSettings() — mở màn App info của chính app),
                           DelegatePerfLogger/RecordingPerfLogger/VideoStatsLogger (3 công cụ đo hiệu năng,
                           hiện không có chỗ nào gọi tới — xem mục 5)
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

**11 hiệu ứng** hiện có trong `EffectRepository.all` (10 hiệu ứng khớp HOÀN TOÀN `Design_App_HandAr.md` mục 4 + `finger_frame` thêm sau, xem `Finger_Frame_Filter_Plan.md`)
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
- **Cỡ hiệu ứng = `frame.r` × `EffectState.sizeScale`, tính lại MỖI frame** (không phải cỡ ban đầu chỉnh 1 lần).
  Muốn to/nhỏ hơn cho 1 effect thì chỉnh `sizeScale` trong `effect/catalog/`, đừng sửa visual chung
  (`AnimatedGifVisual`) hay `SizeSource`. `fire_ball` (small 1.6, burst_to_big 2.5) và `magic_shield` (3.2) đang dùng giá trị chỉnh theo mắt, chưa đo; riêng `magic_shield`:
  shield_show/shield_hide phải cùng hệ số vì `ShieldHideVisual` thu nhỏ từ `frame.r` đang khớp.
  Chi tiết: `Code_Walkthrough.md` mục 1.2.
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
- **Từ chối quyền có 2 trạng thái khác nhau, xử lý khác nhau** (từ commit `8f810f4`): `shouldShowRequestPermissionRationale`
  trả `true` = từ chối thường, xin lại được → `CameraRecordFragment` báo Toast rồi `popBackStack()`,
  `PermissionFragment` để nguyên switch tắt cho user bấm lại. Trả `false` (sau khi đã từ chối) = "Don't ask again",
  hệ thống nuốt luôn mọi lần `launch()` sau nên bấm nút/switch sẽ **không có phản hồi gì** → phải mở
  `PermissionDeniedDialog` (`ui/widget/`) dẫn sang màn cài đặt quyền bằng `Context.openAppSettings()`
  (`utils/PermissionUtils.kt`, `ACTION_APPLICATION_DETAILS_SETTINGS`). Đừng thay bằng `MaterialAlertDialogBuilder`:
  dialog hệ thống không ăn theme app nên mất font Noto Serif. Sau khi user quay lại từ Settings phải kiểm tra quyền
  lần nữa trong `onResume` (`CameraRecordFragment` dùng cờ `awaitingSettingsResult`, `PermissionFragment` gọi sẵn
  `refreshSwitchStates()`).
- Asset hiệu ứng phải theo đúng `Asset_Format_Guidelines.md` (mọi ảnh dùng `.webp`: ảnh tĩnh có alpha 512–768px; ảnh động
  ≤256×256, ≤20 frame, ≤500KB; sprite sheet chia lưới hết; WAV PCM16/Mono/44100Hz, 0.3–3s; tên theo mẫu `<effect_id>_<trạng thái>`, xem `Design_App_HandAr.md` mục 5.1) và
  đặt trong `res/drawable-nodpi/` để tránh bị phóng theo mật độ màn hình.
- **Tiếng hiệu ứng luôn lặp vòng**: `SoundEffectPlayer` gọi `SoundPool.play(..., loop = -1)` và `AudioMixer` tự lặp PCM khi
  hết, nên mọi WAV tiếng hiệu ứng phải là file lặp mượt (đầu nối đuôi). Không có "tiếng phát 1 lần" — muốn có phải sửa
  `AudioMixer` trong `recording/` (cần yêu cầu rõ). Hiệu ứng có `background` riêng thì camera bị ẩn cả lúc xem lẫn trong video.
- `local.properties` là file máy cá nhân, không commit (đã trong `.gitignore`).
- **3 công cụ log hiệu năng (`DelegatePerfLogger`, `RecordingPerfLogger`, `VideoStatsLogger`/`logRecordingStats`
  trong `utils/`) vẫn còn trong repo nhưng đã GỠ HẾT chỗ gọi trong `CameraRecordFragment` (29/09/2026),
  sau khi dùng xong cho đợt đo CPU/GPU + fps ghi hình — không phải bug, cố ý giữ file lại để dùng lại
  sau này, đừng xoá file. Muốn đo lại: xem `Perf_Notes.md` mục 9/9.1/9.2 để biết gắn ở đâu
  (`setupMediaPipe()`, analyzer `detectAsync`, `onDestroyView`, `startRecordingFrameLoop`,
  callback `stop()` của `VideoRecorder`) và nhớ tắt Toast trong `DelegatePerfLogger`/`VideoStatsLogger`
  trước khi đo diện rộng (chỉ giữ Log, tránh làm phiền UI lúc test) rồi gắn nhãn `// TẠM` khi gắn lại,
  gỡ ngay sau khi đo xong.
- **`HandLandmarkerProvider` ưu tiên `Delegate.GPU`, có fallback CPU tự động (bullet ngay dưới)** (đổi từ `Delegate.CPU` ngày 29/09/2026, dựa trên số liệu
  đo thật bằng `DelegatePerfLogger` — xem `Perf_Notes.md` mục 9): GPU nhanh hơn ~35-40% (fps ~30 vs ~22),
  latency thấp hơn ~40%, rớt frame ~0-4% so với CPU rớt tới ~25%, trên máy test. GPU cần ~6-10 giây
  khởi động (build shader/EGL context) trước khi đạt tốc độ ổn định — đừng hoảng nếu vài giây đầu sau khi
  mở camera thấy giật/chậm hơn CPU, đó là bình thường. **Chưa đo trên nhiều máy khác nhau** — nếu sau này
  gặp máy nào GPU khởi tạo cực chậm hoặc không ổn định, coi lại quyết định này bằng cách gắn lại
  `DelegatePerfLogger` (`utils/DelegatePerfLogger.kt`, hiện không còn gắn sẵn trong code, xem cách gắn
  ở `Perf_Notes.md` mục 9) trước khi đổi tay, đừng đoán.
- **`HandLandmarkerProvider.getOrCreate()` từng gây ANR** (fix 29/09/2026): bên trong nó gọi
  `HandLandmarker.createFromOptions()`, việc này với `Delegate.GPU` có thể mất vài trăm ms tới vài giây
  (build EGL context + compile shader) — nặng nhất khi `numHands` đổi khác cache (đổi qua lại effect 1
  tay/2 tay) vì phải `close()` rồi tạo lại từ đầu. Trước đây gọi đồng bộ trên main thread ngay trong
  `setupMediaPipe()` → treo UI thread → ANR ngay khi bấm vào effect. **Bài học: đo hiệu năng (`DelegatePerfLogger`)
  chỉ đo latency suy luận mỗi frame, KHÔNG đo chi phí tạo model — một công cụ đo tốt vẫn có thể bỏ sót nguyên
  nhân gốc nếu không đọc lại toàn bộ luồng code gọi tới nó.** Fix: chạy `getOrCreate(...)` trên
  `backgroundExecutor` (đúng thread `detectAsync` đang dùng) qua `withContext`, chỉ về main thread để gán
  `handLandmarker` + collect kết quả — `startCamera()` không cần đợi vì đã dùng `handLandmarker?.` sẵn.
  Bất kỳ chỗ nào gọi hàm tạo model/decoder nặng (MediaPipe, MediaCodec, ...) đồng bộ trên main thread —
  kể cả nhìn qua thấy "chạy nhanh" — đều nên nghi ngờ và kiểm tra lại tương tự. Xem `Code_Walkthrough.md`
  mục 6.2, `Test_Checklist.md` D7/D8.
- **`HandLandmarkerProvider.createWithFallback()` — GPU mặc định, tự rơi về CPU** (29/09/2026, kế hoạch + lịch sử
  quyết định ở `CameraLoading_Fallback_Plan.md`, giải thích đầy đủ ở `Camera_X_Hand_Landmarker.md` mục 14): dùng thẳng
  CPU (không thử GPU) khi `ActivityManager.isLowRamDevice()` hoặc tổng RAM < 3GB; còn lại build GPU trên 1 thread
  phụ, chờ tối đa **5 giây** (`Future.get(timeout)`), quá hạn hoặc GPU ném lỗi thì build CPU. Một khi đã fallback,
  cờ `@Volatile forcedCpuForSession` giữ CPU cho **cả phiên app** (đổi effect khác không thử lại GPU, không bắt
  người dùng chờ lại 5s). `HandLandmarker.createFromOptions()` là lệnh native đồng bộ, **không có API huỷ** — nên
  khi timeout thì luồng build GPU bị bỏ rơi chạy nốt ở nền rồi vứt kết quả; đây là đánh đổi có chủ đích, đừng cố
  "sửa" bằng cách chờ thêm hay đoán cách huỷ. Hai ngưỡng (3GB, 5s) chọn theo cảm tính: nhánh timeout đã bắt được
  **thật** trên máy test (log `HandLandmarkerProvider W GPU init qua 5s...`), nhánh RAM thấp mới chỉ xác nhận qua
  đọc code. Nhãn `DELEGATE_PERF_LABEL` của `DelegatePerfLogger` (nếu gắn lại để đo) **không tự đổi theo delegate
  thực tế** — muốn biết lần đó có fallback không thì đọc log tag `HandLandmarkerProvider`. Ghi chú `TẠM` ở đầu
  file provider chỉ nhắc gỡ **ghi chú** khi cơ chế đã ổn định qua nhiều đợt release, không phải gỡ code.
- **Loading overlay ở `CameraRecordFragment`** (`layout_camera_loading` cuối cây view trong
  `fragment_camera_record.xml`; `showLoadingOverlay()` / `hideLoadingOverlayAfterMinDuration()` /
  `setCameraControlsEnabled()`): nền đen phủ kín cả top bar + bottom bar, **mặc định `visible`** trong XML (không
  phải `gone`) để có mặt từ khung hình đầu tiên, giữ tối thiểu 500ms cho khỏi nhấp nháy, hiện dòng phụ sau 3,5s
  nếu vẫn chờ, disable nút Effect/Record/Action trong lúc đó. Back **hệ thống** vẫn thoát bình thường (xử lý ở
  `OnBackPressedCallback`, không phụ thuộc thứ tự vẽ view). Thêm view mới vào layout camera thì đừng đặt *sau*
  `layout_camera_loading` — nó sẽ vẽ đè lên overlay. Test: `Test_Checklist.md` D9/D10.
- **⚠️ Bug "ma" chưa tái hiện (30/09/2026):** 1 lần trong 20+ lần test, app tự thoát ~1–2s sau khi GPU init xong, crash native
  `signal 7 (Bus error)`, không có `FATAL EXCEPTION`. Nguyên nhân **chưa xác nhận** (nghi GPU tạo trên thread phụ nhưng
  `detectAsync` gọi từ thread khác). Nếu gặp crash lạ ở màn camera: bắt `adb logcat -b crash` trước, rồi đọc
  `Camera_X_Hand_Landmarker.md` mục 14.4 — đừng sửa theo suy đoán.

- **Working tree trên máy Windows có ~90 file hiện "modified" chỉ vì khác kiểu xuống dòng (CRLF) + `.idea/`** — index của repo là LF (`git ls-files --eol`).
  Khi sửa file: giữ nguyên kiểu xuống dòng hiện có của file, **nhưng nếu index là LF thì để LF** (`HandFrame.kt` từng là CRLF → commit sẽ báo đổi cả file; đã chuyển về LF
  01/10/2026). Khi commit: `git add` đúng từng file cần, đừng `git add .`/`-A`; sau `git add` chạy `git diff --cached --stat` — file nào đổi gần hết số dòng là nghi sai kiểu xuống dòng.
- **Kiểm chứng hạ tầng bằng visual gỡ lỗi tạm trước khi làm hiệu ứng thật** (Finger Frame Mốc 1): vẽ ảnh camera nửa trong suốt đè lên preview để thấy ngay ma trận
  chiếu có khớp không. Dư ảnh nhẹ ở live khi người cử động là bình thường (bitmap luồng phân tích trễ hơn `PreviewView`); dấu hiệu lỗi thật là lệch cố định khi đứng yên,
  ngược chiều hoặc sai tỉ lệ. Video ghi ra không được dư ảnh (nền và lớp phủ cùng một bitmap).

- **Cử chỉ của hiệu ứng "có fade/làm mượt" phải lỏng** (Finger Frame Mốc 2): `OverlayView.drawFrame()` thoát sớm khi không có cử chỉ khớp
  (visual không còn được gọi `draw()`), nên cử chỉ chặt làm hình biến mất đột ngột. Dùng cử chỉ lỏng (`Gestures.twoHandsFrame` = đủ 2 tay) và để
  visual/tracker tự quyết định mở-khép + mờ dần. Viền vẽ từ landmark thô sẽ rung và trễ khi tay di chuyển nhanh — đó là lý do cần tracker
  (làm mượt + dự đoán) ở Mốc 3, không phải lỗi của phép chiếu.

- **Làm mượt landmark: khử rung và độ trễ đánh đổi nhau, và có trần trễ không làm mượt nào gỡ được** (Finger Frame Mốc 3): EMA alpha cố định
  làm khung trễ rõ khi tay nhanh; dùng alpha thích nghi theo tốc độ (đứng yên mượt, di chuyển nhanh alpha → 1, như 1€ Filter) hết rung mà
  không tăng trễ. Nhưng phần trễ còn lại là do đường ống (MediaPipe + analyzer ~25–30 fps, vị trí vẽ luôn là của frame đã xử lý xong), không
  phải do làm mượt — muốn giảm nữa phải dự đoán/ngoại suy theo vận tốc. Khi chỉnh ngưỡng (tỉ lệ cái–trỏ, diện tích) dùng log `FingerFrameDbg`
  thay vì đoán; ngưỡng tắt theo diện tích nên đặt thấp hơn ngưỡng bật (hysteresis) để khung nhỏ vẫn giữ được.

- **Filter ảnh trong 1 vùng bất kỳ: `BitmapShader` + `drawPath`, không `clipPath`** (Finger Frame Mốc 4): `Paint` mang `BitmapShader(bitmap)` + `setLocalMatrix(HandFrame.cameraMatrix())`
  + `ColorMatrixColorFilter` rồi `drawPath(quad)` cho mép khử răng cưa và ảnh khớp tuyệt đối với viền (cùng phép chiếu `px()/py()`); `clipPath` không khử
  răng cưa trên canvas phần cứng. `ColorMatrix` thang 0–255 (cột offset đảo màu là 255). Shader gắn cứng 1 bitmap nên cache theo tham chiếu, chỉ tạo lại khi
  bitmap camera đổi; `DashPathEffect` phụ thuộc bề rộng canvas nên chỉ tạo lại khi bề rộng đổi (live ≠ video).
- **Hiệu ứng 2 tay đòi hỏi delegate GPU của MediaPipe** (Finger Frame Mốc 5b, đo 02/10/2026): trên máy test, `HandLandmarkerProvider` từng chuyển sang CPU vì GPU init quá 5 giây (pin ~7%, tiết kiệm điện) → MediaPipe chỉ ~2,5 fps, trễ ~450 ms (cả `finger_frame` lẫn `black_hole`), khung giật và lên rất chậm. Nâng timeout lên 30 giây → GPU bật, ~12 fps, trễ ~110 ms. Khi thấy hiệu ứng 2 tay giật, **việc đầu tiên là xem log `HandLandmarkerProvider` có dòng `GPU init qua ...` / `GPU init loi` không** trước khi nghi ngờ code vẽ. Việc tô ruột bằng `BitmapShader` chỉ tốn ~0,6 fps (đã đo A/B chỉ-viền vs tô-ruột). Đo 5e: 3/3 lần chạy với timeout 5 giây đều rơi sang CPU (GPU init chậm trên máy test, chưa đo thời gian init); `finger_frame` và `black_hole` trên CPU đều ~2,7 fps → giật do MediaPipe, không do code vẽ. Quyết định timeout/cải thiện kịch bản CPU để phiên khác xử lý.
- **Ngoại suy (`lead`) cần khoảng trễ tham chiếu theo nhịp kết quả thật**: ngưỡng reset vận tốc phải rộng hơn jitter của MediaPipe. Kết quả ~12 fps cách nhau trung vị 83 ms nhưng 17% > 100 ms, nên `MAX_DT_MS=100` (của làm mượt) làm `lead` tụt về 0 từng nhịp; tách riêng `MAX_VEL_DT_MS=250` đưa tỉ lệ lần cập nhật có lead lên ~76%. Đừng suy nhịp kết quả từ fps camera — đo bằng log.
- **Cách đo/ghi log hiệu năng đã dùng** (đã gỡ phần nối dây TẠM, các lớp `DelegatePerfLogger`/`RecordingPerfLogger`/`logRecordingStats` trong `utils/` vẫn còn): xuất log từ Android Studio chỉ giữ phần đuôi bộ đệm (bị Camera HAL làm đầy) → dùng `adb -s <serial> logcat -s DelegatePerf RecPerf VideoStats HandLandmarkerProvider > file.txt` (cú pháp `-s` trước các tag; PowerShell ghi UTF-16 — đọc bằng `iconv -f UTF-16`). Chạy `adb logcat -c` trước khi test.

- **ViewModel làm dữ liệu cũ sống dai hơn trước — soát cả dữ liệu item MANG THEO, không chỉ dữ liệu
  item VẼ RA.** Bẫy thật đã sập ở Bước 2 của `MVVM_Migration_Plan.md`: `VideoListViewModel` cache
  `List<VideoItem>`, `VideoItem` giữ `File`, và `VideoListFragment` truyền `file.absolutePath` sang
  player khi bấm item. Đổi tên video ở `VideoPlayerFragment` rồi back ra là bấm vào item ăn ngay
  `can_not_play_video`, **dù** `onBindViewHolder` không hề hiện tên file nên nhìn UI thì tưởng
  rename vô hại. Trước khi kết luận "state cũ này không ảnh hưởng gì", phải soát cả những gì item
  truyền đi qua callback/`Directions`, không chỉ những gì nó vẽ. Dạng lỗi này **chỉ xuất hiện sau
  khi thêm ViewModel** — trước đó list load lại mỗi lần view được tạo nên dữ liệu luôn tươi.
- **Làm mới danh sách video: `viewModel.load()` ở `onViewCreated`, KHÔNG dùng cờ báo từ màn khác.**
  `VideoListFragment` nằm trong back stack gần như suốt phiên (tab Bộ sưu tập) nên VM sống dai hơn
  view rất nhiều; load theo vòng đời **view** là cách duy nhất phủ hết, vì mọi đường quay lại màn
  (pop từ player, chuyển tab, back từ camera) đều tạo lại view. Cơ chế cờ
  `KEY_VIDEO_LIST_STALE` cũ đã **bỏ hẳn** (01/10/2026): nó chỉ phủ đường `VideoPlayerFragment`, còn
  video mới quay đi camera → recordedPreview → share → `popBackStack` về effectList, không chạm màn
  này nên không ai đặt cờ → video vừa quay không hiện lên. Đánh đổi: quét lại thư mục mỗi lần vào
  màn. Chi tiết 3 vòng quyết định: `MVVM_Migration_Plan.md` mục 4.

- **Màn nào cần `ViewModel`, màn nào không.** Có state / timer / dữ liệu / sự kiện một lần → có
  `ViewModel` (`ui/<màn>/<Man>ViewModel.kt`, factory viết tay, **không** DI — xem
  `MVVM_Migration_Plan.md` mục 0.2). Màn chỉ hiện nội dung tĩnh rồi bấm sang màn kế
  (`WelcomeFragment`, `Onboarding1-3Fragment`) → **không** tạo VM rỗng. `CameraRecordFragment` là ca
  riêng: có VM nhưng VM chỉ giữ phần thuần dữ liệu (`currentEffect`, `statePcmMap`, `bgmPcm`,
  `gestureStateMachine`), mọi tài nguyên gắn View ở lại Fragment để không phá bất biến "camera nằm lại
  back stack = instance sống, view chết" (mục 5.4 của kế hoạch).
- **`navGraphViewModels` import từ `androidx.navigation`**, không phải `androidx.navigation.fragment`
  (đổi package ở bản Navigation mới; dự án dùng 2.9.7). Dùng cho VM chia sẻ giữa `Survey1Fragment` và
  `Survey2Fragment`.
- **Repository là `class` nhận `Context` ở constructor, không phải `object`.** Áp dụng cho
  `VideoRepository`, `VideoFileRepository`, `FavouriteManager`, `SurveyRepository`,
  `EffectAudioRepository`. Ngoại lệ có chủ đích: `LanguageRepository` (`AppCompatDelegate` là API
  tĩnh) và `VideoFileRepository` (chỉ làm việc trên đường dẫn tuyệt đối) **không** cần `Context` —
  đã ghi rõ trong KDoc từng file để không ai tưởng là quên.
- **`LanguageFragment` là màn duy nhất mà ViewModel KHÔNG sống sót qua thao tác của chính nó.**
  `AppCompatDelegate.setApplicationLocales()` tạo lại Activity hoàn toàn → Fragment mới + VM mới. Sau
  khi tạo lại, `LanguageRepository.currentTag()` đọc lại từ hệ thống nên state khởi tạo vẫn đúng.
  Đừng "sửa" chỗ này bằng cách cố giữ state — cần UI không giật thì phải xử lý ở `android:configChanges`.
- **`AppCompatDelegate.getApplicationLocales()` RỖNG khi app chưa từng `setApplicationLocales`** (app đang theo ngôn ngữ
  hệ thống) — máy tiếng Việt cũng trả rỗng. `LanguageRepository.currentTag()` vì thế rơi về `Locale.getDefault().language`
  khi danh sách rỗng, rồi quy về `"vi"`/`"en"`. Bản đầu chỉ đọc `getApplicationLocales()` nên màn Ngôn ngữ luôn tick English
  trên máy tiếng Việt chưa từng đổi ngôn ngữ trong app (01/10/2026). Bài học: API "đọc cấu hình đã đặt" khác API "đọc giá trị
  đang áp dụng" — khi cần hiển thị trạng thái hiện hành, phải xử lý nhánh "chưa đặt gì".
- **Text `PermissionDeniedDialog` phải trung tính theo ngữ cảnh**: dialog dùng chung cho `PermissionFragment` (onboarding) và
  `CameraRecordFragment`, nên `permission_denied` ("Cần cấp quyền"/"Permission required") và `denied_permission_message` chỉ nói
  *vì sao app cần quyền* + *vào Cài đặt bật lại*, không viết kiểu "tính năng này không hoạt động…" hay "bị từ chối" gắn với 1 màn.
  Phần khác nhau giữa 2 màn (nút Thoát/Đóng, nội dung Thông báo) truyền qua tham số của dialog, đừng nhân bản chuỗi.

- **⚠️ Giới hạn đã biết của `GestureDisplay`: dialog hướng dẫn suy ra từ `EffectState.gesture`, nhưng không phải hiệu ứng nào cũng
  "điều khiển bằng gesture".** `gestureDisplayMap` khoá theo INSTANCE `GestureRecognizer`, nên (1) hiệu ứng khớp mọi bàn tay rồi
  tự tính cỡ/tâm (Trái đất: chụm ngón) phải dùng instance riêng — hiện là `Gestures.pinchTracking`, cùng logic `anyHandPresent`
  (sửa 01/10/2026; trước đó dialog hiện nhầm "Giơ tay"); (2) tương tác nằm trong visual (Gojo: chạm 2 đầu ngón trỏ) không thuộc
  state nào nên dialog không có dòng cho nó (icon `ic_action_index_touch_2hands` chưa dùng). Đây là cách chữa tạm — **cần nghiên cứu
  cách khác** (ví dụ `EffectDefinition`/`EffectState` khai thẳng danh sách hướng dẫn tách khỏi `gesture`) trước khi thêm hiệu ứng
  kiểu này nữa. Thêm hiệu ứng mới: luôn mở dialog Action kiểm tra có dòng hướng dẫn đúng.
- **Tên hiệu ứng là string resource, không phải chuỗi cứng.** `EffectDefinition.nameRes` (`@StringRes`) trỏ tới
  `effect_name_<id>` trong `values/` (en) và `values-vi/`. Hiển thị bằng `setText(nameRes)`/`getString(nameRes)`. Tìm kiếm ở
  `EffectRepository.findByName(context, query)` lọc theo tên đã dịch nên **phải truyền context của Activity/Fragment**
  (`requireContext()`), không dùng application context — trên Android đời cũ nó có thể chưa theo ngôn ngữ app đã chọn. Thêm
  effect mới phải thêm đủ 2 string. `item_effect.xml` (màn danh sách) cỡ chữ tên là 10sp; `item_effect_picker.xml` vẫn 16sp.

## 6. Bản đồ `docs/` — đọc đúng file khi cần đào sâu

| File | Khi nào đọc |
|---|---|
| `Camera_X_Hand_Landmarker.md` | Tài liệu gốc, đầy đủ nhất: hình học 21 điểm mốc, CameraX, Canvas, mix audio/video, refactor hiệu năng, cử chỉ 2 tay, danh sách cạm bẫy thực tế |
| `HandAr_Refactor_Plan.md` | Trước khi đụng vào pipeline ghi hình (resolution, bitrate, bỏ mic, tách thread, PTS baseline) |
| `HandAr_Plan.md` | Lịch sử/kế hoạch Phase A–N: từ 1 màn → 5 màn + nav graph (I), đến hiệu ứng/state/nhạc nền/canvas (II) — có mục "việc đã bàn nhưng cố ý hoãn", đừng đề xuất lại |
| `Design_App_HandAr.md` | Ý tưởng sản phẩm, app tham khảo, hiệu ứng đề xuất; mục 5 là bảng asset cần chuẩn bị cho 10 hiệu ứng |
| `Fragment_Review_Checklist.md` | Mỗi khi thêm/sửa Fragment |
| `Asset_Format_Guidelines.md` | Mỗi khi thêm hiệu ứng/asset mới |
| `Perf_Notes.md` | Trước khi đổi cấu hình liên quan hiệu năng (resolution, bitrate, GIF vs sprite sheet, delegate CPU vs GPU — mục 9) |
| `App_Size_Optimization_16KB_Compliance.md` | Trước khi đụng cấu hình build/R8/shrinkResources hoặc bản 16KB page size |
| `App_Size_Optimization_Plan.md` | Giai đoạn 2 của việc giảm dung lượng: mốc đo bằng bản release, keep rule R8, CI + `bundletool`, và danh sách việc đã cố ý loại bỏ (WAV→OGG, split ABI, PAD…) |
| `Test_Checklist.md` | Sau mỗi thay đổi — chạy checklist A–H phù hợp, I khi đụng cử chỉ, J–M khi đụng effect procedural/tia sét/Dragon Ball (đặc biệt D: chống regression — 6 bug quay video D1–D6 + ANR/race/loading/fallback khi mở camera D7–D10) |
| `CameraLoading_Fallback_Plan.md` | Trước khi đụng `HandLandmarkerProvider.createWithFallback()` hoặc loading overlay của camera — lý do từng ngưỡng, phương án đã cân nhắc và bỏ (preload) |
| `DelegatePerf_Plan.md` | Khi cần đo lại CPU vs GPU bằng `DelegatePerfLogger` — cách gắn và cách đọc số liệu (kết quả đo đã nằm ở `Perf_Notes.md` mục 9) |
| `Camera_X_Face_Landmarker.md` | Tài liệu lý thuyết cho tính năng nhận diện khuôn mặt (Face Landmarker) — **chưa có code Face nào trong app**, chỉ đọc khi định làm Face AR |
| `Finger_Frame_Filter_Theory.md` | Lý thuyết cho kiểu hiệu ứng "filter trong khung 2 tay" (kiểu FingerLens): dựng tứ giác từ 4 đầu ngón, làm mượt (EMA/1€), vẽ trong vùng (`BitmapShader` thay `clipPath`), lý thuyết từng filter (ColorMatrix, colormap, Sobel, glitch…) và AGSL — **chưa có code**, kèm link tài liệu/video |
| `Finger_Frame_Filter_Plan.md` | Kế hoạch triển khai hiệu ứng demo đầu tiên (khung đảo màu): hạ tầng đã có/cần thêm, các mốc, rào cản (preview vs bitmap analyzer, fade-out, đa luồng) — đọc trước khi code tính năng này |
| `MVVM_Migration_Plan.md` | Kế hoạch 7 bước chuyển sang MVVM (**đã xong cả 7 bước**, 13 Fragment có `ViewModel`; Welcome/Onboarding cố ý không có) — quy ước `ViewModel`/Repository ở mục 0.2, các bẫy đã gặp ở mục 3–6; đọc trước khi thêm `ViewModel`/Repository mới cho một Fragment |
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
dùng — nay là `GestureGuideDialog` mở từ `btn_action` ở màn camera (mục 3), mỗi cử chỉ đã có icon riêng
(`ic_action_*`).
