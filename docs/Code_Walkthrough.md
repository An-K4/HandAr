# Code Walkthrough — "dòng này làm gì?" & "file này liên quan gì tới file kia?"

> **Cập nhật lần cuối tại commit `ddcadce`**. **Note cho agent:** file này bám theo TỪNG
> DÒNG code hiện tại nên lỗi thời nhanh hơn các doc lý thuyết khác — sau khi có commit mới đổi
> cấu trúc file, chữ ký hàm, hay logic ở `OverlayView`/`CameraRecordFragment`/`recording/`/`effect/`,
> hãy đọc lại code liên quan và sửa lại đoạn tương ứng trong file này (và dòng commit hash ở trên)
> thay vì để nó sai lệch âm thầm.

> Khác với các file khác trong `docs/` (lý thuyết, kế hoạch, bài học), file này bám sát **mã nguồn
> hiện tại từng dòng**. Mục tiêu: đọc xong 1 mục thì đọc code thật của mục đó không còn phải nhảy
> qua lại 3-4 file mới hiểu.
>
> Quy ước ký hiệu: `file.kt → file2.kt` nghĩa là "file.kt import/gọi trực tiếp file2.kt".
> Đường dẫn file luôn tính từ `app/src/main/java/com/example/handar/`.

---

## 0. Bản đồ quan hệ tổng quát (đọc cái này trước)

```text
MainActivity.kt ── giữ vòng đời HandLandmarkerProvider (effect/)

nav_graph.xml điều hướng qua các Fragment trong ui/*
    splash → welcome → onboarding1(Skip→survey) → onboarding2 → onboarding3 → survey → permission → effectList → effectPreview → cameraRecord → recordedPreview → share (nút Thử lại ở share tạo camera MỚI, popUpTo share inclusive)
    (thứ tự này đổi từ commit `24d7626`/`b1a3cf9` — language không còn trong chuỗi này, xem dòng settings/language bên dưới)
                                                                        │                  ▲                 ⇅ (nút Effect / back)
                                                                        │                  └─ (tick, effect khác) ─ effectPicker
                                                                        └──────────────→ videoList → videoPlayer

ui/share/ShareFragment.kt   (mở từ recordedPreview.save(), popUpTo recordedPreview inclusive)
    → ui/widget/VideoSeekBarController.kt   (dùng lại y hệt recordedPreview, xem mục 9)
    → nút Trang chủ: popBackStack(effectListFragment, inclusive=false)
    → nút Thử lại: actionShareToCameraRecord(effectId), popUpTo chính shareFragment inclusive (camera mới)

ui/welcome/WelcomeFragment.kt   (màn THỨ HAI của cụm mở app 1 lần, ngay sau splash)
    → chỉ có 1 nút → action_welcome_to_onboarding_1 (popUpTo inclusive)

ui/language/LanguageFragment.kt · ui/settings/SettingsFragment.kt   (KHÔNG nằm trong luồng mở app 1 lần —
settings mở từ icon hamburger ở view_top_bar.xml, có sẵn mọi lúc; language chỉ mở từ settings)
    → SettingsFragment: 6 hàng, chỉ hàng Ngôn ngữ có logic (action_settings_to_language, không popUpTo)
    → LanguageFragment: 2 hàng tick vi/en (KHÔNG dùng RecyclerView vì chỉ 2 lựa chọn cố định), back = navigateUp()

ui/permission/PermissionFragment.kt   (màn cuối của cụm mở app 1 lần, giữa survey và effectList)
    → PermissionFragment: 2 SwitchMaterial (Camera = CAMERA, Thông báo = POST_NOTIFICATIONS — Android < 13 không có quyền này
      nên luôn coi là granted). Switch chỉ phản ánh trạng thái quyền thật (`refreshSwitchStates()` ở onViewCreated + onResume để
      cập nhật khi user vừa cấp tay trong Settings): bật khi chưa cấp → xin quyền; tắt khi đã cấp → trả về bật (Android không
      cho app tự thu hồi quyền). Nút bắt đầu → action_permission_to_effectList (popUpTo inclusive), **không chặn** khi chưa
      cấp quyền — màn camera có nhánh xin lại (mục 6.2)

ui/effectlist/EffectListFragment.kt
    → effect/EffectRepository.kt (lấy List<EffectDefinition> để hiển thị; findByName(query) cho
      ô search real-time — filter theo displayName.contains(ignoreCase = true))
    → ui/effectlist/EffectAdapter.kt (RecyclerView, có updateItems() để nạp lại kết quả search)
    → ui/widget/GridSpacingItemDecoration.kt (ItemDecoration chỉ chèn gap GIỮA 2 cột, không
      thêm margin ở 2 mép ngoài — dùng chung với VideoListFragment, xem AGENTS.md mục 5)

ui/effectpreview/EffectPreviewFragment.kt   (xem trước effect + nút Create, luôn mở từ effectList/effectPicker — mục 6.1)
    → effect/EffectRepository.kt (findById(args.effectId) → tên hiển thị trên top bar)
    → res/layout/view_effect_top_bar.xml (top bar dùng chung với màn camera, qua <include>)
    → Create → cameraRecord bằng Safe Args (actionEffectPreviewToCameraRecord)

ui/effectpicker/EffectPickerFragment.kt   (màn chọn effect, mở từ nút Effect của camera — mục 6.1)
    → effect/EffectRepository.kt (EffectRepository.all; không có search/yêu thích)
    → ui/effectpicker/EffectPickerAdapter.kt (chọn đúng 1 item, viền cyan qua state_selected)
    → ui/widget/GridSpacingItemDecoration.kt
    → trả kết quả bằng Safe Args (actionEffectPickerToEffectPreview → màn xem trước), không dùng result API

ui/videolist/VideoListFragment.kt
    → ui/videolist/VideoRepository.kt (liệt kê file .mp4, đọc metadata/thumbnail)
    → ui/videolist/VideoAdapter.kt (RecyclerView, item chỉ có ảnh + nút play giữa, không tên/thời lượng)
    → ui/widget/GridSpacingItemDecoration.kt (dùng chung với EffectListFragment)

ui/player/VideoPlayerFragment.kt   (mở từ videoList khi chọn 1 video; menu ⋮ thêm từ commit `64cf7f0`)
    → ui/widget/VideoSeekBarController.kt   (dùng lại y hệt recordedPreview/share, xem mục 9)
    → ui/widget/RenameDialog.kt   (menu ⋮ → Đổi tên; File.renameTo tại chỗ, không đổi extension)
    → ui/widget/ConfirmDialog.kt   (menu ⋮ → Xoá; xác nhận rồi File.delete() + popBackStack)
    → action_videoPlayer_to_share (menu ⋮ → Chia sẻ; KHÔNG popUpTo — videoPlayer vẫn nằm trong back
      stack; effectId truyền "" và fromRecordedPreview=false nên shareFragment ẩn nút Thử lại)

ui/camera/CameraRecordFragment.kt   ★ file trung tâm, "nhạc trưởng" của 1 phiên quay
    → effect/EffectRepository.kt          (tra EffectDefinition theo args.effectId)
    → effect/HandLandmarkerProvider.kt    (bật MediaPipe, lắng nghe kết quả qua SharedFlow)
    → OverlayView.kt                      (đẩy kết quả tay vào, gọi vẽ khi ghi hình)
    → recording/VideoRecorder.kt          (điều khiển ghi MP4)
    → audio/SoundEffectPlayer.kt          (phát tiếng hiệu ứng ra loa khi KHÔNG ghi)
    → audio/BgmPlayer.kt                  (phát nhạc nền ra loa)
    → utils/AudioUtils.kt (loadWavPcm)    (đọc file .wav → PCM để feed vào AudioMixer)
    → utils/RecordingPerfLogger.kt, utils/VideoStatsLogger.kt (đo đạc, KHÔNG phải logic chính)

OverlayView.kt                      ★ file trung tâm thứ hai, "bộ não vẽ"
    → effect/model/*.kt                   (đọc cấu hình EffectDefinition/EffectState đang chọn)
    → effect/background/*.kt              (vẽ nền nếu effect có khai báo)
    → effect/visual/*.kt, visual/image/*.kt, visual/canvas/**/*.kt  (vẽ hiệu ứng lên tay)

effect/EffectRepository.kt
    → effect/catalog/*.kt                 (mỗi file trả về 1 EffectDefinition dựng sẵn)
    → effect/gesture/Gesture.kt (Gestures) (gán GestureRecognizer cho từng EffectState)
    → effect/model/*.kt                    (kiểu dữ liệu để dựng EffectDefinition)

effect/catalog/*.kt  (mỗi hiệu ứng "đặc biệt" nằm ở đây thay vì khai trực tiếp trong Repository)
    → effect/visual/canvas/gojo/*.kt       (GojoEffect.kt dùng)
    → effect/visual/canvas/drawcanvas/*.kt (CanvasDrawEffect.kt dùng)
    → effect/model/AnchorSource.kt, SizeSource.kt (BlackHoleEffect.kt, EarthEffect.kt dùng)

recording/VideoRecorder.kt          ★ file trung tâm thứ ba, "nhạc trưởng ghi hình"
    → recording/VideoEncoderWrapper.kt    (MediaCodec H.264 nhận Surface để vẽ lên)
    → recording/AudioEncoderWrapper.kt    (MediaCodec AAC nhận PCM)
    → recording/EffectAudioClock.kt       (sinh nhịp PCM đều đặn — "tại sao có audio dù ko mic")
    → recording/AudioMixer.kt             (trộn PCM hiệu ứng + PCM nhạc nền vào nhịp trên)
    → recording/MuxerCoordinator.kt       (gộp 2 track thành .mp4, chờ đủ cả 2 mới start)
```

Ba file có dấu ★ ở trên là nơi nên đọc kỹ nhất — phần lớn "tại sao code lại viết thế này" nằm ở đó.

---

## 1. Khai báo một hiệu ứng — `effect/model/*.kt`, `EffectRepository.kt`, `effect/catalog/*.kt`

### 1.1 Các kiểu dữ liệu gốc (`effect/model/`)

**`EffectDefinition`** — 1 hiệu ứng người dùng chọn được ở màn danh sách.
```kotlin
data class EffectDefinition(
    val id: String,                 // khoá để tra cứu — CameraRecordFragmentArgs.effectId truyền qua nav
    val displayName: String,        // tên hiển thị ở EffectListFragment
    val thumbnailRes: Int,          // ảnh thumbnail
    val requiredNumHands: Int,      // 1 hoặc 2 — quyết định HandLandmarkerProvider.getOrCreate(numHands)
    val states: List<EffectState>,  // danh sách trạng thái ứng với từng cử chỉ
    val background: EffectBackground? = null,  // nền mặc định (khi không state nào khớp cũng còn nền này)
    val bgm: EffectBgm? = null,                // nhạc nền phát suốt khi ở màn quay
    val stateMode: StateMode = StateMode.Momentary  // xem mục 1.3
)
```
Khối `init { require(...) }`: nếu **bất kỳ** state nào có `background` riêng thì `EffectDefinition`
**bắt buộc** phải có `background` mặc định — lý do: `OverlayView.drawFrame()` cần một nền để fallback
về khi không state nào khớp cử chỉ (xem mục 5.4).

