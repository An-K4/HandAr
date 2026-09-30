# Kế hoạch chuyển sang MVVM — HandAr

> **Cập nhật lần cuối tại commit `d8cebab`** (2026-09-30). **Note cho agent:** sau khi hoàn
> thành bất kỳ Bước nào dưới đây, tick ô trạng thái ở Mục 2, cập nhật commit hash ở dòng này,
> và nếu Bước đó đổi cấu trúc thư mục/quy ước, cập nhật `AGENTS.md` mục 4 + `Code_Walkthrough.md`
> theo đúng Bước 7.

> **Lý do refactor:** bảo trì và thêm tính năng dễ hơn — không phải vì bug hiện tại. Hai màn sắp
> làm (Bộ sưu tập dùng chung dữ liệu yêu thích/danh sách hiệu ứng, và các thao tác video mở rộng)
> sẽ tự nhiên dùng lại được VM/Repository nếu làm trước; `docs/HandAr_Plan.md` mục K2 ghi
> "ViewModel: chưa làm" — tài liệu này là quyết định đảo ngược, cập nhật K2 ở Bước 7.

> **Không đổi gì ở `recording/`, `effect/`, `OverlayView.kt`, `audio/`.** Kế hoạch này chỉ chạm
> `ui/*` và `utils/` (phần được `ui/*` dùng: `FavouriteManager`, `VideoRepository`). Nếu một
> Bước nào phát hiện cần sửa ngoài phạm vi này, dừng lại, không tự ý mở rộng phạm vi.

---

## 0. Nguyên tắc áp dụng cho toàn bộ kế hoạch

Đọc mục này một lần trước khi bắt đầu Bước 1, và quay lại đối chiếu trước mỗi Bước.

### 0.1. Đơn vị làm việc

- Mỗi dòng trong bảng "Danh sách commit" ở mỗi Bước là **một commit riêng**. Không gộp hai dòng
  vào một commit, kể cả khi chúng nhỏ — lý do đã nêu ở `Fragment_Review_Checklist.md` mục 7 và
  `HandAr_Plan.md` mục 11: "khi refactor, đừng đọc, hãy diff". Một commit gộp "tách class" với
  "sửa logic" thì diff không còn đọc được nữa.
- Trong một commit **di chuyển/bọc lại code** (đổi `object` thành `class`, tách hàm), không sửa
  hành vi. Nếu thấy tiện sửa luôn một chỗ khác trong lúc di chuyển, ghi lại và làm ở commit sau.
- Sau **mỗi** commit: `./gradlew :app:assembleDebug :app:lintDebug` phải xanh trước khi sang
  commit tiếp theo. Không dồn nhiều commit rồi mới build thử.

### 0.2. Quy ước kỹ thuật (áp dụng cho mọi ViewModel/Repository viết mới)

**Thư viện.** Bước 1 thêm:

```toml
# gradle/libs.versions.toml — [versions]
lifecycle = "2.11.0"

# [libraries]
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-savedstate = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-savedstate", version.ref = "lifecycle" }
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragment" }  # fragment = "1.9.1" — bản stable mới nhất lúc làm Bước 1 (23/09/2026), không phải 1.8.5
```

```kotlin
// app/build.gradle.kts — dependencies { ... }
implementation(libs.androidx.lifecycle.viewmodel.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.lifecycle.viewmodel.savedstate)
implementation(libs.androidx.fragment.ktx)
```

`fragment-ktx` là nơi định nghĩa `by viewModels()` — `navigation-fragment-ktx` hiện có kéo theo
`fragment` nhưng không chắc kéo theo `fragment-ktx`; khai tường minh để không phụ thuộc vào một
transitive dependency có thể đổi. `lifecycle-viewmodel-savedstate` là artifact **riêng**, không
nằm trong `lifecycle-viewmodel-ktx` — thiếu nó thì `createSavedStateHandle()` dùng ở Bước 3.3
(`VideoPlayerViewModel`) không compile; thêm ngay từ Bước 1 dù chưa dùng tới, để khỏi phải quay
lại sửa dependency giữa chừng Bước 3. Kiểm tra phiên bản `2.11.0`/`1.8.5` còn là bản ổn định mới
nhất tại thời điểm làm Bước 1 (lifecycle đổi khá thường xuyên) — không tự động tin số trong tài
liệu này nếu ngày làm đã cách xa 2026-09-29.

**Không dùng Hilt/Koin.** Đã quyết định ở lượt thảo luận trước: dự án đang tối ưu dung lượng
APK (`App_Size_Optimization_16KB_Compliance.md`, `App_Size_Optimization_Plan.md`) và thời gian
build; DI framework không bắt buộc cho quy mô 17 Fragment. Factory viết tay theo mẫu ở 0.3.

**Không giữ `Context` của Activity trong ViewModel.** Hai lựa chọn, chọn theo từng trường hợp:
- Repository là `class` (không phải `object`) nhận `context: Context` ở constructor, tự gọi
  `context.applicationContext` bên trong (đúng cạm bẫy `requireContext()` đã ghi ở
  `Fragment_Review_Checklist.md` mục 4) — dùng khi Repository cần IO (file, SharedPreferences).
- ViewModel không cần Context (chỉ gọi Repository) → `ViewModel` thường, không phải
  `AndroidViewModel`. Chỉ dùng `AndroidViewModel` khi thực sự không tránh được việc VM tự cần
  Context (ví dụ đọc string resource ngay trong VM) — hạn chế, vì nó trộn lẫn trách nhiệm giữa
  VM và Repository.

**UiState.** Một `data class` duy nhất mỗi màn, đặt trong cùng file với ViewModel nếu nhỏ (dưới
~10 dòng), tách file riêng `<Man>UiState.kt` nếu lớn hoặc có nhiều biến thể. Mẫu tối thiểu:

```kotlin
data class VideoListUiState(
    val isLoading: Boolean = true,
    val items: List<VideoItem> = emptyList(),
    val isEmpty: Boolean = false
)
```

Expose bằng `StateFlow`, không `LiveData` (dự án không có lý do dùng Java interop, `StateFlow`
dùng chung API coroutine đã có sẵn trong `viewModelScope`, không cần thêm observer kiểu
`LiveData`):

```kotlin
private val _uiState = MutableStateFlow(VideoListUiState())
val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()
```

**Sự kiện một lần** (Toast, điều hướng do VM khởi xướng, mở intent ngoài). Dùng `Channel` +
`receiveAsFlow()`, không `SharedFlow(replay = 0)` — `Channel` đảm bảo mỗi sự kiện được nhận
đúng một lần kể cả khi có nhiều collector nối tiếp (Fragment tạo lại view), tránh đúng lớp bug
"phát lại sự kiện cũ khi quay lại màn" mà `SharedFlow` dễ mắc nếu cấu hình `replay` sai:

```kotlin
private val _events = Channel<VideoPlayerEvent>(Channel.BUFFERED)
val events = _events.receiveAsFlow()

sealed interface VideoPlayerEvent {
    data object CannotPlay : VideoPlayerEvent
    data class RenameFailed(val reason: RenameFailReason) : VideoPlayerEvent
    data object RenameSucceeded : VideoPlayerEvent
}
```

**Fragment thu `uiState`/`events`.** Luôn qua `viewLifecycleOwner.lifecycleScope` +
`repeatOnLifecycle(Lifecycle.State.STARTED)` (không dùng `launchWhenStarted`, đã deprecated) —
đúng lý do đã ghi ở `Fragment_Review_Checklist.md` mục 3: dùng `lifecycleScope` trần thay vì
`viewLifecycleOwner.lifecycleScope` thì vào/ra 3 lần có 3 collector chạy song song.

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.uiState.collect { state -> render(state) } }
        launch { viewModel.events.collect { event -> handleEvent(event) } }
    }
}
```

**Factory viết tay** (khi VM cần constructor param, tức hầu hết trường hợp ở đây vì Repository
không tự singleton hoá qua DI):

```kotlin
class VideoListViewModel(private val repository: VideoRepository) : ViewModel() {
    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { VideoListViewModel(VideoRepository(context.applicationContext)) }
        }
    }
    // ...
}

// trong Fragment
private val viewModel: VideoListViewModel by viewModels {
    VideoListViewModel.factory(requireContext())
}
```

`viewModelFactory { initializer { ... } }` là API có sẵn trong `lifecycle-viewmodel-ktx`
2.5.0+, không cần viết `ViewModelProvider.Factory` thủ công dài dòng.

**Điều hướng vẫn ở Fragment.** VM không cầm `NavController`. Với thao tác cần điều hướng (ví dụ
xoá video xong thì `popBackStack()`), VM phát sự kiện (`RenameSucceeded`), Fragment nhận sự kiện
rồi tự gọi `findNavController()` — giữ nguyên toàn bộ các chốt `currentDestination?.id != ...`
đang có, chỉ chuyển "quyết định khi nào gọi" từ callback trực tiếp sang qua sự kiện VM.

**`SavedStateHandle`** thay cho `onSaveInstanceState` thủ công khi state cần sống qua process
death (ví dụ `playbackPosition` ở `VideoPlayerFragment`) — VM constructor nhận thêm
`savedStateHandle: SavedStateHandle`, factory dùng `AbstractSavedStateViewModelFactory` hoặc
`CreationExtras` (API `viewModelFactory` ở trên hỗ trợ qua `this.createSavedStateHandle()` trong
`initializer`).

### 0.3. Việc KHÔNG làm trong kế hoạch này

- Không đổi `recording/`, `effect/*`, `OverlayView.kt`, `audio/*`.
- Không thêm DI framework.
- Không đổi từ View/XML sang Compose.
- Không viết ViewModel rỗng cho màn tĩnh không state (Splash/Welcome/Onboarding — xem Bước 6,
  có ngoại lệ đã nêu).
- Không gộp việc "chuyển MVVM" với việc "sửa bug tìm thấy trong lúc đọc code" — mọi bug tìm thấy
  liệt kê riêng trong Bước tương ứng, sửa ở **commit riêng sau khi đã di chuyển xong**, không
  trộn vào commit di chuyển.

### 0.4. Nếu một Bước lỡ tay làm hỏng thứ đang chạy tốt

`git revert` đúng (các) commit của Bước đó — vì mỗi Bước độc lập tương đối với Bước sau (Bước
sau chưa bắt đầu nếu Bước trước chưa xong), revert không kéo theo hiệu ứng dây chuyền. Không
`git reset --hard` nếu đã có commit của Bước sau đè lên.

---

## 1. Danh sách 17 Fragment và Bước phụ trách

| Fragment | Bước | Có state thật? |
|---|---|---|
| `VideoListFragment` | 2 | Có (list, loading, empty) |
| `VideoPlayerFragment` | 3 | Có (playback position, menu, dialog) |
| `RecordedPreviewFragment` | 3 | Có (dialog xác nhận) |
| `ShareFragment` | 3 | Có (fullscreen/card) |
| `EffectListFragment` | 4 | Có (search query, favourite) |
| `EffectPreviewFragment` | 4 | Nhẹ (chỉ đọc effect theo id) |
| `EffectPickerFragment` | 4 | Có (selectedEffectId) |
| `CameraRecordFragment` | 5 | Có, nhiều nhất — chỉ tách phần thuần Kotlin, **không** thêm ViewModel cho toàn Fragment (xem Bước 5) |
| `SplashFragment` | 6 | Nhẹ (timer) |
| `Survey1Fragment` | 6 | Có (lựa chọn), **cần quyết định nơi lưu** — xem 6.3 |
| `Survey2Fragment` | 6 | Có (lựa chọn) |
| `LanguageFragment` | 6 | Có (ngôn ngữ đang chọn) |
| `SettingsFragment` | 6 | Sẽ có khi nối logic 5 mục còn lại |
| `PermissionFragment` | 6 | Có (trạng thái 2 quyền) |
| `WelcomeFragment` | 6 | Không — chỉ ghi quy tắc, không tạo VM |
| `Onboarding1Fragment` | 6 | Không — chỉ ghi quy tắc, không tạo VM |
| `Onboarding2Fragment` / `Onboarding3Fragment` | 6 | Không — chỉ ghi quy tắc, không tạo VM |

---

## 2. Trạng thái tổng quan (tick khi xong)

- [x] **Bước 1** — Hạ tầng + quy ước (commit `90bb322`, `b5c2d0f`)
- [x] **Bước 2** — `VideoListFragment` (màn mẫu) (commit `3a34c66`, `79c12c3`)
- [x] **Bước 3** — `VideoPlayerFragment`, `ShareFragment`, `RecordedPreviewFragment` (commit `08e2a7c`, `c4fbad0`, `091cb0c`, `3e2e494`)
- [x] **Bước 4** — `EffectListFragment`, `EffectPreviewFragment`, `EffectPickerFragment` + `FavouriteManager` (commit `5449139`, `8fec4ed`, `2a8590e` fix `onDestroyView`, `02e681a`, `d8cebab`)
- [ ] **Bước 5** — Tách `GestureStateMachine`, `computeRecordingSize` khỏi `CameraRecordFragment`, thêm `CameraRecordViewModel` (mốc 5.4, quyết định bổ sung 2026-09-30)
- [ ] **Bước 6** — Splash, Survey×2, Language, Settings, Permission, Welcome, Onboarding×3
- [ ] **Bước 7** — Cập nhật `AGENTS.md`, `Code_Walkthrough.md`, `Fragment_Review_Checklist.md`, `HandAr_Plan.md` (K2), `README.md`, **`Test_Checklist.md`** (mục 9.6 — bổ sung ca thiếu, rồi test toàn bộ)

---

## 3. Bước 1 — Hạ tầng + quy ước

### Danh sách commit

| # | Nội dung |
|---|---|
| 1.1 | `chore: add lifecycle viewmodel + fragment-ktx dependencies` — chỉ sửa `libs.versions.toml` + `app/build.gradle.kts` theo 0.2, không file `.kt` nào đổi |
| 1.2 | `docs: add MVVM section to Fragment_Review_Checklist` — thêm mục 9 mới vào `Fragment_Review_Checklist.md` (nội dung ở Bước 7.3, làm sớm ở đây vì Bước 2 cần tham chiếu ngay) |

### Việc cụ thể

- Thêm dependency đúng khối trong 0.2.
- Mở `Fragment_Review_Checklist.md`, thêm mục **9. Riêng cho Fragment có ViewModel** (nội dung
  soạn sẵn ở Bước 7.3 để dùng luôn từ đây, tránh phải quay lại sửa Bước 1 sau khi đã làm Bước 7).
- Xác nhận `by viewModels()` resolve được (viết tạm 1 dòng dùng thử, build xanh rồi xoá) trước
  khi Bước 2 phụ thuộc vào nó.

---

## 4. Bước 2 — `VideoListFragment` (màn mẫu)

Làm trước vì nhỏ nhất (2,2 KB) và không đụng ExoPlayer/dialog — nếu quy ước ở Bước 1 có vấn đề
gì, phát hiện ở đây rẻ nhất.

### Danh sách commit

| # | Nội dung |
|---|---|
| 2.1 | `refactor: make VideoRepository a class taking Context in its constructor` — chỉ đổi chữ ký, không đổi logic `loadAll`/`readMetadata` |
| 2.2 | `refactor: add VideoListViewModel, wire VideoListFragment` |

### 2.1 — `VideoRepository`

Trước:

```kotlin
object VideoRepository {
    suspend fun loadAll(context: Context): List<VideoItem> = withContext(Dispatchers.IO) { ... }
}
```

Sau:

```kotlin
class VideoRepository(context: Context) {
    private val appContext = context.applicationContext

    suspend fun loadAll(): List<VideoItem> = withContext(Dispatchers.IO) {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        // ... phần còn lại giữ nguyên, chỉ bỏ tham số context truyền tay
    }

    private fun readMetadata(file: File): VideoItem { /* giữ nguyên 100% */ }
}
```

Chỉ một nơi gọi `VideoRepository.loadAll(context)` hiện tại (`VideoListFragment`) — grep xác
nhận trước khi sửa: `findstr /S /N /I "VideoRepository" app\src\main\*.*`.

### 2.2 — `VideoListViewModel` + Fragment

```kotlin
// ui/videolist/VideoListUiState.kt (hoặc lồng trong file VM nếu ngại thêm file)
data class VideoListUiState(
    val isLoading: Boolean = true,
    val items: List<VideoItem> = emptyList(),
    val isEmpty: Boolean = false
)

