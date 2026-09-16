# Kế hoạch phát triển HandAr — Phase A → N

> Tài liệu này gộp hai kế hoạch trước đó thành một mạch liền — **Phần I** là nội dung của
> `HandAr_App_Expansion_Plan.md` cũ (Phase A–F, đưa app từ 1 màn hình thành 5 màn hình),
> **Phần II** là `HandAr_Next_Phase_Plan.md` cũ (Phase G–N, nền / nhạc nền / an toàn dữ liệu /
> hiệu ứng canvas). Hai file đó đã bị xoá — đây là bản duy nhất.
>
> Phần trước nữa (Phase 0–5, dựng pipeline ghi hình) vẫn nằm riêng ở `HandAr_Refactor_Plan.md`,
> vì nó nói về một tầng khác của app và gần như không đổi nữa.

## Bản đồ toàn bộ chặng đường

| Phase | Nội dung | Tình trạng |
|---|---|---|
| 0–5 | Pipeline ghi hình (xem `HandAr_Refactor_Plan.md`) | ✅ Xong |
| A–F | Từ 1 màn hình thành 5 màn hình — **Phần I** | ✅ Xong |
| G | Đổi hình dạng dữ liệu hiệu ứng — **Phần II** | ✅ Xong |
| H | An toàn dữ liệu khi quay | ✅ Xong |
| I | Background cấp hiệu ứng | ✅ Xong |
| J | Nhạc nền liên tục | ✅ Xong |
| K | Nợ kỹ thuật rẻ tiền | ✅ K1 xong |
| L | Nền theo từng state | ✅ Xong |
| M | Hiệu ứng vẽ bằng canvas | ⚠️ M0–M7 xong, còn M8–M9 |
| N | Dọn nợ hiệu năng + cấu trúc lại package | ⬜ Chưa làm |

---

# Phần I — Từ 1 màn hình thành 5 màn hình (Phase A–F)

> Tiếp nối `HandAr_Refactor_Plan.md` (đã hoàn thành Phase 0-5, pipeline ghi hình đã ổn định).
> Mục tiêu: từ 1 màn hình duy nhất (`MainActivity` làm hết mọi việc) → app hoàn chỉnh 5 màn hình, có thể chọn hiệu ứng, xem lại, và quản lý video đã quay.

---

## 0. Quyết định kiến trúc đã chốt

| Hạng mục | Lựa chọn | Lý do |
|---|---|---|
| Điều hướng giữa màn hình | **Fragments + Navigation Component** | Giữ nguyên View/XML hiện có, không cần viết lại UI bằng Compose, tận dụng toàn bộ code `OverlayView`/`PreviewView` đang chạy tốt |
| Phát video trong app | **ExoPlayer (Media3)** | Chuẩn hiện tại của Google, xử lý tốt các định dạng/edge-case mà `VideoView` (cũ, ít được bảo trì) hay gặp lỗi |
| Kiến trúc mic/audio | **Giữ nguyên phương án B** (đã chốt ở Refactor Plan trước — không mic, chỉ `EffectAudioClock` + `AudioMixer`) | Không đổi gì, đã ổn định |
| Truyền tham số giữa màn hình | **Safe Args** (plugin `androidx.navigation.safeargs.kotlin`, dùng các class `*Directions` / `*Args` được sinh ra) | ⚠️ **Đã đổi so với quyết định ban đầu.** Ban đầu chốt dùng Bundle thủ công (`bundleOf(...)` + `requireArguments()`) vì tưởng plugin safeargs bắt buộc phải apply tường minh `org.jetbrains.kotlin.android`, trong khi dự án dùng AGP 9 với Kotlin built-in. Thực tế plugin apply được bình thường, nên dự án đã chuyển sang Safe Args. Mọi tham số phải khai `<argument>` trong nav graph (nhớ `app:argType` viết thường: `string`) thì class Directions/Args mới sinh đúng |
| View | **View Binding** (`viewBinding = true`) | Thay `findViewById`, giảm lỗi id sai. Lưu ý: binding chỉ dùng trên main thread trong khoảng `onViewCreated` → `onDestroyView` |

---

## 1. Vấn đề kiến trúc hiện tại cần giải quyết trước khi thêm màn hình

| # | Vấn đề | Vì sao cản trở việc thêm màn hình |
|---|---|---|
| 1 | 2 hiệu ứng (`happy3`/`banana`) đang **hardcode thẳng** trong `OverlayView.GifLayer` và `MainActivity` | Muốn có "màn List Hiệu ứng" để chọn, bắt buộc phải có 1 **data model** đại diện cho "1 hiệu ứng" trước — hiện tại khái niệm này chưa tồn tại trong code |
| 2 | `MainActivity` đang ôm hết: xin quyền, MediaPipe, Camera, Recording, UI nút bấm | Cần tách thành 1 `Fragment` riêng cho màn Camera, các màn khác không được kéo theo toàn bộ logic này |
| 3 | Chưa có nơi nào đọc lại danh sách video đã quay (chỉ ghi file, không list) | Cần 1 lớp quét thư mục (`Repository`) trước khi có thể hiển thị màn "Danh sách Video" |

---

## 2. Cấu trúc package mục tiêu

> **Trạng thái thực tế sau Phase B** (commit `c65a4d3`): cấu trúc bên dưới đã được áp dụng, với 2 điều chỉnh — `EffectListFragment`/`EffectListAdapter` chưa làm RecyclerView (hiện là 1 nút bấm tạm, hardcode `effectId = "happy_cat"`, sẽ hoàn thiện ở Phase F), và `HandLandmarkerProvider` được giải phóng ở `MainActivity.onDestroy` chứ không theo Fragment (cố ý — tránh tạo lại detector mỗi lần ra vào màn camera).

```
com.example.handar/
├── MainActivity.kt                 → CHỈ còn là NavHost, không còn logic camera/recording
├── effect/
│   ├── EffectAsset.kt              → sealed class: StaticImage / AnimatedGif / SpriteSheet
│   ├── EffectVisual.kt             → interface chung + factory createEffectVisual(), 1 lớp impl/loại asset
│   ├── GestureRecognizer.kt        → fun interface + các công thức cử chỉ dựng sẵn (Gestures.singleHandPalmOpen...)
│   ├── EffectState.kt              → data class gom gesture+asset+sound theo từng trạng thái
│   ├── EffectDefinition.kt         → data class mô tả 1 hiệu ứng (giữ 1 List<EffectState>)
│   ├── EffectRepository.kt         → danh sách hiệu ứng có sẵn trong app
│   └── HandLandmarkerProvider.kt   → cache/tạo lại HandLandmarker theo requiredNumHands, phân phối kết quả qua SharedFlow
├── ui/
│   ├── effectlist/
│   │   ├── EffectListFragment.kt
│   │   └── EffectListAdapter.kt    → RecyclerView hiển thị hiệu ứng để chọn
│   ├── camera/
│   │   └── CameraRecordFragment.kt → gần như nguyên trạng logic MainActivity cũ, đổi lifecycle owner
│   ├── preview/
│   │   └── RecordedPreviewFragment.kt → xem lại NGAY sau khi bấm Stop
│   ├── videolist/
│   │   ├── VideoListFragment.kt
│   │   ├── VideoListAdapter.kt
│   │   └── VideoRepository.kt      → quét getExternalFilesDir(MOVIES)
│   └── player/
│       └── VideoPlayerFragment.kt  → ExoPlayer
├── OverlayView.kt                  → SỬA: nhận EffectDefinition thay vì hardcode 2 GifLayer cố định
├── VideoRecorder.kt, AudioMixer.kt, EffectAudioClock.kt,
│   MuxerCoordinator.kt, wrapper/*  → GIỮ NGUYÊN, không đổi (đã ổn định qua Phase 0-5)
└── utils/                          → giữ nguyên
```

---

## 3. Data model cốt lõi: `EffectAsset` + `EffectVisual` + `GestureRecognizer` + `EffectDefinition`

> Cập nhật so với bản đầu: thay vì gắn cứng "2 GIF theo xoè/nắm tay", thiết kế mới tách riêng 3 trục độc lập: **loại file hiệu ứng** (PNG tĩnh / GIF / sprite sheet), **cách vẽ hiệu ứng đó ra canvas**, và **công thức nhận diện cử chỉ**. Mục đích: thêm loại asset mới (ví dụ Lottie sau này) hoặc cử chỉ mới (ví dụ 2 tay) không cần sửa `OverlayView`.

### 3.1. `EffectAsset` — sealed class nhận diện loại file qua kiểu dữ liệu, không đoán qua đuôi file

```kotlin
sealed class EffectAsset {
    data class StaticImage(val resId: Int) : EffectAsset()

    data class AnimatedGif(val resId: Int) : EffectAsset()

    data class SpriteSheet(
        val resId: Int,
        val columns: Int,
        val rows: Int,
        val frameCount: Int,
        val frameDurationMs: Int
    ) : EffectAsset()
}
```

### 3.2. `EffectVisual` — interface chung, mỗi loại asset có 1 lớp implement riêng

```kotlin
interface EffectVisual {
    fun setActive(active: Boolean)
    /** Vẽ đúng 1 frame hiện tại lên canvas, tại (cx, cy) với bán kính r. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float)
}

fun createEffectVisual(context: Context, asset: EffectAsset): EffectVisual = when (asset) {
    is EffectAsset.StaticImage -> StaticImageVisual(context, asset.resId)
    is EffectAsset.AnimatedGif -> AnimatedGifVisual(context, asset.resId)   // chính là GifLayer cũ, đổi tên
    is EffectAsset.SpriteSheet -> SpriteSheetVisual(context, asset)
}
```

**Vì sao dùng `sealed class` thay vì enum/int cờ định loại:** Kotlin ép `when` phải xử lý đủ mọi nhánh (không có `else`) — nếu sau này thêm 1 loại asset mới (ví dụ `Lottie`) mà quên viết `EffectVisual` tương ứng, code **sẽ báo lỗi biên dịch ngay**, không phải đợi runtime mới phát hiện thiếu xử lý.

**Sprite sheet so với GIF:** vì sprite sheet chỉ là 1 `Bitmap` tĩnh (không phải `Drawable` có trạng thái nội bộ như `AnimatedImageDrawable`), `SpriteSheetVisual` **không cần** buffer trung gian, không cần `ALLOCATOR_SOFTWARE`, không cần `canvas.scale()` bù trừ — toàn bộ cạm bẫy đã gặp với GIF (Mục 7) không áp dụng cho loại asset này. Điểm cần tự làm: tính `frameIndex` theo **đồng hồ hệ thống** (`elapsedMs / frameDurationMs % frameCount`), không theo số lần gọi `draw()` — vì `draw()` được gọi từ 2 nơi có nhịp độ khác nhau (`onDraw()` live vs `recordingFrameThread` cố định fps), tính theo số lần gọi sẽ khiến animation chạy nhanh/chậm khác nhau giữa live và video ghi ra.

### 3.3. `GestureRecognizer` — thay `isPalmOpen` hardcode bằng công thức truyền vào

```kotlin
fun interface GestureRecognizer {
    /** Nhận TOÀN BỘ tay phát hiện được (có thể nhiều tay), trả về true/false. */
    fun recognize(hands: List<List<NormalizedLandmark>>): Boolean
}

object Gestures {
    val singleHandPalmOpen = GestureRecognizer { hands ->
        val landmark = hands.firstOrNull() ?: return@GestureRecognizer false
        isPalmOpen(landmark, landmark[0])   // hàm cũ giữ nguyên, chỉ đổi cách gọi
    }

    // Ví dụ mở rộng sau này — không cần sửa gì ở chỗ khác:
    val bothHandsOpen = GestureRecognizer { hands ->
        hands.size >= 2 && hands.all { isPalmOpen(it, it[0]) }
    }
}
```

### 3.4. `EffectState` + `EffectDefinition` — danh sách trạng thái, KHÔNG giới hạn 2

> **Sửa so với bản đầu:** thiết kế trước đó tách riêng `activeGesture`/`inactiveGesture` và `activeAsset`/`inactiveAsset` — vừa **không mở rộng được quá 2 trạng thái**, vừa tách rời 3 thứ (gesture, asset, sound) vốn luôn đi cùng nhau theo từng trạng thái — dễ gây lỗi khi thêm trạng thái mới (quên sửa 1 trong 3 chỗ). Giải pháp: gom cả 3 vào **1 đơn vị** `EffectState`, `EffectDefinition` giữ 1 `List<EffectState>` không giới hạn số lượng — hiệu ứng “làm phép” với 4-5 cử chỉ tay khác nhau vẫn dùng chung 1 model, không cần sửa gì thêm.

```kotlin
data class EffectState(
    val id: String,                  // dùng để debug/log, ví dụ "open", "fist_punch", "peace_sign"
    val gesture: GestureRecognizer,
    val asset: EffectAsset,
    val soundRes: Int
)

data class EffectDefinition(
    val id: String,
    val displayName: String,
    val thumbnailRes: Int,
    val requiredNumHands: Int,
    val states: List<EffectState>,      // 👈 KHÔNG giới hạn 2, muốn 5 trạng thái cũng được
    val idleAsset: EffectAsset? = null  // hiện gì khi KHÔNG trạng thái nào khớp (null = không vẽ gì)
)

object EffectRepository {
    val all = listOf(
        EffectDefinition(
            id = "happy_cat",
            displayName = "Mèo vui / Chuối khóc",
            thumbnailRes = R.drawable.thumb_happy_cat,
            requiredNumHands = 1,
            states = listOf(
                EffectState("open", Gestures.singleHandPalmOpen, EffectAsset.AnimatedGif(R.drawable.happy_happy_happy_cat), R.raw.happy_happy_happy_cat),
                EffectState("closed", Gestures.singleHandFist, EffectAsset.AnimatedGif(R.drawable.banana_cat_crying), R.raw.banana_cat_crying)
            )
        ),
        // Ví dụ hiệu ứng “làm phép” với 4 cử chỉ khác nhau — vẫn cùng 1 model, không cần sửa code:
        EffectDefinition(
            id = "magic_spell",
            displayName = "Phù thuỷ",
            thumbnailRes = R.drawable.thumb_magic,
            requiredNumHands = 1,
            states = listOf(
                EffectState("fireball", Gestures.fistPunch, EffectAsset.SpriteSheet(R.drawable.fireball_sheet, 4, 4, 16, 40), R.raw.fire_sound),
                EffectState("lightning", Gestures.peaceSign, EffectAsset.AnimatedGif(R.drawable.lightning), R.raw.thunder_sound),
                EffectState("shield", Gestures.singleHandPalmOpen, EffectAsset.StaticImage(R.drawable.shield), R.raw.shield_sound),
                EffectState("heal", Gestures.thumbsUp, EffectAsset.SpriteSheet(R.drawable.heal_sheet, 3, 3, 9, 60), R.raw.heal_sound)
            )
        )
    )

    fun findById(id: String): EffectDefinition = all.first { it.id == id }
}
```