**`EffectState`** — 1 trạng thái trong 1 hiệu ứng (ứng với 1 cử chỉ cụ thể).
```kotlin
data class EffectState(
    val id: String,                     // dùng làm key trong statePcmMap (CameraRecordFragment)
    val gesture: GestureRecognizer,     // hàm nhận diện, xem mục 2
    val asset: EffectAsset?,            // hình/gif/sprite/procedural vẽ đè lên tay — null nếu state chỉ có tiếng
    val soundRes: Int?,                 // null nếu state không phát tiếng
    val background: EffectBackground? = null,   // đè nền mặc định của Definition khi state này khớp
    val sizeSource: SizeSource = SizeSource.PalmRadius,     // xem mục 1.2
    val anchorSource: AnchorSource = AnchorSource.PalmCenter // xem mục 1.2
)
```
`init { require(asset != null || soundRes != null) }` — state không hình không tiếng thì vô nghĩa,
lỗi này nổ ngay lúc `EffectRepository.all` được khởi tạo (app crash sớm ngay khi mở, dễ phát hiện).

**`EffectAsset`** (sealed class) — asset để vẽ, 4 loại:
| Loại | Vẽ bởi | Ghi chú |
|---|---|---|
| `StaticImage(resId)` | `visual/image/StaticImageVisual.kt` | ảnh tĩnh |
| `AnimatedGif(resId, oneShot=false)` | `visual/image/AnimatedGifVisual.kt` | `oneShot=true` dùng cho hiệu ứng chạy 1 lần rồi dừng (xem `GojoVisual` mục 3.7) |
| `SpriteSheet(resId, columns, rows, frameCount, frameDurationMs)` | `visual/image/SpriteSheetVisual.kt` | hiện **chưa có effect nào trong `EffectRepository.all` dùng loại này** — code vẫn còn nhưng chưa được khai báo dùng ở catalog nào |
| `Procedural(id, create: (Context, EffectScope) -> EffectVisual)` | tuỳ `create` lambda trả về gì | dùng cho hiệu ứng vẽ bằng code thay vì ảnh có sẵn — xem `CanvasDrawEffect.kt`, `GojoEffect.kt` |

**`EffectBackground`** (sealed class): `Solid(colorRes)` · `Image(resId)` · `Animated(resId)` —
render bởi `effect/background/*.kt` tương ứng (mục 4).

**`EffectBgm(resId, gainPercent=50)`** — nhạc nền: `resId` là file `.wav`/`.mp3` trong `res/raw`,
`gainPercent` là % âm lượng khi **trộn vào track ghi hình** (không phải âm lượng phát ra loa —
xem `BgmPlayer` vs `AudioMixer` ở mục 7.2 và 8.3, đây là hai đường hoàn toàn khác nhau).

### 1.2 `AnchorSource` & `SizeSource` — "vẽ ở đâu, to cỡ nào"

Đây là 2 enum quyết định `HandFrame.cx/cy` (tâm vẽ) và `HandFrame.r` (kích thước) được tính thế nào
trong `OverlayView.drawFrame()` (mục 5.4) — mặc định `PalmCenter`/`PalmRadius` phù hợp phần lớn
hiệu ứng bám lòng bàn tay, còn 3 cặp còn lại phục vụ hiệu ứng đặc biệt:

| AnchorSource | Công thức tâm | SizeSource đi kèm thường dùng | Effect ví dụ |
|---|---|---|---|
| `PalmCenter` (mặc định) | trung bình khớp ngón giữa (9) của các tay | `PalmRadius` (cổ tay 0 → khớp giữa 9) | phần lớn effect |
| `PinchMidpoint` | trung điểm ngón cái (4) + ngón trỏ (8) | `PinchDistance` | `EarthEffect.kt` — trái đất to nhỏ theo khoảng nhón tay |
| `IndexFingertip` | đầu ngón trỏ (8) | (thường giữ `PalmRadius`) | `GojoEffect.kt` — quả cầu bám đầu ngón trỏ |
| `TwoHandMidpoint` | trung điểm khớp giữa (9) của 2 tay | `TwoHandDistance` | `BlackHoleEffect.kt` — hố đen to theo khoảng cách 2 tay |

Cả hai đều đọc trực tiếp từ `EffectState` **đang khớp** (`currentEffect.states.getOrNull(matchedIndex)`),
nghĩa là **mỗi state trong cùng 1 effect có thể dùng anchor/size khác nhau** — hiện tại không effect
nào trong `EffectRepository.all` tận dụng việc trộn nhiều anchor/size trong 1 effect, nhưng model
đã hỗ trợ sẵn.

### 1.3 `StateMode` — Momentary vs Latched

```kotlin
enum class StateMode { Momentary, Latched }
```
Dùng trong `OverlayView.resolveMatchedIndex()` (mục 5.3):
- **`Momentary`** (mặc định): còn giữ cử chỉ mới còn hiển thị hiệu ứng; buông tay/đổi cử chỉ khác
  không khớp state nào → `matchedIndex = -1`, hiệu ứng biến mất.
- **`Latched`**: một khi khớp 1 state, **giữ nguyên** state đó (`latchedIndex`) kể cả khi tay biến mất
  khỏi khung hình hay đổi sang cử chỉ không khớp gì — chỉ đổi khi khớp một cử chỉ khác. Dùng cho
  `TestEffectBackgroundEffect.kt` (đổi nền) vì đổi nền liên tục theo từng frame lúc tay đang di
  chuyển giữa 2 cử chỉ sẽ giật hình.

⚠️ **`StateMode` chỉ ảnh hưởng `OverlayView` (chọn vẽ gì)** — không ảnh hưởng
`CameraRecordFragment.handleGesture()` (chọn phát tiếng gì). Hai nơi này nhận diện **độc lập** nhau,
xem mục 6.3 để hiểu vì sao và hậu quả khi sửa 1 bên mà quên bên kia.

### 1.4 `EffectRepository.all` và `effect/catalog/`

`EffectRepository.all` là 1 `List<EffectDefinition>` phẳng, lắp ráp từ 2 nguồn:
1. **Khai trực tiếp trong file** — các effect đơn giản (`black_background_with_monster`, `rock_on_ily`, `absolute_cinema_two_hand`, `heart_or_cross`).
2. **Gọi hàm factory từ `effect/catalog/*.kt`** — các effect phức tạp hơn (dùng `AnchorSource`/
   `SizeSource` khác mặc định, dùng `Procedural`, hoặc nhiều state):
   - `cameraShutterEffect()` — **5 state** (OK, peace, like, rock on, call), mỗi state 1 cử chỉ khác nhau nhưng cùng dùng chung 1 GIF + 1 tiếng chụp ảnh; `requiredNumHands = 2` dù cử chỉ là 1 tay
   - `testEffectBackgroundEffect()` — dùng `StateMode.Latched` + mỗi state đổi 1 nền khác nhau
   - `canvasDrawEffect()` — dùng `Procedural` + `EffectScope.shared()` (mục 3.3)
   - `earthEffect()`, `blackHoleEffect()` — dùng `AnchorSource`/`SizeSource` khác mặc định
   - `gojoEffect()` — dùng `Procedural` + composition (mục 3.7)

`findById(id)` dùng `.first { it.id == id }` — **crash nếu không tìm thấy id**. An toàn trong thực tế
vì `id` luôn đến từ chính `EffectRepository.all` qua Safe Args (`EffectListFragmentDirections
.actionEffectListToEffectPreview(effect.id)`, rồi `EffectPreviewFragmentDirections.actionEffectPreviewToCameraRecord(effect.id)`), không có đường nào truyền `id` tuỳ ý vào.

---

## 2. Nhận diện cử chỉ — `effect/gesture/`

**`GestureRecognizer`** (`fun interface`) — hợp đồng cực đơn giản:
```kotlin
fun interface GestureRecognizer {
    fun recognize(hands: List<List<NormalizedLandmark>>): Boolean
}
```
`hands` là danh sách bàn tay, mỗi bàn tay là 21 `NormalizedLandmark` (toạ độ 0..1 do MediaPipe trả
về, đánh số theo chuẩn HandLandmarker: 0=cổ tay, 4=đầu ngón cái, 8=đầu ngón trỏ, 9=khớp giữa ngón
giữa, 12/16/20=đầu ngón giữa/áp út/út...).

**`GestureUtils.kt`** — các hàm hình học nền tảng, mọi công thức trong `Gestures` đều build từ đây:

- `isFingerExtended(landmark, tip, pip, wrist)`: so `distance(tip, wrist)` với `distance(pip, wrist)`
  — ngón duỗi thì đầu ngón xa cổ tay hơn khớp giữa ngón. Áp dụng y hệt cho 4 ngón (chỉ, giữa, áp
  út, út) qua `isIndexExtended`/`isMiddleExtended`/`isRingExtended`/`isPinkyExtended`.
- `isThumbExtended`: khác công thức vì ngón cái gập theo chiều ngang chứ không dọc — so
  `distance(đầu ngón cái[4], gốc ngón út[17])` với `distance(gốc ngón cái[2], gốc ngón út[17])`.
  Comment trong code tự nhận: **công thức này chưa chặt với mọi kiểu bàn tay**, cần cẩn thận nếu
  thấy gesture liên quan ngón cái (thumbs up, rock on, ILY, OK) nhận sai.
- `thumbIndexPinchRatio`: tỉ lệ `distance(ngón cái, ngón trỏ) / palmLength` — dùng cho gesture 👌
  (`singleHandOkSign`, ngưỡng `< 0.35`).
- `fingerCurlRatio(mcp, pip, dip, tip)`: tỉ lệ đường thẳng đầu-cuối / tổng 3 đốt xương — ngón càng
  cong thì tỉ lệ càng nhỏ. Dùng cho `isIndexCurled`/`isPinkyCurled` (ngưỡng `< 0.88`) — riêng cho
  `twoHandsHeart` (mục dưới), **khác cơ chế** với `isFingerExtended` ở trên (dùng khoảng cách tuyệt
  đối tới cổ tay, không dùng tỉ lệ cong).
- `segmentsCross(a, b, c, d)`: kiểm tra 2 đoạn thẳng AB và CD có cắt nhau không (thuật toán cross
  product dấu) — dùng cho `twoHandsCrossedFingers` (dấu ❌ do 2 ngón tay chéo nhau thật, không phải
  chỉ gần nhau).

**`Gestures` object** — nơi lắp ráp công thức thành từng cử chỉ cụ thể, gán trực tiếp vào
`EffectState.gesture` trong `EffectRepository`/`catalog/`. Mỗi val là 1 `GestureRecognizer` lambda,
đọc trực tiếp trong `Gesture.kt` sẽ thấy công thức — bảng dưới chỉ tóm để tra nhanh xem cử chỉ nào
ứng với hàm nào (đọc code khi cần chỉnh ngưỡng):