// ui/videolist/VideoListViewModel.kt
class VideoListViewModel(private val repository: VideoRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = repository.loadAll()
            _uiState.value = VideoListUiState(isLoading = false, items = items, isEmpty = items.isEmpty())
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { VideoListViewModel(VideoRepository(context.applicationContext)) }
        }
    }
}
```

`VideoListFragment` sau khi sửa — bỏ hẳn `viewLifecycleOwner.lifecycleScope.launch { ... }` gọi
`VideoRepository.loadAll` trực tiếp, thay bằng collect `uiState`:

```kotlin
private val viewModel: VideoListViewModel by viewModels { VideoListViewModel.factory(requireContext()) }

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    // ... phần setup adapter/layoutManager/decoration giữ nguyên

    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiState.collect { state ->
                binding.progress.isVisible = state.isLoading
                binding.textEmpty.isVisible = state.isEmpty
                adapter.submit(state.items)
            }
        }
    }
}
```

**Lợi ích ngay được (đúng như đã hứa ở lượt thảo luận trước):** rời màn rồi quay lại không phải
quét lại thư mục — `VideoListViewModel` sống qua `onDestroyView`, `init { load() }` chỉ chạy một
lần theo đúng vòng đời VM (tuy nhiên: nếu muốn "refresh khi quay lại vì có thể vừa xoá video ở
`VideoPlayerFragment`", phải chủ động gọi `viewModel.load()` ở `onResume` — quyết định điểm này
khi làm, ghi chú lại trong code vì đây là hành vi có thể gây nhầm "tưởng cache mà không thấy
video mới").

**Đã quyết định lúc code (2026-09-30): làm mới có điều kiện, không refresh mù ở `onResume`.**

`VideoPlayerFragment` đặt cờ `VideoListFragment.KEY_VIDEO_LIST_STALE = true` vào
`nav.previousBackStackEntry?.savedStateHandle` (hàm `markVideoListStale`) ngay trước `popBackStack()`,
ở **cả hai** đường: `deleteVideoAndExit()` và nhánh `renameTo` thành công.
`VideoListFragment.onResume()` đọc cờ, `remove` nó rồi gọi `viewModel.load()`. Cách này vừa không giữ
dữ liệu cũ, vừa giữ được lợi ích "vào xem rồi back ra thì không quét lại thư mục" — khác hẳn việc gọi
`load()` vô điều kiện ở `onResume` (làm mất sạch lợi ích cache).

⚠️ **Bẫy đã sập một lần, ghi lại để không sập lại:** ban đầu tôi kết luận "rename không cần làm mới
vì item chỉ hiện thumbnail, không hiện tên file" — **sai**. `VideoItem` giữ `File`, và
`VideoListFragment` truyền `videoItem.file.absolutePath` sang player khi bấm vào item. Sau rename,
path trong cache của VM là path cũ đã không còn tồn tại, nên bấm vào video vừa đổi tên là ăn ngay
Toast `can_not_play_video` + `popBackStack()`. Bài học tổng quát cho cả kế hoạch này: khi quyết định
một state cũ "có ảnh hưởng thấy được hay không", phải soát **cả dữ liệu item mang theo và truyền đi
qua callback**, không chỉ soát những gì `onBindViewHolder` vẽ ra màn hình. Đây là dạng lỗi chỉ xuất
hiện *sau khi* thêm ViewModel: trước đó list load lại mỗi lần view được tạo nên path luôn tươi.

**Nợ kỹ thuật có chủ đích:** cờ `KEY_VIDEO_LIST_STALE` là giải pháp tạm không cần ViewModel ở phía
player. Khi làm Bước 3.3 (`VideoPlayerViewModel` phát `Event.Deleted`/`Event.Renamed`), **bỏ cờ này
đi**, cho `VideoPlayerFragment` phát sự kiện qua VM và `VideoListFragment` nhận qua đó — nhớ nối
**cả** `Renamed`, không chỉ `Deleted`.

---

## 5. Bước 3 — `VideoPlayerFragment`, `ShareFragment`, `RecordedPreviewFragment`

Ba màn dùng chung khuôn (`ExoPlayer` + `VideoSeekBarController` + thao tác file), nên tách
chung một `VideoFileRepository` trước, rồi mới làm từng Fragment.

### Danh sách commit

| # | Nội dung |
|---|---|
| 3.1 | `feat: add VideoFileRepository (rename/delete/exists off main thread)` |
| 3.2 | `refactor: RecordedPreviewViewModel, wire RecordedPreviewFragment` |
| 3.3 | `refactor: VideoPlayerViewModel (SavedStateHandle), wire VideoPlayerFragment` |
| 3.4 | `refactor: ShareViewModel, wire ShareFragment` |

### 3.1 — `VideoFileRepository`

Vấn đề thật đang có (đã nêu ở lượt thảo luận trước): `File(videoPath).delete()`,
`oldFile.renameTo(newFile)`, `newFile.exists()` trong `VideoPlayerFragment`/`RecordedPreviewFragment`
chạy thẳng trên main thread. Gom vào một Repository, chạy trên `Dispatchers.IO`:

```kotlin
// ui/videolist/VideoFileRepository.kt (cùng package với VideoRepository vì cùng miền dữ liệu "file video")
class VideoFileRepository {
    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) { File(path).delete() }

    sealed interface RenameResult {
        data class Success(val newPath: String) : RenameResult
        data object NameAlreadyExists : RenameResult
        data object Failed : RenameResult
        data object EmptyName : RenameResult
    }

    suspend fun rename(path: String, newName: String): RenameResult = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withContext RenameResult.EmptyName
        val oldFile = File(path)
        val newFile = File(oldFile.parentFile, "$trimmed.${oldFile.extension}")
        if (newFile == oldFile) return@withContext RenameResult.Success(path)
        if (newFile.exists()) return@withContext RenameResult.NameAlreadyExists
        if (oldFile.renameTo(newFile)) RenameResult.Success(newFile.absolutePath) else RenameResult.Failed
    }
}
```

Không cần Context — hoạt động thuần trên đường dẫn tuyệt đối đã có sẵn.

**Đã làm (2026-09-30), khác snippet trên ở 2 chỗ, đều cố ý:**

- Thêm `RenameResult.Unchanged` cho nhánh `newFile == oldFile`. Snippet trên gộp nhánh này vào
  `Success(path)` — **sai hành vi**: bản gốc `VideoPlayerFragment.renameVideo()` là
  `if (newFile == oldFile) return`, tức KHÔNG Toast "đổi tên thành công" và KHÔNG `popBackStack()`.
  Gộp vào `Success` thì người dùng sửa tên rồi đổi lại y như cũ sẽ bị đá ra khỏi màn player kèm
  Toast sai.
- Thêm `exists(path)`, vì `VideoPlayerFragment` dòng ~73 có `!file.exists()` cũng đang chạy trên
  main thread — cùng loại việc, cùng miền dữ liệu.

KDoc của từng nhánh `RenameResult` ghi rõ nó tương ứng hành vi nào của bản gốc (Toast gì, hay im
lặng) để mốc 3.3 map 1-1 được mà không phải đọc lại bản gốc.

### 3.2 — `RecordedPreviewFragment`

State cần VM: chỉ có `confirmDialog` đang hiện hay không, và kết quả `save()`/`discardAndExit()`.
Phần ExoPlayer **ở lại Fragment** (đúng lý do đã nêu ở lượt thảo luận trước: player gắn trực
tiếp với View, VM sống lâu hơn View nên không nên giữ `ExoPlayer` trong VM).

```kotlin
class RecordedPreviewViewModel(
    private val fileRepository: VideoFileRepository,
    private val videoPath: String
) : ViewModel() {
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        data object Discarded : Event
        data class SavedTo(val newPath: String? = null) : Event // null = không đổi path (chỉ điều hướng sang Share)
    }

    fun discardAndExit() {
        viewModelScope.launch {
            fileRepository.delete(videoPath)
            _events.send(Event.Discarded)
        }
    }

    fun save() {
        viewModelScope.launch { _events.send(Event.SavedTo()) }
    }
}
```

Nhận xét: `save()` ở bản gốc không làm gì ngoài điều hướng (video đã lưu sẵn lúc dừng ghi) —
việc bọc nó vào VM có vẻ thừa, nhưng **đáng làm** vì tương lai gần (tính năng nav_collection có
thể thêm bước xử lý trước khi điều hướng, ví dụ ghi lịch sử) sẽ có đúng một chỗ để thêm, không
phải sửa lại Fragment.

`RecordedPreviewFragment` giữ nguyên toàn bộ phần `ExoPlayer`/`ConfirmDialog`, chỉ đổi
`discardAndExit()`/`save()` thành gọi VM rồi collect `events`:

```kotlin
viewModel.discardAndExit() // thay cho gọi thẳng file.delete() + popBackStack()
// ...
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordedPreviewViewModel.Event.Discarded -> {
                    val nav = findNavController()
                    if (nav.currentDestination?.id == R.id.recordedPreviewFragment) nav.popBackStack()
                }
                is RecordedPreviewViewModel.Event.SavedTo -> {
                    val nav = findNavController()
                    if (nav.currentDestination?.id == R.id.recordedPreviewFragment) {
                        nav.navigate(RecordedPreviewFragmentDirections.actionRecordedPreviewToShare(videoPath, effectId, fromRecordedPreview = true))
                    }
                }
            }
        }
    }
}
```

Chốt cửa `currentDestination?.id` **giữ nguyên trong Fragment**, không chuyển vào VM (VM không
có `NavController` — đúng quy ước 0.2).

**Đã làm (2026-09-30), khác snippet trên ở 2 chỗ:**

- `Event.SavedTo(newPath: String? = null)` → đổi thành `Event.Saved` không tham số. `save()` không
  đổi path (video đã lưu sẵn lúc dừng ghi), nên tham số `newPath` luôn `null` — một tham số chỉ có
  một giá trị khả dĩ thì thà bỏ đi, thêm lại khi thật sự cần.
- ⚠️ **Regression do chính mốc này gây ra, đã xử lý trong cùng commit:** xoá file giờ chạy bất đồng
  bộ trên `Dispatchers.IO`, nên `ConfirmDialog` dismiss **xong trước** khi Fragment nhận
  `Event.Discarded` và `popBackStack()`. `setOnDismissListener { player?.play() }` vì thế gọi
  `play()` và video (đã bị xoá) phát tiếp một nhịp trước khi thoát màn. Bản gốc không gặp vì nó
  `delete()` + `popBackStack()` đồng bộ → `onDestroyView` đã `setOnDismissListener(null)` trước khi
  dismiss kịp chạy. Sửa bằng cờ `discarding` trong Fragment:
  `setOnDismissListener { if (!discarding) player?.play() }`.

  **Bài học cho các mốc còn lại của Bước 3 và Bước 6:** mỗi lần chuyển một thao tác từ đồng bộ sang
  `viewModelScope` + sự kiện, phải soát lại **thứ tự** các callback đang dựa vào việc thao tác đó
  kết thúc ngay lập tức (dialog dismiss listener, `OnBackPressedCallback`, `onDestroyView`). Đây
  không phải "đổi hành vi ngoài ý muốn" nên được sửa trong cùng commit di chuyển — khác với bug có
  sẵn, thứ vẫn phải để commit riêng theo nguyên tắc 0.1.

### 3.3 — `VideoPlayerFragment`

Đây là Fragment phức tạp nhất trong Bước này: có `playbackPosition` cần sống qua process death
(`onSaveInstanceState` hiện có), menu dropdown, dialog xoá, rename.

```kotlin
class VideoPlayerViewModel(
    private val fileRepository: VideoFileRepository,
    private val savedStateHandle: SavedStateHandle,
    val videoPath: String
) : ViewModel() {
    var playbackPosition: Long
        get() = savedStateHandle["playback_position"] ?: 0L
        set(value) { savedStateHandle["playback_position"] = value }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        data object Deleted : Event
        data class Renamed(val newPath: String) : Event
        data object RenameNameExists : Event
        data object RenameFailed : Event
        data object FileMissing : Event
    }

    fun checkFileExists() {
        if (!File(videoPath).exists()) viewModelScope.launch { _events.send(Event.FileMissing) }
    }

    fun delete() {
        viewModelScope.launch {
            fileRepository.delete(videoPath)
            _events.send(Event.Deleted)
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            when (val result = fileRepository.rename(videoPath, newName)) {
                is VideoFileRepository.RenameResult.Success -> _events.send(Event.Renamed(result.newPath))
                VideoFileRepository.RenameResult.NameAlreadyExists -> _events.send(Event.RenameNameExists)
                VideoFileRepository.RenameResult.Failed, VideoFileRepository.RenameResult.EmptyName -> _events.send(Event.RenameFailed)
            }
        }
    }
}
```

**Điểm cần cẩn thận:** `videoPath` đổi sau khi rename thành công (`Event.Renamed`), nhưng
`ExoPlayer` đang phát file cũ — bản gốc xử lý bằng `nav.popBackStack()` ngay sau rename thành
công (không ở lại màn này), nên không phát sinh vấn đề "player đang phát file đã đổi tên". Giữ
nguyên hành vi đó: `Event.Renamed` → Fragment `popBackStack()`, không cố gắng "load lại player
với path mới" — đổi hành vi này không nằm trong phạm vi Bước 3.

**Đã làm (2026-09-30), khác snippet trên ở 4 chỗ:**

- **KHÔNG làm `checkFileExists()` / `Event.FileMissing`.** Chốt `!file.exists()` ở đầu
  `onViewCreated` **giữ nguyên đồng bộ trong Fragment**. Lý do: nó là cửa chặn `setupPlayer(file)`
  ngay dòng dưới. Chuyển sang bất đồng bộ thì `setupPlayer` chạy trước trên file không tồn tại,
  ExoPlayer báo lỗi → `onPlayerError` Toast `can_not_play_video`, rồi `Event.FileMissing` Toast
  **lần hai** + pop. Một `File.exists()` một lần lúc vào màn không đáng đổi lấy cái đó.
  Hệ quả: `VideoFileRepository.exists()` thêm ở mốc 3.1 hiện **chưa dùng ở đâu** — Bước 7 rà lại,
  còn không dùng thì xoá.
- **`Event.Renamed` không mang `newPath`.** Bản gốc `popBackStack()` ngay sau rename thành công nên
  Fragment không cần path mới để làm gì; mang theo một giá trị không ai đọc chỉ gây tưởng là có
  reload player.
- **Hai nhánh `EmptyName` và `Unchanged` không phát sự kiện nào** — đúng bản gốc: `return` im lặng,
  không Toast, không thoát màn. Kế hoạch gộp `EmptyName` vào `RenameFailed` là sai hành vi (sẽ Toast
  `can_not_rename_video` khi người dùng bỏ trống tên).
- **`onSaveInstanceState` thay bằng ghi vào VM ở `onStop`.** Bỏ hẳn `onCreate`/`onSaveInstanceState`
  và hằng `KEY_PLAYBACK_POSITION` ở Fragment. `onStop` luôn chạy trước khi hệ thống lưu state, nên
  `player?.let { viewModel.playbackPosition = it.currentPosition }` ở đó là chỗ chắc chắn nhất.
  `onDestroyView` cũng ghi, nhưng bỏ `?: 0L` của bản gốc: `onStop` đã ghi rồi, `player` null ở
  `onDestroyView` thì giữ giá trị cũ chứ không xoá về 0.

⚠️ **Cùng regression như mốc 3.2, đã xử lý trong cùng commit:** `confirmDeleteDialog` cũng có
`setOnDismissListener { player?.play() }`, nên cần cờ `deleting` y như cờ `discarding` ở
`RecordedPreviewFragment`. Đúng như bài học ghi ở 3.2 — lần này đã soát trước chứ không để sập.

Bug thật tìm thấy lúc đọc code (đã ghi ở lượt thảo luận trước), sửa ở **commit riêng sau khi
việc di chuyển xong**, không trộn vào 3.3: `requestCameraPermission`/`requestNotificationPermission`
là của `PermissionFragment` — bug tương tự ở đây là hai callback kết quả nếu đến muộn khi view
đã huỷ. Với `VideoPlayerFragment`, việc chuyển sự kiện qua VM + `repeatOnLifecycle` **tự động
xử lý đúng** trường hợp này (Fragment không còn `STARTED` thì collector không chạy), nên không
cần sửa gì thêm riêng — đây là một trong những lợi ích thật của việc chuyển MVVM, ghi lại làm
bằng chứng cụ thể thay vì chỉ nói chung chung.

### 3.4 — `ShareFragment`

State: `isFullscreen`. Không có thao tác file (chỉ đọc `videoPath` để share/phát), nên VM ở đây
chủ yếu quản lý `isFullscreen` và bọc `tryAgain()`/`goHome()` cho nhất quán:

```kotlin
class ShareUiState(val isFullscreen: Boolean = false)