**Cách `OverlayView` dùng `states`:** duyệt theo đúng thứ tự khai báo, lấy trạng thái **đầu tiên** có `gesture.recognize(hands) == true`:
```kotlin
val matchedState = effect.states.firstOrNull { it.gesture.recognize(hands) }
// null -> dùng idleAsset (hoặc không vẽ gì nếu idleAsset == null)
```

> ⚠️ **Thứ tự trong `states` chính là độ ưu tiên.** Vì chỉ lấy trạng thái đầu tiên khớp, nếu 2 công thức `GestureRecognizer` vô tình cùng khớp 1 lúc (ví dụ “nắm đấm” và “1 ngón trỏ” chồng lấn nếu viết lỏng lẻo), kết quả phụ thuộc hoàn toàn vào thứ tự khai báo. Đây là trách nhiệm của người viết `GestureRecognizer` (thiết kế các công thức loại trừ lẫn nhau rõ ràng), không phải thứ code tự đảm bảo được.

**Thay đổi ở `OverlayView`:** thay `GifLayer` hardcode bằng `createEffectVisual(context, asset)` cho từng `EffectState`, gọi qua `setEffect(effect: EffectDefinition)`. `OverlayView` từ đây chỉ làm việc với interface `EffectVisual` chung, không cần biết bên trong là GIF hay sprite hay PNG, và không cần biết đang có 2 hay 5 trạng thái.

**Thay đổi ở `CameraRecordFragment`/`MainActivity`:** biến theo dõi debounce đổi từ `lastPalmOpen: Boolean?` thành `lastStateId: String?` (dùng `EffectState.id` thay vì `Boolean`) — cơ chế debounce (`pendingState`/`pendingStateSince`) giữ nguyên logic, chỉ đổi kiểu dữ liệu đang theo dõi từ `Boolean` sang `String?`.

> ⚠️ **Ràng buộc kỹ thuật cần biết về `requiredNumHands`:** xem chi tiết ở Mục 3.5 (`HandLandmarkerProvider`) — không đơn giản như chỉ đọc giá trị này rồi gọi `setNumHands()`.

### 3.5. `HandLandmarkerProvider` — tạo lại `HandLandmarker` đúng lúc, không leak callback theo Fragment

`HandLandmarker.HandLandmarkerOptions.setNumHands(n)` chỉ đọc giá trị **đúng 1 lần lúc `createFromOptions()`** — không có API để đổi `n` tại chỗ, muốn đổi bắt buộc phải `close()` object cũ rồi `createFromOptions()` lại. Vì mỗi `EffectDefinition` có `requiredNumHands` riêng (hiệu ứng đơn giản cần 1 tay, hiệu ứng “làm phép” có thể cần 2 tay), cần 1 cơ chế **tạo lại đúng lúc** (khi `numHands` thực sự đổi), không tạo lại thừa khi không cần.

**Cái khó không phải ở phần “so sánh rồi tạo lại” (đơn giản)** — mà là `setResultListener { ... }` bị **đóng cứng** ngay lúc `build()`, không đổi được sau. Nếu callback này tham chiếu trực tiếp tới `overlayView`/state của 1 Fragment instance cụ thể, và `HandLandmarker` được cache để tái sử dụng giữa các lần vào `CameraRecordFragment`, callback cũ sẽ **trỏ tới Fragment đã bị destroy** — gây leak hoặc crash.

**Giải pháp:** tách hẳn detector khỏi Fragment bằng 1 object trung gian, phân phối kết quả qua `SharedFlow` thay vì callback trực tiếp:

```kotlin
object HandLandmarkerProvider {
    private var cached: HandLandmarker? = null
    private var cachedNumHands: Int = -1
    private val _results = MutableSharedFlow<Pair<HandLandmarkerResult, MPImage>>(extraBufferCapacity = 1)
    val results = _results.asSharedFlow()   // Fragment nào đang active tự collect từ đây

    @Synchronized
    fun getOrCreate(context: Context, numHands: Int): HandLandmarker {
        if (cached == null || cachedNumHands != numHands) {
            cached?.close()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.CPU).build())
                .setNumHands(numHands)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, inputImage -> _results.tryEmit(result to inputImage) }  // callback CỐ ĐỊNH, không đổi theo Fragment
                .build()
            cached = HandLandmarker.createFromOptions(context, options)
            cachedNumHands = numHands
        }
        return cached!!
    }
}
```

`CameraRecordFragment` không tự tạo `HandLandmarker` nữa — gọi `HandLandmarkerProvider.getOrCreate(context, effect.requiredNumHands)` (tự động biết tái sử dụng hay tạo lại), rồi `collect` từ `HandLandmarkerProvider.results` trong `viewLifecycleOwner.lifecycleScope` — Flow tự huỷ đăng ký đúng lúc `onDestroyView` nhờ gắn theo `viewLifecycleOwner`, không cần tự quản lý dọn dẹp callback thủ công.

**Không cần “nơi lưu trữ trung gian” riêng cho `numHands`:** `requiredNumHands` đã có sẵn trong `EffectDefinition`, đọc được ngay qua `EffectRepository.findById(effectId)` từ Safe Args — `CameraRecordFragment` tự đọc được giá trị cần, không cần truyền qua kênh phụ nào khác. `HandLandmarkerProvider` chỉ đóng vai trò cache + phân phối kết quả.

> 💡 **Đo trước khi quyết định có cần cơ chế cache này không:** thử đo thời gian `createFromOptions()` thực tế mất bao lâu trên máy bạn (`System.currentTimeMillis()` quanh nó). Nếu chỉ ~20-50ms, có thể đơn giản hoá: tạo mới `HandLandmarker` **mỗi lần vào Fragment**, đóng lúc rời đi, bỏ hẳn phần cache/so sánh trong `HandLandmarkerProvider` — vẫn đúng yêu cầu “cấu hình theo đúng effect”, chỉ là không tối ưu tái sử dụng. Nếu đo ra >150-200ms (đủ để cảm nhận “giật” khi chuyển màn), lúc đó áp dụng đúng cache ở trên mới thực sự đáng công.

---

## 4. Navigation Graph

```
effectListFragment (start destination)
        │  chọn 1 effect, truyền effectId qua Safe Args
        ▼
cameraRecordFragment
        │  bấm Stop, có file video xong
        ▼
recordedPreviewFragment (Lưu / Xoá / Chia sẻ)
        │  bấm "Xong" hoặc back
        ▼
quay lại effectListFragment (popBackStack)

--- lối vào riêng, độc lập ---
(nút "Thư viện" đặt ở effectListFragment hoặc cameraRecordFragment)
        ▼
videoListFragment
        │  chọn 1 video
        ▼
videoPlayerFragment
```

**Quy ước đặt tên trong `nav_graph.xml`** (đã áp dụng từ Phase B):

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Destination | camelCase, khớp tên class, giữ hậu tố `Fragment` | `cameraRecordFragment` |
| Action | `action_<từ>_to_<đến>`, bỏ hậu tố `Fragment` | `action_cameraRecord_to_recordedPreview` |
| Id của View trong layout | snake_case | `btn_toggle_record` |
| `app:argType` | **luôn viết thường** | `string`, không phải `String` |

> ⚠️ `argType` viết hoa (`String`) không bị compiler bắt: Navigation sẽ hiểu đó là **tên class đầy đủ**, đi tìm class không tồn tại và ném exception lúc parse graph.

**Cấu hình `popUpTo` cho action `cameraRecordFragment → recordedPreviewFragment`:**

```xml
app:popUpTo="@id/cameraRecordFragment"
app:popUpToInclusive="true"
```

Mục đích: back từ Preview về thẳng `effectListFragment`, **không** rơi lại vào màn camera (tránh mở lại camera/MediaPipe không cần thiết), và giải phóng camera ngay lúc navigate.

**Vì sao trỏ `popUpTo` vào chính `cameraRecordFragment` chứ không vào `effectListFragment`:** nếu destination được trỏ tới **không có trong back stack**, `popUpTo` im lặng không làm gì cả — không lỗi, không cảnh báo. Ở Phase F sẽ có đường vào camera không đi qua màn List (từ `videoListFragment`), lúc đó cách trỏ vào `effectListFragment` sẽ hoặc gỡ nhầm màn khác, hoặc không gỡ gì. Quy tắc: **`popUpTo` nên nói về màn mình đang rời, không nói về màn mình đoán là đang nằm dưới.**

> `popUpTo` cũng là thứ quyết định vòng đời: với `popUpToInclusive="true"`, `CameraRecordFragment` bị huỷ hẳn (`onDestroyView → onDestroy → onDetach`) lúc navigate, không nằm lại back stack. Xem `Fragment_Review_Checklist.md` mục 0.

---

## 5. Thứ tự Phase đề xuất

```
Phase A → Effect Data Model (nền tảng, làm trước tiên, rủi ro thấp)
Phase B → Navigation skeleton + chuyển MainActivity → CameraRecordFragment
Phase C → Recorded Preview screen (xem lại ngay sau khi Stop)
Phase D → Video List (thư viện video đã quay)
Phase E → Video Player (ExoPlayer)
Phase F → Polish, liên kết điều hướng, test toàn diện
```

**Vì sao thứ tự này:**
- Phase A phải làm trước tất cả — không có `EffectDefinition`, không màn nào khác có ý nghĩa.
- Phase B là bước "đau" nhất (chuyển toàn bộ logic Activity → Fragment) nhưng **không đổi logic recording/MediaPipe gì cả** — chỉ đổi lifecycle owner, nên rủi ro chủ yếu nằm ở lifecycle (Fragment bị destroy/recreate khác Activity), không nằm ở nghiệp vụ.
- Phase C-D-E độc lập tương đối với nhau, có thể làm song song nếu muốn, nhưng đề xuất làm tuần tự để dễ test từng phần.
- Phase F luôn ở cuối, dùng để nối các mảnh và test navigation đầy đủ (bấm back ở mọi màn, xoay màn hình nếu hỗ trợ, v.v.)

---

## 6. Chi tiết từng Phase

### Phase A — Effect Data Model
- Tạo `EffectAsset`, `EffectVisual` (+ 3 lớp impl: `StaticImageVisual`, `AnimatedGifVisual` đổi tên từ `GifLayer` cũ, `SpriteSheetVisual` mới), `GestureRecognizer`, `EffectState`, `EffectDefinition`, `EffectRepository`, `HandLandmarkerProvider` (chi tiết mục 3).
- Sửa `OverlayView`: đổi 2 `GifLayer` hardcode (`happy3Layer`/`bananaCryingLayer`) thành khởi tạo động qua `createEffectVisual()` cho từng `EffectState` trong `effect.states`, nhận `EffectDefinition` qua hàm `setEffect()`. Đổi logic nhận diện cử chỉ thành duyệt `states.firstOrNull { it.gesture.recognize(hands) }`.
- Sửa `CameraRecordFragment`/`MainActivity`: đổi `lastPalmOpen: Boolean?` thành `lastStateId: String?`, giữ nguyên cơ chế debounce, chỉ đổi kiểu dữ liệu theo dõi. Load đúng `soundRes` theo `EffectState` đang khớp thay vì 2 biến `activeSoundPcm`/`inactiveSoundPcm` cố định.
- Thay `setupMediaPipe()` tự tạo `HandLandmarker` bằng gọi `HandLandmarkerProvider.getOrCreate(context, effect.requiredNumHands)`, collect kết quả qua `SharedFlow` trong `viewLifecycleOwner.lifecycleScope` thay vì `setResultListener` trực tiếp.
- **Đo thời gian `createFromOptions()` trước** (xem Mục 3.5) để quyết định có thực sự cần phần cache trong `HandLandmarkerProvider` hay chỉ cần tạo mới/đóng đơn giản mỗi lần vào Fragment.
- **Chưa cần làm `SpriteSheetVisual`/`StaticImageVisual` đầy đủ ngay** ở Phase này nếu chưa có asset thực tế loại đó — có thể chỉ viết `AnimatedGifVisual` trước (đủ dùng cho effect hiện có, chỉ có 2 trạng thái GIF), thêm 2 lớp còn lại khi thực sự có hiệu ứng dùng đúng loại asset đó — đúng tinh thần đơn giản trước, phức tạp hoá khi có nhu cầu thật.
- **Test bằng cách:** chạy lại đúng bộ Checklist B (Live preview) + D (kịch bản đặc biệt) hiện có — đảm bảo hành vi không đổi dù đã refactor cách nạp effect và cách tạo `HandLandmarker`.

### Phase B — Navigation skeleton ✅ ĐÃ HOÀN THÀNH (commit `c65a4d3`)

**Đã làm:**
- Dependency: `navigation-fragment-ktx` + `navigation-ui-ktx` + `recyclerview`, bật `viewBinding = true`. (Lúc Phase B chưa dùng plugin Safe Args; hiện tại dự án **đã** apply `androidx.navigation.safeargs.kotlin` — xem Mục 0.)
- `activity_main.xml` chỉ còn `FragmentContainerView` (`android:name` = `NavHostFragment`, `app:defaultNavHost="true"`, `app:navGraph`). `MainActivity` rút gọn còn `enableEdgeToEdge` + `setContentView` + `HandLandmarkerProvider.release()` ở `onDestroy`.
- `CameraRecordFragment` (`ui/camera/`): di chuyển nguyên trạng logic `MainActivity` cũ, không đụng pipeline recording.
- `EffectListFragment` (`ui/effectlist/`): **tạm thời chỉ là 1 nút** navigate kèm `effectId = "happy_cat"` hardcode. RecyclerView + Adapter dời sang Phase F.
- `effectId` lúc Phase B truyền qua `bundleOf("effectId" to id)`, nhận bằng `requireArguments().getString("effectId")!!` trong `onCreate`. **Hiện đã thay bằng Safe Args**: `EffectListFragmentDirections.actionEffectListToCameraRecord(effect.id)`.