| Tên | Ý nghĩa | Ghi chú |
|---|---|---|
| `anyHandPresent` | có tay bất kỳ | state mặc định/idle |
| `anyHandPointing` | bất kỳ tay nào đang chỉ (`isPointing`) | `gojoEffect()` dùng — không cần đúng 1 tay chỉ |
| `singleHandPalmOpen` / `singleHandFist` | ✋ / ✊ tay đầu tiên trong `hands` | dùng `landmark[0]` (tay 0), không phải tay cụ thể trái/phải |
| `singleHandPointing` | ☝️ chỉ trỏ, các ngón khác gập | |
| `singleHandPeaceSign` | ✌️ | |
| `singleHandThreeFingers` | 3 ngón trỏ+giữa+áp út | dùng cho `testEffectBackgroundEffect` |
| `singleHandThumbsUp` | 👍 | |
| `singleHandRockOn` | 🤘 | |
| `singleHandCall` | 🤙 | |
| `singleHandOkSign` | 👌 | duy nhất dùng `thumbIndexPinchRatio` thay vì `isThumbExtended` |
| `singleHandILoveYou` | 🤟 | |
| `bothHandsPalmOpen` / `bothHandsFist` | cần **cả 2** tay cùng cử chỉ | `hands.size >= 2 && hands.all { ... }` |
| `twoHandsHeart` | 🫶 trái tim 2 tay | dùng `fingerCurlRatio`, ngưỡng khoảng cách chuẩn hoá theo `palmLength` trung bình 2 tay |
| `twoHandsCrossedFingers` | ❌ | dùng `segmentsCross`, thử lần lượt 4 cặp ngón (trỏ/giữa/áp út/út), chỉ cần 1 cặp chéo là đủ |

Quan trọng: **thứ tự khai báo `states` trong `EffectDefinition` là độ ưu tiên khi nhiều gesture cùng
khớp** — cả `OverlayView.resolveMatchedIndex()` lẫn `CameraRecordFragment.handleGesture()` đều dùng
`states.indexOfFirst`/`states.firstOrNull`, tức khớp **cái đầu tiên tìm thấy**, không phải cái khớp
"tốt nhất".

---

## 3. Vẽ hiệu ứng lên tay — `effect/visual/`

### 3.1 `EffectVisual` — hợp đồng chung

```kotlin
interface EffectVisual {
    fun setActive(active: Boolean)              // bật/tắt khi state được/không được chọn
    fun draw(canvas: Canvas, frame: HandFrame)  // vẽ 1 frame
    fun onHandFrame(frame: HandFrame) = Unit    // optional: nhận dữ liệu tay MỖI KHI MediaPipe trả kết quả mới (không phải mỗi khi vẽ)
}
```
`createEffectVisual(context, asset, scope)` là factory `when` dispatch theo kiểu con của
`EffectAsset` (mục 1.1) — đây là **điểm nối duy nhất** giữa "khai báo" (`EffectAsset`) và "cách vẽ
thật" (`EffectVisual` cụ thể). Muốn thêm 1 loại asset mới: thêm nhánh `is EffectAsset.XXX -> ...`
ở đây.

Phân biệt `draw()` và `onHandFrame()`: `draw()` được gọi **mỗi frame vẽ** (60 lần/giây ở live, 25
lần/giây ở luồng ghi hình — 2 tần số khác nhau!), còn `onHandFrame()` chỉ gọi **mỗi khi MediaPipe
trả về kết quả mới** (khoảng ít hơn, tuỳ tốc độ xử lý AI). Hiệu ứng cần tích luỹ dữ liệu theo cử chỉ
(vẽ nét bút, phát hiện chạm) nên dùng `onHandFrame` để không xử lý logic tích luỹ 2 lần dư ở tần số
vẽ. Xem `StrokeVisual.onHandFrame` và `GojoVisual.onHandFrame`.

### 3.2 `HandFrame` — "hệ toạ độ dùng chung" giữa OverlayView và mọi EffectVisual

```kotlin
class HandFrame {
    var hands: List<List<NormalizedLandmark>> = emptyList()  // dữ liệu tay thô (toạ độ chuẩn hoá 0..1)
    var handedness: List<HandSide> = emptyList()              // Left/Right/Unknown, ĐÃ đảo theo mirror
    var cx = 0f; var cy = 0f; var r = 0f                       // tâm & bán kính vẽ, TÍNH SẴN bởi OverlayView
    var elapsedMs = 0L                                         // dùng bởi ProceduralVisual, xem 3.4
    fun px(normX: Float): Float   // đổi toạ độ chuẩn hoá (0..1) sang pixel canvas thật, có xử lý mirror
    fun py(normY: Float): Float
}
```
`OverlayView.drawFrame()` gọi `frame.setProjection(mirrorX, imgWidth, imgHeight, localScale,
offsetX, offsetY)` **mỗi frame** trước khi giao cho visual vẽ — nghĩa là **mọi `EffectVisual` không
tự tính toạ độ pixel, chỉ gọi `frame.px()`/`frame.py()`** để đổi từ toạ độ chuẩn hoá MediaPipe sang
pixel canvas đang vẽ (canvas live và canvas ghi hình có kích thước khác nhau, `HandFrame` che giấu
sai khác này). Đây là lý do các `EffectVisual` (như `GojoVisual`, `StrokeVisual`) có thể dùng
chung logic vẽ cho cả live lẫn recording mà không cần biết đang vẽ lên canvas nào.

### 3.3 `EffectScope` — chia sẻ state giữa các `EffectState` trong CÙNG 1 effect

```kotlin
class EffectScope {
    private val models = HashMap<String, Any>()
    fun <T : Any> shared(key: String, create: () -> T): T = models.getOrPut(key, create) as T
}
```
Dùng khi nhiều `EffectState` (nhiều cử chỉ khác nhau) của cùng 1 effect cần **thao tác trên cùng
một dữ liệu**, ví dụ `canvasDrawEffect()`:
- state `stroke` (chỉ trỏ) → `StrokeVisual` **ghi thêm điểm** vào `StrokeModel`
- state `stroke_clear` (nắm tay) → `ClearOnActivate` **xoá** `StrokeModel`
- state `idle_skeleton` (bất kỳ) → `SkeletonOnlyVisual` **không đụng** `StrokeModel`

Cả 3 state đều gọi `scope.shared("stroke_model") { StrokeModel() }` — **cùng 1 key** nên nhận lại
**cùng 1 instance** `StrokeModel`, dù chúng là 3 `EffectVisual` khác nhau được tạo ở 3 lần gọi
`createEffectVisual()` riêng biệt trong `OverlayView.setEffect()`.

⚠️ **Có 2 `EffectScope` riêng biệt** — `liveScope` và `recordingScope` — được tạo mới **mỗi lần**
`OverlayView.setEffect()` chạy (tức mỗi lần vào màn quay với 1 effect). Nghĩa là: `StrokeModel` của
`liveVisuals` và `StrokeModel` của `recordingVisuals` là **2 instance khác nhau, độc lập hoàn
toàn**. Chúng chỉ "giống nhau" trên thực tế vì cùng nhận cùng 1 luồng `onHandFrame()` input (xem
mục 5.2) — không phải vì chia sẻ bộ nhớ.

### 3.4 `ProceduralVisual` — lớp cha cho hiệu ứng vẽ bằng code có "thời gian đã active"

```kotlin
abstract class ProceduralVisual : EffectVisual {
    @Volatile private var activateAtMs = 0L
    final override fun setActive(active: Boolean) { ... }  // ghi nhận mốc bắt đầu active
    final override fun draw(canvas: Canvas, frame: HandFrame) {
        frame.elapsedMs = ... // tính từ activateAtMs
        onDraw(canvas, frame)  // subclass override cái này, KHÔNG override draw() trực tiếp
    }
    protected abstract fun onDraw(canvas: Canvas, frame: HandFrame)
}
```
`@Volatile` trên `activateAtMs` vì `setActive()` được gọi từ `OverlayView.setResult()` (chạy trên
coroutine main-thread khi nhận kết quả MediaPipe), còn `draw()` có thể được gọi từ
`recordingFrameThread` (khi `forRecording=true`) — 2 thread khác nhau đọc/ghi cùng biến.
`StrokeVisual` là subclass hiện có duy nhất của lớp này.

### 3.5 3 cách vẽ ảnh có sẵn — `visual/image/`

| Class | Cách hoạt động |
|---|---|
| `StaticImageVisual` | decode bitmap 1 lần, mỗi `draw()` chỉ tính `Matrix` scale theo `frame.r` rồi `drawBitmap` |
| `AnimatedGifVisual` | dùng `AnimatedImageDrawable` (API 28+) tự chạy animation nội bộ; mỗi `draw()` **render GIF vào 1 buffer bitmap 256×256 cố định** (`renderToBuffer()`) rồi mới scale buffer đó ra theo `frame.r` — làm vậy để chi phí scale luôn cố định bất kể ảnh gốc to nhỏ. `oneShot=true` (dùng bởi `GojoVisual`) đăng ký `AnimationCallback.onAnimationEnd` để set cờ `finished`, cho phép code ngoài hỏi `hasFinishedPlaying()` |
| `SpriteSheetVisual` | tự tính `frameIndex` từ thời gian trôi qua kể từ `setActive(true)` (`activatedAtMs`), cắt đúng ô `src: Rect` trong sheet rồi vẽ ra `dst: RectF` theo `frame.r`. **Hiện chưa có effect nào trong `EffectRepository.all` dùng class này** |

### 3.6 Hiệu ứng vẽ canvas thủ công — `visual/canvas/drawcanvas/`

Dùng bởi `canvasDrawEffect()`. 4 file phối hợp qua `EffectScope` (mục 3.3):

- **`StrokeModel`**: buffer 2 mảng `FloatArray` (xs, ys) tăng gấp đôi khi đầy, giới hạn
  `MAX_POINTS=2000` (khi đầy thì bỏ điểm cũ nhất — dịch mảng bằng `System.arraycopy`).
  `addPoint()` lọc điểm quá gần điểm trước (`MIN_DIST_NORM=0.005`) để tránh path dày đặc vô ích.
  `version` tăng mỗi lần đổi dữ liệu — đây là cơ chế để `StrokeVisual` biết có cần dựng lại `Path`
  Android hay không (dựng `Path` từ hàng nghìn điểm mỗi frame sẽ rất tốn). **Có `synchronized(lock)`
  nội bộ** vì `addPoint()` được gọi từ `onHandFrame()` (main thread, qua `OverlayView.setResult()`)
  còn `withPoints()` được gọi từ `onDraw()` — với `recordingVisuals` thì `onDraw()` chạy trên
  `recordingFrameThread`, khác thread với `onHandFrame()` → cần khoá.
- **`StrokeVisual`** (kế thừa `ProceduralVisual`): `onHandFrame()` lấy đầu ngón trỏ (`hand[8]`) đẩy
  vào `StrokeModel`. `onDraw()` chỉ dựng lại `Path` khi `model.version != builtVersion` (cache),
  luôn vẽ `path` + gọi `HandSkeletonRenderer` để hiện khung xương tay pha trong lúc vẽ.
