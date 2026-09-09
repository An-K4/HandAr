# Checklist Tự Soát Fragment — HandAr

> Dùng mỗi khi thêm một Fragment mới (Phase C: `RecordedPreviewFragment`, Phase D: `VideoListFragment`, Phase E: `VideoPlayerFragment`) hoặc sửa một Fragment đã có.
> Rút ra từ quá trình review Phase B — mọi lỗi gặp trong phase đó đều bị bắt bởi đúng một trong các mục dưới đây.

---

## 0. Nền tảng: Fragment có HAI vòng đời

Đây là gốc rễ của gần như mọi bug Fragment. Nhớ đúng một điều:

```
Fragment instance:  onAttach → onCreate → ... ... ... ... ... → onDestroy → onDetach
View của Fragment:           onCreateView → onViewCreated → onDestroyView
                                  ↑_________________________________↓
                                  có thể lặp lại NHIỀU LẦN
```

**Back stack giữ lại fragment mà bạn điều hướng *rời khỏi*, không phải fragment bị back ra.** Đây là chỗ rất dễ nhầm — đã kiểm chứng bằng log thật trên máy:

```
List --navigate--> Camera
   List:   onPause → onStop → onDestroyView            ← instance SỐNG, KHÔNG có onDestroy
   Camera: onAttach → onCreate → onCreateView → ...    ← tạo mới hoàn toàn

Camera --Back--> List
   Camera: onDestroyView → onDestroy → onDetach        ← huỷ HẲN
   List:   onCreateView → onViewCreated → onStart → onResume   ← KHÔNG có onAttach/onCreate
```

Vậy trạng thái "view chết mà instance sống" xảy ra với màn bạn **đi khỏi**, và khi quay lại thì `onCreateView` chạy lại trên **cùng một instance** — mọi field không phải View vẫn giữ nguyên giá trị cũ.

**`popUpTo` quyết định fragment nào sống sót.** Ở Phase C, action `cameraRecord → recordedPreview` khai `app:popUpTo="@id/cameraRecordFragment"` + `app:popUpToInclusive="true"`, nghĩa là camera bị gỡ khỏi stack **ngay lúc navigate** → nó chạy đủ `onDestroyView → onDestroy → onDetach`, camera phần cứng được giải phóng ngay, và không có fragment nào của app nằm lại trong back stack ở trạng thái "instance sống, view chết" ngoài `EffectListFragment`.

Nói cách khác: **cùng một đoạn code, lifecycle khác nhau tuỳ cách khai `popUpTo` trong nav graph.** Trước khi suy luận về vòng đời của một màn, luôn xem nav graph trước.

Hệ quả trực tiếp:
- Field trỏ vào View mà không null hoá ở `onDestroyView` → **leak cả cây view**. Đúng trong **mọi** trường hợp, vì `onDestroyView` luôn chạy và thread nền/callback có thể sống lâu hơn View.
- Tài nguyên tạo một lần ở constructor/`onCreate` mà huỷ ở `onDestroyView` → **lần quay lại dùng phải đồ đã chết**. Chỉ phát tác khi instance sống sót, tức khi fragment nằm trong back stack (xem sơ đồ trên).
- Dữ liệu nặng để trong field không phải View (bitmap, kết quả detect) **không** được giải phóng khi view chết → nằm lì trong RAM suốt thời gian fragment ở back stack. Nên null hoá ở `onDestroyView` luôn.
- State logic (cờ debounce, id trạng thái đang active) **không** bị reset khi quay lại → hành vi lần thứ hai có thể khác lần đầu. Cân nhắc reset ở `onViewCreated`.

---

## 1. Bảng cấp phát ↔ giải phóng

Liệt kê **mọi** tài nguyên trong Fragment. Mỗi dòng phải điền đủ 2 cột, và 2 cột phải **đối xứng**.

| Tài nguyên | Tạo ở đâu | Huỷ ở đâu |
|---|---|---|
| `_binding` | `onCreateView` | `onDestroyView` (`= null`) |
| Field trỏ View (vd `overlayView`) | `onViewCreated` | `onDestroyView` (`= null`) |
| `ExecutorService` | `onViewCreated` | `onDestroyView` (`shutdown()`) |
| `SoundPool` / `MediaPlayer` | `onViewCreated` | `onDestroyView` (`release()`) |
| `ExoPlayer` (Phase E) | `onViewCreated` | `onDestroyView` (`release()`) |
| Thread nền | khi bắt đầu việc | `onDestroyView` (dừng + `join`) |
| Singleton dùng chung (`HandLandmarkerProvider`) | lazy trong provider | `MainActivity.onDestroy` — **cố ý** không theo Fragment |