**Lệch so với kế hoạch ban đầu:**
1. ~~Không dùng Safe Args → dùng Bundle thủ công (Mục 0).~~ **Không còn đúng**: dự án đã chuyển sang Safe Args, xem Mục 0.
2. `EffectListFragment` chưa có RecyclerView → dời Phase F.
3. Bỏ `ViewCompat.setOnApplyWindowInsetsListener` khỏi `MainActivity` — nó set padding systembar cho toàn NavHost, làm camera preview có viền đen. Insets cho nút record để lại Phase F.

**Bài học lifecycle rút ra** (chi tiết đầy đủ trong `Fragment_Review_Checklist.md`):
- Tài nguyên (`backgroundExecutor`, `SoundEffectPlayer`, `statePcmMap`) tạo ở `onViewCreated`, huỷ ở `onDestroyView` — **nơi tạo và nơi huỷ phải đối xứng**.
- Dùng `viewLifecycleOwner` cho cả `lifecycleScope` lẫn `bindToLifecycle`, không dùng `this`.
- Thread nền (`recordingFrameThread`) không được chạm `binding`; capture `OverlayView` ra biến local trước khi tạo thread.
- Mọi callback đến muộn (`stop {}`, `cameraProviderFuture.addListener`) phải chốt cửa bằng `_binding ?: return@...` / `context ?: return@...`.
- `onDestroyView` theo thứ tự: cắt `videoRecorder` → `join` thread → `stop()` recorder → shutdown executor/release player → null hoá **mọi** tham chiếu View.
- Không giới thiệu `ViewModel` ở phase này (giữ nguyên khuyến nghị ban đầu — làm đơn giản trước).

### Phase C — Recorded Preview screen

**Thứ tự làm: C1 → C2 → C3 → C4 → C5**, mỗi bước build + chạy được rồi mới đi tiếp (đừng gộp C2 với C3 — nếu Preview vừa không hiện vừa không phát được sẽ không biết lỗi ở navigation hay ExoPlayer).

**C1 — Dependency Media3**
- `libs.versions.toml`: `media3 = "1.8.0"` + 2 library `media3-exoplayer` (engine) và `media3-ui` (`PlayerView`, có sẵn play/pause/seek).
- **Và** khai `implementation(libs.androidx.media3.exoplayer)` / `implementation(libs.androidx.media3.ui)` trong `app/build.gradle.kts` — toml chỉ là danh mục, không tự thêm dependency vào module.
- Nếu resolve lỗi phiên bản: hạ về `1.4.1`.

**C2 — Nav graph + điều hướng khi Stop xong**
- Thêm destination `recordedPreviewFragment` với `<argument android:name="videoPath" app:argType="string"/>` (viết thường!).
- Thêm action `action_cameraRecord_to_recordedPreview` kèm `popUpTo` + `popUpToInclusive` (xem Mục 4).
- Đổi callback `VideoRecorder.stop { }` từ hiện Toast thành `navigate(...)` kèm `bundleOf("videoPath" to outputFile.absolutePath)`, với **2 lớp bảo vệ bắt buộc**:
  1. `if (!isAdded) return@stop` — callback về sau 1-2s với clip dài (ca D2), người dùng có thể đã back; `findNavController()` trên fragment đã detach sẽ ném `IllegalStateException`.
  2. `if (nav.currentDestination?.id != R.id.cameraRecordFragment) return@stop` — `navigate(actionId)` yêu cầu action tồn tại ở destination hiện tại; gọi lần hai (double-tap, callback về 2 lần) sẽ ném `IllegalArgumentException: navigation destination ... is unknown`.
- Làm stub `RecordedPreviewFragment` chỉ in `videoPath` ra TextView để nghiệm thu riêng phần điều hướng trước.

**C3 — Phát video bằng ExoPlayer**
- Layout: `androidx.media3.ui.PlayerView` + hàng 3 nút.
- `onViewCreated`: `ExoPlayer.Builder(requireContext()).build()` → gán `playerView.player`, `setMediaItem(MediaItem.fromUri(...))`, `prepare()`, `playWhenReady = true`.
- `onDestroyView`: `playerView.player = null` **trước**, rồi `player?.release()`, rồi `player = null`. Release ở `onDestroyView` chứ không phải `onDestroy` — ExoPlayer giữ codec phần cứng, quên release là chiếm codec, lần sau không phát được (cùng bản chất với vấn đề camera).

**C4 — Ba hành động**
- **Xong** (thay tên "Lưu" cho thành thật): video đã nằm sẵn trong `getExternalFilesDir(MOVIES)` từ lúc `VideoRecorder.start()`, nút này chỉ `popBackStack()`.
- **Xoá:** release player **trước**, rồi `File(videoPath).delete()`, rồi `popBackStack()`. Nên có dialog xác nhận — hành động không hoàn tác được.
- **Chia sẻ:** xem C5.

**C5 — FileProvider cho nút Chia sẻ**
- `res/xml/file_paths.xml`: `<external-files-path name="movies" path="Movies/" />` — thẻ `external-files-path` ứng với `getExternalFilesDir(...)`, `path` khớp `Environment.DIRECTORY_MOVIES` mà `VideoRecorder.start()` đang dùng. Sai thẻ/sai path → `IllegalArgumentException: Failed to find configured root`.
- `AndroidManifest.xml`: `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">` + `<meta-data name="android.support.FILE_PROVIDER_PATHS" resource="@xml/file_paths"/>`.
- Intent: `ACTION_SEND` + `type = "video/mp4"` + `EXTRA_STREAM` = `FileProvider.getUriForFile(...)` + **`addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`**. Thiếu flag này thì share sheet vẫn mở nhưng app nhận không đọc được file.

**Nghiệm thu Phase C:** chạy A-F như thường lệ, cộng thêm mục G-H trong `Test_Checklist.md` (đặc biệt H9, H11, H12), cộng 3 ca riêng của phase này: xoá xong file biến mất thật (`adb shell ls /sdcard/Android/data/com.example.handar/files/Movies`); chia sẻ sang app khác mở được; bấm Stop rồi bấm lia lịa vào nút → chỉ điều hướng đúng 1 lần.

### Phase D — Video List (thư viện)
- `VideoRepository`: quét `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)`, liệt kê file `.mp4`, map thành `data class VideoItem(val file: File, val durationMs: Long, val createdAt: Long)`.
- Sinh thumbnail bằng `MediaMetadataRetriever.getFrameAtTime(0)` — nên chạy trên background thread (`Dispatchers.IO` hoặc `Executors`), tránh block UI khi list dài.
- `VideoListFragment`: `RecyclerView` hiển thị thumbnail + thời lượng (format `mm:ss`) + ngày quay (`file.lastModified()` format lại).
- Bấm vào 1 item → `navigate` sang `videoPlayerFragment` kèm `file.absolutePath`.
- **Cân nhắc thêm (không bắt buộc ngay):** nút xoá video ngay tại list (long-press hoặc icon), tránh phải vào Player mới xoá được.

### Phase E — Video Player
- `VideoPlayerFragment`: `PlayerView` (Media3) full màn hình, controls mặc định (play/pause/seek) đã có sẵn qua `PlayerView`, không cần tự vẽ.
- **Bắt buộc xử lý đúng lifecycle** để tránh leak: khởi tạo `ExoPlayer` ở `onViewCreated`, `player.release()` ở `onDestroyView` (không phải `onDestroy` — Fragment view có thể bị destroy/recreate nhiều lần trong khi Fragment instance còn sống).

### Phase F — Polish & liên kết
- **Hoàn thiện `EffectListFragment`** (dời từ Phase B): thay nút bấm tạm bằng `RecyclerView` + `EffectListAdapter`, mỗi item hiện `thumbnailRes` + `displayName`, bấm vào thì navigate kèm `effectId` thật. Nhớ `layoutManager` — thiếu nó list hiện trắng và **không có lỗi nào trong logcat**.
- **Xử lý window insets**: `MainActivity` đã bỏ listener insets (nó làm camera preview có viền đen). Cần apply insets riêng cho nút record trong `CameraRecordFragment` để nút không bị navigation bar che.
- Thêm nút/icon "Thư viện" (ví dụ icon góc trên `EffectListFragment` hoặc `CameraRecordFragment`) điều hướng sang `VideoListFragment`.
- Rà lại toàn bộ hành vi nút Back ở từng màn — đặc biệt đảm bảo **không thể back vào giữa lúc đang quay dở** (camera vẫn mở, `VideoRecorder.isRecording == true`) mà không có cảnh báo, tránh mất video đang quay dở hoặc leak tài nguyên camera.
- Chạy lại **toàn bộ** `HandAr_Manual_Test_Checklist.md` hiện có (mục A-F) sau khi refactor xong Phase B, vì đây là bộ test duy nhất xác nhận hành vi ghi hình không bị phá vỡ trong quá trình chuyển từ Activity sang Fragment.
- Bổ sung thêm few test case mới riêng cho navigation: "back liên tục qua 5 màn có gây crash không", "xoay hiệu ứng qua lại nhiều lần rồi mới bấm Record có load đúng effect không".

---

## 7. Rủi ro lớn nhất cần lưu ý xuyên suốt

**Toàn bộ pipeline recording (`VideoRecorder`, `AudioMixer`, `EffectAudioClock`, `MuxerCoordinator`, wrapper) đã rất ổn định qua nhiều vòng debug — mục tiêu của kế hoạch này là KHÔNG đụng vào logic bên trong các class đó**, chỉ đổi **nơi chúng được gọi** (từ Activity sang Fragment) và **cách chọn effect** (từ hardcode sang `EffectDefinition`). Nếu trong lúc refactor phát hiện cần sửa logic recording, nên dừng lại và đối chiếu với `HandAr_Refactor_Plan.md` + `Camera_X_Hand_Landmarker.md` trước, tránh vô tình phá lại các bug đã mất công fix (đặc biệt Mục D trong checklist test — đây là những bug rất dễ tái phát nếu đổi sai chỗ trong lúc di chuyển code).

---

# Phần II — Nền, nhạc nền, an toàn dữ liệu và hiệu ứng canvas (Phase G–N)

> Tiếp nối `HandAr_Refactor_Plan.md` (Phase 0–5, pipeline ghi hình) và
> **Phần I** (Phase A–F, 5 màn hình).
> Tại thời điểm viết, app đã chạy đủ luồng: chọn hiệu ứng → quay → xem lại → thư viện → phát.
> Kế hoạch này giải quyết ba việc: **mở rộng khả năng biểu đạt của một hiệu ứng**
> (nền riêng, nhạc nền liên tục, trạng thái chỉ có tiếng),
> **bịt các lỗ làm mất video đang quay**, và **trả vài món nợ kỹ thuật rẻ tiền**.
>
> **Cập nhật 15/09/2026:** Phase G–K đã triển khai xong (`594ad8d` → `db66aa4`). Mỗi phase có
> một khối "Lệch so với kế hoạch" ghi những chỗ code thật khác tài liệu — đó đều là quyết định
> đã chốt lúc triển khai, phần mô tả gốc giữ nguyên làm hồ sơ ý định ban đầu.
> Bổ sung **Phase L và M** cho vòng tiếp theo, phục vụ hai hiệu ứng trong
> `Design_App_HandAr.md`: *Dịch chuyển tức thời giữa các phòng* (L) và *Vẽ canvas* (M).

---

## 0. Quyết định đã chốt

| Hạng mục | Lựa chọn | Lý do |
|---|---|---|
| Pha nhạc nền giữa live và video | **Vào màn camera là phát ngay; bấm Record thì dừng và phát lại từ đầu**, đồng thời vị trí nhạc trong track ghi cũng về 0 | Video luôn nghe "đúng đầu bài", và cái người dùng nghe lúc quay khớp đúng cái nằm trong file — đổi lại một lần nhạc giật lại ở thời điểm bấm Record, chấp nhận được vì nó trùng với khoảnh khắc người dùng biết mình vừa bắt đầu quay |
| Nơi phát nhạc nền lúc live | **Tách `BgmPlayer` riêng (`MediaPlayer`)**, không dùng chung `SoundPool` với tiếng hiệu ứng | `SoundPool` nạp toàn bộ file vào RAM và sinh ra để bắn tiếng ngắn; nhạc nền dài vài chục giây dùng `SoundPool` là sai công cụ. Tách riêng cũng cho phép nhạc nền và tiếng hiệu ứng có vòng đời độc lập |
| Loại nền hỗ trợ | **Cả 3: màu trơn, ảnh tĩnh, ảnh động** | Ba loại này khớp đúng ba loại `EffectAsset` đã có, tái dùng được cách nghĩ và một phần cách vẽ |
| Camera khi hiệu ứng có nền | **Ẩn hoàn toàn** | Không có chế độ pha trộn/làm mờ — giữ một quy tắc duy nhất: có `background` thì không thấy camera, không có thì thấy camera |
| Xoay màn hình | **Khoá `portrait`** | Xoá hẳn một lớp bug vòng đời (xoay lúc đang quay = `onDestroyView` = mất clip) với chi phí một dòng manifest. App vốn là app quay dọc |
| Phát hiện hết dung lượng (chốt lại lúc làm H) | **Bắt lỗi ghi thật (`writeFailed`)**, không kiểm tra ngưỡng `usableSpace` | Bắt được mọi nguyên nhân ghi hỏng chứ không riêng đĩa đầy; đổi lại không chặn trước được lúc bấm Record. Xem khối ghi chú đầu Phase H |
| Nền theo từng state (L) | **Cả effect hoặc có nền, hoặc không** — `require` ở `EffectDefinition` | Giữ nguyên luật "có nền thì không thấy camera" đã chốt ở I; tránh camera nháy hiện/ẩn theo cử chỉ và tránh phải đổi visibility của View từ luồng vẽ |
| Hiệu ứng canvas (M) | **`EffectAsset` trỏ tới *code*, không mô tả hình vẽ bằng dữ liệu** | Mô tả hình bằng dữ liệu = tự viết một ngôn ngữ vẽ + trình thông dịch cho nó. Mỗi kiểu vẽ một class; biến thể (màu, số lượng, tốc độ) là tham số constructor |