class ShareViewModel(val videoPath: String, val effectId: String, val fromRecordedPreview: Boolean) : ViewModel() {
    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    fun showFullscreen() { _uiState.value = ShareUiState(isFullscreen = true) }
    fun showCard() { _uiState.value = ShareUiState(isFullscreen = false) }
}
```

**Đã quyết định (2026-09-30): VẪN LÀM**, không bỏ qua. Lý do người dùng chốt: sau này thêm tính
năng mới ở màn Share thì đã có sẵn chỗ, không phải refactor rời rạc đúng một màn giữa các màn đã
chuyển. Ghi lại vì đây là lựa chọn ngược với "giá trị thấp" mà kế hoạch cảnh báo — chấp nhận có hai
tầng `showFullscreen` (VM đổi state, Fragment thi hành UI) để đổi lấy tính nhất quán.

**Đã làm, khác snippet trên ở 3 chỗ:**

- **VM không giữ `videoPath`/`effectId`/`fromRecordedPreview`.** `navArgs` đã là nguồn duy nhất và
  Fragment vẫn cần chúng trực tiếp (intent share, `setupPlayer`, `binding.btnTryAgain.isVisible`).
  Copy sang VM chỉ tạo bản thứ hai không ai đọc. Vì thế VM **không có tham số constructor** → dùng
  `by viewModels()` trần, **không cần factory**.
- **Thêm `Event.GoHome`/`Event.TryAgain` và `onBackPressed()`.** Snippet chỉ có `isFullscreen`; nhưng
  đã làm VM thì để `goHome`/`tryAgain` gọi thẳng `findNavController()` từ listener là nửa vời. Quyết
  định back hệ thống (`đang fullscreen thì thu nhỏ, không thì về home`) chuyển vào VM vì nó chỉ đọc
  state; việc điều hướng và chốt cửa `currentDestination` vẫn ở Fragment.
- ⚠️ **Cần cờ `renderedFullscreen` ở Fragment — đây là cái giá thật của tầng VM ở màn này.**
  `applyFullscreen`/`applyCard` **dời hẳn** `playerView` giữa hai container (`removeView` +
  `addView`), không phải chỉ đổi visibility. Render thẳng từ `StateFlow` thì lần collect đầu tiên
  (state mặc định `isFullscreen = false`) sẽ chạy `applyCard()` trên một `playerView` vốn đã nằm
  đúng trong `cardVideoContainer` → re-parent vô ích. Nên Fragment giữ riêng "cây View hiện đang ở
  trạng thái nào", chỉ thi hành khi state khác nó. Gán lại `false` ở `onViewCreated` (view mới
  inflate luôn ở dạng card), nhờ đó quay lại từ back stack với VM đang giữ `isFullscreen = true` thì
  lần collect đầu tự dựng lại fullscreen — điều bản gốc **không** làm được vì cờ `isFullscreen` cũ
  là field Fragment, không phản ánh cây View mới.

  **Bài học chung:** khi render từ `StateFlow` mà hành động render **không idempotent** (dời view,
  thêm/bớt view, phát animation, mở dialog), phải so state mới với state đã render, không được thi
  hành mỗi lần collect. Guard `if (isFullscreen) return` của bản gốc chính là thứ đang làm việc này
  — chuyển sang VM thì nó phải được dựng lại ở tầng Fragment chứ không mất đi.

---

## 6. Bước 4 — `EffectListFragment`, `EffectPreviewFragment`, `EffectPickerFragment` + `FavouriteManager`

### Danh sách commit

| # | Nội dung |
|---|---|
| 4.1 | `refactor: make FavouriteManager a class taking Context` |
| 4.2 | `refactor: EffectListViewModel (search + favourite), wire EffectListFragment` |
| 4.3 | `refactor: EffectPreviewViewModel, wire EffectPreviewFragment` |
| 4.4 | `refactor: EffectPickerViewModel, wire EffectPickerFragment` |

### 4.1 — `FavouriteManager`

Cùng lý do 2.1 — đổi `object` thành `class`, giữ nguyên logic `SharedPreferences`:

```kotlin
class FavouriteManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // isFavourite/toggle giữ nguyên, chỉ bỏ tham số context truyền tay
}
```

Một nơi gọi duy nhất hiện tại: `EffectAdapter`. Sau Bước 4.2, `EffectAdapter` **không** tự gọi
`FavouriteManager` nữa — trạng thái favourite chuyển vào `EffectListUiState` (xem 4.2), Adapter
chỉ nhận `isFavourite: Boolean` đã tính sẵn qua `item` và gọi callback `onToggleFavourite` lên
Fragment/VM thay vì tự đọc/ghi `SharedPreferences`. Đây là thay đổi có ý nghĩa thật (không chỉ
đổi vỏ): Adapter hiện đang tự ý đọc/ghi persistence — di chuyển trách nhiệm này vào VM là đúng
tinh thần MVVM, không phải thủ tục hình thức.

### 4.2 — `EffectListViewModel`

```kotlin
data class EffectListUiState(
    val items: List<EffectDefinition> = EffectRepository.all,
    val favouriteIds: Set<String> = emptySet(),
    val query: String = ""
)

