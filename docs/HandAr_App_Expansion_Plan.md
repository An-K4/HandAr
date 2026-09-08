# Kế hoạch Refactor — HandAr thành App Hoàn Chỉnh (Multi-screen)

> Tiếp nối `HandAr_Refactor_Plan.md` (đã hoàn thành Phase 0-5, pipeline ghi hình đã ổn định).
> Mục tiêu: từ 1 màn hình duy nhất (`MainActivity` làm hết mọi việc) → app hoàn chỉnh 5 màn hình, có thể chọn hiệu ứng, xem lại, và quản lý video đã quay.

---

## 0. Quyết định kiến trúc đã chốt

| Hạng mục | Lựa chọn | Lý do |
|---|---|---|
| Điều hướng giữa màn hình | **Fragments + Navigation Component** | Giữ nguyên View/XML hiện có, không cần viết lại UI bằng Compose, tận dụng toàn bộ code `OverlayView`/`PreviewView` đang chạy tốt |
| Phát video trong app | **ExoPlayer (Media3)** | Chuẩn hiện tại của Google, xử lý tốt các định dạng/edge-case mà `VideoView` (cũ, ít được bảo trì) hay gặp lỗi |
| Kiến trúc mic/audio | **Giữ nguyên phương án B** (đã chốt ở Refactor Plan trước — không mic, chỉ `EffectAudioClock` + `AudioMixer`) | Không đổi gì, đã ổn định |

---

## 1. Vấn đề kiến trúc hiện tại cần giải quyết trước khi thêm màn hình

| # | Vấn đề | Vì sao cản trở việc thêm màn hình |
|---|---|---|
| 1 | 2 hiệu ứng (`happy3`/`banana`) đang **hardcode thẳng** trong `OverlayView.GifLayer` và `MainActivity` | Muốn có "màn List Hiệu ứng" để chọn, bắt buộc phải có 1 **data model** đại diện cho "1 hiệu ứng" trước — hiện tại khái niệm này chưa tồn tại trong code |
| 2 | `MainActivity` đang ôm hết: xin quyền, MediaPipe, Camera, Recording, UI nút bấm | Cần tách thành 1 `Fragment` riêng cho màn Camera, các màn khác không được kéo theo toàn bộ logic này |
| 3 | Chưa có nơi nào đọc lại danh sách video đã quay (chỉ ghi file, không list) | Cần 1 lớp quét thư mục (`Repository`) trước khi có thể hiển thị màn "Danh sách Video" |

---

## 2. Cấu trúc package mục tiêu

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

**Lưu ý khi cấu hình `nav_graph.xml`:** dùng `popUpTo` đúng cách ở action `cameraRecordFragment → recordedPreviewFragment` để nếu người dùng back từ Preview, **không quay lại giữa lúc camera đang mở** (tránh mở lại camera/MediaPipe không cần thiết) — nên back thẳng về `effectListFragment`.

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

### Phase B — Navigation skeleton
- Thêm dependency: `androidx.navigation:navigation-fragment-ktx`, `navigation-ui-ktx`, cộng plugin Safe Args.
- Tạo `nav_graph.xml`, `MainActivity` đổi `setContentView` thành layout chỉ chứa `NavHostFragment`.
- Tạo `CameraRecordFragment`: **di chuyển gần như nguyên trạng** toàn bộ nội dung `MainActivity` cũ vào đây (đổi `this` → `requireContext()`, đổi `findViewById` → `binding`/`view.findViewById` theo `onViewCreated`).
- Nhận `effectId` qua Safe Args (`arguments`), gọi `EffectRepository.findById(effectId)` để lấy đúng effect, truyền vào `overlayView.setEffect(...)`.
- Tạo `EffectListFragment` tạm thời (UI đơn giản 1 cột `RecyclerView`, mỗi item hiện `thumbnailRes` + `displayName`) — bấm vào 1 item thì `navigate` sang `cameraRecordFragment` kèm `effectId`.
- **Lưu ý lifecycle quan trọng:** các biến `@Volatile private var latestCameraBitmap`, `backgroundExecutor`, `handLandmarker` cần khởi tạo/dọn dẹp đúng theo `onViewCreated`/`onDestroyView` của Fragment (không phải `onCreate`/`onDestroy` như Activity) — nếu không cẩn thận, camera có thể không dừng đúng lúc khi rời Fragment, gây leak hoặc camera bị chiếm giữ khi quay lại màn List.
- **Khuyến nghị: KHÔNG giới thiệu `ViewModel` ngay ở Phase này** — cứ giữ logic y hệt Activity cũ, đặt trong Fragment, chạy đúng trước đã. Tách `ViewModel` để sống sót qua xoay màn hình là việc có thể làm sau, khi thực sự thấy cần (đúng tinh thần "làm đơn giản trước, phức tạp hoá khi có nhu cầu thật" bạn đã áp dụng ở phần đo FPS/audio trước đây).