**Cách soát nhanh:** đếm số field trỏ vào View → `onDestroyView` phải có **đúng bấy nhiêu** dòng gán `null`.

**Lỗi thật đã gặp ở Phase B:**
- `private val backgroundExecutor = Executors.newSingleThreadExecutor()` (tạo ở constructor) + `shutdown()` ở `onDestroyView` → lần vào thứ hai: `RejectedExecutionException`, camera không có frame nào.
- Có `_binding = null` nhưng quên `overlayView = null` → leak toàn bộ view tree kèm bitmap/GIF đã decode.

---

## 2. Ba câu hỏi cho mỗi khối code

Với mỗi hàm / lambda / callback, hỏi đúng 3 câu:

**a) Ai gọi nó? b) Trên thread nào? c) Ở giai đoạn nào của vòng đời?**

| Nếu câu trả lời là... | Thì bắt buộc |
|---|---|
| Thread nền (recording loop, `setAnalyzer`) | **Không được** chạm `binding`. Capture view ra biến local **trước** khi tạo thread |
| Callback đến muộn (`future.addListener`, `stop {}`, coroutine, network) | Chốt cửa ngay dòng đầu: `val b = _binding ?: return@...`, `val ctx = context ?: return@...` |
| Chạy trước `onCreateView` (`onCreate`, property initializer) | **Không được** chạm `binding`, `viewLifecycleOwner`; không gọi `requireContext()` ở property initializer |
| Main thread, trong khoảng `onViewCreated` → `onDestroyView` | Dùng `binding` / `requireContext()` thoải mái |

**Lỗi thật đã gặp ở Phase B:**
- `checkAndRequestPermission()` đặt trong `onCreate`: khi quyền đã cấp thì chạy thẳng `startCamera()` → `binding` còn null → NPE. Lần chạy **đầu tiên** không lộ (vì phải chờ dialog), chỉ crash từ lần thứ hai.
- `cameraProviderFuture.addListener { binding.preview... }` → back nhanh trong lúc chờ future → NPE.
- Thread ghi hình gọi `binding.overlay.drawHandEffects(...)` → back giữa lúc đang quay → NPE.

---

## 3. Bảng đối chiếu Activity → Fragment

| Trong Activity | Trong Fragment |
|---|---|
| `onCreate` | `onCreateView` (inflate) + `onViewCreated` (mọi thứ đụng View) |
| `onDestroy` | `onDestroyView` |
| `this` (làm Context) | `requireContext()` |
| `this` (làm LifecycleOwner) | **`viewLifecycleOwner`** |
| `lifecycleScope` | **`viewLifecycleOwner.lifecycleScope`** |
| `bindToLifecycle(this, ...)` | `bindToLifecycle(viewLifecycleOwner, ...)` |
| `findViewById(R.id.x)` | `binding.x` (main thread, trong khoảng sống của View) |
| `intent.extras` | `requireArguments()` |

Dùng `lifecycleScope` / `this` thay vì `viewLifecycleOwner`: coroutine hoặc camera **không bị huỷ** khi rời màn → chạm View đã chết; camera không unbind → lần sau không mở được; vào/ra 3 lần thì có 3 collector chạy song song.

---

## 4. `requireXxx()` hay bản nullable?

Cùng một giá trị, khác nhau ở cách phản ứng khi vắng mặt:

| | `context` (`Context?`) | `requireContext()` (`Context`) |
|---|---|---|
| Khi chưa attach / đã detach | trả `null` | ném `IllegalStateException` kèm tên Fragment |
| Triết lý | "vắng mặt là bình thường" | "vắng mặt là **bug**, nổ ngay tại chỗ" |

**Câu hỏi để chọn:** *nếu context không có ở đây, đó là tình huống hợp lệ hay bug của tôi?*

- `onViewCreated`, click listener, hàm gọi từ main thread → **`requireContext()`** (fail fast, thông báo rõ ràng hơn NPE)
- Callback đến muộn → **`context ?: return`** (detach là chuyện bình thường, người dùng có quyền back)