class EffectListViewModel(private val favouriteManager: FavouriteManager) : ViewModel() {
    private val _uiState = MutableStateFlow(EffectListUiState())
    val uiState: StateFlow<EffectListUiState> = _uiState.asStateFlow()

    init { refreshFavourites() }

    fun onQueryChanged(query: String) {
        if (query == _uiState.value.query) return
        _uiState.value = _uiState.value.copy(query = query, items = EffectRepository.findByName(query))
    }

    fun toggleFavourite(effectId: String) {
        val nowFavourite = favouriteManager.toggle(effectId)
        val current = _uiState.value.favouriteIds
        _uiState.value = _uiState.value.copy(
            favouriteIds = if (nowFavourite) current + effectId else current - effectId
        )
    }

    private fun refreshFavourites() {
        val ids = EffectRepository.all.map { it.id }.filter { favouriteManager.isFavourite(it) }.toSet()
        _uiState.value = _uiState.value.copy(favouriteIds = ids.toSet())
    }
}
```

**Lưu ý quan trọng cho tính năng nav_collection sắp tới:** `favouriteIds` giờ nằm trong VM chứ
không rải trong Adapter — đây chính xác là chỗ mà lượt thảo luận trước dự đoán "Bộ sưu tập sẽ
dùng chung dữ liệu yêu thích với `EffectListFragment`". Khi làm màn đó, cân nhắc nâng
`FavouriteManager` (và có thể `EffectListViewModel.favouriteIds`) lên `Activity`-scope
(`by activityViewModels()`) hoặc tách riêng một `FavouriteRepository` dùng chung — **không nằm
trong phạm vi Bước 4**, chỉ ghi chú để người làm màn Bộ sưu tập sau này không phải đoán lại.

`EffectAdapter` sửa để nhận `favouriteIds: Set<String>` + `onToggleFavourite: (String) -> Unit`
thay vì tự gọi `FavouriteManager` — đây là thay đổi API của Adapter, không phải chỉ đổi bên
trong, nên `EffectListFragment` phải sửa chỗ khởi tạo Adapter cùng commit 4.2 (không tách được).

**Đã làm (2026-09-30), 3 điểm cần biết:**

- `updateItems(items)` đổi thành `submit(items, favouriteIds)` — một lời gọi duy nhất cho một lần
  render, thay vì hai đường cập nhật rời (items từ Fragment, favourite từ Adapter tự đọc prefs).
  Adapter khởi tạo với `emptyList()`/`emptySet()`, dữ liệu thật đến từ lần collect đầu.
- ⚠️ **Phải khôi phục ô tìm kiếm từ VM ở `onViewCreated`.** VM sống qua `onDestroyView`: đi sang
  `EffectPreview` rồi back lại thì `query` còn trong VM nhưng `EditText` mới inflate là rỗng → danh
  sách đang lọc mà ô tìm kiếm trống. Sửa bằng `setText(viewModel.uiState.value.query)` **trước** khi
  `addTextChangedListener` (set sau thì listener tự kích hoạt). Đây lại đúng dạng lỗi "VM sống dai
  hơn View" của mục 4 — lần này soát trước. Bản gốc không gặp vì Adapter được tạo lại với
  `EffectRepository.all` mỗi lần view được tạo.
- `FavouriteManager` **không** đẩy sang `Dispatchers.IO`: đọc là `getStringSet` từ map trong bộ nhớ,
  ghi là `edit { }` của androidx (mặc định `apply()`, đã bất đồng bộ). Giữ đồng bộ để `toggle()` trả
  kết quả ngay, không phải thêm state "đang lưu".

🐛 **Bug có sẵn phát hiện lúc đọc code, KHÔNG sửa trong commit 4.2** (đúng nguyên tắc 0.1 — bug có
sẵn đi commit riêng): `EffectListFragment` **không có `onDestroyView()`**. `_binding` không bị null
hoá, `recyclerEffect.adapter` không bị cắt — vi phạm thẳng `Fragment_Review_Checklist.md` mục 1 và
mục 8, và đây là màn home nên nó nằm trong back stack gần như suốt phiên. Sửa ở commit riêng ngay
sau 4.2:

```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    binding.recyclerEffect.adapter = null
    _binding = null
}
```

(`EffectPreviewFragment`/`EffectPickerFragment` cũng soát lại cùng lúc khi làm 4.3/4.4.)

**Đã sửa (2026-09-30)** ở commit riêng ngay sau 4.2, đúng nội dung trên. Không gộp vào 4.2 để diff
của 4.2 chỉ còn việc chuyển MVVM.

📌 **Ghi cho Bước 7:** `EffectListFragment` thiếu `onDestroyView` mà vẫn build xanh, chạy được, không
ai phát hiện cho tới khi đọc kỹ để refactor — đúng loại lỗi `Fragment_Review_Checklist.md` được viết
ra để bắt, nhưng checklist chỉ có tác dụng nếu thật sự chạy qua nó. Bước 7 rà **cả 18 Fragment** theo
mục 1 + mục 8 của checklist, không chỉ những màn kế hoạch này chạm tới.

### 4.3 — `EffectPreviewFragment`

State thật ở đây rất mỏng: `effect` đọc từ `args.effectId` qua `EffectRepository.findById`, và
`previewDrawable` (thuộc View, ở lại Fragment). VM ở đây gần như chỉ bao 1 hàm tìm kiếm đơn
giản, giá trị thấp. **Đề xuất: cân nhắc bỏ qua Fragment này**, chỉ ghi
lại lý do ở Bước 7 — tương tự cân nhắc đã nêu cho `ShareFragment` ở Bước 3. Nếu vẫn làm (để đồng
bộ 100%, đúng tinh thần "sau này thêm gì đỡ phải refactor thêm đợt" mà bạn đã chọn ở lượt trước):

```kotlin
class EffectPreviewViewModel(val effect: EffectDefinition) : ViewModel() {
    companion object {
        fun factory(effectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { EffectPreviewViewModel(EffectRepository.findById(effectId)) }
        }
    }
}
```

Toàn bộ phần `AnimatedImageDrawable`/`onStart`/`onStop`/`onDestroyView` **giữ nguyên trong
Fragment** — đây là tài nguyên gắn View, không thuộc VM (đúng quy tắc đã nhắc nhiều lần).

**Đã làm (2026-09-30): VẪN LÀM**, không bỏ qua — cùng lý do người dùng đã chốt ở mốc 3.4 (thêm tính
năng sau này đỡ phải refactor rời rạc một màn). Khác snippet trên ở 2 chỗ:

- **Thêm `Event.GoBack`/`Event.CreateVideo`** và hai hàm `onBackClicked()`/`onCreateClicked()`, thay
  vì để listener gọi thẳng `findNavController()`. Cùng khuôn với `ShareViewModel`.
- **Bỏ `onCreate` + `lateinit var effect` ở Fragment**, `EffectRepository.findById(args.effectId)`
  chuyển vào `initializer` của factory. Lưu ý hành vi: `findById` dùng `first { }` nên id sai vẫn
  ném `NoSuchElementException` như trước, chỉ đổi thời điểm — trước là ở `onCreate`, giờ là lần đầu
  đọc `viewModel`. Không sửa thành `findByIdOrNull` ở đây: đổi cách xử lý id sai là việc khác, ngoài
  phạm vi mốc này.

📌 **Điểm đã kiểm chứng để yên tâm với sự kiện qua `Channel`:** rủi ro "sự kiện cũ còn trong Channel
bị phát lại khi quay lại màn" **không xảy ra** ở cả `EffectPreviewFragment` lẫn `ShareFragment`, vì
mọi action rời hai màn này đều `popUpToInclusive="true"` chính nó (`action_effectPreview_to_cameraRecord`,
`action_share_to_cameraRecord`, và `GoHome` thì `popBackStack(effectListFragment, false)`) — destination
bị gỡ khỏi back stack nên VM bị clear cùng lúc, không còn Channel nào để phát lại. Đã đọc
`nav_graph.xml` xác nhận, không suy diễn. Nếu sau này thêm action rời màn mà **không** `popUpTo` chính
nó, phải soát lại chỗ này.

### 4.4 — `EffectPickerFragment`

State: `selectedEffectId`. Nhỏ, tương tự 4.3 về mức độ cần thiết, nhưng có logic thật hơn một
chút (validate id có tồn tại trong repository không):

```kotlin
class EffectPickerViewModel(currentEffectId: String) : ViewModel() {
    private val _selectedEffectId = MutableStateFlow(
        currentEffectId.takeIf { id -> EffectRepository.all.any { it.id == id } }
    )
    val selectedEffectId: StateFlow<String?> = _selectedEffectId.asStateFlow()

    fun select(effectId: String) { _selectedEffectId.value = effectId }
}
```

**Đã làm (2026-09-30), 3 điểm:**

- **Thêm `Event.Close`/`Event.OpenPreview(effectId)` + `onBackClicked()`/`onConfirmClicked()`.** Quyết
  định "xác nhận thì đi đâu" (`id == currentEffectId` → chỉ đóng màn, khác → sang preview) chuyển vào
  VM vì nó thuần so sánh dữ liệu; vì thế VM cần giữ `currentEffectId`. `Close` dùng chung cho cả nút
  back và xác nhận-không-đổi, hai đường đó cùng `popBackStack()`.
- **`EffectPickerAdapter` đổi API: bỏ tham số `selectedId`, thêm `setSelectedId(newId)`.** Trước đây
  Adapter **tự giữ** lựa chọn (`private fun select()` đổi `selectedId` rồi mới gọi `onSelected`), giờ
  cú bấm chỉ báo lên VM, VM đổi state, collector gọi `setSelectedId` xuống. `selectedId` còn lại
  trong Adapter chỉ là bản ghi "viền đang vẽ ở item nào" để tính vị trí cần notify — **giữ nguyên
  việc cập nhật từng item bằng `PAYLOAD_SELECTION`**, không hạ xuống `notifyDataSetChanged` cả lưới.
  Đây cùng một khuôn với cờ `renderedFullscreen` ở mốc 3.4: render không idempotent thì Fragment/Adapter
  phải biết mình đang vẽ gì.
  - Thêm chốt `if (newPosition >= 0)` trước khi notify: bản gốc không cần vì `select()` chỉ được gọi
    từ cú bấm vào item có thật, còn `setSelectedId` có thể nhận `null`.
- **`renderConfirmEnabled()` nhận tham số** thay vì đọc field, vì nguồn sự thật giờ là state trong
  collector chứ không phải field Fragment.

📌 **Ghi cho Bước 7:** `EffectPickerFragment.onDestroyView` chỉ null `_binding`, **không** cắt
`recyclerEffectPicker.adapter` — cùng loại thiếu sót với `EffectListFragment`. Tôi **không** sửa trong
commit 4.4 (nguyên tắc 0.1: bug có sẵn đi riêng) và cũng không làm commit lẻ cho một dòng — gộp vào
đợt rà 18 Fragment ở Bước 7.

---

## 7. Bước 5 — Tách phần thuần Kotlin khỏi `CameraRecordFragment` (+ mốc 5.4: `CameraRecordViewModel`)

**Đây là Bước rủi ro cao nhất trong toàn kế hoạch.**

> ⚠️ **Đoạn dưới đây là quyết định BAN ĐẦU, đã được đảo ngược một phần ở mốc 5.4 (2026-09-30).**
> Phần "không đưa tài nguyên gắn View vào VM" vẫn đúng và vẫn áp dụng. Phần "không thêm ViewModel"
> thì không còn: mốc 5.4 thêm `CameraRecordViewModel` giữ **chỉ** 4 thứ thuần dữ liệu. Đọc mục 5.4
> trước khi trích dẫn đoạn này.

Không thêm `ViewModel` cho
`CameraRecordFragment` ở Bước này — lý do đã thống nhất ở lượt thảo luận trước: phần lớn tài
nguyên (`VideoRecorder`, `BgmPlayer`, `SoundEffectPlayer`, `HandLandmarker`, thread ghi hình,
`OverlayView`) gắn chặt với View/Context theo đúng chủ đích thiết kế ("camera nằm lại back stack
= instance sống, view chết" — `AGENTS.md` mục 5), đưa chúng vào VM chỉ chuyển chỗ phức tạp chứ
không giảm. Việc đáng làm ở đây là tách phần **thuần Kotlin, không đụng Android**, ra khỏi
Fragment ~27 KB (27.742 byte tại `abfb32a`; từng ~24 KB trước khi thêm loading overlay + fallback GPU→CPU, sẽ còn phình thêm nếu tiếp tục thêm logic vào Fragment) để dễ đọc và dễ sửa hơn — không phải "MVVM hoá Camera".

### Danh sách commit

| # | Nội dung |
|---|---|
| 5.1 | `refactor: extract GestureStateMachine from CameraRecordFragment` |
| 5.2 | `refactor: extract computeRecordingSize into standalone util` |
| 5.3 | (tuỳ chọn, chỉ làm nếu 5.1–5.2 suôn sẻ) `refactor: extract RecordingTimer from CameraRecordFragment` |

**Cố ý không đưa `RecordingSession`/`VideoRecorder` wrapper vào danh sách này** dù đã nhắc ở lượt
thảo luận trước — sau khi đọc kỹ `CameraRecordFragment.toggleRecording()`, việc bọc
`VideoRecorder` phụ thuộc trực tiếp vào `binding.overlay.width/height`, `activeEffect`,
`bgmPlayer`, `currentEffect?.bgm` — tách nó ra sẽ phải truyền vào 5-6 tham số hoặc để lại phần
lớn logic ở Fragment, giá trị thấp so với rủi ro. Nếu vẫn muốn làm, tách thành một kế hoạch phụ
riêng sau khi 5.1–5.2 đã chứng minh cách tách an toàn.

### 5.1 — `GestureStateMachine`

Trích đúng phần debounce trong `handleGesture()`, `clearActiveEffect()`, và state
(`lastStateId`, `pendingState`, `pendingStateSince`, `activeEffect`) — đây là phần **không** cần
`Context`, `binding`, hay bất kỳ Android API nào ngoài `SystemClock.uptimeMillis()` (giữ nguyên,
không đổi sang `System.currentTimeMillis()` — khác nhau khi máy sleep, đã dùng đúng loại đồng hồ
từ đầu).

```kotlin
// ui/camera/GestureStateMachine.kt
class GestureStateMachine(private val debounceMs: Long = 200L) {
    data class ActiveEffect(val pcm: ShortArray, val startedAtMs: Long)