### Phase C — Recorded Preview screen
- Sau khi `VideoRecorder.stop()` gọi callback `onStopped` (hiện tại chỉ hiện Toast), đổi thành `findNavController().navigate(...)` sang `RecordedPreviewFragment`, truyền đường dẫn file (`outputFile.absolutePath`) qua Safe Args.
- `RecordedPreviewFragment`: dùng `ExoPlayer` phát ngay video vừa quay (không cần thumbnail, phát trực tiếp).
- 3 hành động cần có:
  - **Lưu:** video vốn đã lưu sẵn trong `getExternalFilesDir(MOVIES)` từ lúc `VideoRecorder.start()` — nút này chỉ cần `popBackStack` về `effectListFragment` (hoặc `videoListFragment` nếu muốn xem ngay).
  - **Xoá:** `file.delete()` rồi `popBackStack`.
  - **Chia sẻ:** `Intent.ACTION_SEND` kèm `FileProvider.getUriForFile(...)` (cần khai báo `<provider>` FileProvider trong `AndroidManifest.xml` nếu chưa có).

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
- Thêm nút/icon "Thư viện" (ví dụ icon góc trên `EffectListFragment` hoặc `CameraRecordFragment`) điều hướng sang `VideoListFragment`.
- Rà lại toàn bộ hành vi nút Back ở từng màn — đặc biệt đảm bảo **không thể back vào giữa lúc đang quay dở** (camera vẫn mở, `VideoRecorder.isRecording == true`) mà không có cảnh báo, tránh mất video đang quay dở hoặc leak tài nguyên camera.
- Chạy lại **toàn bộ** `HandAr_Manual_Test_Checklist.md` hiện có (mục A-F) sau khi refactor xong Phase B, vì đây là bộ test duy nhất xác nhận hành vi ghi hình không bị phá vỡ trong quá trình chuyển từ Activity sang Fragment.
- Bổ sung thêm few test case mới riêng cho navigation: "back liên tục qua 5 màn có gây crash không", "xoay hiệu ứng qua lại nhiều lần rồi mới bấm Record có load đúng effect không".

---

## 7. Rủi ro lớn nhất cần lưu ý xuyên suốt

**Toàn bộ pipeline recording (`VideoRecorder`, `AudioMixer`, `EffectAudioClock`, `MuxerCoordinator`, wrapper) đã rất ổn định qua nhiều vòng debug — mục tiêu của kế hoạch này là KHÔNG đụng vào logic bên trong các class đó**, chỉ đổi **nơi chúng được gọi** (từ Activity sang Fragment) và **cách chọn effect** (từ hardcode sang `EffectDefinition`). Nếu trong lúc refactor phát hiện cần sửa logic recording, nên dừng lại và đối chiếu với `HandAr_Refactor_Plan.md` + `Camera_X_Hand_Landmarker.md` trước, tránh vô tình phá lại các bug đã mất công fix (đặc biệt Mục D trong checklist test — đây là những bug rất dễ tái phát nếu đổi sai chỗ trong lúc di chuyển code).