---

## 1. Thứ tự Phase và lý do

```
Phase G → Đổi hình dạng dữ liệu hiệu ứng   (nền tảng — làm trước tất cả)
Phase H → An toàn dữ liệu khi quay          (độc lập, rủi ro thấp, đang mất clip thật)
Phase I → Background                        (dựng trên G)
Phase J → Nhạc nền liên tục                 (dựng trên G, chạm vùng recording/)
Phase K → Nợ kỹ thuật rẻ tiền
--- ranh giới: G–K đã xong ---
Phase L → Nền theo từng state             (đủ để làm "Dịch chuyển tức thời giữa các phòng")
Phase M → Hiệu ứng vẽ bằng canvas          (đủ để làm "Vẽ canvas", mở đường cho Tia sét / Trái Đất / Hố đen)
Phase N → Dọn nợ hiệu năng của L/M + cấu trúc lại package
```

**Vì sao G đứng trước:** ba thay đổi kiểu dữ liệu (asset cho phép null, thêm `background`,
thêm `bgm`) đều sửa đúng một bộ file — `EffectDefinition`, `EffectState`, `OverlayView`,
`CameraRecordFragment`. Làm một lượt rẻ hơn ba lượt, và mọi màn UI làm sau (thư viện,
lưới chọn hiệu ứng, xuất video) sẽ dựng trên hình dạng dữ liệu cuối cùng thay vì phải sửa lại.

**Vì sao H tách khỏi phần còn lại:** H không phụ thuộc G, không phụ thuộc bất cứ thứ gì.
Nó có thể làm song song hoặc chen vào bất cứ lúc nào — và nên làm sớm, vì mỗi ngày trôi qua
là một khả năng mất clip thật.

**Vì sao J đứng cuối trong nhóm tính năng:** J là thay đổi **duy nhất** trong kế hoạch này
buộc phải sửa vào package `recording/` — vùng mà `README.md` và cả hai plan trước đều dặn
không đụng vào. Để nó sau I để khi chạm vào audio thì mọi thứ khác đã ổn định, không phải
đoán xem lỗi đến từ đâu.

---

## 2. Phase G — Đổi hình dạng dữ liệu hiệu ứng ✅ ĐÃ HOÀN THÀNH (`594ad8d`)

> **Lệch so với kế hoạch:** không có. `EffectBackground` và `EffectBgm` được tách thành file
> riêng (`EffectBackground.kt`, `EffectBgm.kt`) thay vì nằm chung — chỉ là cách chia file.

Mục tiêu của Phase G là **chỉ đổi kiểu dữ liệu và cho compile xanh**, không làm cho nền hay
nhạc nền chạy được. Kết thúc Phase G, 10 hiệu ứng hiện có phải chạy **y hệt như trước**.

### G1 — `EffectState.asset` cho phép null (trạng thái chỉ có tiếng)

```kotlin
data class EffectState(
    val id: String,
    val gesture: GestureRecognizer,
    val asset: EffectAsset?,      // null = trạng thái này chỉ phát tiếng, không vẽ gì
    val soundRes: Int?
) {
    init {
        require(asset != null || soundRes != null) {
            "EffectState khong co ca hinh lan tieng — trang thai nay vo nghia: " + id
        }
    }
}
```

`require` trong `init` chặn được thứ mà kiểu dữ liệu không chặn được: cả hai trường đều
nullable một cách hợp lệ, nhưng **cả hai cùng null** thì trạng thái đó không làm gì cả —
một hiệu ứng khai sai như vậy sẽ im lặng không có biểu hiện gì lúc chạy, rất khó truy.

Sửa `OverlayView`:

```kotlin
private var liveVisuals: List<EffectVisual?> = emptyList()
private var recordingVisuals: List<EffectVisual?> = emptyList()

fun setEffect(effect: EffectDefinition) {
    this.effect = effect
    liveVisuals = effect.states.map { st -> st.asset?.let { createEffectVisual(context!!, it) } }
    recordingVisuals = effect.states.map { st -> st.asset?.let { createEffectVisual(context!!, it) } }
}
```

và ở cuối `drawHandEffects`:

```kotlin
visuals.forEachIndexed { index, visual -> visual?.setActive(index == matchedIndex) }
visuals[matchedIndex]?.draw(canvas, cx, cy, r)
```

**Không cần sửa gì ở phần phát tiếng.** `CameraRecordFragment` đang dựng `statePcmMap` theo
`state.id` và `state.soundRes`, hoàn toàn không quan tâm tới `asset` — nên trạng thái chỉ có
tiếng chạy đúng ngay mà không phải động vào logic âm thanh.

### G2 — `EffectBackground`

```kotlin
sealed class EffectBackground {
    data class Solid(val colorRes: Int) : EffectBackground()
    data class Image(val resId: Int) : EffectBackground()
    data class Animated(val resId: Int) : EffectBackground()
}
```

Dùng `sealed class` cùng lý do đã ghi ở **Phần I** mục 3.1: thêm loại nền
mới mà quên viết cách vẽ tương ứng thì **lỗi biên dịch ngay**, không đợi runtime.

Ba nhánh cố ý khớp một–một với ba nhánh của `EffectAsset` — ai đã hiểu `EffectAsset` thì không
phải học lại khái niệm mới.

### G3 — `EffectBgm`

```kotlin
data class EffectBgm(
    val resId: Int,
    /** Am luong nhac nen, 0–100. Mac dinh 50 de tieng hieu ung van noi len tren. */
    val gainPercent: Int = 50
)
```

Tách thành data class thay vì để trần `bgmRes: Int?` vì âm lượng là thứ **chắc chắn** sẽ phải
chỉnh theo từng bài nhạc (xem Phase J, mục chống vỡ tiếng), và sửa một data class dễ hơn thêm
tham số thứ hai vào `EffectDefinition` sau này.

### G4 — `EffectDefinition` sau khi đổi

```kotlin
data class EffectDefinition(
    val id: String,
    val displayName: String,
    val thumbnailRes: Int,
    val requiredNumHands: Int,
    val states: List<EffectState>,
    val background: EffectBackground? = null,   // null = hien thi camera that
    val bgm: EffectBgm? = null                  // null = khong co nhac nen
)
```

Cả hai trường mới đều có giá trị mặc định → **10 `EffectDefinition` hiện có trong
`EffectRepository` không phải sửa một chữ nào.**

### G5 — Dọn `idleAsset`

`EffectDefinition.idleAsset` đang được khai nhưng **không nơi nào đọc tới** (`grep` toàn bộ
`app/src/main/java` chỉ ra đúng một dòng: chính chỗ khai báo). Đề xuất **xoá** ở Phase G.

Lý do xoá thay vì implement: một trường không ai dùng nhưng trông như đang dùng là thứ gây
hiểu nhầm đắt hơn là tiện. Nếu sau này thật sự cần vẽ gì đó lúc không cử chỉ nào khớp, cách
đúng trong hình dạng dữ liệu mới là thêm một `EffectState` cuối danh sách với gesture luôn
trả `true` — tận dụng đúng quy tắc "thứ tự khai báo là độ ưu tiên" đã có, không cần khái niệm riêng.

### Nghiệm thu Phase G

- Build xanh, `EffectRepository` không phải sửa.
- Chạy lại Checklist **B** (live preview) và **D** (chống regression) — cả 10 hiệu ứng phải
  hành xử **y hệt** bản trước. Phase G không được phép làm thay đổi bất cứ hành vi nào nhìn thấy được.
- Thêm một hiệu ứng thử nghiệm có một trạng thái `asset = null` + `soundRes` khác null,
  xác nhận: đúng cử chỉ đó thì **có tiếng, không có hình**, và không crash.

---

## 3. Phase H — An toàn dữ liệu khi quay ✅ ĐÃ HOÀN THÀNH (`a617f25`)

> **Lệch so với kế hoạch — đây là các quyết định đã chốt lúc triển khai, phần mô tả bên dưới
> giữ nguyên làm hồ sơ ý định ban đầu:**
>
> 1. **H3 làm theo hướng khác hẳn: phát hiện ghi hỏng thật, không đoán theo ngưỡng dung lượng.**
>    Không có `MIN_FREE_BYTES_TO_START`, `LOW_SPACE_STOP_BYTES`, không kiểm tra `usableSpace`,
>    không có chuỗi `not_enough_storage`. Thay vào đó: `MuxerCoordinator.writeVideo()` /
>    `writeAudio()` bắt exception và trả `false` → `VideoRecorder.writeFailed` → vòng lặp
>    `recordingFrameThread` kiểm tra cờ này **mỗi frame** → `onLowStorageDuringRecording()`
>    dừng và lưu.
>    *Đánh đổi:* phản ứng theo sự thật (đĩa đầy, lỗi I/O, quota — bắt được hết) thay vì đoán
>    theo một con số ngưỡng; đổi lại không chặn trước được lúc bấm Record và mất vài frame cuối.
> 2. **Không dùng `mainHandler` riêng** — tái dùng `timerHandler` sẵn có để post về main thread.
> 3. **Toast "đã lưu" chuyển vào trong callback `stop {}`**, chỉ hiện khi file thật sự hợp lệ,
>    thay vì hiện ngay lúc bấm Back như kế hoạch ghi.
> 4. **Việc phát hiện "chưa có frame đầu" nằm ở `VideoRecorder`, không nằm ở Fragment:**
>    `hadValidOutput()` trả về `muxer.hasStarted`. Fragment chỉ hỏi rồi xoá file và
>    `popBackStack()`. Gọn hơn và đúng chỗ hơn so với việc Fragment tự theo dõi cờ `onFirstFrame`.
> 5. Hàm dừng có thêm tham số thứ hai: `stopRecordingAndGoToPreview(ignoreMinDuration, showSavedToast)`.

Ba lỗ hiện tại đều dẫn tới cùng một hậu quả: người dùng quay xong mà không có file.

### H1 — Khoá xoay màn hình

`AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="portrait"
    ... >
```

Hiện manifest **không khai** `screenOrientation`, nghĩa là xoay máy lúc đang quay sẽ huỷ
view của `CameraRecordFragment` → `onDestroyView` chạy → `recorder.stop()` được gọi nhưng
callback `stop {}` bị chốt cửa bởi `if (!isAdded) return@stop`, không ai điều hướng, không ai
báo gì — clip nằm lại trên đĩa mà người dùng không biết là nó tồn tại.

Khoá `portrait` xử lý gọn, và sau khi khoá thì **không còn lý do bắt buộc phải thêm `ViewModel`**
cho màn camera (xem Phase K).

### H2 — Back khi đang quay thì lưu, không huỷ

Đăng ký trong `onViewCreated`:

```kotlin
val backCallback = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
        Toast.makeText(requireContext(), R.string.recording_saved_on_back, Toast.LENGTH_SHORT).show()
        stopRecordingAndGoToPreview(ignoreMinDuration = true)
    }
}
requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
```

và bật/tắt `backCallback.isEnabled` đúng hai nơi đang đổi icon nút record — tức là ngay trong
`btnToggleRecord.setOnClickListener`, cùng chỗ đã đọc `videoRecorder?.isRecording`.

**Hai chi tiết dễ sai:**

1. **`MIN_RECORD_DURATION_MS`.** `toggleRecording()` hiện từ chối dừng nếu mới quay dưới
   ngưỡng tối thiểu và hiện Toast. Với nút Record thì đúng (chống double-tap), nhưng với
   phím Back thì sai — người dùng bấm Back là muốn rời màn, chặn lại sẽ thành app không thoát được.
   Vì vậy cần tách nhánh dừng ra thành hàm riêng `stopRecordingAndGoToPreview(ignoreMinDuration)`
   và Back luôn truyền `true`.
2. **Chưa có frame đầu.** Nếu `onFirstFrame` chưa bắn thì muxer chưa start, file `.mp4` trên
   đĩa là file rỗng/hỏng — lưu lại chỉ tạo rác trong thư viện. Quy tắc: nếu chưa có frame đầu
   thì **dừng, xoá file, `popBackStack()` bình thường**, không Toast "đã lưu".

`viewLifecycleOwner` là bắt buộc ở đây — dùng `this` thì callback sống sót qua `onDestroyView`
và sẽ chạy trên một fragment không còn view.

### H3 — Hết dung lượng thì tự dừng và lưu

Bitrate thực đo được ~2.9 Mbps ≈ **22 MB mỗi phút** (xem `README.md` mục Hiệu năng). Hai ngưỡng:

| Hằng số | Giá trị đề xuất | Ý nghĩa |
|---|---|---|
| `MIN_FREE_BYTES_TO_START` | 150 MB | Từ chối bắt đầu quay — đủ cho khoảng 6–7 phút |
| `LOW_SPACE_STOP_BYTES` | 50 MB | Đang quay mà tụt dưới mức này thì tự dừng và lưu |

**Trước khi start**, trong nhánh `else` của `toggleRecording()`:

```kotlin
val outDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES)
if ((outDir?.usableSpace ?: 0L) < MIN_FREE_BYTES_TO_START) {
    Toast.makeText(requireContext(), R.string.not_enough_storage, Toast.LENGTH_LONG).show()
    return
}
```

**Trong lúc quay**, kiểm tra trong `recordingFrameThread` — nhưng **không phải mỗi frame**:
`usableSpace` là một lệnh gọi hệ thống, gọi 25 lần/giây trong vòng lặp có ngân sách 40 ms/frame
là tự chuốc thêm một biến số hiệu năng. Đếm frame và kiểm tra mỗi ~2 giây (50 frame).

Và vì **thread ghi hình tuyệt đối không được chạm `binding`** (bài học Phase B), nó chỉ được
post ngược về main thread:

```kotlin
// trong vong lap recordingFrameThread
if (++spaceCheckCounter >= 50) {
    spaceCheckCounter = 0
    if (outDirForThread.usableSpace < LOW_SPACE_STOP_BYTES) {
        mainHandler.post { onLowStorageDuringRecording() }
        break
    }
}
```