    private var lastStateId: String? = null
    private var pendingState: String? = null
    private var pendingStateSince = 0L
    var activeEffect: ActiveEffect? = null
        private set

    fun reset() {
        lastStateId = null
        pendingState = null
        pendingStateSince = 0
        activeEffect = null
    }

    sealed interface Result {
        data object NoChange : Result
        data class Activate(val stateId: String, val soundRes: Int) : Result
        data object Clear : Result
    }

    /** effect.states.firstOrNull { it.gesture.recognize(hands) } đã được gọi bên ngoài,
     *  hàm này chỉ nhận kết quả đã match để không phụ thuộc vào EffectDefinition/HandLandmarkerResult. */
    fun onMatchedState(matchedStateId: String?, soundRes: Int?, momentaryClearsOnNull: Boolean): Result {
        if (matchedStateId == null) {
            return if (momentaryClearsOnNull) { clear(); Result.Clear } else Result.NoChange
        }
        val now = SystemClock.uptimeMillis()
        if (matchedStateId != pendingState) {
            pendingState = matchedStateId
            pendingStateSince = now
            return Result.NoChange
        }
        if (lastStateId == matchedStateId || now - pendingStateSince < debounceMs) return Result.NoChange

        lastStateId = matchedStateId
        if (soundRes == null) { clear(keepPendingState = true); return Result.NoChange }
        return Result.Activate(matchedStateId, soundRes)
    }

    fun setActiveEffect(pcm: ShortArray, startedAtMs: Long) { activeEffect = ActiveEffect(pcm, startedAtMs) }

    fun clear(keepPendingState: Boolean = false) {
        activeEffect = null
        lastStateId = null
        if (!keepPendingState) { pendingState = null; pendingStateSince = 0 }
    }
}
```

⚠️ **Đây chính xác là loại thay đổi mà `Fragment_Review_Checklist.md` mục 7 cảnh báo** — hàm cũ
`handleGesture()` có nhánh gọi `videoRecorder?.audioMixer?.triggerEffect(pcm)` và
`soundEffectPlayer.playForSound(soundRes)` **xen giữa** logic debounce thuần. Khi tách, những
dòng gọi Android API đó **ở lại `CameraRecordFragment`**, chỉ gọi sau khi nhận `Result.Activate`
từ state machine:

```kotlin
// trong CameraRecordFragment, thay handleGesture() cũ
private fun handleGesture(result: HandLandmarkerResult) {
    val effect = currentEffect ?: return
    val hands = result.landmarks()
    val matchedState = if (hands.isEmpty()) null else effect.states.firstOrNull { it.gesture.recognize(hands) }

    when (val r = gestureStateMachine.onMatchedState(
        matchedState?.id, matchedState?.soundRes, effect.stateMode == StateMode.Momentary
    )) {
        is GestureStateMachine.Result.Activate -> {
            val pcm = statePcmMap[r.stateId] ?: return
            gestureStateMachine.setActiveEffect(pcm, SystemClock.elapsedRealtime())
            videoRecorder?.audioMixer?.triggerEffect(pcm)
            soundEffectPlayer.playForSound(r.soundRes)
        }
        GestureStateMachine.Result.Clear -> {
            soundEffectPlayer.stopEffect()
            videoRecorder?.audioMixer?.triggerEffect(null)
        }
        GestureStateMachine.Result.NoChange -> Unit
    }
}
```

**Bắt buộc trước khi commit 5.1:** xuất bản 2 hàm gốc (`handleGesture`, `clearActiveEffect`) ra
file `.txt` bằng `git show HEAD:...`, so từng nhánh `if` với bản mới — đúng quy trình
`Fragment_Review_Checklist.md` mục 7. Đặc biệt soát kỹ: bản gốc có **2 chỗ gọi `clearActiveEffect`**
(một khi `hands.isEmpty()`, một khi `matchedState == null`) — cả hai đều dẫn tới cùng nhánh
`Momentary` trong bản mới, xác nhận không sót nhánh nào khi gộp `hands.isEmpty()` vào chung điều
kiện `matchedState == null` (đưa `null` vào khi `hands.isEmpty()` là đúng vì
`firstOrNull { }` trên list rỗng cũng trả `null` — nhưng **phải tự tay xác nhận**, không suy
diễn).

`resetGestureState()` đổi thành gọi `gestureStateMachine.reset()`, gọi ở đúng chỗ cũ
(`onViewCreated`) — không đổi vị trí gọi, chỉ đổi thứ được gọi.

### Đã làm 5.1 (2026-09-30) — 3 chỗ snippet kế hoạch SAI hành vi, đã sửa

1. **`Result.Clear` phải trả về ở nhánh `soundRes == null`, không phải `NoChange`.** Snippet kế hoạch
   viết `if (soundRes == null) { clear(keepPendingState = true); return Result.NoChange }`. Nhưng bản
   gốc nhánh đó gọi `clearActiveEffect(keepPendingState = true)` — mà `clearActiveEffect` **có** gọi
   `soundEffectPlayer.stopEffect()` + `videoRecorder?.audioMixer?.triggerEffect(null)`. Trả `NoChange`
   thì Fragment không gọi hai hàm đó → **tiếng effect đang phát sẽ không tắt** khi chuyển sang một
   state không có tiếng. Đã trả `Result.Clear`.
2. **`activeEffect` phải giữ `@Volatile`.** Snippet để `var activeEffect: ActiveEffect? = null` trơn.
   Bản gốc đánh dấu `@Volatile` vì `onMatchedState` chạy trên thread của HandLandmarker
   (`backgroundExecutor`) còn `activeEffect` được đọc từ **main thread** trong `toggleRecording()` để
   nối tiếp effect vào `audioMixer`. Bỏ `@Volatile` là mất bảo đảm nhìn thấy giá trị mới giữa hai
   thread — lỗi kiểu chỉ xuất hiện thỉnh thoảng, rất khó truy. Các field debounce còn lại **không**
   `@Volatile`, đúng bản gốc (chỉ dùng trong `onMatchedState`).
3. **`DEBOUNCE_MS` chuyển vào `GestureStateMachine`** (`DEFAULT_DEBOUNCE_MS = 200L`, tham số
   constructor có default) và **xoá khỏi companion của Fragment** — snippet không nói xoá, để lại thì
   thành hằng chết.

Chi tiết nữa: local `val result` của snippet **che tham số** `result: HandLandmarkerResult` của
`handleGesture` (Kotlin chỉ cảnh báo, vẫn compile) — đổi tên thành `gestureResult`.

### Bảng đối chiếu từng nhánh bản gốc → bản mới (làm trước khi commit, theo mục 7 checklist)

| Bản gốc | Bản mới |
|---|---|
| `hands.isEmpty()` + `Momentary` → `clearActiveEffect()` | `matchedStateId = null` + `momentaryClearsOnNull` → `clear()` → `Result.Clear` → Fragment `stopEffect` + `triggerEffect(null)` |
| `hands.isEmpty()` + không Momentary → chỉ `return` | `Result.NoChange` |
| `matchedState == null` + `Momentary` → `clearActiveEffect()` | gộp cùng nhánh trên (`firstOrNull` trên list rỗng cũng trả `null`) — **đã tự tay xác nhận hai nhánh gốc có thân hàm giống nhau từng dòng** |
| `matchedState.id != pendingState` → set `pendingState`/`pendingStateSince`, không làm gì thêm | y nguyên, trả `Result.NoChange` |
| `lastStateId != id && now - since >= DEBOUNCE_MS` | đảo thành `if (lastStateId == id \|\| now - since < debounceMs) return NoChange` — cùng điều kiện |
| `lastStateId = id` rồi `soundRes == null` → `clearActiveEffect(keepPendingState = true)` | `lastStateId = matchedStateId` rồi `clear(keepPendingState = true)` → `Result.Clear` (xem điểm 1) |
| `statePcmMap[id] ?: return` (sau khi đã set `lastStateId`) | Fragment: `statePcmMap[gestureResult.stateId] ?: return` — `lastStateId` cũng đã set trong SM trước đó, nên state sau khi pcm thiếu là giống nhau |
| `activeEffect = ActiveEffect(pcm, SystemClock.elapsedRealtime())` | `setActiveEffect(pcm, SystemClock.elapsedRealtime())` — **giữ `elapsedRealtime` ở call site**, khác `uptimeMillis` dùng cho debounce, hai đồng hồ khác nhau là cố ý từ bản gốc |
| `triggerEffect(pcm)` + `playForSound(soundRes)` | y nguyên, trong nhánh `Activate` ở Fragment |
| `resetGestureState()` (không gọi `stopEffect`/`triggerEffect`) | `gestureStateMachine.reset()` — cũng không gọi, `reset()` khác `clear()` đúng ở điểm này |

### 5.2 — `computeRecordingSize`

Hàm thuần, không trạng thái, tách thẳng ra `object` hoặc top-level `fun` trong file riêng
(`ui/camera/RecordingSizeCalculator.kt` hoặc tương tự), giữ nguyên 100% logic đang có — chỉ
di chuyển vị trí, không đổi hành vi.

**Đã làm (2026-09-30).** Chọn **top-level `fun`** (không `object`) trong `ui/camera/RecordingSizeCalculator.kt`:
hàm không state thì `object` chỉ thêm một tầng tên gọi vô ích. Cùng package nên **call site ở dòng
`computeRecordingSize(binding.overlay.width, binding.overlay.height)` không đổi một ký tự**, cũng không
cần thêm import — diff sạch đúng một khối bị cắt và một file mới.

Thân hàm copy nguyên xi, chỉ đổi default `720` thành hằng có tên `DEFAULT_TARGET_SHORT_SIDE` (private,
cùng file). KDoc ghi lại hai điều bản gốc không nói mà đọc code mới thấy: `it - it % 2` là vì encoder
H.264 cần chiều chia hết cho 2, và hai nhánh trả về sớm (`<= 0`, cạnh ngắn đã nhỏ hơn mục tiêu) trả
nguyên xi **không** làm chẵn — giữ đúng bản gốc, không "sửa cho nhất quán".

`CameraRecordFragment`: 27.742 byte (trước Bước 5) → 27.347 (sau 5.1) → 26.749 (sau 5.2).

### 5.3 — `RecordingTimer` (tuỳ chọn — ĐÃ QUYẾT ĐỊNH BỎ, xem cuối mục)

Bọc `timerHandler`/`timerRunnable`/`recordStartUiTimeMs` thành 1 class nhỏ nhận callback
`onTick: (String) -> Unit` (đã format sẵn bằng `DateUtils.formatElapsedTime`). Giá trị thấp hơn
5.1/5.2 (ít logic hơn hẳn, không có nhánh dễ nhầm như debounce cử chỉ), **chỉ làm nếu còn
thời gian/muốn làm cho đủ**, có thể bỏ qua mà không ảnh hưởng gì tới các Bước sau.

⚠️ **Bước này động vào logic đang chạy thật** (không chỉ di chuyển chỗ ở như các Bước khác) —
`handleGesture()` là nơi state machine mới thay thế hoàn toàn logic cũ. So diff từng nhánh với
bản gốc cẩn thận như đã ghi ở 5.1 trước khi commit.

#### 5.3 — quyết định: bỏ qua (2026-09-30)

Không làm. Hai lý do cụ thể tìm được khi đọc code, ghi lại để sau này ai muốn làm thì biết trước sẽ
vướng gì:

1. **`timerHandler` đang làm hai việc.** Ngoài chạy tick (dòng ~543, ~547) nó còn là đường post về
   main thread từ thread ghi hình: `timerHandler.post { onLowStorageDuringRecording() }`. Tách
   `RecordingTimer` ra thì dòng này phải tạo `Handler` riêng hoặc đi qua timer — việc báo hết dung
   lượng chẳng liên quan gì timer.
2. **`recordStartUiTimeMs` có hai người dùng.** Ngoài hiện đồng hồ, nó còn dùng ở chốt thời lượng tối
   thiểu (`elapsed = elapsedRealtime - recordStartUiTimeMs` so với `MIN_RECORD_DURATION_MS`). Đưa vào
   `RecordingTimer` thì logic "có cho dừng ghi hay không" phải đi hỏi timer — thêm phụ thuộc mới vào
   đúng chỗ nhạy cảm, không tương xứng lợi ích.

📌 **Phát hiện riêng, chưa xử lý:** `recordStartUiTimeMs` bị **gán hai lần** — dòng ~512 (ngay trước
khi `VideoRecorder` start) và dòng ~541 trong `startRecordingTimerUI()` (callback `onFirstFrame`). Nên
chốt thời lượng tối thiểu dùng giá trị nào tuỳ `onFirstFrame` có kịp chạy chưa. Nhìn thì có vẻ cố ý
(dòng 512 là giá trị dự phòng nếu khung hình đầu không bao giờ tới) nhưng **chưa được xác nhận** —
đưa vào danh sách rà ở Bước 7.

### 5.4 — `CameraRecordViewModel` (phạm vi hẹp)

> **Quyết định bổ sung 2026-09-30, đảo ngược một phần mục 7.** Mục 7 chốt "không thêm ViewModel cho
> `CameraRecordFragment`". Quyết định mới: **có thêm, nhưng chỉ giữ phần thuần dữ liệu.** Lý do người
> dùng nêu và tôi xác nhận bằng code:
>
> 1. **Tính nhất quán.** Sau Bước 6 sẽ có 5/18 Fragment không có VM, thuộc **hai loại**:
>    *(a)* `WelcomeFragment`, `Onboarding1/2/3Fragment` — không có state gì, VM rỗng là boilerplate,
>    có quy tắc giải thích (mốc 6.6); *(b)* `CameraRecordFragment` — **có state nhiều nhất toàn app
>    mà vẫn không có VM**. Loại (b) là chỗ lệch thật, không quy tắc nào che được, người đọc code sau
>    này sẽ phải hỏi "sao màn này khác".
> 2. **Lợi ích đo được, không phải chỉ cho đẹp kiến trúc.** `statePcmMap` và `bgmPcm` đang được
>    **decode lại toàn bộ file WAV trên main thread mỗi lần view được tạo lại** (trong
>    `onViewCreated`). Vào màn chọn effect rồi Back là decode lại từ đầu, dù `currentEffect` không
>    đổi. Đưa chúng vào VM là hết hẳn việc này. **Lưu ý: lợi ích này chỉ đúng với `statePcmMap` và
>    `bgmPcm`** — `currentEffect` và `gestureStateMachine` đã không bị tạo lại theo view, việc gom
>    chúng vào VM là để nhất quán chỗ đặt state, không phải sửa lãng phí.

### Phạm vi — chỉ 4 thứ, đều thuần dữ liệu

| Chuyển vào VM | Kiểu | Vì sao được |
|---|---|---|
| `currentEffect` | `EffectDefinition?` | Chỉ là kết quả `EffectRepository.findByIdOrNull(args.effectId)`, gán **đúng một lần** ở `onCreate` và không bao giờ gán lại (grep xác nhận: 1 chỗ gán, 16 chỗ đọc) → thành `val` trong VM |
| `statePcmMap` | `Map<String, ShortArray>` | PCM đã decode, không giữ View/Context |
| `bgmPcm` | `ShortArray?` | như trên |
| `gestureStateMachine` | `GestureStateMachine` | Đã tách ở 5.1, thuần Kotlin. **Không** phải để sửa một lãng phí: nó là property thân class (`private val gestureStateMachine = GestureStateMachine()`) nên đã chỉ tạo một lần theo **Fragment instance**, không theo view — khác `statePcmMap`/`bgmPcm` vốn nằm trong `onViewCreated` nên thật sự bị decode lại. Đưa vào VM chỉ để gom chỗ đặt state cho nhất quán |

### KHÔNG chuyển — ở lại Fragment y nguyên

`VideoRecorder`, `BgmPlayer`, `SoundEffectPlayer`, `HandLandmarker`, `backgroundExecutor`,
`recordingFrameThread`, `OverlayView`, `binding`, `latestCameraBitmap`, `latestHandResult`,
`timerHandler`/`timerRunnable`/`recordStartUiTimeMs`, `permissionDeniedDialog`, toàn bộ loading overlay.

Lý do giữ nguyên là **bất biến đã ghi ở `AGENTS.md` mục 5**: "camera nằm lại back stack = instance
sống, view chết". Đưa tài nguyên gắn View vào VM sẽ phá bất biến đó và chỉ chuyển chỗ phức tạp.

### Repository mới

`loadWavPcm` cần `Context` (`utils/AudioUtils.kt`), nên theo quy ước 0.2 phải bọc thành Repository là
`class` nhận `Context`:

```kotlin
// ui/camera/EffectAudioRepository.kt
class EffectAudioRepository(context: Context) {
    private val appContext = context.applicationContext