- **`ClearOnActivate`**: không kế thừa `ProceduralVisual` (implement `EffectVisual` trực tiếp) —
  `setActive(true)` khi **chuyển từ không active sang active** (cạnh lên, `wasActive` tự theo dõi)
  thì gọi `model.clear()`. Vẽ khung xương tay khi active.
- **`SkeletonOnlyVisual`**: chỉ vẽ khung xương, không làm gì khác — dùng làm state "idle" cuối
  danh sách của `canvasDrawEffect()` (khớp `Gestures.anyHandPresent`).
- **`HandSkeleton.kt`** chứa `HAND_CONNECTIONS` (danh sách cặp landmark nối với nhau tạo hình bàn
  tay) và `HandSkeletonRenderer` — class dùng **chung bởi cả 3 chỗ trên** để đảm bảo màu/độ dày
  khung xương đồng nhất (đọc comment trong file: trước khi gom lại, 3 nơi khai `Paint` màu hơi khác
  nhau khiến khung xương đổi màu khi đổi trạng thái — bug đã fix bằng cách gom vào 1 class).
  `strokeWidth`/`dotRadius` tính theo **tỉ lệ `canvas.width`**, không dùng pixel cố định — vì canvas
  ghi hình (~720px) hẹp hơn canvas live (~1080px), pixel cố định sẽ vẽ dày gấp ~1.5 lần trong video
  so với lúc xem live.

### 3.7 Hiệu ứng Gojo — `visual/canvas/gojo/`

Dùng bởi `gojoEffect()`. Minh hoạ cách **composition 1 `EffectVisual` bên trong 1 `EffectVisual`
khác**:
- **`GojoModel`**: chỉ 1 field `@Volatile var wasTouching: Boolean` — trạng thái "2 đầu ngón trỏ có
  đang chạm nhau không", chia sẻ qua `EffectScope` giống `StrokeModel`.
- **`GojoVisual`**: tự khởi tạo **một `AnimatedGifVisual` con** (`mergeAnim`) bên trong constructor
  của chính nó — dùng lại logic vẽ GIF một lần (`oneShot=true`) thay vì viết lại từ đầu. Luồng xử lý:
  1. `onHandFrame()`: tính khoảng cách 2 đầu ngón trỏ chia `palmLength` trung bình 2 tay, so ngưỡng
     `TOUCH_RATIO_THRESHOLD=0.5f`. Cạnh lên (`touching && !wasTouching`) thì `mergeAnim.setActive(true)`
     — bắt đầu phát animation "sáp nhập".
  2. `draw()`: nếu đang `wasTouching` → nếu `mergeAnim` **chưa** phát xong thì vẽ animation sáp
     nhập; phát xong rồi (`hasFinishedPlaying()`) thì vẽ quả cầu tím cố định thay thế. Nếu **không**
     touching → vẽ quả cầu xanh/đỏ riêng ở đầu ngón trỏ mỗi tay đang chỉ (`isPointing`), màu theo
     `frame.handedness` (trái=xanh, phải=đỏ).

---

## 4. Vẽ nền — `effect/background/`

`BackgroundRenderer` interface (`draw`, `setActive`, `release`) + factory `createBackgroundRenderer`
dispatch theo `EffectBackground` (giống mẫu `EffectVisual`/`EffectAsset` ở mục 3.1). 3 implementation:

- **`SolidBackgroundRenderer`**: `drawRect` đơn giản với `Paint` màu cố định.
- **`ImageBackgroundRenderer`**: decode bitmap ở chế độ `RGB_565` (nhẹ hơn ARGB_8888, chấp nhận mất
  chút chất lượng màu để đổi lấy bộ nhớ — nền thường không cần độ chi tiết cao) rồi scale-crop-center
  (dùng `max(cw/bw, ch/bh)` để lấp đầy canvas, crop phần dư — kiểu "center crop" quen thuộc). Cache
  `Matrix` theo `lastW`/`lastH`, chỉ tính lại khi kích thước canvas đổi (live vs recording đổi qua
  lại khi bắt đầu/dừng ghi).
- **`AnimatedBackgroundRenderer`**: tương tự `AnimatedGifVisual` (mục 3.5) — render GIF vào 1 buffer
  bitmap kích thước **gốc** của GIF (khác với `AnimatedGifVisual` dùng buffer cố định 256×256), rồi
  scale buffer lên full canvas mỗi frame.

`OverlayView.setEffect()` cache các `BackgroundRenderer` theo `EffectBackground` (dùng `HashMap`,
key là chính object `EffectBackground` — sealed `data class` nên so sánh bằng giá trị) để 2 state
khác nhau dùng chung 1 nền không tạo `BackgroundRenderer` (và decode ảnh) 2 lần.

---

## 5. `OverlayView.kt` — đi từng hàm

Đây là `View` custom vẽ đè lên `PreviewView` của CameraX (layout `fragment_camera_record.xml`),
đồng thời **cũng là nơi vẽ hình cho canvas ghi hình** (không phải chỉ vẽ live) — 1 class, 2 chế độ
vẽ, phân biệt bằng tham số `forRecording` trong `drawFrame()`.

### 5.1 `setEffect(effect: EffectDefinition)` — gọi 1 lần khi vào màn quay

Dựng **song song 2 bộ** mọi thứ — hậu tố `live*`/`recording*`:
```kotlin
val liveScope = EffectScope(); val recordingScope = EffectScope()
liveVisuals = effect.states.map { st -> st.asset?.let { createEffectVisual(context!!, it, liveScope) } }
recordingVisuals = effect.states.map { st -> st.asset?.let { createEffectVisual(context!!, it, recordingScope) } }
```
Lý do tách hẳn 2 bộ `EffectVisual`/`BackgroundRenderer` cho live và recording (thay vì dùng chung
1 bộ, vẽ 2 lần): **live chạy trên main thread (`onDraw`), recording chạy trên
`recordingFrameThread`** — nếu dùng chung instance, các state nội bộ có tính thời gian (`activeAtMs`
trong `ProceduralVisual`, animation state trong `AnimatedImageDrawable`) sẽ bị 2 thread đọc/ghi
tranh chấp và fps của canvas này ảnh hưởng canvas kia. Cái giá phải trả: bộ nhớ dùng gấp đôi (2 bitmap
GIF buffer, 2 bitmap decode ảnh...) — chấp nhận được vì asset hiệu ứng vốn đã được nén nhỏ (xem
`Asset_Format_Guidelines.md`).

`releaseBackgrounds()` được gọi **đầu hàm** — nghĩa là gọi `setEffect()` lần 2 (đổi effect) sẽ giải
phóng nền cũ trước khi dựng nền mới, tránh leak `AnimatedImageDrawable`.

### 5.2 `setResult(handResult, imgWidth, imgHeight)` — gọi mỗi khi có kết quả MediaPipe mới

Đây là **nơi DUY NHẤT** trong `OverlayView` chạy nhận diện cử chỉ (đọc comment trong code — cố tình
làm vậy để cả live và recording không nhận diện lại 2 lần dư thừa, và tránh việc 2 thread cùng ghi
`matchedIndex`):
```kotlin
val matched = resolveMatchedIndex(hands)   // xem 5.3
matchedIndex = matched                     // @Volatile — đọc lại ở drawFrame() từ thread khác
liveVisuals.forEachIndexed { i, v -> v?.setActive(i == matched) }
recordingVisuals.forEachIndexed { i, v -> v?.setActive(i == matched) }
if (matched != -1 && hands.isNotEmpty()) {
    liveVisuals.getOrNull(matched)?.onHandFrame(gestureFrame)
    recordingVisuals.getOrNull(matched)?.onHandFrame(gestureFrame)
}
invalidate()   // xin vẽ lại (live) — recording KHÔNG dựa vào invalidate(), nó có vòng lặp riêng
```
Gọi `setActive()` cho **mọi** visual (kể cả cái không khớp, để nó nhận `false` và tự dừng animation)
— đây là lý do mỗi `EffectVisual.setActive(false)` đều phải dừng đúng animation/tài nguyên của
riêng nó (`AnimatedGifVisual.setActive(false)` gọi `drawable.stop()`).

### 5.3 `resolveMatchedIndex(hands)` — hiện thực hoá `StateMode`

```kotlin
val liveMatch = currentEffect.states.indexOfFirst { it.gesture.recognize(hands) }
return when (currentEffect.stateMode) {
    StateMode.Momentary -> liveMatch
    StateMode.Latched -> { if (liveMatch != -1) latchedIndex = liveMatch; latchedIndex }
}
```
Xem lại mục 1.3 để hiểu ý nghĩa 2 chế độ.

### 5.4 `drawFrame(canvas, handResult, mirrorX, forRecording)` — hàm vẽ dùng chung cho cả 2 chế độ

Thứ tự trong hàm, theo đúng thứ tự code:
1. **Chọn & vẽ nền**: ưu tiên `state.background` của state đang khớp, fallback về
   `effect.background` mặc định nếu state không có nền riêng hoặc không state nào khớp.
   `allBackgrounds.forEach { it.setActive(it === background) }` — dùng **so sánh instance** (`===`)
   để chỉ nền đang thật sự hiển thị mới chạy animation, các nền khác (cache nhưng không dùng lúc
   này) bị tạm dừng.
2. Nếu không có tay hoặc `matchedIndex == -1` → **return sớm, chỉ có nền, không vẽ gì thêm**.
3. **Tính phép chiếu toạ độ** (`localScale`, `localOffsetX/Y`) — dùng công thức "center crop":
   `max(targetW/imgWidth, targetH/imgHeight)` để ảnh camera lấp đầy canvas (có thể crop 2 bên).
   `targetW/targetH` là kích thước canvas **đang vẽ** (khác nhau giữa live view và canvas ghi hình
   — đây chính là lý do phải tính lại mỗi frame thay vì cache).
4. **Đảo trái/phải `handedness`** nếu `mirrorX=true` — vì camera trước bị lật gương, "tay trái thật"
   hiện lên màn hình ở phía tay phải người xem, MediaPipe trả nhãn theo ảnh gốc (chưa lật) nên phải
   tự đảo lại nếu muốn nhãn khớp với cảm giác trực quan của người dùng.
5. **Tính `frame.cx/cy`** theo `anchorSource` của state đang khớp (4 nhánh `when`, xem mục 1.2).
6. **Tính `frame.r`** theo `sizeSource` của state đang khớp (3 nhánh `when`, xem mục 1.2).
7. Gọi `visuals.getOrNull(matchedIndex)?.draw(canvas, frame)`.

`mirrorX` luôn được truyền `true` từ cả `onDraw()` (live) lẫn `CameraRecordFragment` (recording,
tham số cứng trong lời gọi `overlay.drawFrame(canvas, handResult, mirrorX = true, forRecording =
true)`) — camera trước luôn cần mirror, hiện **không có đường nào truyền `false`**.

### 5.5 `onDraw(canvas)` — vòng lặp vẽ live