`outDirForThread` phải được capture ra biến local **trước** khi tạo thread (cùng quy tắc với
`overlayView`), và `onLowStorageDuringRecording()` chạy trên main thread phải mở đầu bằng
`_binding ?: return` vì nó là một callback đến muộn điển hình.

### H4 — Chuỗi mới cần thêm vào `strings.xml`

`recording_saved_on_back`, `not_enough_storage`, `stopped_low_storage`.

### Nghiệm thu Phase H

| Ca | Thao tác | Kỳ vọng |
|---|---|---|
| H-a | Quay 10s → bấm Back | Toast "đã lưu", sang màn xem lại, file phát được |
| H-b | Bấm Record → bấm Back ngay (<1s) | Không kẹt lại màn camera; không sinh file rác trong thư viện |
| H-c | Lấp đĩa còn <150 MB → bấm Record | Không quay, Toast báo thiếu chỗ |
| H-d | Lấp đĩa còn ~60 MB → quay liên tục | Tự dừng, Toast báo, file vẫn phát được đến giây cuối |
| H-e | Xoay máy lúc đang quay | Màn hình không xoay, quay tiếp bình thường |

Cộng thêm Checklist **D** và **H** như thường lệ.

---

## 4. Phase I — Background ✅ ĐÃ HOÀN THÀNH (`acbb766`)

> **Lệch so với kế hoạch:**
>
> 1. **I4 (bỏ tạo `rotatedBitmap` khi có nền) chưa làm.** `latestCameraBitmap` vẫn được tạo mỗi
>    frame kể cả khi nền phủ kín và không ai vẽ nó. Khoản tối ưu này vẫn còn nguyên trên bàn —
>    xem lại khi có lý do đo được.
> 2. **I5 đã làm và ghi ở nơi khác:** quy chuẩn asset nền nằm trong
>    `Asset_Format_Guidelines.md` **mục 5**, chi tiết hơn bảng phác trong kế hoạch này.
> 3. `OverlayView` lộ ra hai hàm cho thread ghi hình: `hasBackground()` và
>    `drawRecordingBackground(canvas)` — đúng tinh thần I3, chỉ khác tên gọi.

### I1 — Nơi vẽ nền

Nền được vẽ ở **hai chỗ, cùng một nguồn dữ liệu, hai instance riêng** — giống hệt cách
`OverlayView` đang giữ `liveVisuals` và `recordingVisuals` tách đôi:

```kotlin
interface BackgroundRenderer {
    fun draw(canvas: Canvas)     // phu kin canvas, center-crop
    fun release()
}
```

`OverlayView` vẽ nền **trước** hiệu ứng, ở cả hai đường:

- `onDraw()` → `liveBackground?.draw(canvas)` rồi mới `drawHandEffects(..., forRecording = false)`
- `drawHandEffects(..., forRecording = true)` → thread ghi hình gọi `recordingBackground?.draw(canvas)` trước

Cần **hai instance** vì nền `Animated` có trạng thái nội bộ (frame hiện tại), mà hai đường vẽ
chạy ở hai nhịp khác nhau — đúng cạm bẫy đã ghi cho GIF ở plan trước. Và cũng như
`SpriteSheetVisual`, frame của nền động phải tính theo **đồng hồ hệ thống**, không theo số lần
gọi `draw()`.

### I2 — Ẩn camera

Khi `effect.background != null`:

- `binding.preview.visibility = View.INVISIBLE` — `PreviewView` nằm dưới `OverlayView` trong
  `FrameLayout`, mà nền phủ kín nên thực tế đã bị che; đặt `INVISIBLE` để khỏi tốn công vẽ.
- **Vẫn phải bind `ImageAnalysis` như cũ** — không có nó thì không có bàn tay, không có hiệu ứng.
  Chỉ `Preview` là thứ không cần thiết nữa.

> Cân nhắc (không bắt buộc ở I2): khi có nền, có thể **bỏ hẳn việc bind use case `Preview`**
> thay vì chỉ ẩn View. Rẻ hơn nữa, nhưng đổi lại phải xử lý trường hợp đổi hiệu ứng mà không
> rời màn (chưa có). Làm bản `INVISIBLE` trước, đo bằng `RecPerf`, chỉ đi tiếp nếu số liệu đáng.

### I3 — Thread ghi hình

Vòng lặp hiện tại bị chốt bởi `if (bitmap != null)` — với hiệu ứng có nền, khung hình không
phụ thuộc vào ảnh camera nữa, nên điều kiện phải nới ra:

```kotlin
val hasBackground = recordingBackground != null
if (hasBackground || bitmap != null) {
    videoRecorder?.pushFrame { canvas ->
        if (hasBackground) {
            recordingBackground.draw(canvas)
        } else {
            /* nguyen khoi drawBitmap camera hien co, khong sua */
        }
        handResult?.let { overlay.drawHandEffects(canvas, it, mirrorX = true, forRecording = true) }
    }
}
```

Giữ nguyên khối `drawBitmap` + `Matrix` hiện có, **không refactor chung** với đường vẽ nền —
đó là đoạn đã cân chỉnh đúng phép lật gương và center-crop, gộp lại chỉ để "cho gọn" là đúng
kiểu thay đổi build xanh nhưng sai hình (bài học "đừng đọc, hãy diff").

`mirrorX = true` giữ nguyên kể cả khi có nền: toạ độ tay vẫn đến từ camera trước, việc có hiển
thị camera hay không không đổi phép lật.

### I4 — Cơ hội hiệu năng đi kèm

Khi có nền, `latestCameraBitmap` không còn ai vẽ. Nếu chuỗi xử lý trong `ImageAnalysis` đang
tạo `rotatedBitmap` **chỉ** để phục vụ việc vẽ đó, thì ở chế độ nền có thể bỏ hẳn bước tạo
bitmap mỗi frame — một khoản cấp phát lớn biến mất khỏi đường nóng.

**Phải xác minh trước khi làm:** đọc kỹ đoạn quanh `CameraRecordFragment.kt:312` xem MediaPipe
có đang dùng chung chính bitmap đó không. Nếu có thì không bỏ được. Và theo đúng quy ước dự án:
**đo trước khi kết luận** — chạy `RecPerf` hai lần cùng cấu hình để biết ngưỡng nhiễu, máy nguội.

### I5 — Quy chuẩn asset nền (bổ sung vào `Asset_Format_Guidelines.md`)

| Loại | Quy chuẩn |
|---|---|
| `Solid` | Khai bằng `colorRes` trong `colors.xml`, không dùng số hex trần trong `EffectRepository` |
| `Image` | Tỉ lệ dọc ~9:19.5, cạnh ngắn ≥ 720px, đặt trong `res/drawable-nodpi/`, WebP |
| `Animated` | Toàn màn hình nên nặng hơn hiệu ứng nhiều: ≤ 720p, loop ngắn (≤ 3s), file ≤ 1.5 MB |

Nền được vẽ **center-crop** — mọi chi tiết quan trọng phải nằm trong vùng an toàn giữa khung,
vì cạnh sẽ bị cắt trên máy có tỉ lệ khác.

### Nghiệm thu Phase I

- Hiệu ứng có nền: không thấy camera ở bất kỳ khoảnh khắc nào, kể cả frame đầu và lúc xoay tay nhanh.
- Hiệu ứng không có nền: hành vi **không đổi** so với trước Phase I (Checklist B + D).
- Video ghi ra và live preview có **cùng** nền, cùng vị trí crop.
- Nền động chạy cùng tốc độ ở live và trong video (quay 30s, so hai bên).
- `RecPerf`: FPS ghi hình không tụt so với bản không nền (máy nguội, đo 2 lần).

---

## 5. Phase J — Nhạc nền liên tục ✅ ĐÃ HOÀN THÀNH (`d52b6f6`)

> **Lệch so với kế hoạch:** rất ít.
>
> 1. `onResume()` gọi `bgmPlayer?.startFromBeginning()` nhưng có chốt
>    `if (videoRecorder?.isRecording != true)` — quay lại app giữa lúc đang quay thì **không**
>    giật nhạc về đầu, tránh phá mất pha đã neo ở `onFirstFrame`.
> 2. `audioMixer.setBgm(bgmPcm, gainPercent)` + `resetBgmPos()` gọi **trước** `start()`, đúng như
>    mục J3 phân tích (lúc đó `mix()` chưa chạy nên vị trí nào cũng như nhau).
> 3. Phần sửa `AudioMixer` đúng phạm vi cam kết ở mục 8: chỉ thêm ba trường và cộng thêm một
>    nguồn PCM vào `mix()`, không đụng `effectClock`, `totalAudioSamples`, hay PTS.

> ⚠️ Đây là thay đổi **duy nhất** trong kế hoạch này chạm vào package `recording/`.
> Đọc `HandAr_Refactor_Plan.md` mục về kiến trúc audio trước khi sửa `AudioMixer`.

### J1 — `BgmPlayer` cho phần nghe lúc live

File mới `BgmPlayer.kt` đặt **cạnh `SoundEffectPlayer.kt`**, ngoài package `recording/` — cùng
lý do `SoundEffectPlayer` được tách riêng: phát ra loa và trộn vào file là hai việc khác nhau,
không được để lẫn.

```kotlin
class BgmPlayer(context: Context, resId: Int, gainPercent: Int) {
    private var player: MediaPlayer? = MediaPlayer.create(context, resId)?.apply {
        isLooping = true
        val v = gainPercent / 100f
        setVolume(v, v)
    }
    fun startFromBeginning() { player?.seekTo(0); player?.start() }
    fun pause() { player?.pause() }
    fun release() { player?.release(); player = null }
}
```

Vòng đời: tạo ở `onViewCreated`, `startFromBeginning()` khi camera sẵn sàng, `pause()` ở
`onPause`, `release()` ở `onDestroyView` — đối xứng đúng như bảng cấp phát/giải phóng trong
`Fragment_Review_Checklist.md`.

### J2 — Khe nhạc nền trong `AudioMixer`

`AudioMixer` hiện có đúng một khe `effectPcm` và `mix()` đã tự loop khi chạm cuối mảng.
Thêm khe thứ hai **luôn bật**, trộn trước khi trộn tiếng hiệu ứng:

```kotlin
@Volatile private var bgmPcm: ShortArray? = null
private var bgmPos = 0
private var bgmGain = 0.5f

fun setBgm(pcm: ShortArray?, gainPercent: Int) { ... }    // dat 1 lan luc vao man
fun resetBgmPos() { synchronized(lock) { bgmPos = 0 } }    // goi luc bat dau ghi
```

Trong `mix()`, cộng nhạc nền vào `result` trước vòng lặp tiếng hiệu ứng hiện có, **nhân
`bgmGain` trước khi cộng**, và giữ nguyên `coerceIn(Short.MIN_VALUE, Short.MAX_VALUE)` cuối cùng.

**Vì sao bắt buộc phải giảm âm lượng nhạc nền:** `mix()` cộng thẳng hai nguồn rồi mới kẹp biên.
Hai nguồn đều gần biên độ tối đa thì tổng liên tục chạm trần → tiếng vỡ, rè. Đây không phải
tuỳ chọn thẩm mỹ mà là điều kiện để track audio không hỏng. `gainPercent = 50` là điểm xuất
phát an toàn, chỉnh theo từng bài.

### J3 — Điểm đồng pha

Theo quyết định đã chốt, nhạc nền trong video **luôn bắt đầu từ giây 0**. Có hai mốc thời gian
dễ nhầm:

- `videoRecorder.start()` — chỉ mới chuẩn bị encoder, **chưa** có audio nào được sinh ra.
- Frame đầu tiên thực sự được encode (`onFirstFrame`) — đây mới là lúc `EffectAudioClock` khởi
  động và `mix()` bắt đầu được gọi. Toàn bộ PTS audio/video neo vào đúng mốc này.

Vì `mix()` chưa chạy trước mốc đó, `resetBgmPos()` gọi ở đâu trước `onFirstFrame` cũng cho kết
quả như nhau trong file. Nhưng phần **live** thì khác: để cái tai nghe được khớp với cái nằm
trong file, `BgmPlayer.startFromBeginning()` phải gọi **trong `onFirstFrame`**, không phải lúc
bấm nút.

```kotlin
onFirstFrame = {
    startRecordingTimerUI()
    bgmPlayer?.startFromBeginning()      // live giat lai ve dau, khop dung track ghi
}
```

Lưu ý sự bất đối xứng có chủ ý với tiếng hiệu ứng: tiếng hiệu ứng dùng `triggerEffect(pcm,
elapsedSamples)` để **bắt vào giữa chừng** cho khớp cái đang phát, còn nhạc nền thì cố tình
**quay về 0**. Hai lựa chọn ngược nhau, đúng theo hai mục đích khác nhau — ghi rõ ở đây vì
người đọc code sau này chắc chắn sẽ tưởng là lỗi không nhất quán.

### J4 — Bộ nhớ và quy chuẩn file nhạc nền

`loadWavPcm` nạp **toàn bộ** file vào RAM dưới dạng `ShortArray`. PCM 16-bit mono 44100 Hz tốn
**88,2 KB mỗi giây**:

| Độ dài | RAM |
|---|---|
| 5s | ~0,44 MB |
| 15s | ~1,3 MB |
| 30s | ~2,6 MB |
| 60s | ~5,3 MB |

Quy chuẩn đề xuất cho `Asset_Format_Guidelines.md`: **nhạc nền là loop ≤ 30 giây**, cùng định
dạng bắt buộc với tiếng hiệu ứng (**PCM 16-bit / Mono / 44100 Hz**) — sai sample rate không
crash, chỉ làm nhạc chạy sai tốc độ, đúng cạm bẫy đã ghi.

### Nghiệm thu Phase J

| Ca | Kỳ vọng |
|---|---|
| J-a | Vào màn camera → nhạc nền phát ngay, loop mượt, không có khoảng lặng ở điểm nối |
| J-b | Bấm Record → nhạc giật về đầu đúng một lần, rồi chạy liên tục |
| J-c | Mở video vừa quay → nhạc bắt đầu từ giây 0 của bài, khớp với cái đã nghe lúc quay |
| J-d | Làm cử chỉ liên tục lúc có nhạc nền → tiếng hiệu ứng vẫn nghe rõ, **không rè, không vỡ** |
| J-e | Hiệu ứng **không có** `bgm` → track audio không đổi gì so với trước Phase J |
| J-f | Rời màn camera → nhạc tắt hẳn, vào lại phát lại từ đầu; vào/ra 10 lần không leak (`MediaPlayer` đã release) |

