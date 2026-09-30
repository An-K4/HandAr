# Kế hoạch chuyển sang MVVM — HandAr

> **Cập nhật lần cuối tại commit `b5c2d0f`** (2026-09-29). **Note cho agent:** sau khi hoàn
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
- [ ] **Bước 2** — `VideoListFragment` (màn mẫu)
- [ ] **Bước 3** — `VideoPlayerFragment`, `ShareFragment`, `RecordedPreviewFragment`
- [ ] **Bước 4** — `EffectListFragment`, `EffectPreviewFragment`, `EffectPickerFragment` + `FavouriteManager`
- [ ] **Bước 5** — Tách `GestureStateMachine`, `RecordingSession` khỏi `CameraRecordFragment`
- [ ] **Bước 6** — Splash, Survey×2, Language, Settings, Permission, Welcome, Onboarding×3
- [ ] **Bước 7** — Cập nhật `AGENTS.md`, `Code_Walkthrough.md`, `Fragment_Review_Checklist.md`, `HandAr_Plan.md` (K2), `README.md`

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

Cân nhắc thật khi làm: `isFullscreen` chỉ dùng để đổi `visibility` 2 nhóm View và di chuyển
`binding.playerView` giữa 2 container — **việc di chuyển View vẫn phải ở Fragment** (VM không
cầm View). VM ở đây chỉ giữ cờ, Fragment đọc cờ để quyết định gọi `showFullscreen()`/`showCard()`
thật (hàm build UI) — nghĩa là có 2 tầng "showFullscreen": 1 ở VM (đổi state), 1 ở Fragment (thi
hành UI theo state). Nếu thấy tầng VM ở đây quá mỏng để đáng làm, **được phép bỏ qua `ShareFragment`
ở Bước 3** và ghi lại lý do trong Bước 7 (mục "cố ý không thêm VM") — quyết định cuối cùng để lúc
code thật, đây không phải màn có giá trị cao như 3.2/3.3.

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

---

## 7. Bước 5 — Tách phần thuần Kotlin khỏi `CameraRecordFragment`

**Đây là Bước rủi ro cao nhất trong toàn kế hoạch.** Không thêm `ViewModel` cho
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

### 5.2 — `computeRecordingSize`

Hàm thuần, không trạng thái, tách thẳng ra `object` hoặc top-level `fun` trong file riêng
(`ui/camera/RecordingSizeCalculator.kt` hoặc tương tự), giữ nguyên 100% logic đang có — chỉ
di chuyển vị trí, không đổi hành vi.

### 5.3 — `RecordingTimer` (tuỳ chọn)

Bọc `timerHandler`/`timerRunnable`/`recordStartUiTimeMs` thành 1 class nhỏ nhận callback
`onTick: (String) -> Unit` (đã format sẵn bằng `DateUtils.formatElapsedTime`). Giá trị thấp hơn
5.1/5.2 (ít logic hơn hẳn, không có nhánh dễ nhầm như debounce cử chỉ), **chỉ làm nếu còn
thời gian/muốn làm cho đủ**, có thể bỏ qua mà không ảnh hưởng gì tới các Bước sau.

⚠️ **Bước này động vào logic đang chạy thật** (không chỉ di chuyển chỗ ở như các Bước khác) —
`handleGesture()` là nơi state machine mới thay thế hoàn toàn logic cũ. So diff từng nhánh với
bản gốc cẩn thận như đã ghi ở 5.1 trước khi commit.

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