```kotlin
override fun onDraw(canvas: Canvas) {
    drawFrame(canvas, result, mirrorX = true, forRecording = false)
    postInvalidateOnAnimation()   // tự xin vẽ lại frame tiếp theo — vòng lặp vô hạn theo VSync
}
```
`postInvalidateOnAnimation()` khiến `onDraw` tự gọi lại liên tục theo nhịp làm mới màn hình (thường
60Hz) — **độc lập hoàn toàn với tốc độ MediaPipe trả kết quả**. Nếu MediaPipe chưa có kết quả mới,
`result` vẫn là kết quả cũ → hiệu ứng "đứng hình" ở vị trí cũ cho tới khi có kết quả mới, không phải
biến mất — đây là hành vi cố ý, không phải bug.

---

## 6. `CameraRecordFragment.kt` — đi qua vòng đời

### 6.1 Chuẩn bị (`onCreate`, `onViewCreated`)

`onCreate`: `currentEffect = EffectRepository.findById(args.effectId)` — `args` là `navArgs()` sinh
bởi Safe Args từ `<argument android:name="effectId" app:argType="string"/>` trong `nav_graph.xml`.

`onViewCreated` chuẩn bị **toàn bộ dữ liệu âm thanh trước khi vào camera**, để lúc phát không bị
giật do I/O:
- `soundEffectPlayer = SoundEffectPlayer(context, currentEffect.states.mapNotNull { it.soundRes })`
  — load **trước** mọi file âm thanh của effect này vào `SoundPool` (mục 7.1).
- `statePcmMap: Map<String, ShortArray>` — với **mỗi** state có `soundRes`, gọi
  `loadWavPcm(context, soundRes)` để đọc sẵn dữ liệu PCM thô (mục 7.3), key là `state.id`. Map này
  dùng để feed vào `AudioMixer` khi ghi hình (khác hẳn mục đích với `soundEffectPlayer`, vốn chỉ
  để phát ra loa lúc live).
- Nếu `currentEffect.bgm != null`: vừa load PCM (`bgmPcm`, để trộn vào video khi ghi) **vừa** tạo
  `BgmPlayer` (để phát ra loa ngay lúc đứng ở màn quay, trước khi bấm nút ghi) — **2 đường hoàn
  toàn tách biệt** dùng cùng 1 file tài nguyên, xem mục 7.2.
- `overlay.setEffect(currentEffect)` — trigger mục 5.1.
- **Chrome UI của màn quay** (`fragment_camera_record.xml`, `FrameLayout`: camera/overlay nằm dưới, 2 thanh `LinearLayout`
  nổi lên trên): top bar (layout chung `view_effect_top_bar` qua `<include>`, gồm `btn_back` + `text_effect_name`, không có nút đổi camera — app cố ý không làm; truy cập qua `binding.layoutCameraTopBar.root/.btnBack/.textEffectName`)
  và bottom bar 3 nút `[btn_effect] [btn_toggle_record] [btn_action]`. Inset hệ thống được áp lên **2 thanh bọc
  ngoài** (`layoutCameraTopBar` top, `layoutCameraBottomContainer` bottom), không áp lên từng nút bên trong.
  `layoutCameraBottomContainer` là khối dọc neo đáy chứa `tvRecordingTimer` (ẩn mặc định) rồi tới hàng nút
  `layoutCameraBottomBar` — nhờ vậy đồng hồ luôn nằm ngay trên nút record mà không hard-code chiều cao nút,
  và khi nó hiện ra thì chỉ đẩy chính nó lên, nút record đứng yên.
  - `hideChromeWhileRecording()`: gọi ngay khi bắt đầu ghi — ẩn top bar (`GONE`) và 2 cột Effect/Action
    (`INVISIBLE`, không phải `GONE`, vì chúng là 2 ô weight 1 kẹp 2 bên nút record; `GONE` sẽ làm nút record
    lệch tâm). Cố ý **không có hàm hiện lại**: dừng ghi hợp lệ luôn rời màn (preview/pop), hiện lại chỉ gây nháy.
    Đồng hồ vẫn do `startRecordingTimerUI()`/`stopRecordingTimerUI()` bật/tắt như cũ.
  - `bindEffectInfo(effect: EffectDefinition?)`: đổ tên + thumbnail; nhận `null` / thumbnail lỗi thì rơi về nền đen
    của `btn_effect` (chuẩn bị cho trường hợp vào màn camera khi chưa có effect nào).
  - `navigateBack()`: logic chung của nút back trên top bar **và** `backCallback` (back hệ thống). Đang ghi →
    `stopRecordingAndGoToPreview(ignoreMinDuration = true, showSavedToast = true)`; không ghi → `popBackStack()`.
    `btnBack` bị disable cùng lúc với `btnToggleRecord` khi đang dừng ghi (tránh pop thẳng làm mất video).
  - `btn_effect` → `openEffectPicker()`: `navigate(actionCameraRecordToEffectPicker(currentEffect.id))`, chốt cửa
    `currentDestination` để chống bấm đúp. Action này **không** `popUpTo` nên camera nằm lại back stack ở trạng thái
    "instance sống, view chết" → `resetGestureState()` (`onViewCreated`) reset `lastStateId`/`pendingState`/
    `pendingStateSince`/`activeEffect`, còn `onDestroyView` null hoá `latestCameraBitmap`/`latestHandResult` để không
    giữ bitmap nặng trong RAM. Không reset thì quay lại từ màn chọn (huỷ) sẽ: (1) không phát lại tiếng của cử chỉ
    đang giơ (debounce coi là đã kích hoạt), (2) cài `activeEffect` cũ vào video nếu bấm Record ngay.
  - `btn_action` → `GestureGuideDialog(requireContext(), currentEffect).show()` (từ commit `ddcadce`, trước đó
    chưa gắn logic). Dialog (`ui/camera/GestureGuideDialog.kt`, layout `dialog_gesture_guide.xml`, nền trong suốt +
    dim 0.6f) liệt kê bằng `GridLayoutManager(2 cột)` + `GestureAdapter` tất cả cử chỉ của `effect.states`,
    `.distinct()` để gộp các state dùng chung 1 gesture (khác `soundRes`) thành 1 dòng. Map gesture →
    tên/icon hiển thị nằm ở `effect/gesture/GestureDisplay.kt` (`gestureDisplayMap`, hàm mở rộng
    `GestureRecognizer.toDisplay()`) — **mọi gesture đang dùng chung 1 icon tạm `ic_action`**, chưa có
    bộ icon riêng từng cử chỉ, không phải bug. Thêm gesture mới vào `object Gestures` (mục 2) mà không
    thêm vào `gestureDisplayMap` thì dialog sẽ rơi về `unknownGestureDisplay` (fallback).
- Nếu `currentEffect.background != null`: ẩn `PreviewView` (`binding.preview.visibility =
  View.INVISIBLE`) — hiệu ứng có nền riêng (như `canvasDrawEffect`, `testEffectBackgroundEffect`)
  thì không cần thấy hình camera thật phía sau, `OverlayView` tự vẽ nền đè lên.

**`EffectPickerFragment`** (`ui/effectpicker/`, layout `fragment_effect_picker.xml`, item `item_effect_picker.xml`):
- Top bar riêng (back + tiêu đề có gạch chân `horizontal_divider` + nút tick `ic_confirm`); vì không nằm trong
  `MainActivity.destinationsWithMainChrome` nên top bar/bottom nav chung tự ẩn. Tiêu đề căn giữa bằng constraint
  vào cả 2 mép (không phụ thuộc bề rộng 2 nút). Inset: `applySystemBarsInsetsMargin(top)` cho top bar,
  `applySystemBarsInsetsPadding(bottom)` cho RecyclerView.
- `selectedEffectId` (field của instance, khởi tạo từ `args.currentEffectId`, `null` nếu id rỗng/không có trong
  repository → tick bị khoá + mờ). Adapter tự cập nhật viền bằng payload (`notifyItemChanged(pos, payload)`)
  để không bind lại ảnh và không bị cross-fade nháy.
- Item cao theo tỉ lệ cột (`H,20:21`) thay vì 180dp cố định như `item_effect.xml`; viền là `foreground`
  `selector_effect_picker_border` (chỉ `state_selected` mới có viền, dùng lại `border_effect_decorative`).
  Tên effect (`text_name`) hiện 1 dòng, `ellipsize=end` (`maxLines=1`): tên dài bị cắt bằng “…”. Ở `item_effect.xml` (màn danh sách)
  tên còn chừa `layout_marginEnd=52dp` (14dp lề + 26dp icon tim + 12dp cách) để “…” không nằm dưới icon tim — phải khai margin
  từng cạnh (`Start/Top/End/Bottom`), không dùng chung `layout_margin` vì `layout_margin` ghi đè các margin riêng lẻ.
- Tick (`confirm()`): id == `currentEffectId` → `popBackStack()` (camera cũ còn ở dưới, không tạo lại); khác →
  `navigate(actionEffectPickerToEffectPreview(id))` với `popUpTo` camera inclusive (camera mới chỉ được tạo khi bấm Create ở màn xem trước). Back (nút/hệ thống) = huỷ.
  Cả `cancel()` và `confirm()` đều chốt cửa `currentDestination`: bấm đúp mà không chốt thì `popBackStack()` lần 2
  sẽ pop luôn camera bên dưới.

**`EffectPreviewFragment`** (`ui/effectpreview/`, layout `fragment_effect_preview.xml`):
- Luôn được mở từ `effectListFragment` (bấm 1 effect) hoặc `effectPickerFragment` (tick effect khác). Nhận `effectId` qua Safe
  Args, `onCreate` tra `EffectRepository.findById`. Không nằm trong `destinationsWithMainChrome` nên top bar/bottom nav chung tự ẩn.
- Media minh hoạ phủ kín cả màn (`ImageView` `centerCrop`, nền root đen) nằm dưới cùng; top bar dùng `view_effect_top_bar` (chung với
  màn quay); nút Create (`btn_create`, nền `bg_create_button` cyan, chữ đen) nổi góc dưới phải, lề 24dp + inset thanh điều hướng.
- **Media hiện là TẠM**: `DEMO_PREVIEW_RES = R.drawable.black_hole` cho mọi effect. Decode bằng `ImageDecoder.decodeDrawable` →
  `AnimatedImageDrawable` (`REPEAT_INFINITE`), `start()` ở `onStart`, `stop()` ở `onStop`; `previewDrawable` **phải null hoá ở
  `onDestroyView`** vì drawable giữ callback về ImageView (giữ lại = leak cả cây view). Khi có media thật theo từng effect thì
  khai vào `EffectDefinition`; nếu là video (mp4) thì đổi sang Media3/ExoPlayer như `RecordedPreviewFragment`.
- Create → `actionEffectPreviewToCameraRecord(effect.id)` với `popUpTo` **chính màn này** inclusive: màn xem trước bị gỡ, nên back ở
  camera vẫn về `effectList` và các luồng sau camera (recordedPreview, H11...) không đổi. Back ở màn này = `popBackStack()`.
  Cả `goBack()` và `create()` chốt cửa `currentDestination` (cùng lý do picker).

### 6.2 Xin quyền → khởi tạo MediaPipe → mở camera