Cùng họ: `requireArguments()`, `requireView()`, `requireActivity()`.

**Cạm bẫy Context:** `requireContext()` trả về Context của **Activity**. Đưa nó cho object sống lâu hơn Fragment (singleton, cache tĩnh, WorkManager) = leak cả Activity. Object sống dài → `applicationContext` (xem `HandLandmarkerProvider` gọi `context.applicationContext` bên trong).

---

## 5. Nullable là lời hứa cần kiểm chứng

Mỗi `!!`, `lateinit`, `?.let` là một lời hứa. Với mỗi cái, truy ngược xem lời hứa có đúng trong **mọi** đường đi tới không.

- `binding` = `_binding!!` → hứa "View còn sống"
- `lateinit var currentEffect` → hứa "tôi đã gán ở đâu đó"; **mọi `lateinit` phải có đúng một dòng gán và bạn phải chỉ ra được nó nằm ở đâu**
- `?.let { }` trên giá trị đáng lẽ phải khác null → **nuốt lỗi im lặng**, nguy hiểm hơn crash

**Lỗi thật đã gặp ở Phase B:**
- `EffectRepository.findById(...)` gọi mà quên gán vào `currentEffect` → compiler không báo (bỏ giá trị trả về là hợp lệ về cú pháp) → `UninitializedPropertyAccessException` lúc chạy.
- `_binding?.let { previewView = it.preview }` đặt trong `onCreate` → `_binding` null → khối lệnh bị bỏ qua **không một tiếng động** → crash ở dòng ngay sau.

---

## 6. Dừng việc trước khi cắt tham chiếu

Thứ tự trong `onDestroyView` có ý nghĩa. Mẫu chuẩn (đang dùng ở `CameraRecordFragment`):

```
1. Làm cho điều kiện vòng lặp thành sai   (videoRecorder = null)
2. Chờ thread thoát thật                  (recordingFrameThread?.join(500))
3. Đóng tài nguyên nặng                   (recorder?.stop() — KHÔNG truyền callback đụng UI)
4. Shutdown executor, release sound player
5. Null hoá MỌI tham chiếu View           (cuối cùng)
```

`join(n)` chỉ chờ tối đa `n` ms rồi đi tiếp **bất kể** thread đã dừng hay chưa — nên bước 1 phải có, không thì `join` vô nghĩa. Bước 5 phải là bước cuối, khi chắc chắn không còn ai đọc View.

---

## 7. Riêng cho refactor: đừng đọc, hãy diff

Khi **di chuyển** code (Activity → Fragment, tách class), lỗi nguy hiểm nhất không phải lỗi logic mà là **lỗi chép sai** — chúng đọc lên vẫn hợp lý nên không thể phát hiện bằng đọc hiểu.

Ba lỗi thật ở Phase B, cả ba đều build xanh và chạy được:
- `offsetY = (recH - w * s) / 2f` — đúng ra là `h * s` → khung hình video lệch dọc (regression thẳng vào mục D)
- `if (w <= 0 && h <= 0)` — đúng ra là `||` → mất guard chia cho 0 (ca F2)
- icon record/stop bị đảo hai nhánh

Cách bắt: xuất bản gốc ra file rồi so máy móc từng dòng.

```bash
git show HEAD:app/src/main/java/com/example/handar/MainActivity.kt > MainActivity_old.kt.txt
```

Android Studio: chọn 2 file → chuột phải → **Compare Files**.

Và: **commit ở mỗi mốc chạy được**, để luôn có bản gốc sạch mà đối chiếu.

---

## 8. Soát nhanh trước khi commit

```
[ ] Mọi field trỏ View đều được null hoá ở onDestroyView (đếm cho khớp)
[ ] Tài nguyên: nơi tạo và nơi huỷ đối xứng
[ ] Không có binding nào bị chạm từ thread nền
[ ] Mọi callback bất đồng bộ đều chốt cửa: _binding ?: return / context ?: return
[ ] Dùng viewLifecycleOwner, không dùng this / lifecycleScope
[ ] Mọi lateinit đều chỉ ra được dòng gán
[ ] onCreate không chạm View
[ ] Nếu là refactor: đã diff với bản gốc, không có dòng nào lệch ngoài ý muốn
[ ] ./gradlew.bat :app:assembleDebug :app:lintDebug xanh
```
