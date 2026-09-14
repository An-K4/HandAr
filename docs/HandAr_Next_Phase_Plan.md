# Kế hoạch Phase G–K — Nền, nhạc nền, và an toàn dữ liệu

> Tiếp nối `HandAr_Refactor_Plan.md` (Phase 0–5, pipeline ghi hình) và
> `HandAr_App_Expansion_Plan.md` (Phase A–F, 5 màn hình).
> Tại thời điểm viết, app đã chạy đủ luồng: chọn hiệu ứng → quay → xem lại → thư viện → phát.
> Kế hoạch này giải quyết ba việc: **mở rộng khả năng biểu đạt của một hiệu ứng**
> (nền riêng, nhạc nền liên tục, trạng thái chỉ có tiếng),
> **bịt các lỗ làm mất video đang quay**, và **trả vài món nợ kỹ thuật rẻ tiền**.

---

## 0. Quyết định đã chốt

| Hạng mục | Lựa chọn | Lý do |
|---|---|---|
| Pha nhạc nền giữa live và video | **Vào màn camera là phát ngay; bấm Record thì dừng và phát lại từ đầu**, đồng thời vị trí nhạc trong track ghi cũng về 0 | Video luôn nghe "đúng đầu bài", và cái người dùng nghe lúc quay khớp đúng cái nằm trong file — đổi lại một lần nhạc giật lại ở thời điểm bấm Record, chấp nhận được vì nó trùng với khoảnh khắc người dùng biết mình vừa bắt đầu quay |
| Nơi phát nhạc nền lúc live | **Tách `BgmPlayer` riêng (`MediaPlayer`)**, không dùng chung `SoundPool` với tiếng hiệu ứng | `SoundPool` nạp toàn bộ file vào RAM và sinh ra để bắn tiếng ngắn; nhạc nền dài vài chục giây dùng `SoundPool` là sai công cụ. Tách riêng cũng cho phép nhạc nền và tiếng hiệu ứng có vòng đời độc lập |
| Loại nền hỗ trợ | **Cả 3: màu trơn, ảnh tĩnh, ảnh động** | Ba loại này khớp đúng ba loại `EffectAsset` đã có, tái dùng được cách nghĩ và một phần cách vẽ |
| Camera khi hiệu ứng có nền | **Ẩn hoàn toàn** | Không có chế độ pha trộn/làm mờ — giữ một quy tắc duy nhất: có `background` thì không thấy camera, không có thì thấy camera |
| Xoay màn hình | **Khoá `portrait`** | Xoá hẳn một lớp bug vòng đời (xoay lúc đang quay = `onDestroyView` = mất clip) với chi phí một dòng manifest. App vốn là app quay dọc |

---

## 1. Thứ tự Phase và lý do

```
Phase G → Đổi hình dạng dữ liệu hiệu ứng   (nền tảng — làm trước tất cả)
Phase H → An toàn dữ liệu khi quay          (độc lập, rủi ro thấp, đang mất clip thật)
Phase I → Background                        (dựng trên G)
Phase J → Nhạc nền liên tục                 (dựng trên G, chạm vùng recording/)
Phase K → Nợ kỹ thuật rẻ tiền
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

## 2. Phase G — Đổi hình dạng dữ liệu hiệu ứng

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

Dùng `sealed class` cùng lý do đã ghi ở `HandAr_App_Expansion_Plan.md` mục 3.1: thêm loại nền
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

## 3. Phase H — An toàn dữ liệu khi quay

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

## 4. Phase I — Background

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

## 5. Phase J — Nhạc nền liên tục

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

## 6. Phase K — Nợ kỹ thuật rẻ tiền

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

## 7. Việc đã bàn nhưng cố ý hoãn

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

## 8. Rủi ro lớn nhất

**Phase J là chỗ duy nhất có thể phá lại công sức của Phase 0–5.** `AudioMixer` là một mắt
xích trong chuỗi `EffectAudioClock → AudioMixer → AudioEncoderWrapper → MuxerCoordinator`, nơi
mọi PTS đều neo vào một mốc duy nhất. Thay đổi được phép làm ở J2 chỉ là **cộng thêm một nguồn
PCM vào đúng hàm `mix()`** — không đổi chữ ký hàm cũ, không đổi thời điểm gọi, không đụng tới
`effectClock`, `totalAudioSamples`, hay bất cứ gì liên quan tới PTS.

Nếu trong lúc làm J thấy mình đang phải sửa thêm thứ gì ngoài `mix()` và hai trường mới, **dừng
lại** và đối chiếu `HandAr_Refactor_Plan.md` trước khi đi tiếp.