`checkAndRequestPermission()` → (nếu đã có quyền CAMERA) → `setupMediaPipe()` + `startCamera()`.

`setupMediaPipe()`:
```kotlin
handLandmarker = HandLandmarkerProvider.getOrCreate(requireContext(), currentEffect.requiredNumHands)
viewLifecycleOwner.lifecycleScope.launch {
    HandLandmarkerProvider.results.collect { (result, inputImage) ->
        latestHandResult = result
        overlayView?.setResult(result, inputImage.width, inputImage.height)  // → OverlayView mục 5.2
        handleGesture(result)   // → mục 6.3, ĐỘC LẬP với OverlayView
    }
}
```
Coroutine này chạy trên **main thread** (không chỉ định `Dispatchers` khác, `lifecycleScope` mặc
định `Dispatchers.Main`), nhận kết quả qua `SharedFlow` mà `HandLandmarkerProvider` phát ra từ
callback `setResultListener` của MediaPipe (chạy trên thread nội bộ của MediaPipe, `tryEmit` an
toàn cross-thread nhờ `MutableSharedFlow`).

`startCamera()`: dựng `ImageAnalysis` chạy trên `backgroundExecutor` (thread riêng, KHÔNG phải main
thread) — đây là "thread camera" nhắc tới trong README/`Camera_X_Hand_Landmarker.md`. Mỗi frame:
convert `ImageProxy` → `Bitmap`, xoay theo `rotationDegrees`, ghi vào `@Volatile latestCameraBitmap`,
rồi gọi `handLandmarker?.detectAsync(mpImage, timestamp)` (bất đồng bộ — không block thread này chờ
kết quả, kết quả tới sau qua `SharedFlow` ở trên). `cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA`
— **cố định camera trước**, không có UI đổi camera sau (đúng như README mục "việc cố ý hoãn").

### 6.3 `handleGesture(result)` — bộ nhận diện THỨ HAI, độc lập với `OverlayView`

```kotlin
val matchedState = currentEffect.states.firstOrNull { it.gesture.recognize(hands) }
```
Đây là **lần nhận diện gesture thứ 2** trên cùng 1 `result` (lần 1 là `OverlayView.setResult()` →
`resolveMatchedIndex()`). Mục đích khác nhau hoàn toàn:
- `OverlayView` nhận diện để biết **vẽ visual nào**.
- `CameraRecordFragment.handleGesture()` nhận diện để biết **phát/trộn âm thanh nào**, kèm cơ chế
  **debounce** mà `OverlayView` không có:

```kotlin
if (matchedState.id != pendingState) {
    pendingState = matchedState.id; pendingStateSince = now       // ghi nhận ứng viên mới
} else if (lastStateId != matchedState.id && now - pendingStateSince >= DEBOUNCE_MS) {
    lastStateId = matchedState.id                                  // xác nhận, chỉ kích hoạt 1 LẦN
    // ... trigger âm thanh
}
```
`DEBOUNCE_MS = 200` — cử chỉ phải **giữ ổn định 200ms liên tục** mới coi là "đã chuyển sang state
mới" và kích âm thanh — tránh vô tình phát tiếng liên tục khi tay đang run rẩy/chuyển động qua lại
giữa 2 cử chỉ (khác với vẽ visual, vốn nên phản hồi ngay lập tức không cần debounce).

Khi trigger:
```kotlin
val pcm = statePcmMap[matchedState.id] ?: return
activeEffect = ActiveEffect(pcm, SystemClock.elapsedRealtime())  // nhớ lại để nếu BẮT ĐẦU GHI muộn hơn còn nối tiếp đúng chỗ
videoRecorder?.audioMixer?.triggerEffect(pcm)   // nếu đang ghi hình → feed thẳng vào AudioMixer (mục 8.3)
soundEffectPlayer.playForSound(soundRes)         // luôn phát ra loa qua SoundPool, BẤT KỂ có đang ghi hay không
```
`activeEffect` (biến `ActiveEffect(pcm, startedAtMs)`) tồn tại để giải quyết trường hợp: người dùng
làm cử chỉ **trước khi** bấm nút ghi — khi `toggleRecording()` bắt đầu ghi, nó đọc `activeEffect` và
tính `elapsedSamples` để `audioMixer.triggerEffect(pcm, elapsedSamples)` bắt đầu **từ đúng vị trí
giữa âm thanh** thay vì phát lại từ đầu — tránh giật/lặp tiếng khi vừa bắt đầu ghi.

`clearActiveEffect()`: khi tay biến mất hoặc không khớp state nào (chỉ khi `StateMode.Momentary`
— dòng `if (currentEffect.stateMode == StateMode.Momentary) clearActiveEffect()`). Với hiệu ứng
`Latched`, **âm thanh KHÔNG tự tắt** khi buông tay (khớp với hành vi visual cũng giữ nguyên) —
nhất quán giữa 2 bộ nhận diện dù chúng chạy tách biệt.

### 6.4 Ghi hình — `toggleRecording()` và `startRecordingFrameLoop()`

`toggleRecording()` khi **bắt đầu** ghi:
```kotlin
val (recW, recH) = computeRecordingSize(binding.overlay.width, binding.overlay.height)
videoRecorder = VideoRecorder(requireContext(), recW, recH, 25).apply {
    onFirstFrame = { startRecordingTimerUI(); bgmPlayer?.startFromBeginning() }
    audioMixer.setBgm(bgmPcm, currentEffect.bgm?.gainPercent ?: 50)
    audioMixer.resetBgmPos()
    start()
    activeEffect?.let { ... audioMixer.triggerEffect(it.pcm, elapsedSamples) }  // xem 6.3
}
startRecordingFrameLoop(25)
```
`computeRecordingSize()`: hạ độ phân giải ghi hình xuống **cạnh ngắn tối đa 720px** (giữ tỉ lệ, làm
tròn số chẵn `it - it % 2` vì `MediaCodec`/H.264 yêu cầu kích thước chẵn) — độc lập với độ phân giải
hiển thị live (thường 1080px), lý do hiệu năng (xem `Perf_Notes.md`).

`startRecordingFrameLoop(fps=25)` — chạy trên **1 `Thread` riêng** (`recordingFrameThread`), vòng
lặp thủ công tự canh nhịp (không dùng `Handler.postDelayed`) để bám sát `intervalMs = 1000/fps`:
```kotlin
while (videoRecorder?.isRecording == true) {
    val frameStartNs = System.nanoTime()
    videoRecorder?.pushFrame { canvas ->
        if (!effectHasBackground) { /* vẽ bitmap camera đã lật gương lên canvas trước */ }
        overlay.drawFrame(canvas, handResult, mirrorX = true, forRecording = true)   // → OverlayView 5.4
    }
    if (videoRecorder?.writeFailed == true) { /* dừng ghi, báo hết dung lượng */ }
    val sleepMs = (intervalMs - workNs/1_000_000).coerceAtLeast(0)
    if (sleepMs > 0) Thread.sleep(sleepMs)
}
```
Đọc `latestCameraBitmap` và `latestHandResult` — cả 2 đều `@Volatile`/field thường được ghi từ
thread khác (`backgroundExecutor` cho bitmap, main-thread coroutine cho hand result) — **luôn lấy
giá trị MỚI NHẤT tại thời điểm đọc, chấp nhận rớt frame cũ** — đây chính là "tách thread hiển thị
khỏi thread ghi hình" nhắc trong README/`HandAr_Refactor_Plan.md`. Nếu `effectHasBackground` (effect
có nền riêng, như `canvasDrawEffect`) thì **không vẽ bitmap camera** — chỉ vẽ nền + hiệu ứng, camera
thật không xuất hiện trong video (khớp với việc ẩn `PreviewView` ở mục 6.1).

`stopRecordingAndGoToPreview()`: khoá `btnToggleRecord` **và** `btnBack` (`isEnabled = false`, nút back đã bị ẩn khi ghi nhưng vẫn khoá cho chắc) rồi gọi `recorder.stop { ... }` (callback bất đồng bộ, xem
`VideoRecorder.stop()` mục 8.1) — trong callback, nếu `!recorderToStop.hadValidOutput()` (muxer
chưa từng `start()` được, tức là chưa nhận đủ audio+video track — ghi quá ngắn hoặc lỗi) thì
**xoá file rác** và quay lại màn trước thay vì điều hướng sang preview với file hỏng.

---

## 7. Âm thanh live (không liên quan file ghi hình) — `audio/`, `utils/AudioUtils.kt`

### 7.1 `SoundEffectPlayer` — phát tiếng hiệu ứng ra loa

Dùng `SoundPool` (không phải `MediaPlayer`) vì cần **độ trễ thấp, phát nhiều lần liên tục** khi đổi
cử chỉ nhanh. `setMaxStreams(1)` — chỉ 1 tiếng hiệu ứng phát cùng lúc (đổi cử chỉ thì tiếng cũ nên
dừng, không chồng lẫn). `soundIds: Map<Int, Int>` load **tất cả** `soundResList` (toàn bộ `soundRes`
của effect hiện tại) ngay lúc khởi tạo — lý do `CameraRecordFragment` truyền cả danh sách vào
constructor thay vì load từng cái theo nhu cầu.

### 7.2 `BgmPlayer` — nhạc nền phát ra loa, HOÀN TOÀN TÁCH BIỆT khỏi track ghi hình

Dùng `MediaPlayer` thường, `isLooping = true`. **Không liên quan gì tới `AudioMixer`** — đây chỉ
là để người dùng **nghe** nhạc nền khi đứng ở màn quay (kể cả trước khi bấm ghi). Muốn nhạc nền
**xuất hiện trong file MP4** thì phải qua đường khác hoàn toàn: `loadWavPcm()` đọc cùng file này
thành PCM (`bgmPcm` trong `CameraRecordFragment`) rồi truyền vào `audioMixer.setBgm()` — 2 luồng
dữ liệu độc lập dùng chung 1 resource id.

### 7.3 `loadWavPcm(context, resId)` — tự parse header WAV thủ công

Không dùng thư viện — tự đọc byte: tìm chunk `"data"` (so bằng giá trị int little-endian
`0x61746174` bị viết nhầm thành `0x61746164` trong code — thực ra `0x64617461` mới là "data" đọc
xuôi, nhưng do đọc little-endian nên khớp đúng, comment trong code ghi rõ). Nếu không tìm thấy chunk
`data` hợp lệ (file không chuẩn RIFF) thì fallback `start = 44` (bỏ qua header WAV chuẩn 44 byte) —
hoạt động với file WAV PCM 16-bit/mono/44100Hz như `Asset_Format_Guidelines.md` yêu cầu, **không
đảm bảo đúng với WAV có định dạng khác** (stereo, nén, sample rate khác).

---

## 8. Pipeline ghi hình chi tiết — `recording/`

### 8.1 `VideoRecorder` — nhạc trưởng

`start()`: tạo file output (`hand_ar_record_<timestamp>.mp4` trong
`getExternalFilesDir(DIRECTORY_MOVIES)`), `prepare()` cả 2 encoder, **CHƯA** bắt đầu `EffectAudioClock`
(chờ tới frame đầu tiên).