    fun loadStatePcm(effect: EffectDefinition?): Map<String, ShortArray> =
        effect?.states.orEmpty().mapNotNull { state ->
            state.soundRes?.let { state.id to loadWavPcm(appContext, it) }
        }.toMap()

    fun loadBgmPcm(effect: EffectDefinition?): ShortArray? =
        effect?.bgm?.let { loadWavPcm(appContext, it.resId) }
}
```

### ⚠️ Điểm quyết định: KHÔNG đổi threading ở mốc này

Hiện `statePcmMap`/`bgmPcm` decode **đồng bộ trên main thread** trong `onViewCreated`, nên chúng
**luôn sẵn sàng** trước khi người dùng có thể bấm Record. Nếu mốc này đẩy luôn sang
`viewModelScope + Dispatchers.IO`, sẽ sinh ra cửa sổ thời gian mà `statePcmMap` còn rỗng — bấm Record
hoặc giơ cử chỉ trong lúc đó là **mất tiếng hiệu ứng**, đúng loại bug D1 trong `Test_Checklist.md`.

Vì vậy mốc 5.4 **giữ nguyên decode đồng bộ**, chỉ làm trong `init` của VM (tức chạy một lần theo vòng
đời VM thay vì mỗi vòng đời view). Việc chuyển sang `Dispatchers.IO` + state `isAudioReady` để chặn
nút Record là **mốc 5.5 riêng**, chỉ làm nếu muốn, và phải nối vào loading overlay đã có
(`CameraLoading_Fallback_Plan.md`) thay vì tự dựng cơ chế chờ thứ hai.

### Danh sách commit

| # | Nội dung |
|---|---|
| 5.4a | `feat: add EffectAudioRepository (wav pcm decoding)` — chỉ thêm file, chưa nối |
| 5.4b | `refactor: add CameraRecordViewModel (effect + pcm + gesture state), wire CameraRecordFragment` |

### Khung VM

```kotlin
class CameraRecordViewModel(
    audioRepository: EffectAudioRepository,
    effectId: String
) : ViewModel() {
    val currentEffect: EffectDefinition? = effectId.takeIf { it.isNotEmpty() }
        ?.let { EffectRepository.findByIdOrNull(it) }

    // Decode đồng bộ, một lần theo vòng đời VM — xem ghi chú threading ở trên.
    val statePcmMap: Map<String, ShortArray> = audioRepository.loadStatePcm(currentEffect)
    val bgmPcm: ShortArray? = audioRepository.loadBgmPcm(currentEffect)

    val gestureStateMachine = GestureStateMachine()

    companion object {
        fun factory(context: Context, effectId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CameraRecordViewModel(EffectAudioRepository(context.applicationContext), effectId)
            }
        }
    }
}
```

**Không** có `uiState`/`events` ở mốc này: mọi state hiển thị của màn camera (ẩn/hiện top bar khi ghi,
đồng hồ, loading overlay, enable nút) đều gắn trực tiếp View và đã được xử lý đúng ở Fragment. Thêm
`StateFlow` cho chúng là mốc khác, và phải soát lại vấn đề "render không idempotent" đã ghi ở 3.4.

### Việc cụ thể ở Fragment

- Bỏ `onCreate` (chỉ còn việc gán `currentEffect`, giờ VM làm).
- Bỏ 4 field: `currentEffect`, `statePcmMap` (`lateinit`), `bgmPcm`, `gestureStateMachine`.
- 16 chỗ đọc `currentEffect` → `viewModel.currentEffect`. Cân nhắc một `private val currentEffect get() = viewModel.currentEffect`
  để diff nhỏ lại và không phải sửa 16 chỗ — **quyết định lúc code**, nhưng nếu làm thì phải ghi comment
  rõ đây là alias đọc, không phải field.
- `soundEffectPlayer` vẫn khởi tạo ở `onViewCreated` nhưng đọc `viewModel.currentEffect`.
- `bgmPlayer` vẫn khởi tạo ở `onViewCreated` (gắn Context), chỉ `bgmPcm` lấy từ VM.
- `resetGestureState()` → `viewModel.gestureStateMachine.reset()`, gọi đúng chỗ cũ.

### Rủi ro và cách soát

| Rủi ro | Cách soát |
|---|---|
| `statePcmMap` không còn decode lại → nếu effect đổi mà VM không đổi thì sai PCM | Không xảy ra: mọi lối vào camera với effect khác đều `popUpToInclusive` chính `cameraRecordFragment` (`nav_graph.xml` dòng ~162) → Fragment mới → VM mới. **Phải đọc lại `nav_graph.xml` xác nhận lúc code**, không tin dòng này |
| `gestureStateMachine` đổi vòng đời | Trước: theo Fragment instance (đã sống qua view recreation rồi). Sau: theo VM — **chỉ khác đúng một ca**, khi Activity bị tạo lại (ví dụ đổi font scale) thì Fragment instance mới nhưng VM cũ, nên state machine sống sót thay vì được tạo mới. Vô hại vì `resetGestureState()` vẫn được gọi ở `onViewCreated` trong mọi trường hợp — test H14, H15 của `Test_Checklist.md` bắt đúng ca này |
| `ShortArray` lớn sống lâu hơn trước (theo VM chứ không theo view) | Đó là mục đích. Nhưng phải xác nhận heap không tăng tích luỹ: test H7/H16 với Profiler |
| VM giữ `EffectAudioRepository` → giữ `applicationContext` | Đúng quy ước 0.2, không leak Activity |

### Đã làm 5.4 (2026-09-30)

- **5.4a** `EffectAudioRepository` — chỉ thêm file. KDoc ghi to lý do cố ý đồng bộ (không `suspend`).
- **5.4b** `CameraRecordViewModel` + nối Fragment.

Điểm thực thi đáng ghi: thay vì sửa 16 chỗ đọc `currentEffect`, Fragment dùng **3 alias chỉ đọc**:

```kotlin
private val currentEffect: EffectDefinition? get() = viewModel.currentEffect
private val statePcmMap: Map<String, ShortArray> get() = viewModel.statePcmMap
private val gestureStateMachine: GestureStateMachine get() = viewModel.gestureStateMachine
```

Nhờ đó diff của 5.4b chỉ còn đúng phần **đổi nguồn dữ liệu** (bỏ field, bỏ `onCreate`, bỏ decode),
không lẫn 16 dòng đổi tên biến. Có comment ngay trên khối nói rõ đây là alias đọc, `get()` chứ không
phải field, không gán được — để không ai tưởng state vẫn ở Fragment.

`bgmPcm` **không** làm alias vì chỉ dùng đúng 1 chỗ (`audioMixer.setBgm`), đọc thẳng
`viewModel.bgmPcm` rõ hơn.

`BgmPlayer` vẫn khởi tạo ở `onViewCreated` (gắn Context, phải chết cùng view); chỉ PCM lấy từ VM.
Hai import `EffectRepository` và `loadWavPcm` thành mồ côi ở Fragment, đã xoá.

---
---

## 8. Bước 6 — Splash, Survey×2, Language, Settings, Permission, Welcome, Onboarding×3

### Quy tắc chọn có-VM hay không (nhắc lại, áp dụng nghiêm ở Bước này)

Màn có state, timer, dữ liệu hay sự kiện một lần → có VM. Màn chỉ hiện nội dung tĩnh và bấm sang
màn kế → **không** tạo VM rỗng, chỉ đảm bảo Fragment đó tuân thủ `Fragment_Review_Checklist.md`
như bình thường. Ghi quy tắc này vào `AGENTS.md` ở Bước 7 để người viết Fragment tĩnh mới sau
này không tự hỏi "có cần VM không".

### Danh sách commit

| # | Nội dung | Có VM? |
|---|---|---|
| 6.1 | `refactor: SplashViewModel, wire SplashFragment` + fix `ObjectAnimator` not being cancelled | Có |
| 6.2 | `feat: SurveyRepository + SurveyViewModel (nav-graph scoped), wire Survey1/2Fragment` | Có |
| 6.3 | `refactor: LanguageRepository + LanguageViewModel, wire LanguageFragment` | Có |
| 6.4 | `feat: SettingsViewModel (events to open Store/Share/Feedback/URL), wire SettingsFragment` | Có |
| 6.5 | `refactor: PermissionViewModel, wire PermissionFragment` + fix callback not guarding against a null binding | Có |
| 6.6 | `docs: record the "static screens need no ViewModel" rule in AGENTS.md` | Không — chỉ tài liệu, không sửa `WelcomeFragment`/`Onboarding1-3Fragment` |

### 6.1 — `SplashFragment`

```kotlin
class SplashViewModel : ViewModel() {
    private val _ready = Channel<Unit>(Channel.CONFLATED)
    val ready = _ready.receiveAsFlow()