J-d và J-e là hai ca quan trọng nhất — chúng là thứ xác nhận việc chạm vào `AudioMixer` không
phá lại các bug đã mất công fix ở Phase 0–5.

---

## 6. Phase K — Nợ kỹ thuật rẻ tiền ✅ K1 ĐÃ HOÀN THÀNH (`db66aa4`)

> **Lệch so với kế hoạch:** làm nhiều hơn phần đã ghi — ngoài `hello_blank_fragment`,
> `done`/`share` viết hoa và `recording_timer_default`, còn rút thêm các chuỗi Toast đang
> hardcode trong `CameraRecordFragment` ra `strings.xml`: `camera_permission_denied`,
> `recording_too_short`. K2 (danh sách hoãn) vẫn nguyên trạng, chưa việc nào được làm.

Chỉ gồm những việc có tỉ lệ lợi ích/chi phí cao và không rủi ro.

### K1 — Dọn `strings.xml`
- Xoá `hello_blank_fragment` (placeholder do Android Studio sinh, không ai dùng).
- Thống nhất hoa/thường: `done` = "xong", `share` = "chia sẻ" trong khi `delete` = "Xóa" — đây
  là `contentDescription` đọc bởi TalkBack, nên viết hoa đầu câu cho đồng bộ.
- Đưa `"0:00"` hardcode trong `fragment_camera_record.xml` ra `strings.xml`.

### K2 — Hoãn có điều kiện (ghi lại để khỏi quên)
- **`ViewModel`**: sau khi khoá `portrait` (H1), lý do cấp thiết duy nhất còn lại là process
  death. Chưa làm.
- **Đòn bẩy hiệu năng `Perf_Notes.md` mục 5–6** (hạ `targetShortSide`, `setOutputImageRotationEnabled`,
  `KEY_I_FRAME_INTERVAL`): chỉ đụng khi có số đo cho thấy cần, không đụng vì "nghe hợp lý".
- **Gỡ `RecordingPerfLogger` và các đoạn `// TẠM`**: giữ lại xuyên suốt Phase G–K vì Phase I
  và J đều cần nó để nghiệm thu; gỡ sau khi J xong và số liệu đã ổn định.

---

## 7. Phase L — Nền theo từng state ✅ ĐÃ HOÀN THÀNH (`f51750b`)

> Hiệu ứng đích: **Dịch chuyển tức thời giữa các phòng** (`docs/Design_App_HandAr.md` mục 4).
> 4 state, mỗi state là một căn phòng: số 1 → phòng khách (tiếng TV), số 2 → phòng tắm (tiếng
> nước), số 3 → bếp (bát đũa), nắm tay → phòng ngủ (tiếng ngáy). Cả 4 state đều
> `asset = null` — hiệu ứng này không vẽ gì lên tay, nó chỉ đổi nền và đổi tiếng.

### L1 — `EffectState.background`

```kotlin
data class EffectState(
    val id: String,
    val gesture: GestureRecognizer,
    val asset: EffectAsset?,
    val soundRes: Int?,
    val background: EffectBackground? = null   // null = dung background mac dinh cua effect
)
```

Luật phân giải: nền hiển thị = `state đang khớp`.background ?: `effect`.background.

Mô hình tư duy: **một state = một cảnh** (cử chỉ + hình bám tay + tiếng + nền). Nền là thứ duy
nhất có giá trị mặc định ở cấp effect, để không phải khai lại ở mọi state.

Ràng buộc bắt buộc, đặt trong `EffectDefinition.init`:

```kotlin
require(states.none { it.background != null } || background != null) {
    "Effect co state mang background rieng thi phai khai background mac dinh: " + id
}
```

**Vì sao:** `CameraRecordFragment` dùng `overlay.hasBackground()` đúng một lần lúc
`onViewCreated` để ẩn `PreviewView`. Nếu cho phép state A có nền còn state B không, camera sẽ
phải hiện/ẩn liên tục theo cử chỉ — vừa nháy hình, vừa buộc phải đổi visibility của View từ
luồng vẽ. Ràng buộc trên giữ nguyên luật đã chốt từ Phase I: **một effect hoặc ở chế độ nền
(camera tắt suốt, các state đổi nền cho nhau), hoặc ở chế độ camera (không state nào có nền)** —
không có trạng thái thứ ba.

### L2 — `StateMode`: bám theo cử chỉ hay giữ trạng thái cuối

Đây là thứ **không có trong thiết kế hiện tại** và không có nó thì hiệu ứng teleport sai hẳn ý nghĩa.

```kotlin
enum class StateMode { Momentary, Latched }

// trong EffectDefinition
val stateMode: StateMode = StateMode.Momentary
```

- **`Momentary`** (mặc định — đúng hành vi của cả 10 hiệu ứng hiện có): không cử chỉ nào khớp
  → không vẽ gì, tắt tiếng.
- **`Latched`**: state khớp gần nhất **được giữ nguyên** cho tới khi một state khác khớp.

Teleport phải là `Latched`: người dùng giơ số 2 để vào phòng tắm rồi hạ tay xuống nghỉ — họ vẫn
đang **ở trong phòng tắm**. Với `Momentary`, nền nhảy về mặc định ngay khi mất cử chỉ.

Hai nơi phải hiểu `stateMode`:

| Nơi | Hiện tại | Cần thành |
|---|---|---|
| `OverlayView` (nền + visual) | không khớp → `return`, không vẽ gì | `Latched`: giữ `matchedIndex` cũ để chọn nền |
| `CameraRecordFragment.handleGesture` | không khớp → `clearActiveEffect()`, tắt tiếng | `Latched`: **không** tắt tiếng, tiếng TV chạy tiếp |

**Giao giữa hai luật, dễ hiểu nhầm:** `Latched` chỉ chốt *việc chọn state*, không chốt *việc vẽ
lên tay*. Visual bám tay vẫn chỉ được vẽ khi có tay trong khung — điều này đã đúng sẵn
(`drawHandEffects` thoát sớm khi `hands.isEmpty()`). Với teleport thì không ảnh hưởng gì vì cả
4 state đều `asset = null`.

### L3 — Gộp về một cửa vào duy nhất

Hiện `onDraw` vẽ `liveBackground` **trước**, rồi mới gọi `drawHandEffects` — nơi `matchedIndex`
mới được tính. Với nền theo state thì thứ tự đó không dùng được nữa: phải biết state trước mới
biết vẽ nền nào.

```kotlin
fun drawFrame(canvas: Canvas, handResult: HandLandmarkerResult?, mirrorX: Boolean, forRecording: Boolean)
// 1. phan giai state mot lan (co ap dung StateMode)
// 2. ve background tuong ung
// 3. ve visual cua state do (neu co tay va co asset)
```

`recordingFrameThread` gọi đúng một hàm này, thay cho cặp `hasBackground()` +
`drawRecordingBackground()` + `drawHandEffects()` hiện tại.

Hai cái lợi kèm theo: không còn khả năng hai lần nhận diện trong cùng một frame cho ra hai kết
quả khác nhau; và nền được vẽ **cả khi không có tay** — hiện hai lệnh `return` sớm trong
`drawHandEffects` đang chặn mất điều đó.

### L4 — Bộ nhớ: 4 ảnh toàn màn hình là vấn đề thật

`OverlayView` giữ **hai bộ** renderer (live + recording). 4 phòng → 8 bitmap. 720p ARGB_8888 là
~3,7 MB mỗi cái → **~30 MB**. Trên máy yếu, đó là đủ để GC bắt đầu chen vào thread ghi hình —
đúng thứ `README.md` đang ghi là "GC làm đứng thread ghi: 0 lần".

Ba biện pháp, làm cả ba:

1. **`RGB_565` cho nền `Image`.** Nền là lớp dưới cùng, phủ kín, **không cần alpha** (điều này
   `Asset_Format_Guidelines.md` mục 5 đã ghi). Giảm đúng một nửa.
2. **Chia sẻ `Bitmap` giữa live và recording.** Lý do phải có hai instance là **trạng thái
   animation**, không phải pixel — `ImageBackgroundRenderer` và `SolidBackgroundRenderer` không
   có trạng thái gì. Cho hai renderer dùng chung một `Bitmap` đã decode (ở kích thước lớn hơn
   trong hai canvas), mỗi renderer chỉ giữ `Matrix` riêng của mình. Chỉ `Animated` mới thật sự
   cần hai bộ.
3. **Dedup theo `equals`.** `EffectBackground` là `data class` nên dùng được ngay làm khoá:
   `HashMap<EffectBackground, BackgroundRenderer>().getOrPut { ... }`. Teleport có 4 nền khác
   nhau nên không hưởng lợi, nhưng hiệu ứng nhiều state dùng chung một nền thì có.
   ⚠️ `release()` phải duyệt `cache.values`, **không** duyệt list song song với `states` —
   duyệt list sẽ release cùng một renderer nhiều lần.

Sau cả ba: ~4 × 2,2 MB ≈ **9 MB** thay vì 30 MB.

Tiện thể dọn luôn: `ImageBackgroundRenderer.draw()` và `AnimatedBackgroundRenderer.draw()` hiện
cấp phát một `Matrix` **mỗi frame**. Ghi nhớ kích thước canvas lần trước và chỉ tính lại khi nó
đổi — vừa hết cấp phát, vừa hết tính toán thừa.

### L5 — `setActive(Boolean)` cho `BackgroundRenderer`

Không phải để cho đẹp đối xứng mà là hiệu năng thật: `AnimatedImageDrawable` của nền **không
được chọn** vẫn đang `start()` và giải mã frame liên tục. Ba nền động trong một effect là ba
luồng giải mã chạy song song trong khi chỉ một cái được nhìn thấy.

`Solid` / `Image` thì `setActive` là no-op — đúng như `StaticImageVisual` đang làm.

Kèm lợi ích thứ hai: renderer biết mình được kích hoạt lúc nào → mở đường cho nền phản ứng theo
cử chỉ (chớp sáng rồi tối dần khi vừa đổi phòng), dùng đúng cơ chế `setActive` + đồng hồ hệ thống
mà `SpriteSheetVisual` đang dùng.

### L6 — Cử chỉ đếm ngón tay

Teleport cần 4 cử chỉ mới trong `object Gestures`: giơ 1 ngón (trỏ), 2 ngón (trỏ + giữa), 3 ngón
(trỏ + giữa + áp út), và nắm tay (đã có).

Luật cũ vẫn áp dụng nguyên: **các công thức phải loại trừ lẫn nhau**, và **thứ tự khai trong
`states` chính là độ ưu tiên**. Riêng bộ đếm ngón này rất dễ chồng nhau (giơ 3 ngón thường thoả
luôn điều kiện "trỏ và giữa đang duỗi"), nên công thức phải khẳng định cả **ngón nào đang duỗi
lẫn ngón nào đang gập**, không chỉ đếm số ngón duỗi.

### L7 — Tiếng nền phòng

Không cần làm gì thêm: `SoundEffectPlayer.playForSound` đã gọi `soundPool.play(..., loop = -1, ...)`
và `AudioMixer.triggerEffect` đã tự lặp PCM khi chạm cuối mảng. Tiếng TV/nước/ngáy là tiếng chạy
liên tục và cơ chế hiện tại phục vụ đúng.

Hai điều cần nhớ:
- `statePcmMap` nạp **toàn bộ** PCM của **mọi** state vào RAM. 4 tiếng nền × 88,2 KB/giây →
  giữ mỗi file ≤ 5–10 giây.
- Đừng nhầm với `EffectBgm`: `bgm` là nhạc nền **của cả hiệu ứng**, chạy suốt bất kể cử chỉ;
  tiếng phòng là `soundRes` **của từng state**, đổi theo cử chỉ. Teleport dùng cái thứ hai.

### L8 — Hoãn có chủ ý

Chuyển cảnh mượt (nền cũ mờ dần sang nền mới) sẽ làm hiệu ứng tên là "dịch chuyển" trông đắt tiền
hơn hẳn, nhưng đó là một `TransitionBackgroundRenderer` bọc ngoài hai renderer và blend alpha
trong ~150 ms. Thêm sau được mà không phá gì đã có. **Không làm ở vòng đầu.**

### Nghiệm thu Phase L

| Ca | Kỳ vọng |
|---|---|
| L-a | Giơ số 1/2/3/nắm tay → đổi đúng 4 phòng, đúng 4 tiếng nền |
| L-b | Hạ tay xuống → **vẫn ở nguyên phòng vừa vào**, tiếng vẫn chạy (đây là ca chính của `Latched`) |
| L-c | 10 hiệu ứng cũ (`Momentary`) hành xử **không đổi** — Checklist B + D |
| L-d | Quay 60s, đổi phòng liên tục → `RecPerf` không tụt FPS, không có GC làm đứng thread ghi |
| L-e | Vào/ra màn camera 10 lần → không leak bitmap (`release()` chạy đúng một lần cho mỗi renderer) |
| L-f | Video ghi ra khớp đúng nền đang thấy ở live tại mọi thời điểm |

---

## 8. Phase M — Hiệu ứng vẽ bằng canvas ⚠️ ĐÃ LÀM M0–M7, CÒN M8–M9