`pushFrame(draw: (Canvas) -> Unit)` — gọi bởi `CameraRecordFragment.startRecordingFrameLoop()` mỗi
frame:
```kotlin
if (recordStartTimeNs < 0) {                      // LẦN GỌI ĐẦU TIÊN — mốc 0 của mọi PTS
    recordStartTimeNs = System.nanoTime()
    effectClock.start { pcmChunk, len -> ... }     // BẮT ĐẦU sinh audio TỪ ĐÂY, không phải từ start()
    onFirstFrame?.let { Handler.createAsync(Looper.getMainLooper()).post(it) }
}
val canvas = surface.lockHardwareCanvas()          // Surface của VideoEncoderWrapper — vẽ THẲNG vào encoder
draw(canvas)                                        // OverlayView.drawFrame() (qua lambda) vẽ vào đây
surface.unlockCanvasAndPost(canvas)
drainVideoEncoder()                                 // đọc output đã encode xong, đẩy vào MuxerCoordinator
```
Lý do bắt đầu `effectClock`/mốc PTS ở **`pushFrame()` lần đầu** chứ không phải `start()`: khoảng
thời gian giữa lúc bấm nút ghi (`start()`) và lúc `recordingFrameThread` thực sự vẽ được frame đầu
tiên là không xác định (phụ thuộc lịch trình thread) — nếu neo mốc 0 vào `start()` thì audio/video
có thể lệch nhau vài chục ms ngay từ đầu clip.

`drainVideoEncoder()`: mỗi output buffer lấy PTS gốc từ `MediaCodec` rồi **trừ đi
`recordStartTimeNs/1000`** — quy PTS về mốc 0 tại đúng frame đầu tiên thật sự được vẽ (không phải
mốc lúc `MediaCodec` được `configure()`).

`stop(onStopped)`: chạy trên **`Thread` mới** (không block caller) — `signalEndOfInputStream()` rồi
`drainVideoEncoder()` lần cuối để vét hết frame còn trong hàng đợi encoder, ghi `lastStopHadValidOutput
= muxer?.hasStarted == true` (để `CameraRecordFragment` biết có nên giữ file hay xoá), release mọi
encoder + muxer, cuối cùng post callback về main thread.

### 8.2 `EffectAudioClock` — vì sao app không dùng mic mà vẫn có audio track đều đặn

```kotlin
fun start(chunkMs: Int = 20, onChunk: (pcmChunk: ShortArray, len: Int) -> Unit) {
    thread = Thread {
        val startNs = System.nanoTime(); var producedSamples = 0L
        while (running) {
            val expectedSamples = (System.nanoTime() - startNs) * sampleRate / 1_000_000_000L
            val pending = (expectedSamples - producedSamples).toInt()
            if (pending >= chunkSample) { onChunk(silence, chunkSample); producedSamples += chunkSample }
            else Thread.sleep(2)
        }
    }
}
```
Đây **không phải** vòng lặp "ngủ 20ms rồi sinh chunk" đơn giản (kiểu đó sẽ trôi lệch dần theo thời
gian do độ trễ tích luỹ của `Thread.sleep`) — mà là vòng lặp **so sánh số sample "đáng lẽ đã sinh"
theo đồng hồ thật** (`expectedSamples`, tính từ `elapsedNs`) với **số sample đã sinh thực tế**
(`producedSamples`). Chỉ sinh chunk mới khi đã "nợ" đủ 1 chunk — nghĩa là clock này tự bám sát thời
gian thực (wall clock) bất kể `Thread.sleep` có ngủ hơi lâu hơn 2ms dự kiến, tránh audio bị trôi
theo thời lượng clip (khác hẳn video PTS vốn tính theo `totalAudioSamples` cộng dồn, nhưng **cả 2
đều neo về cùng 1 mốc 0** — mốc `pushFrame()` đầu tiên).

`silence: ShortArray(chunkSample)` — chunk **toàn số 0** được sinh liên tục 50 lần/giây
(`chunkMs=20`), nội dung 0 vì đây chỉ là "nhịp trống rỗng" — âm thanh thật (hiệu ứng + nhạc nền)
được `AudioMixer.mix()` cộng thêm vào TRÊN nhịp trống này ở `VideoRecorder.pushFrame`'s callback
(`audioMixer.mix(pcmChunk, len)`), không phải từ mic.

### 8.3 `AudioMixer` — cộng dồn 2 nguồn PCM lên nhịp trống

```kotlin
fun mix(micChunk: ShortArray, len: Int): ShortArray {
    val result = micChunk.copyOf(len)     // copy nhịp trống (toàn 0) làm nền
    // cộng bgm (nếu có), tự lặp vòng khi hết (bgmPos wrap về 0)
    // cộng effectPcm (nếu có), tự lặp vòng khi hết (effectPos wrap về 0)
    // mỗi phép cộng đều .coerceIn(Short.MIN_VALUE, Short.MAX_VALUE) chống tràn số (clipping an toàn)
}
```
`triggerEffect(pcm, startPos)`: đặt `effectPcm` mới VÀ vị trí bắt đầu (`startPos % pcm.size`) — dùng
bởi `CameraRecordFragment` để nối tiếp đúng chỗ khi hiệu ứng đã kêu từ trước lúc bắt đầu ghi (mục
6.3). `effectPcm = null` (gọi từ `clearActiveEffect()`) khiến `mix()` bỏ qua bước cộng effect — dừng
tiếng hiệu ứng trong track ghi hình ngay lập tức, nhưng **không dừng `bgm`** (2 nguồn độc lập nhau
trong `mix()`, xoá 1 không ảnh hưởng nguồn kia). `setBgm()`/`resetBgmPos()` được gọi 1 lần lúc
`toggleRecording()` bắt đầu — nhạc nền trong video luôn phát **từ đầu bài** đồng bộ với lúc bắt đầu
ghi, không nối tiếp vị trí đang phát ở loa live (khác hẳn cách xử lý `effectPcm`).

### 8.4 Encoder wrappers & Muxer

`VideoEncoderWrapper`: `computeBitrate = width*height*fps*0.1` (0.1 bit/pixel — hệ số kinh nghiệm,
xem `Perf_Notes.md` để biết cách hệ số này được chọn), `KEY_I_FRAME_INTERVAL = 1` (I-frame mỗi
giây — README mục Hiệu năng liệt kê đây là 1 trong các đòn bẩy tối ưu **chưa dùng**, tăng lên 3 sẽ
giảm bitrate nhưng README ghi rõ "còn chưa dùng" nên đừng tự ý đổi mà không đo lại). `createInputSurface()`
tạo `Surface` để `VideoRecorder.pushFrame()` `lockHardwareCanvas()` vẽ thẳng vào — Canvas API thường
(`OverlayView.drawFrame`) hoạt động y hệt như vẽ lên 1 `View` bình thường dù thực chất đang ghi vào
encoder.

`AudioEncoderWrapper`: AAC-LC mono 44100Hz 128kbps cố định.

`MuxerCoordinator`: `maybeStart()` chỉ gọi `muxer.start()` khi **cả `videoTrack >= 0` VÀ
`audioTrack >= 0`** — vì `MediaMuxer` yêu cầu khai đủ mọi track trước khi start, không thể thêm
track sau khi đã start. `writeVideo`/`writeAudio` bọc try/catch bắt lỗi ghi đĩa (hết dung lượng) —
trả `false` thay vì crash, được `VideoRecorder`/`AudioEncoderWrapper` chuyển thành cờ `writeFailed`
để `CameraRecordFragment` phát hiện và dừng ghi an toàn (`onLowStorageDuringRecording()`).

---

## 9. Danh sách & phát lại video — `ui/videolist/`, `ui/player/`, `ui/recordedpreview/`

- **`VideoRepository.loadAll(context)`**: liệt kê file `.mp4` trong `getExternalFilesDir(DIRECTORY_MOVIES)`
  (đúng thư mục `VideoRecorder.start()` ghi vào), lọc `length() > 0` (bỏ file rỗng do ghi lỗi giữa
  chừng chưa bị dọn), đọc `duration`+`thumbnail` qua `MediaMetadataRetriever` trên `Dispatchers.IO`.
  `coroutineContext.ensureActive()` giữa vòng lặp — cho phép huỷ sớm nếu Fragment bị đóng khi đang
  load danh sách dài.
- **`VideoAdapter`** (`ui/videolist/`) không tự vẽ thumbnail nữa — root của `item_video.xml` **chính là**
  `ui/widget/VideoThumbnailView.kt` (custom `FrameLayout`), nên `onBindViewHolder` chỉ gọi
  `holder.binding.root.setThumbnail(item.thumbnail)`. `VideoThumbnailView` tự bo góc ảnh (dùng
  `clipRoundedCorners()`, xem lý do ở `RoundedOutline.kt`) và có sẵn `showExpandButton`
  (`R.styleable.VideoThumbnailView_showExpandButton`, `btn_expand`) — hiện `item_video.xml` **không**
  bật cờ này (thuộc về danh sách video, không cần nút expand).
- **`RecordedPreviewFragment`** (`ui/recordedpreview/`, sau khi vừa ghi xong) vs **`VideoPlayerFragment`** (`ui/player/`, từ danh sách) —
  2 Fragment riêng biệt dù đều dùng `ExoPlayer` phát cùng 1 file. `RecordedPreviewFragment` chỉ có
  **1 nút Save** (`btnSave`) — file MP4 đã được ghi sẵn từ lúc dừng quay nên Save không phải thao tác
  ghi đĩa, mà điều hướng sang **`ShareFragment`** (`actionRecordedPreviewToShare(videoPath, effectId)`,
  `popUpTo` chính `recordedPreviewFragment` inclusive — từ commit `1011121`, trước đó Save thoát
  thẳng khỏi app). Back (top bar hoặc hệ thống) không thoát thẳng mà mở `ConfirmDialog` hỏi xác
  nhận: nút Thoát xoá file rồi pop, nút Save (trong dialog) gọi lại đúng hàm `save()` — không còn nút Xoá/Chia sẻ riêng, không dùng `FileProvider` ở màn này nữa (đổi từ commit `40f2ba0`). `VideoPlayerFragment`
  có thêm `onSaveInstanceState`/khôi phục `playbackPosition` (giữ vị trí phát khi xoay màn hình) —
  `RecordedPreviewFragment` không có, chấp nhận phát lại từ đầu nếu xoay màn hình ngay sau khi ghi.