    init {
        viewModelScope.launch {
            delay(SPLASH_DELAY_MS)
            _ready.send(Unit)
        }
    }
    companion object { const val SPLASH_DELAY_MS = 5000L }
}
```

Đổi từ `view.postDelayed(...)` (gắn với View, mất khi `onDestroyView`) sang `viewModelScope.delay`
(gắn với VM, sống qua việc view bị tạo lại) — khác biệt hành vi thật cần lưu ý: nếu Splash
Fragment bị tạo lại view giữa chừng (ví dụ đổi cấu hình dù đã khoá portrait — hiếm nhưng có thể
xảy ra khi đổi font scale hệ thống), bản cũ **reset lại đếm từ đầu** (timer gắn View), bản mới
**tiếp tục đếm** (timer gắn VM) — đây là cải thiện đúng hướng, ghi rõ vào commit message vì là
thay đổi hành vi có chủ đích, không phải tác dụng phụ ngoài ý muốn.

Sửa kèm (cùng commit, vì cùng file, cùng mối quan tâm "vòng đời Splash"): `ObjectAnimator` trong
`setupLoadingBar()` không bị `cancel()` ở `onDestroyView` — thêm field giữ tham chiếu, huỷ đúng
chỗ:

```kotlin
private var loadingBarAnimator: ObjectAnimator? = null
// setupLoadingBar(): loadingBarAnimator = ObjectAnimator.ofInt(...).apply { ...; start() }
// onDestroyView(): loadingBarAnimator?.cancel(); loadingBarAnimator = null
```

### 6.2 — `Survey1Fragment` + `Survey2Fragment`

**Điểm cần quyết định trước khi code** (đã nêu ở lượt thảo luận trước, giờ chốt phương án cụ
thể để có thể code thẳng): lưu câu trả lời khảo sát vào `SharedPreferences` qua
`SurveyRepository` mới, cùng khuôn với `FavouriteManager`/`VideoRepository` sau khi đổi ở Bước
2/4 (class nhận Context). Hai câu trả lời hiện tại **chưa dùng vào việc gì** ở phần còn lại của
app — Bước này chỉ đảm bảo chúng **không bị mất**, không thêm màn hình thống kê hay cá nhân hoá
gì dựa trên đó (ngoài phạm vi).

```kotlin
class SurveyRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("survey_answers", Context.MODE_PRIVATE)
    fun saveAnswer1(index: Int) = prefs.edit { putInt("answer_1", index) }
    fun saveAnswer2(index: Int) = prefs.edit { putInt("answer_2", index) }
}
```

VM dùng chung giữa 2 Fragment qua `navGraphViewModels(R.id.nav_graph)` (hoặc scope hẹp hơn nếu
muốn, nhưng dùng chung `nav_graph` là hợp lý vì `survey1Fragment`/`survey2Fragment` luôn đi liền
nhau và cùng bị `popUpTo` gỡ cùng lúc — xem `nav_graph.xml`, action tới `permissionFragment` đều
`popUpTo="@id/survey1Fragment"`):

```kotlin
class SurveyViewModel(private val repository: SurveyRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    fun selectAnswer1(index: Int) { _uiState.value = _uiState.value.copy(answer1Index = index) }
    fun selectAnswer2(index: Int) { _uiState.value = _uiState.value.copy(answer2Index = index) }
    fun submit() { repository.saveAnswer1(_uiState.value.answer1Index); repository.saveAnswer2(_uiState.value.answer2Index) }
}

data class SurveyUiState(val answer1Index: Int = 0, val answer2Index: Int = 0)
```

`submit()` gọi ở đúng 2 chỗ hiện có nút điều hướng ra khỏi cụm Survey (`textSurveySkip` ở
Survey1, `btnSurveyFinish` ở Survey2) — Skip ở Survey1 bỏ qua Survey2 nên chỉ có `answer1Index`,
Repository nhận `Int?` cho `answer2` hoặc để giá trị mặc định, quyết định lúc code không ảnh
hưởng kiến trúc chung.

### 6.3 — `LanguageFragment`

```kotlin
class LanguageRepository {
    fun currentTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    fun setLanguage(tag: String) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
}

class LanguageViewModel(private val repository: LanguageRepository) : ViewModel() {
    private val _selectedTag = MutableStateFlow(if (repository.currentTag().startsWith("vi")) "vi" else "en")
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    fun select(tag: String) {
        repository.setLanguage(tag)
        _selectedTag.value = tag
    }
}
```

`LanguageRepository` không cần Context (`AppCompatDelegate` là API tĩnh) — khác 2.1/4.1, ghi rõ
trong code là cố ý, không phải quên.

⚠️ **Lưu ý duy nhất của màn này:** `AppCompatDelegate.setApplicationLocales()` làm **Activity bị
tạo lại** (đã xác nhận ở lượt thảo luận trước — đây là trường hợp thật duy nhất trong app mà VM
có tác dụng ngay). `LanguageViewModel` sống qua việc Activity tạo lại **chỉ khi** nó được giữ
đúng scope Fragment bình thường (`by viewModels()`) — Activity tạo lại kéo theo Fragment tạo lại
hoàn toàn (không phải "instance sống, view chết" như back stack), nên VM **cũng bị tạo lại** ở
đây, không sống sót qua được. Nghĩa là: nếu muốn UI không "giật" khi đổi ngôn ngữ (ví dụ đang có
animation), phải xử lý ở tầng khác (`android:configChanges` hoặc tương tự) — **ngoài phạm vi Bước
này**, chỉ ghi lại để không ai lầm tưởng VM đã giải quyết vấn đề "Activity tạo lại".

### 6.4 — `SettingsFragment`

5 mục chưa có logic (Đánh giá, Chia sẻ, Góp ý, Về ứng dụng, Chính sách). VM chỉ định nghĩa sự
kiện, **không** tự implement intent thật cho 5 mục đó ở Bước này (nối logic thật là việc khác,
ngoài phạm vi "chuyển MVVM") — chỉ chuẩn bị sẵn chỗ để khi nối logic thật, chỉ sửa VM:

```kotlin
sealed interface SettingsEvent {
    data object OpenPlayStore : SettingsEvent
    data object ShareApp : SettingsEvent
    data object SendFeedback : SettingsEvent
    data object OpenAboutApp : SettingsEvent
    data object OpenPrivacyPolicy : SettingsEvent
}

class SettingsViewModel : ViewModel() {
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    fun onRateUsClicked() = _events.trySend(SettingsEvent.OpenPlayStore)
    fun onShareAppClicked() = _events.trySend(SettingsEvent.ShareApp)
    fun onFeedbackClicked() = _events.trySend(SettingsEvent.SendFeedback)
    fun onAboutAppClicked() = _events.trySend(SettingsEvent.OpenAboutApp)
    fun onPrivacyPolicyClicked() = _events.trySend(SettingsEvent.OpenPrivacyPolicy)
}
```

`SettingsFragment` gắn `setOnClickListener` cho 5 hàng còn lại gọi đúng hàm VM tương ứng, và
`collect events` với `when` mà mỗi nhánh tạm thời để trống hoặc `TODO()` có comment rõ — mục
Ngôn ngữ (đã có logic) **không đổi**, vẫn `findNavController().navigate(...)` thẳng như cũ (điều
hướng nội bộ app, không cần qua VM theo đúng quy ước 0.2).

### 6.5 — `PermissionFragment`

```kotlin
data class PermissionUiState(
    val cameraGranted: Boolean = false,
    val notificationGranted: Boolean = true, // mặc định true trên < API 33, xem notificationPermissionApplicable
    val notificationApplicable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
)

class PermissionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    fun refresh(cameraGranted: Boolean, notificationGranted: Boolean) {
        _uiState.value = _uiState.value.copy(cameraGranted = cameraGranted, notificationGranted = notificationGranted)
    }
}
```

VM ở đây **mỏng có chủ đích** — việc đọc `ContextCompat.checkSelfPermission` vẫn phải ở Fragment
(cần Context, và đây đúng loại "trạng thái hệ thống" nên đọc trực tiếp mỗi `onResume` như bản
gốc, không cache sai trong VM). Giá trị của VM ở đây chủ yếu là chuẩn hoá state thành 1 nguồn,
dọn đường cho phần dialog dùng chung với Camera (đã nhắc ở lượt thảo luận trước, vẫn để **tuỳ
chọn**, không bắt buộc trong Bước 6).

Bug thật cần sửa (**commit riêng sau khi wire VM xong**, đã nêu ở lượt thảo luận trước): callback
`requestCameraPermission`/`requestNotificationPermission` gọi thẳng `binding.switchCamera.isChecked
= granted` không chốt `_binding`. Sau khi chuyển sang collect qua `repeatOnLifecycle(STARTED)`,
trường hợp callback đến muộn khi view đã huỷ **tự động an toàn** (collector dừng khi rời
`STARTED`) — nhưng bản thân dòng code trong `registerForActivityResult` callback **vẫn** chạy
độc lập với `repeatOnLifecycle` (nó không phải một collector, nó là callback hệ thống), nên vẫn
cần chốt cửa tường minh:

```kotlin
private val requestCameraPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    _binding ?: return@registerForActivityResult
    viewModel.refresh(cameraGranted = granted, notificationGranted = viewModel.uiState.value.notificationGranted)
    if (!granted) handleDenial(Manifest.permission.CAMERA)
}
```

### 6.6 — Ghi quy tắc vào `AGENTS.md`

Chỉ thêm 1 đoạn ngắn vào `AGENTS.md` mục 5 (Quy ước & bẫy đã biết):

> Màn hình có state/timer/dữ liệu/sự kiện một lần → có `ViewModel` (`ui/<màn>/`<Man>ViewModel.kt`,
> factory viết tay, không DI — xem `MVVM_Migration_Plan.md` mục 0.2). Màn chỉ điều hướng thuần
> (Welcome, Onboarding1-3) → **không** tạo VM rỗng.

`WelcomeFragment`, `Onboarding1-3Fragment` **không đổi một dòng nào** trong Bước này.

---

## 9. Bước 7 — Cập nhật tài liệu

Làm sau khi Bước 1-6 đã xong và ổn định (không bắt buộc chờ hết cả 6 bước nếu muốn cập nhật dần
— nhưng ít nhất phải cập nhật lại lần cuối sau Bước 6 để không có tài liệu nào lỡ dở).

### 9.1 — `AGENTS.md`

- Cập nhật dòng "Cập nhật lần cuối tại commit ...".
- Mục 2 (Stack): thêm `Lifecycle (ViewModel + runtime-ktx) 2.11.0` vào danh sách thư viện.
- Mục 4 (Cấu trúc thư mục): mỗi package `ui/<màn>/` giờ có thêm `<Man>ViewModel.kt` (+
  `<Man>UiState.kt` nếu tách file) — cập nhật cây thư mục cho khớp.
- Mục 5 (Quy ước & bẫy đã biết): thêm đoạn đã soạn ở 6.6, cộng thêm ghi chú ngắn: "Repository
  (`VideoRepository`, `FavouriteManager`, `VideoFileRepository`, `LanguageRepository`,
  `SurveyRepository`) là `class` nhận `Context` ở constructor (trừ `LanguageRepository` không
  cần), không phải `object` — đổi từ Bước 2/4/6 của `MVVM_Migration_Plan.md`."