> **Tình trạng:** M0–M7 đã triển khai (`EffectAsset.Procedural`, `HandFrame`, `onHandFrame`,
> `ProceduralVisual`, `EffectScope`, hiệu ứng `canvas_draw`). **M8 (`handedness`) và M9
> (`SizeSource`) chưa làm** — chưa có hiệu ứng nào cần tới, làm khi bắt tay vào Gojo / Trái Đất /
> Hố đen.
>
> **Lệch so với kế hoạch:**
>
> 1. **Thêm một state thứ ba `idle_skeleton`** dùng `Gestures.anyHandPresent` làm state cuối
>    danh sách, để khung xương bàn tay luôn được vẽ. Đúng thủ thuật "state mặc định" mà G5 đã
>    mô tả khi bỏ `idleAsset`.
> 2. **`isPalmOpen` / `isFist` được viết lại trong `utils/GestureUtils.kt`**: xoè tay từ "≥ 3
>    ngón duỗi" thành "cả 5 ngón duỗi", nắm tay từ "phủ định của xoè" thành "cả 5 ngón gập".
>    ⚠️ Hai cử chỉ này **không còn phủ kín mọi khả năng** — bàn tay nửa vời giờ không khớp state
>    nào. Thay đổi nằm ở `utils/` nhưng ảnh hưởng **toàn bộ 10 hiệu ứng cũ**; phải chạy lại
>    Checklist B + D, không chỉ test hiệu ứng vẽ canvas.
> 3. **Nhận diện cử chỉ dồn về `setResult`**: `matchedIndex` được giải đúng một lần cho mỗi kết
>    quả MediaPipe và lưu vào field `@Volatile`; `onDraw` và thread ghi hình chỉ đọc lại. Trước
>    đó `resolveMatchedIndex` bị gọi ở cả ba nơi (~85 lần/giây thay vì ~30) và hai thread cùng
>    ghi `latchedIndex`.
> 4. **`setActive` cũng chuyển về `setResult`, gọi cho cả hai bộ visual.** Để nó trong `drawFrame`
>    là một lỗi thật: bộ recording chỉ được vẽ khi đang quay, nên trước lúc bấm Record nó không
>    nhận được tín hiệu kích hoạt nào — `ClearOnActivate` không bao giờ chạy, model tích luỹ mãi,
>    và video sẽ hiện lại toàn bộ nét người dùng tưởng đã xoá.
> 5. **Khung xương gom vào `HandSkeletonRenderer`** (`HandSkeleton.kt`) thay cho hàm rời
>    `drawHandSkeleton` + ba bộ `Paint` khai riêng ở ba lớp — ba bộ đó đã lệch nhau một chỗ
>    (`argb(225,225,225,0)` vs `argb(255,255,255,0)`) làm khung xương đổi màu khi đổi trạng thái.
> 6. **Mọi độ dày nét suy từ `canvas.width`**, không dùng số pixel cố định: canvas ghi hình (~720)
>    hẹp hơn view live (~1080) nên nét cố định sẽ dày hơn ~1,5 lần trong video — phá đúng cam kết
>    của M3 và ca nghiệm thu M-b.
> 7. `canvas_draw` đang mượn tạm `thumbnailRes = R.drawable.stranger_things_monster`.

> Hiệu ứng đích: **Vẽ canvas** (`docs/Design_App_HandAr.md` mục 4). Duỗi ngón trỏ di chuyển →
> vẽ điểm trắng theo đường ngón tay trên nền đen; nắm tay → xoá hết, phát tiếng xé giấy.
> Cùng cơ chế này về sau phục vụ Tia sét, Trái Đất, Cổng dịch chuyển/Hố đen, Gojo.

### M0 — Ý tưởng nền tảng

Với ảnh/GIF/sprite sheet, `EffectAsset` trả lời câu hỏi **"pixel nằm ở đâu, giải mã kiểu gì"**.
Với hiệu ứng canvas, **pixel chính là code** — nên `EffectAsset` chỉ cần trả lời **"gọi class nào"**.

Con đường còn lại — nhét tham số hình học vào một data class để một `CanvasVisual` chung biết vẽ
khiên hay vẽ cầu — chính là tự định nghĩa một ngôn ngữ mô tả hình vẽ rồi tự viết trình thông dịch
cho nó. Đắt hơn nhiều lần so với ~30 dòng Kotlin mỗi hiệu ứng, và mỗi hình mới lại phải mở rộng
ngôn ngữ đó.

**Quy tắc phân ranh class:** hình học/chuyển động khác nhau → class mới. Chỉ khác màu, số lượng,
tốc độ → **tham số constructor**, không đẻ class.

### M1 — `EffectAsset.Procedural`

```kotlin
class Procedural(
    val id: String,                                  // de log/debug, va lam khoa neu can dedup
    val create: (Context, EffectScope) -> EffectVisual
) : EffectAsset()
```

```kotlin
is EffectAsset.Procedural -> asset.create(context, scope)
```

Khai báo trong `EffectRepository` đọc ra như sau — tham số biến thể do lambda giữ, không cần thêm
kiểu dữ liệu nào:

```kotlin
asset = EffectAsset.Procedural("shield") { ctx, _ -> ShieldVisual(ctx, ringCount = 3, colorRes = R.color.cyan) }
asset = EffectAsset.Procedural("shield_red") { ctx, _ -> ShieldVisual(ctx, ringCount = 1, colorRes = R.color.red) }
```

⚠️ Khai bằng `class`, **không** `data class`: lambda so sánh theo định danh nên `equals` tự sinh
sẽ vô nghĩa và gây hiểu nhầm. Nếu sau này cần dedup thì khoá bằng `id`.

### M2 — `HandFrame`: mở rộng thứ truyền vào hàm vẽ

Hợp đồng hiện tại chỉ đưa được **tâm lòng bàn tay** (landmark 9) và bán kính. Không đủ:

| Hiệu ứng | Cần gì thêm |
|---|---|
| Vẽ canvas | đầu ngón trỏ (landmark 8) |
| Trái Đất | khoảng cách ngón cái ↔ ngón trỏ |
| Cổng dịch chuyển / Hố đen | khoảng cách giữa **hai** tay |
| Tia sét | từng đầu ngón đang duỗi |
| Gojo | hai đầu ngón trỏ, và lúc chúng chạm nhau |

Bảy trên mười hiệu ứng trong `Design_App_HandAr.md` cần nhiều hơn `cx/cy/r`.

```kotlin
class HandFrame {                        // 2 instance (live/recording), TAI DUNG, khong cap phat moi frame
    var hands: List<List<NormalizedLandmark>> = emptyList()
    var handedness: List<Handedness> = emptyList()   // song song voi hands — xem M8
    var cx = 0f; var cy = 0f; var r = 0f             // r tinh theo SizeSource — xem M9
    var elapsedMs = 0L
    fun px(lm: NormalizedLandmark): Float    // chieu normalized -> canvas, da tinh mirror + crop
    fun py(lm: NormalizedLandmark): Float
}

interface EffectVisual {
    fun setActive(active: Boolean)
    fun onHandFrame(frame: HandFrame) {}          // xem M3
    fun draw(canvas: Canvas, frame: HandFrame)
}
```

**Quyết định cần chốt trước khi code:** đổi thẳng `EffectVisual.draw` cho cả 3 impl đang chạy
(`StaticImageVisual`, `AnimatedGifVisual`, `SpriteSheetVisual`), hay thêm một interface phụ
`HandAwareVisual` và cho `OverlayView` rẽ nhánh. **Đề xuất: đổi thẳng** — hiện chỉ có 3 impl,
sửa là việc cơ học; thêm 5 hiệu ứng canvas nữa rồi mới đổi thì đắt gấp nhiều lần, và hai hợp
đồng song song sẽ sống mãi. Việc đổi cũng kéo phép chiếu normalized → canvas ra khỏi
`drawHandEffects` — chính chỗ đang làm hàm đó dài và khó đọc.

### M3 — Tách nhịp cập nhật khỏi nhịp vẽ

Đây là điểm quan trọng nhất của Phase M, và nó sinh ra trực tiếp từ hiệu ứng Vẽ canvas.

Nếu mỗi lần `draw()` lại ghi thêm một điểm vào nét vẽ, thì bản live (~60 fps) và bản ghi (25 fps)
sẽ tạo ra **hai nét khác nhau** — nét trong video thưa hơn, mất chi tiết ở đoạn tay di chuyển
nhanh. Người dùng vẽ xong một chữ, mở video ra thấy chữ méo.

Lời giải: **cập nhật theo nhịp camera, vẽ theo nhịp của từng thread.**

- `OverlayView.setResult()` được gọi **đúng một lần cho mỗi kết quả MediaPipe** — đó chính là
  nhịp cần. Gọi `onHandFrame()` cho **cả hai** instance tại đó.
- Hai instance nhận **cùng một chuỗi input** → sinh ra **cùng một model** → không cần chia sẻ
  gì giữa hai thread, không cần khoá.
- `draw()` từ đó chỉ còn việc vẽ lại model, ai vẽ nhịp nấy.

Kèm một luật bắt buộc: **model lưu toạ độ normalized `[0,1]`**, chiếu sang canvas lúc `draw`.
Lưu toạ độ pixel thì model của hai instance khác nhau (hai canvas khác kích thước) và không còn
so sánh được nữa.

### M4 — `ProceduralVisual`: lớp cha dẹp phần lặp

```kotlin
abstract class ProceduralVisual : EffectVisual {
    @Volatile private var activatedAtMs = 0L

    final override fun setActive(active: Boolean) {
        if (active) { if (activatedAtMs == 0L) activatedAtMs = SystemClock.elapsedRealtime() }
        else activatedAtMs = 0L
    }

    final override fun draw(canvas: Canvas, frame: HandFrame) {
        val startedAt = activatedAtMs
        frame.elapsedMs = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt
        onDraw(canvas, frame)
    }

    protected abstract fun onDraw(canvas: Canvas, frame: HandFrame)
}
```

`final override` ở đây là cố ý: nó **ép** mọi hiệu ứng canvas tính thời gian theo đồng hồ hệ
thống, không theo số lần gọi `draw()` — cạm bẫy đã ghi cho `SpriteSheetVisual`, và với hiệu ứng
canvas thì còn dễ mắc hơn.

> **Không có "animation lúc thoát trạng thái", và không cần có.** Vài hiệu ứng trong
> `Design_App_HandAr.md` mô tả nghe như cần nó: *"nắm tay lại vòng khiên thu lại và biến mất"*,
> *"quái vật biến mất, hiệu ứng sóng âm lan toả"*. Mô hình hiện tại (state = hàm của cử chỉ
> **đang** làm) không diễn tả được "vẽ nốt cái gì đó sau khi rời state".
>
> Cách làm đúng: **để chính state nắm tay vẽ cảnh đó**. Asset của state nắm tay là một
> procedural vẽ vòng tròn co lại trong ~400 ms rồi thôi, đếm từ `elapsedMs` — tức là từ lúc nó
> được kích hoạt. Không cần khái niệm mới nào. Ghi lại ở đây vì người viết hiệu ứng sau sẽ đi
> tìm một cơ chế "exit animation" không tồn tại.

### M5 — `EffectScope`: khi hai state cần chung một model

Hiệu ứng Vẽ canvas có hai state, và state "nắm tay" phải với tới nét vẽ của state "ngón trỏ".

Không được tạo model trong `EffectRepository`: repository là singleton sống suốt vòng đời
process, model tạo ở đó sẽ **sống sót qua việc thoát và vào lại màn camera** và dùng chung giữa
mọi lần quay. Đây là loại bug khó truy nhất.

```kotlin
class EffectScope {                  // tao moi moi setEffect: MOT cho live, MOT cho recording
    private val models = HashMap<String, Any>()
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> shared(key: String, create: () -> T): T = models.getOrPut(key, create) as T
}
```

Hai scope riêng cho hai vai → live và recording vẫn không chia sẻ state, vẫn không cần khoá.
Khai báo:

```kotlin
// state ve: ngon tro duoi
asset = EffectAsset.Procedural("stroke") { _, scope ->
    StrokeVisual(scope.shared("stroke_model") { StrokeModel() })
},
// state xoa: nam tay — khong ve gi, chi xoa; tieng xe giay di duong soundRes nhu moi state khac
asset = EffectAsset.Procedural("stroke_clear") { _, scope ->
    ClearOnActivate(scope.shared("stroke_model") { StrokeModel() })
},
soundRes = R.raw.paper_tear
```

Nền đen của hiệu ứng này là `EffectBackground.Solid` cấp effect — không cần gì mới.

### M6 — Ba luật bắt buộc cho mọi hiệu ứng canvas

1. **Thời gian lấy từ đồng hồ hệ thống**, không đếm số lần gọi `draw()`. M4 đã ép sẵn.
2. **Không cấp phát trong `onDraw`.** `Paint`, `Path`, `RectF`, `Matrix` để làm field. Thread
   ghi hình có ngân sách 40 ms/frame và `README.md` đang ghi "GC làm đứng thread ghi: 0 lần".
   Lưu ý: `StaticImageVisual.draw` và hai background renderer **hiện đang** cấp phát `Matrix`
   mỗi frame — đừng copy thói quen đó (và dọn luôn ở L4).
3. **Không dùng `Paint`/state ở `companion object`.** `OverlayView` tạo hai instance riêng chính
   là để hai thread không giẫm chân nhau; state tĩnh phá đúng cơ chế đó.

### M7 — Mở rộng miễn phí: nền vẽ bằng canvas

Khi `Procedural` đã tồn tại, áp nguyên xi sang nền:

```kotlin
class Procedural(val id: String, val create: (Context) -> BackgroundRenderer) : EffectBackground()
```

Cộng với `setActive` ở L5 (nền biết mình được kích hoạt lúc nào), có luôn nền phản ứng theo cử
chỉ — dải sáng, sóng âm lan toả, gradient đập theo nhịp. Không có khái niệm mới nào. **Chỉ làm
khi có hiệu ứng thật cần tới.**

### M8 — `handedness`: biết tay nào là tay nào

**Hiệu ứng đích: Gojo** — *"2 tay chỉ ngón trỏ → quả cầu năng lượng nhỏ ở đầu ngón tay, 1 bên
màu xanh 1 bên màu đỏ"*. Không có thông tin tay trái/phải thì không quyết được bên nào xanh,
bên nào đỏ — và nếu chỉ dựa vào "tay nào nằm bên trái màn hình" thì hai quả cầu sẽ đổi màu cho
nhau mỗi khi người dùng bắt chéo tay.

MediaPipe **đã** trả về thông tin này (`HandLandmarkerResult.handednesses()`), chỉ là hiện không
được chuyển xuống đâu cả. Thêm vào `HandFrame` một danh sách song song với `hands`.