- **`ShareFragment`** (`ui/share/`, mở từ `RecordedPreviewFragment.save()`) — xem lại **cùng file** vừa
  ghi, không nhận video mới. 2 trạng thái UI: **card** thu gọn (mặc định, `groupCard`) và
  **fullscreen** (`groupFullscreen`, mở bằng `btnExpand`) — chuyển qua lại bằng cách **dời chính
  `binding.playerView`** giữa 2 container (`removeView` rồi `addView` sang container kia, giữ
  nguyên index 0 khi về card để nằm dưới icon play trang trí + nút expand), **không tạo lại
  `ExoPlayer`** nên video không giật/phát lại khi expand/collapse. Dùng chung **`VideoSeekBarController`**
  (tách từ `RecordedPreviewFragment` ra `ui/widget/`, xem đoạn `ui/share/ShareFragment.kt` ở mục 0)
  cho thanh seek ở cả 2 trạng thái.
  Nút Trang chủ → `popBackStack(effectListFragment, inclusive=false)`; nút Thử lại →
  `actionShareToCameraRecord(effectId)` với `popUpTo` chính `shareFragment` inclusive — tạo
  **camera mới** (không quay lại camera cũ, giữ đúng bất biến "camera luôn nằm ngay trên
  effectList", xem `AGENTS.md` mục 3). Back hệ thống tự xử lý theo `isFullscreen`: đang fullscreen
  thì thu nhỏ trước, đang ở card thì về thẳng Trang chủ (không có `ConfirmDialog` ở màn này —
  file đã chắc chắn được giữ từ bước trước). **4 nút MXH (Facebook/Instagram/TikTok/YouTube)** —
  mỗi nút gọi `shareVideoToSocialApp()` (`utils/SocialShare.kt`): app đích có cài thì mở kèm sẵn
  file qua `FileProvider` + `Intent.ACTION_SEND`, không thì mở trang app đó trên Play Store. Giới
  hạn đã biết trước (không phải bug): không app nào prefill được caption qua Intent, Instagram
  không mở được Story composer qua đường này (cần intent `ADD_TO_STORY` riêng). Thực tế test tay:
  Facebook tự mở bài đăng kèm video, Instagram mở qua CH Play (chưa cài), TikTok mở bottom sheet
  share tin nhắn/video, YouTube mở trình edit video trước khi đăng — xem thêm note trong
  `AGENTS.md` mục 3.

---

## 10. Utils khác — tra nhanh

| File | Vai trò |
|---|---|
| `FormatUtils.kt` | `formatDuration`/`formatDate` — chỉ hiển thị, không có logic phức tạp |
| `ViewInsetsUtils.kt` | `applySystemBarsInsetsPadding`/`applySystemBarsInsetsMargin`/`matchSystemBarsBottomInsetHeight` — 3 extension function xử lý edge-to-edge (status bar/nav bar che nội dung); hàm thứ 3 dùng để co 1 View (scrim) đúng bằng inset đáy thanh hệ thống — xem `bottom_system_bar_scrim` trong `activity_main.xml`. Đọc kỹ docstring trong file để biết khi nào dùng padding vs margin vs match-height. **Không** dùng cho root của `CameraRecordFragment` (đọc comment trong file, trỏ tới `HandAr_Plan.md` Phase F) |
| `RecordingPerfLogger.kt` | TẠM — công cụ đo fps/GC/nhiệt độ khi ghi hình, log qua `adb logcat -s RecPerf:I`. Gắn với 3 lời gọi trong `CameraRecordFragment` (đánh dấu `// TẠM`) — README nói rõ sẽ gỡ khi dự án dừng phát triển, đừng "dọn dẹp" nó giữa chừng |
| `VideoStatsLogger.kt` | `logRecordingStats()` — chỉ chạy khi `BuildConfig.DEBUG`, in + Toast thống kê file MP4 vừa ghi (size, fps trung bình...). Gọi 1 lần duy nhất trong `stopRecordingAndGoToPreview()` |

---

## 11. Bảng tra nhanh "muốn sửa X thì vào file nào"

| Muốn... | Vào file |
|---|---|
| Thêm hiệu ứng đơn giản (ảnh/GIF theo cử chỉ có sẵn) | `EffectRepository.kt`, khai trực tiếp trong `all` |
| Thêm hiệu ứng phức tạp (nhiều state đặc biệt, anchor/size khác mặc định) | Tạo file mới trong `effect/catalog/`, thêm vào `EffectRepository.all` |
| Thêm cử chỉ mới | `effect/gesture/Gesture.kt` (công thức) + có thể cần thêm hàm helper vào `GestureUtils.kt` |
| Chỉnh ngưỡng nhận diện 1 cử chỉ đang sai | `effect/gesture/GestureUtils.kt` (đọc kỹ công thức, đặc biệt `isThumbExtended` — đã biết chưa chặt) |
| Đổi cách hiệu ứng bám tay (tâm/kích thước) | `effect/model/AnchorSource.kt`/`SizeSource.kt` + set trong `EffectState` |
| Thêm loại asset vẽ mới (không phải ảnh/gif/sprite/procedural) | `effect/model/EffectAsset.kt` (thêm nhánh sealed class) + `effect/visual/EffectVisual.kt` (`createEffectVisual`) |
| Hiệu ứng vẽ canvas tự viết code (không phải ảnh có sẵn) | `effect/visual/canvas/` — noi theo mẫu `drawcanvas/` hoặc `gojo/` |
| Đổi cách chọn nền theo state | `effect/model/EffectBackground.kt` + `effect/background/` |
| Sửa logic phát/trộn âm thanh hiệu ứng | `ui/camera/CameraRecordFragment.kt` (`handleGesture`) — **nhớ đây là bộ nhận diện riêng, khác `OverlayView`** |
| Sửa logic chọn visual hiển thị | `OverlayView.kt` (`resolveMatchedIndex`, `drawFrame`) |
| ⚠️ Sửa bất kỳ gì trong pipeline ghi hình (PTS, mixer, encoder) | Đọc `HandAr_Refactor_Plan.md` trước — xem `docs/AGENTS.md` mục 5 |
| Đổi độ phân giải/bitrate/fps ghi hình | `ui/camera/CameraRecordFragment.kt` (`computeRecordingSize`, tham số `VideoRecorder(...)`) + `recording/VideoEncoderWrapper.kt` (`computeBitrate`) |
| Thêm màn hình mới vào flow khởi động | `res/navigation/nav_graph.xml` + Fragment mới trong `ui/` theo mẫu `ui/onboarding/` |
| Đổi ngôn ngữ / thêm bản dịch | `ui/language/LanguageFragment.kt` (mở từ `ui/settings/SettingsFragment.kt`, KHÔNG còn trong luồng mở app lần đầu) + `res/values-xx/strings.xml` |
| Thêm/sửa mục trong màn Cài đặt | `ui/settings/SettingsFragment.kt` + `item_settings_option.xml`, mở từ `btn_open_settings` ở `view_top_bar.xml` |
| Sửa dialog hướng dẫn cử chỉ (nút Action màn camera) | `ui/camera/GestureGuideDialog.kt`/`GestureAdapter.kt` + `effect/gesture/GestureDisplay.kt` (map tên/icon) |
| Đổi top bar (back + tên effect) của màn camera/xem trước | `res/layout/view_effect_top_bar.xml` — dùng chung qua `<include>`, đừng nhân bản vào thư mục qualifier (xem `docs/AGENTS.md` mục 5) |
| Đổi tên/xoá/chia sẻ video từ thư viện | `ui/player/VideoPlayerFragment.kt` (menu ⋮) + `ui/widget/RenameDialog.kt` / `ui/widget/ConfirmDialog.kt` |
| Sửa logic mở app MXH khi chia sẻ | `utils/SocialShare.kt` (`shareVideoToSocialApp`, `SocialTarget`) |

---

## 12. Những liên kết chéo dễ nhầm khi debug

1. **2 bộ nhận diện gesture chạy song song, độc lập, trên cùng 1 `HandLandmarkerResult`**:
   `OverlayView.resolveMatchedIndex()` (chọn visual, không debounce, tôn trọng `StateMode`) và
   `CameraRecordFragment.handleGesture()` (chọn âm thanh, có debounce 200ms, tự implement
   `Momentary`/`Latched` bằng tay qua `lastStateId`/`pendingState` — **không** dùng lại
   `StateMode` enum). Sửa 1 bên không tự động sửa bên kia. Nếu thấy "hình đúng nhưng tiếng sai"
   hoặc ngược lại — luôn là do lệch giữa 2 bộ này.
2. **`matchedIndex` (OverlayView, kiểu `Int`, index trong `states`) ≠ `matchedState`
   (CameraRecordFragment, kiểu `EffectState`, object đầy đủ)** — tên gần giống nhau, kiểu dữ liệu
   khác nhau, đừng nhầm khi đọc log hoặc đặt breakpoint.
3. **`EffectScope` tạo mới mỗi lần `setEffect()`**, và có **2 scope riêng cho live/recording** —
   `scope.shared(key) { ... }` chỉ đảm bảo "trong cùng 1 scope, cùng key thì cùng instance", KHÔNG
   đảm bảo live và recording thấy cùng dữ liệu (chúng chỉ trông giống nhau vì nhận cùng input từ
   `onHandFrame`, xem mục 3.3/5.2).
4. **`BgmPlayer` (audio/) và `audioMixer.setBgm()` (recording/) dùng chung 1 file `.wav` nhưng là 2
   luồng phát hoàn toàn tách biệt** — chỉnh âm lượng phát ra loa (`BgmPlayer`) không ảnh hưởng âm
   lượng trong video ghi ra (`EffectBgm.gainPercent` → `AudioMixer.mix()`), và ngược lại.
5. **`EffectState.id` được dùng làm key ở 2 map khác nhau, mục đích khác nhau**: `statePcmMap`
   (`CameraRecordFragment`, để trộn PCM khi ghi) và ngầm định trong `SoundEffectPlayer.soundIds`
   (key thực ra là `soundRes` — resource id — chứ KHÔNG phải `state.id`). Đừng nhầm 2 map này dùng
   chung 1 loại key.
6. **`frame.r`/`frame.cx`/`frame.cy` trong `HandFrame` được `OverlayView.drawFrame()` ghi ĐÈ mỗi
   frame trước khi gọi `visual.draw()`** — một `EffectVisual` không nên tự lưu lại `frame` object
   giữa các lần `draw()` để dùng sau, vì nội dung của nó đã bị ghi đè cho state/frame khác.
7. **Kích thước canvas ghi hình (~720px cạnh ngắn) khác canvas live (~1080px, tuỳ máy)** — bất kỳ
   chỗ nào tính kích thước vẽ theo pixel tuyệt đối (thay vì theo tỉ lệ `canvas.width`, như
   `HandSkeletonRenderer` đã làm đúng) đều có nguy cơ vẽ sai tỉ lệ giữa live và video xuất ra —
   đây từng là 1 bug thật (xem comment trong `HandSkeleton.kt`).
8. **`ShareFragment` có 2 lối vào với hành vi khác nhau, đừng giả định effectId luôn hợp lệ**: vào
   từ `RecordedPreviewFragment` thì `fromRecordedPreview=true` + `effectId` thật (nút Thử lại hoạt
   động); vào từ menu ⋮ của `VideoPlayerFragment` thì `fromRecordedPreview=false` + `effectId=""`
   (nút Thử lại bị ẩn ở tầng UI, nhưng nếu sau này có code nào lỡ gọi `actionShareToCameraRecord`
   bất chấp cờ này thì sẽ mở camera với effect rỗng — kiểm tra `args.fromRecordedPreview` trước khi
   sửa gì liên quan tới nút Thử lại).