- ~~Nhân tiện: mục 2 ghi `Java 11` nhưng `build.gradle.kts` khai `VERSION_17`~~ — **Đã sửa xong** ở
  `AGENTS.md` và `README.md` trong đợt cập nhật docs sau `abfb32a` (lúc ghi dòng này chưa commit): nay ghi Java 17. Không còn việc thừa này trong Bước 7,
  chỉ cần giữ nguyên khi cập nhật mục 2.

### 9.2 — `Code_Walkthrough.md`

Đọc lại toàn bộ mục nói về từng Fragment đã đổi (mục 1, 3 theo cấu trúc hiện tại — xác nhận số
mục chính xác lúc làm vì file có thể đã đổi số mục giữa lúc viết kế hoạch này và lúc thực thi),
thêm đoạn giải thích luồng dữ liệu mới (Fragment → ViewModel.uiState/events → Fragment) cho từng
màn đã chuyển. Không xoá mô tả cũ về phần vẫn giữ nguyên trong Fragment (ExoPlayer, dialog, việc
di chuyển View).

### 9.3 — `Fragment_Review_Checklist.md`

Thêm mục mới (đã làm sớm ở Bước 1.2, giờ rà lại cho khớp với những gì thực sự đã làm ở Bước
2-6):

```markdown
## 9. Riêng cho Fragment có ViewModel

Checklist mục 0-8 vẫn áp dụng nguyên vẹn cho phần View/tài nguyên còn lại trong Fragment
(ExoPlayer, dialog, binding). Thêm các câu hỏi sau:

- [ ] VM không giữ `Context` của Activity, không giữ View, không giữ `NavController`
- [ ] Repository VM phụ thuộc là `class` (không `object`) nếu cần thay thế được
- [ ] `uiState` là `StateFlow`, sự kiện một lần là `Channel` (không dùng `SharedFlow` cho sự
      kiện một lần — dễ phát lại nhầm khi Fragment tạo lại view)
- [ ] Fragment thu `uiState`/`events` qua `viewLifecycleOwner.lifecycleScope` +
      `repeatOnLifecycle(STARTED)`, không thu trực tiếp trong `viewModelScope`
- [ ] Điều hướng vẫn chốt cửa `currentDestination?.id` **trong Fragment**, sau khi nhận sự kiện
      từ VM — không chuyển chốt cửa vào VM
- [ ] Nếu VM cần sống qua process death (không chỉ qua việc view bị huỷ tạo lại trong back
      stack), dùng `SavedStateHandle`, không phải field thường
- [ ] Factory dùng `viewModelFactory { initializer { ... } }`, không viết
      `ViewModelProvider.Factory` thủ công trừ khi có lý do đặc biệt
```

### 9.4 — `HandAr_Plan.md` mục K2

Sửa dòng:

```
- **`ViewModel`**: sau khi khoá `portrait` (H1), lý do cấp thiết duy nhất còn lại là process
  death. Chưa làm.
```

thành (giữ nguyên phần lịch sử, thêm cập nhật):

```
- **`ViewModel`**: sau khi khoá `portrait` (H1), lý do cấp thiết duy nhất còn lại là process
  death. **Đã làm** — xem `MVVM_Migration_Plan.md`, hoàn thành tại commit `<điền commit cuối
  Bước 6>`. Lý do đổi quyết định: dọn đường cho tính năng Bộ sưu tập (dùng chung dữ liệu yêu
  thích) và các thao tác video mở rộng, không phải vì process death — process death vẫn hiếm
  gặp thật với app này (không có màn nào giữ state phức tạp cần khôi phục ngoài
  `VideoPlayerFragment.playbackPosition`, đã xử lý qua `SavedStateHandle`).
```

### 9.5 — `README.md`

Nếu README có mục mô tả kiến trúc/stack, thêm 1 dòng nhắc tới ViewModel — chỉ cập nhật nếu
README hiện có mục đó, không thêm mục mới nếu README đang cố tình gọn.

### 9.6 — `Test_Checklist.md` (thêm mới 2026-09-30)

Đã rà toàn bộ `Test_Checklist.md` (395 dòng, mục A–I). **Checklist phủ rất kỹ camera, ghi hình, cử chỉ
và điều hướng, nhưng gần như KHÔNG phủ các màn mà kế hoạch này vừa chuyển MVVM.** Kiểm chứng bằng grep,
không suy diễn:

| Từ khoá | Số lần xuất hiện |
|---|---|
| `rename` / "đổi tên" | 0 / 1 (chỉ nhắc thoáng) |
| "xoá video" | 0 |
| "danh sách video" / `videoList` | 0 / 0 |
| `fullscreen` | 0 |
| "chia sẻ" | 0 |
| "yêu thích" / `favourite` | 0 / 0 |
| "tìm kiếm" / `search` | 0 / 0 |
| "ngôn ngữ" / `Language` | 0 / 0 |
| `Survey` | 0 (chỉ "khảo sát" trong ca A1/G1 như một bước đi qua) |
| `process death` / "Don't keep activities" | 0 / 0 |

Nghĩa là: 7 màn đã có ViewModel (VideoList, VideoPlayer, RecordedPreview, Share, EffectList,
EffectPreview, EffectPicker) chỉ được test **gián tiếp** qua các ca điều hướng mục G, còn chức năng
riêng của chúng thì chưa có ca nào. Bước 7 phải thêm 4 mục mới.

⚠️ **Khi viết thật vào `Test_Checklist.md`, đổi tên mục thành `N`, `O`, `P`, `Q`** (không phải
J/K/L/M như nháp dưới đây): checklist hiện đã dùng tới mục **I**, nên J–M sẽ trùng nếu sau này có
người chèn tiếp. Mã ca cũng đổi theo (`J1` → `N1`, `K1` → `O1`, `L1` → `P1`, `M1` → `Q1`). Giữ nguyên
J/K/L/M trong tài liệu kế hoạch này để không phải sửa hai chỗ, chỉ đổi lúc chép sang checklist.

**Mục J — Danh sách video & trình phát** (chưa có gì trong checklist)

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| J1 | Quay 3 video → vào danh sách | Đủ 3 item, thumbnail đúng, sắp theo mới nhất trước |
| J2 | Vào danh sách → mở 1 video → Back ra | **Không** quét lại thư mục (lợi ích VM ở Bước 2) — dễ thấy nhất khi có nhiều video: không thấy vòng loading lần 2 |
| J3 | Mở video → xoá → về danh sách | Item đã xoá **biến mất ngay** (cờ `KEY_VIDEO_LIST_STALE`) |
| J4 | Mở video → đổi tên thành công → về danh sách → bấm vào video vừa đổi tên | **Phát được** (đây đúng bug đã sập ở Bước 2, xem mục 4) |
| J5 | Đổi tên trùng tên file khác | Toast "đã tồn tại", **ở lại màn** player |
| J6 | Đổi tên để trống | Im lặng, ở lại màn, không Toast |
| J7 | Đổi tên y như cũ | Im lặng, ở lại màn (ca kế hoạch từng định gộp sai, xem mục 5.1 của Bước 3) |
| J8 | Phát giữa video → nhấn Home → mở lại app | Seek đúng vị trí cũ (`SavedStateHandle`) |
| J9 | Danh sách trống (chưa quay video nào) | Hiện đúng empty state, không crash |

**Mục K — Xem lại sau khi quay & chia sẻ** (chưa có gì)

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| K1 | Quay xong → màn xem lại → Back → chọn **Exit** | Video bị xoá, thoát màn; **không** phát thêm một nhịp trước khi thoát (cờ `discarding`, xem 3.2) |
| K2 | Màn xem lại → Back → chọn **Save** | Sang màn chia sẻ |
| K3 | Màn xem lại → nút Save trực tiếp | Sang màn chia sẻ |
| K4 | Màn chia sẻ: expand → collapse → nút back fullscreen | Video dời đúng giữa 2 container, không mất tiếng/không reset vị trí phát |
| K5 | Màn chia sẻ đang fullscreen → nhấn Home → mở lại app | Vẫn ở fullscreen (VM giữ `isFullscreen`, xem 3.4) |
| K6 | Back hệ thống ở fullscreen / ở card | Fullscreen → thu nhỏ; card → về danh sách hiệu ứng |
| K7 | Nút "Try again" (chỉ hiện khi vào từ màn xem lại) | Sang camera đúng effect |
| K8 | 4 nút mạng xã hội, với app đích **chưa cài** | Không crash, có thông báo hợp lý |

**Mục L — Danh sách hiệu ứng, yêu thích, tìm kiếm** (chưa có gì)

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| L1 | Bấm tim vài effect → kill app → mở lại | Vẫn còn yêu thích (`FavouriteManager` sau khi đổi thành class, `PREFS_NAME` không đổi nên **dữ liệu cũ phải đọc được**) |
| L2 | Gõ tìm kiếm → vào màn xem trước → Back ra | Ô tìm kiếm **và** danh sách khớp nhau (xem 4.2) |
| L3 | Bấm tim trong lúc đang lọc tìm kiếm | Icon đổi đúng item, danh sách lọc không bị reset |
| L4 | Màn chọn effect: chọn qua lại nhiều item | Viền cyan chuyển đúng, **không nháy cả lưới** (`PAYLOAD_SELECTION` giữ được sau 4.4) |
| L5 | Màn chọn: vào camera chưa có effect → mở picker | Nút tick mờ, bấm không ăn |

**Mục M — Riêng cho MVVM: state sống qua cái gì** (chưa có gì — đây là mục quan trọng nhất sau refactor)

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| M1 | Bật **Developer options → Don't keep activities**, rồi chạy lại toàn bộ mục J, K, L | Không crash, state khôi phục đúng ở những chỗ dùng `SavedStateHandle` (J8) |
| M2 | Ở màn player đang phát → `adb shell am kill com.example.handar` (mô phỏng process death) → mở lại từ recents | Quay lại đúng màn, `playbackPosition` khôi phục |
| M3 | Đổi **font scale** hệ thống trong lúc đang ở từng màn có VM | View tạo lại nhưng state giữ nguyên; riêng màn chia sẻ giữ đúng fullscreen (K5) |
| M4 | Đổi **ngôn ngữ** ở màn Language | Activity tạo lại — đây là ca **VM KHÔNG sống sót**, đã ghi rõ ở mốc 6.3; kỳ vọng là UI đúng sau khi tạo lại, không phải "state giữ nguyên" |
| M5 | Vào/ra từng màn có VM 5 lần, Profiler → Force GC | Heap về xấp xỉ ban đầu — VM sống lâu hơn view nên nếu có leak thì mục này bắt |
| M6 | Camera: vào màn chọn effect rồi Back, **lặp 5 lần**, đo thời gian từ lúc Back tới lúc preview lên | Sau Bước 5.4 phải **nhanh hơn rõ rệt** so với trước (không decode lại WAV). Nếu không thấy khác biệt thì 5.4 chưa đạt mục đích |

Ngoài ra bổ sung vào **mục H** (vòng đời) 1 ca cho Bước 5.4:

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| H18 | Camera → Effect → Back, lặp 5 lần, rồi giơ cử chỉ có tiếng và quay 1 clip | Tiếng đúng, video có tiếng đúng thời điểm — xác nhận `statePcmMap` dùng lại từ VM vẫn đúng dữ liệu, không bị rỗng/lệch |

📌 Người dùng đã chốt: **test toàn bộ sau khi refactor xong**, nên các ca trên viết vào checklist ở
Bước 7 rồi chạy một lượt, không chạy rải rác từng Bước.

---

## 10. Việc cố ý không làm trong kế hoạch này (ghi lại để khỏi bàn lại)

| Việc | Vì sao không làm |
|---|---|
| ViewModel cho `WelcomeFragment`/`Onboarding1-3Fragment` | Không có state — VM rỗng chỉ là boilerplate, xem quy tắc Bước 6 |
| Hilt/Koin | Không cần cho quy mô 17 Fragment, tăng thời gian build + dung lượng APK, đi ngược nỗ lực tối ưu size đang có |
| `ViewModel`/`RecordingSession` bọc `VideoRecorder` trong `CameraRecordFragment` | Đã cân nhắc lại ở Bước 5 — phụ thuộc quá sâu vào View/tài nguyên gắn Fragment, tách ra không giảm được độ phức tạp, chỉ chuyển chỗ |
| Compose | Ngoài phạm vi, dự án đang View/XML ổn định |
| Sửa `recording/`, `effect/*`, `OverlayView.kt` | Ràng buộc cứng của dự án (`AGENTS.md` mục 5), không liên quan MVVM |
| Viết test (unit test, integration test, test tự động) | Ngoài phạm vi kế hoạch này theo yêu cầu — kế hoạch chỉ refactor sang MVVM, không thêm test source set hay test case nào |