> ⚠️ **Bẫy gương.** Camera trước bị lật gương trước khi hiển thị (`mirrorX = true` ở khắp nơi
> trong `OverlayView`). Cái MediaPipe gọi là "Left" là tay trái **trong ảnh gốc chưa lật**,
> ngược với tay mà người dùng nhìn thấy ở bên trái màn hình. Chốt một lần ở đây và ghi vào
> `Camera_X_Hand_Landmarker.md`: **`HandFrame.handedness` trả về theo góc nhìn của người dùng**,
> tức là đã đảo lại khi `mirrorX = true`. Nếu để mỗi hiệu ứng tự xử lý thì mỗi cái sẽ đoán một
> kiểu, và nửa số đó sẽ sai mà không ai phát hiện cho tới khi thử với tay còn lại.

Nhắc lại luật đã có, vì phần này dễ tưởng là chỗ thay thế nó: `handedness` để **vẽ**, không phải
để **nhận diện cử chỉ**. Việc "cử chỉ nào đang được làm" vẫn hoàn toàn thuộc về
`GestureRecognizer`, không đổi.

### M9 — `SizeSource`: "to bao nhiêu" phải khai được

`OverlayView` hiện tính `r` bằng **một công thức duy nhất** — khoảng cách cổ tay → khớp ngón
giữa (landmark 0 → 9) — và mọi hiệu ứng đều nhận đúng con số đó. Hai hiệu ứng trong
`Design_App_HandAr.md` cần công thức khác:

| Hiệu ứng | Mô tả trong file design | Cần |
|---|---|---|
| Trái Đất | *"phóng to thu nhỏ dựa trên khoảng cách của ngón cái và ngón trỏ"* | khoảng cách landmark 4 ↔ 8 |
| Cổng dịch chuyển / Hố đen | *"to nhỏ theo khoảng cách 2 tay"* | khoảng cách giữa hai bàn tay |

Hiệu ứng procedural thì tự tính được từ `hands`. Nhưng **Trái Đất là ảnh tĩnh**, và Hố đen nhiều
khả năng là GIF/sprite xoáy — bắt viết một class canvas chỉ để vẽ một cái bitmap với bán kính
khác là dùng sai Phase M.

Đưa "kích thước lấy từ đâu" thành thứ khai được ở từng state, cùng chỗ với `asset` và `soundRes`:

```kotlin
enum class SizeSource { PalmRadius, PinchDistance, TwoHandDistance }

// them vao EffectState
val sizeSource: SizeSource = SizeSource.PalmRadius    // mac dinh = dung hanh vi hien tai
```

`OverlayView` tính `r` theo `sizeSource` rồi đưa vào `HandFrame` như cũ. **Cả 3 visual dựa trên
file được hưởng ngay mà không phải sửa một dòng nào** — `r` vẫn là `r`, chỉ khác cách tính ra nó.
Mặc định `PalmRadius` nên 10 hiệu ứng hiện có không phải khai gì thêm.

Ba giá trị là đủ cho toàn bộ danh sách hiệu ứng hiện tại; thêm giá trị mới khi có hiệu ứng thật
cần, đừng đoán trước.

> **Khác biệt kích thước giữa hai state thì dùng hai file asset, không dùng hệ số nhân.**
> Ví dụ Cầu lửa: *"nắm tay → quả cầu lửa nhỏ. Xòe tay → cầu lửa bùng lên to hơn"* — hai state,
> hai file ảnh. Cơ chế: mọi visual đều chuẩn hoá ảnh về đúng `r`
> (`scale = r / max(bitmap.width, bitmap.height)`), nên **kích thước pixel của file không quyết
> định độ lớn khi vẽ** — thứ quyết định là chủ thể chiếm bao nhiêu phần khung ảnh của chính nó.
> Muốn cầu lửa trông to hơn thì vẽ ngọn lửa tràn sát mép khung, muốn nhỏ hơn thì chừa nhiều
> khoảng trong suốt quanh nó. Người làm asset kiểm soát hoàn toàn, không cần thêm tham số nào
> vào code.

### Nghiệm thu Phase M

| Ca | Kỳ vọng |
|---|---|
| M-a | Duỗi ngón trỏ di chuyển → nét vẽ bám **đầu ngón trỏ**, không bám tâm lòng bàn tay |
| M-b | Vẽ một chữ rồi quay lại xem video → **nét trong video trùng khít nét đã thấy trên màn hình** (ca chính của M3) |
| M-c | Nắm tay → xoá sạch + tiếng xé giấy; vẽ tiếp được ngay sau đó |
| M-d | Vẽ liên tục 60s (nét dài) → `RecPerf` không tụt FPS, không GC làm đứng thread ghi |
| M-e | Thoát màn camera rồi vào lại → **canvas trắng tinh**, không còn nét cũ (ca chính của M5) |
| M-f | 10 hiệu ứng cũ hành xử không đổi sau khi đổi hợp đồng `EffectVisual` — Checklist B + D |
| M-g | Hai tay chỉ ngón trỏ → đúng bên xanh, đúng bên đỏ; **bắt chéo hai tay thì màu không đổi chỗ** (ca chính của M8) |
| M-h | Ảnh tĩnh khai `PinchDistance` → to nhỏ mượt theo ngón cái–ngón trỏ; hiệu ứng khai mặc định vẫn y như cũ (ca chính của M9) |

M-b và M-e là hai ca không thể bỏ: chúng là lý do tồn tại của M3 và M5.

---

## 9. Phase N — Dọn nợ của L/M và cấu trúc lại dự án

> Làm **sau khi Phase M chạy ổn**, không chen vào giữa. Cả ba mục dưới đây đều là việc đã biết
> rõ, không có ẩn số thiết kế nào — nên để dồn lại một đợt, làm gọn trong vài commit tách bạch.

### N1 — Bỏ cấp phát trong đường nóng

`drawFrame` chạy 25 lần/giây trên thread ghi hình (ngân sách 40 ms/frame) và ~60 lần/giây trên
UI. Mỗi lần gọi hiện đang cấp phát:

| Chỗ | Cấp phát mỗi frame | Cách sửa |
|---|---|---|
| `(stateBackgrounds + defaultBackground).filterNotNull().distinct()` | 3 `List` + 1 `Set` | Tính sẵn một lần trong `setEffect`, lưu thành field |
| `hands.map { … }.average()` ×3 (cx, cy, r) | 3 `List` + boxing sang `Double` | Vòng lặp thủ công cộng dồn, không qua `map`/`average` |
| `StrokeModel.snapshot()` = `_points.toList()` | copy **toàn bộ** danh sách, ở cả hai instance | Xem N2 |
| `StaticImageVisual` / `AnimatedGifVisual` / hai background renderer: `Matrix()` | 1 `Matrix` mỗi cái | Đưa `Matrix` thành field, chỉ tính lại khi kích thước canvas đổi |

Hai dòng `hands.map{}` và `Matrix()` có từ trước Phase L/M; hai dòng còn lại là nợ mới. Cả bốn
đều vi phạm đúng luật M6 #2 mà chính tài liệu này vừa đặt ra, và ăn thẳng vào con số
"GC làm đứng thread ghi: 0 lần" đang ghi trong `README.md`.

**Quy trình bắt buộc:** đo `RecPerf` trước và sau, máy nguội, mỗi cấu hình đo hai lần để biết
ngưỡng nhiễu — theo đúng quy ước "đo trước khi kết luận" của dự án.

### N2 — Giới hạn và tăng tốc nét vẽ

`StrokeVisual.onHandFrame` thêm một `PointF` **mỗi lần nhận diện**, kể cả khi ngón tay đứng yên:
~30 điểm/giây → 5 phút vẽ liên tục là ~9.000 điểm. Mỗi frame lại dựng lại toàn bộ `Path` từ
đầu, sau khi đã copy cả danh sách.

Ba việc, làm cùng nhau:

1. **Lọc theo khoảng cách tối thiểu** — ngón tay chưa đi đủ xa (ví dụ < 0,5% chiều rộng khung)
   thì không thêm điểm. Vừa chặn điểm trùng, vừa làm nét mượt hơn.
2. **Trần cứng số điểm**, bỏ điểm cũ nhất khi vượt.
3. **Lưu điểm trong `FloatArray` (x, y xen kẽ) + số đếm** thay cho `MutableList<PointF>`, và
   **cache `Path` đã dựng** — chỉ dựng lại khi số điểm hoặc phép chiếu thay đổi. Hết cả copy
   lẫn dựng lại mỗi frame.

### N3 — Cấu trúc lại package

`effect/` hiện chứa hơn 20 file lẫn lộn bốn nhóm khái niệm khác nhau (mô tả hiệu ứng, cử chỉ,
cách vẽ, nền), và `EffectRepository.kt` đã hơn 300 dòng và còn dài ra mỗi lần thêm hiệu ứng.
`OverlayView.kt`, `SoundEffectPlayer.kt`, `BgmPlayer.kt` thì nằm trần ở gốc package cùng
`MainActivity`.

Hướng đề xuất:

```text
com/example/handar/
├── MainActivity.kt
├── audio/        SoundEffectPlayer, BgmPlayer
├── effect/
│   ├── model/        EffectDefinition, EffectState, EffectAsset, EffectBackground,
│   │                 EffectBgm, StateMode (+ SizeSource khi làm M9)
│   ├── gesture/      GestureRecognizer, Gestures, GestureUtils (chuyển từ utils/ về đây)
│   ├── visual/       EffectVisual, HandFrame, EffectScope, ProceduralVisual, factory
│   │   ├── image/    StaticImageVisual, AnimatedGifVisual, SpriteSheetVisual
│   │   └── canvas/   StrokeVisual, StrokeModel, ClearOnActivate, SkeletonOnlyVisual, HandSkeleton
│   ├── background/   BackgroundRenderer + 3 renderer + factory
│   ├── catalog/      mỗi hiệu ứng phức tạp một file trả về EffectDefinition
│   ├── EffectRepository.kt        chỉ còn danh sách, không còn khai chi tiết
│   └── HandLandmarkerProvider.kt
├── recording/    giữ nguyên, không đụng
├── ui/           giữ nguyên (chia theo màn hình đã hợp lý)
└── utils/        chỉ còn thứ thật sự dùng chung: AudioUtils, FormatUtils, logger
```

**Bốn luật cho đợt cấu trúc lại này:**

1. **Chỉ di chuyển file và đổi `package`/`import`. Không sửa một dòng logic nào trong cùng commit.**
   Nếu thấy code cần sửa trong lúc chuyển, ghi lại và làm ở commit sau — diff phải đọc được, nếu
   không thì mất luôn khả năng soát. Đây chính là bài học "khi refactor: đừng đọc, hãy diff".
2. **`nav_graph.xml` khai tên class đầy đủ** (`android:name="com.example.handar.ui…"`). Đề xuất
   trên **không** động tới `ui/`, nhưng nếu có đổi thì phải sửa nav graph — và đây là loại lỗi
   chỉ nổ lúc chạy, không bị compiler bắt.
3. **`GestureUtils` chuyển từ `utils/` sang `effect/gesture/`** là thay đổi có ảnh hưởng rộng
   nhất trong danh sách (nhiều file import). Làm riêng một commit.
4. **Sau khi chuyển xong: chạy lại toàn bộ Checklist A–H.** Một đợt đổi cấu trúc mà build xanh
   không chứng minh được gì cả.

`app/src/main/keepRules/rules.keep` hiện chỉ có comment mẫu, không khai tên class nào — nên
việc đổi package không ảnh hưởng R8. Kiểm tra lại file này nếu sau này có thêm keep rule thật.

---

## 10. Việc đã bàn nhưng cố ý hoãn

Ghi lại để lần sau không phải bàn lại từ đầu:

| Việc | Vì sao hoãn |
|---|---|
| Xuất video ra Gallery (MediaStore) | Đơn giản về kỹ thuật; làm sau khi giao diện đã chốt để không phải sửa hai lần |
| Xoá / chia sẻ / **đổi tên** ngay trong màn thư viện | Phần lớn là di chuyển logic xoá + chia sẻ **đã có** ở `RecordedPreviewFragment` sang; chỉ đổi tên là mới |
| Lưới (grid) + nhóm cho màn chọn hiệu ứng | 10 hiệu ứng vẫn vừa với 1 cột; làm khi số hiệu ứng thực sự vượt |
| Màn Settings | Chưa có đủ tuỳ chọn để xứng một màn riêng |
| Hướng dẫn cử chỉ cho người dùng | Đã cân nhắc và quyết định không làm |
| Đổi camera trước/sau, zoom, tap-to-focus, pause/resume, giới hạn thời lượng | Không nằm trong hướng đi hiện tại của app |

---

## 11. Rủi ro lớn nhất

> **Cập nhật sau khi J xong:** phần dưới đã được nghiệm thu — `AudioMixer` chỉ nhận thêm ba
> trường và một nguồn PCM trong `mix()`, không có gì liên quan tới PTS bị đụng. Giữ lại nguyên
> văn làm tiền lệ cho mọi lần sau phải chạm vào `recording/`.
>
> **Rủi ro lớn nhất của vòng L–M nằm ở chỗ khác:** Phase M đổi hợp đồng `EffectVisual` — thứ mà
> **cả 3 visual đang chạy tốt** đều implement. Đây đúng loại thay đổi mà `README.md` cảnh báo:
> *"Khi refactor: đừng đọc, hãy diff."* Ba bug ở Phase B đều build xanh và chạy được, chỉ lộ ra
> khi so từng dòng với bản gốc. Làm M2 tách thành commit riêng, không gộp với M3–M5, và
> chạy Checklist B + D ngay sau M2 trước khi viết bất kỳ hiệu ứng canvas nào.

**Phase J là chỗ duy nhất có thể phá lại công sức của Phase 0–5.** `AudioMixer` là một mắt
xích trong chuỗi `EffectAudioClock → AudioMixer → AudioEncoderWrapper → MuxerCoordinator`, nơi
mọi PTS đều neo vào một mốc duy nhất. Thay đổi được phép làm ở J2 chỉ là **cộng thêm một nguồn
PCM vào đúng hàm `mix()`** — không đổi chữ ký hàm cũ, không đổi thời điểm gọi, không đụng tới
`effectClock`, `totalAudioSamples`, hay bất cứ gì liên quan tới PTS.

Nếu trong lúc làm J thấy mình đang phải sửa thêm thứ gì ngoài `mix()` và hai trường mới, **dừng
lại** và đối chiếu `HandAr_Refactor_Plan.md` trước khi đi tiếp.
