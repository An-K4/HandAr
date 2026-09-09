# Kịch bản Test Thủ công — HandAr (sau refactor Phase 0-5 + Phase A-B)

> Test trên ít nhất 2 máy: 1 máy khá mới (đại diện hiệu năng tốt) + 1 máy cũ/yếu (đại diện giới hạn phần cứng).
> Ghi lại kết quả từng mục — mục nào FAIL thì note rõ hiện tượng để quay lại phase tương ứng.
>
> **Mục A-F**: kiểm tra pipeline ghi hình (có từ Phase 0-5, không được phép hỏng sau mỗi lần refactor).
> **Mục G-H**: thêm từ Phase B — điều hướng giữa màn hình và vòng đời/leak của Fragment. Đây là lớp bug mới xuất hiện kể từ khi app có nhiều màn; chúng **không** biểu hiện ở lần chạy đầu tiên mà chỉ lộ ra sau nhiều lần vào/ra màn, nên phải test riêng.
>
> Chạy đầy đủ A-H sau mỗi phase từ Phase B trở đi.

---

## A. Quyền & khởi động app

| # | Bước | Kỳ vọng |
|---|---|---|
| A1 | Gỡ cài đặt app cũ, cài lại bản mới, mở app lần đầu | Chỉ hiện đúng 1 popup xin quyền **Camera**, **không còn** popup xin quyền Mic |
| A2 | Từ chối quyền Camera | App hiện Toast từ chối, không crash |
| A3 | Vào Settings hệ thống → cấp lại quyền Camera → mở lại app | App hoạt động bình thường, live preview hiện đúng |

## B. Live preview (chưa ghi hình)

| # | Bước | Kỳ vọng |
|---|---|---|
| B1 | Mở app, đưa tay vào khung hình, xòe tay | GIF con mèo cười hiện đúng vị trí lòng bàn tay, tiếng "happy" phát ra loa |
| B2 | Nắm tay lại | Đổi sang GIF chuối khóc, tiếng tương ứng phát ra loa |
| B3 | Đưa tay ra khỏi khung hình | GIF biến mất, không còn tiếng phát tiếp |
| B4 | Di chuyển tay lại gần/xa camera | Kích thước GIF co giãn theo đúng khoảng cách tay-camera |

## C. Ghi hình — luồng cơ bản

| # | Bước | Kỳ vọng |
|---|---|---|
| C1 | Bấm Record khi tay đang **không** có cử chỉ nào active | Bắt đầu ghi bình thường, icon đổi thành "stop" |
| C2 | Trong lúc ghi, đổi cử chỉ tay 2-3 lần | Mỗi lần đổi, hiệu ứng âm thanh + GIF live đều đúng như phần B |
| C3 | Bấm Stop | Nút Record **bị disable tạm thời**, sau 1-2s bật lại + Toast "Đã lưu video" hiện ra |
| C4 | Mở video vừa quay | Phát được bình thường, không lỗi file |

## D. Kịch bản đặc biệt — bug đã fix, cần test lại để chống regression

| # | Bug gốc | Cách test | Kỳ vọng sau fix |
|---|---|---|---|
| D1 | Trigger hiệu ứng bị mất nếu gesture xảy ra **trước** khi bấm Record | Xòe/nắm tay tạo hiệu ứng **trước**, đợi 1-2s rồi mới bấm Record | Video quay được phải có tiếng+GIF hiệu ứng đó ngay từ đầu, không cần đổi cử chỉ mới có |
| D2 | UI bị đơ khi bấm Stop với clip dài | Quay 1 clip ~60-90s, bấm Stop | Nút Record disable ngay lập tức, KHÔNG bị treo/đơ toàn màn hình trong lúc chờ lưu |
| D3 | Frame đầu video bị đơ/đứng hình | Quay clip bất kỳ, phát lại, tua về đúng 00:00 | Video chuyển động ngay từ khung đầu tiên, không có đoạn đứng hình/đơ ở đầu |
| D4 | Âm thanh hiệu ứng chèn đôi (do mic thu tiếng loa) | Bật loa ngoài to, quay 1 clip có đổi cử chỉ vài lần | Chỉ nghe đúng 1 lớp tiếng hiệu ứng, không có tiếng vọng/lệch nhịp thứ 2 |
| D5 | GIF chồng GIF (ghosting) do lỗi mirror-scale trong recordingFrameThread | Quay 1 clip ~20-30s, di chuyển tay qua lại nhiều vị trí | Video chỉ hiện đúng 1 lớp GIF tại đúng vị trí tay hiện tại, không có vệt bóng/ảnh cũ chồng lên |
| D6 | Camera trong video bị lật ngược (mất mirror) | So sánh video ghi được với live preview lúc quay (cùng 1 cử chỉ/góc tay) | Chiều camera trong video phải khớp với chiều đã thấy lúc live (mirror đúng) |

## E. Số liệu & hiệu năng (dùng `VideoStatsLogger`, chỉ chạy bản Debug)

| # | Bước | Kỳ vọng |
|---|---|---|
| E1 | Quay 3 clip: ~5s, ~20s, ~60s trên máy tốt | Resolution luôn `720×1400` (hoặc theo tỉ lệ máy đó), Bitrate quanh **~2.5-3 Mbps**, Avg FPS **≥ 23-24** ở cả 3 độ dài |
| E2 | Lặp lại E1 trên máy cũ/yếu | Resolution/Bitrate tương tự, Avg FPS có thể thấp hơn 25 — chấp nhận được nếu do giới hạn phần cứng thật (không kèm hiện tượng D5/D3) |
| E3 | So sánh dung lượng file giữa clip cùng độ dài trước và sau toàn bộ refactor | Dung lượng phải **giảm đáng kể** so với bản đầu tiên (baseline ban đầu: ~34MB cho 71s ở FullHD+/4Mbps) |

### Cách đọc Avg FPS khi nó thấp hơn kỳ vọng

Vòng lặp ghi hình nhắm 25fps, tức ngân sách **40ms/frame**:

```kotlin
val sleepMs = (intervalMs - elapsedMs).coerceAtLeast(0)
if (sleepMs > 0) Thread.sleep(sleepMs)
```

Khi `elapsedMs > 40`, `sleepMs = 0` — **vòng lặp đã chạy hết công suất, không nghỉ chút nào**. Nên Avg FPS thấp không có nghĩa "bị chặn ở đâu đó", mà là *công việc mỗi frame vượt quá 40ms trên máy đó*. Quy đổi: `elapsedMs ≈ 1000 / AvgFPS` (ví dụ 20.9fps → ~48ms/frame).

Công việc mỗi frame gồm: `lockHardwareCanvas` → vẽ bitmap camera đã scale → vẽ hiệu ứng → `unlockCanvasAndPost` → `drainVideoEncoder()` — tất cả **đồng bộ trong cùng thread**, đồng thời tranh CPU với MediaPipe (CPU delegate) ở thread phân tích.

**Trước khi đi tìm nguyên nhân, xác định xem có phải regression không** — nếu không, rất dễ mất buổi tối tối ưu một thứ vốn đã như vậy từ đầu:

```bash
git stash && git checkout 41fda0a     # mốc Phase A, trước khi tách Fragment
# cài, quay 1 clip cùng độ dài, CÙNG máy - CÙNG ánh sáng - CÙNG thao tác tay, đọc VideoStats
git checkout main && git stash pop
```

- Bản cũ cũng thấp tương đương → **không phải regression**, là trần phần cứng của máy đó. Ghi ⚠️ kèm model máy vào E2, đi tiếp.
- Bản cũ cao hơn rõ rệt → có regression, lúc đó mới đào: log trung bình `elapsedMs` mỗi 25 frame để tách "vẽ + encode chậm" khỏi "bị tranh CPU".

> **Số liệu tham chiếu đã đo** (2026-09-09, sau Phase C): `720×1560`, bitrate ~2.92 Mbps, 16.2MB/46.5s → Bitrate ✅ và E3 ✅; Avg FPS 20.9-22.3 ⚠️ (dưới ngưỡng 23-24, **chưa đối chiếu baseline**).

## F. Edge case & độ bền

| # | Bước | Kỳ vọng |
|---|---|---|
| F1 | Bấm Record → Stop → Record → Stop liên tục 5 lần nhanh | Không crash, không leak `MediaCodec` (app không bị chậm dần/out-of-memory sau nhiều lần) |
| F2 | Bấm Stop ngay lập tức sau khi vừa bấm Record (chưa có frame nào kịp vẽ) | Không crash, file vẫn được tạo (có thể rất ngắn/gần như trống), `effectClock` không lỗi khi chưa từng `start()` |
| F3 | Xoay điện thoại ngang trong lúc đang quay (nếu app hỗ trợ xoay màn hình) | Không crash — nếu app khóa hướng dọc thì bỏ qua case này |
| F4 | Quay khi pin yếu / máy đang nóng (throttling) | FPS có thể giảm tạm thời nhưng không crash, video vẫn phát được bình thường |
| F5 | Kiểm tra dung lượng bộ nhớ trong (`Environment.DIRECTORY_MOVIES` của app) sau nhiều lần quay | File cũ không bị ghi đè/mất, tên file luôn unique (dựa theo timestamp) |

## G. Điều hướng giữa các màn (từ Phase B)

> Chuẩn bị: mở logcat lọc theo tag app, để sẵn cửa sổ nhìn được. Nhiều lỗi nhóm này chỉ hiện ra dưới dạng exception trong log chứ không làm app đóng ngay.

| # | Bước | Kỳ vọng |
|---|---|---|
| G1 | Mở app | Vào đúng màn **danh sách hiệu ứng** (start destination), không phải màn camera |
| G2 | Chọn 1 hiệu ứng → vào màn camera | Preview lên bình thường, hiệu ứng hiển thị đúng **hiệu ứng vừa chọn** (không phải hiệu ứng mặc định) |
| G3 | Bấm Back từ màn camera | Quay lại màn danh sách, **không** thoát app (nếu thoát app: thiếu `app:defaultNavHost="true"`) |
| G4 | Back tiếp ở màn danh sách | Thoát app bình thường, không crash |
| G5 | Vào camera → Back → vào lại → Back, **lặp 5 lần** | Cả 5 lần preview đều lên. Lần nào preview đen/không có frame → executor hoặc camera đã bị huỷ sai chỗ (xem `Fragment_Review_Checklist.md` mục 1) |
| G6 | Chọn qua lại nhiều hiệu ứng khác nhau rồi mới bấm Record | Video ghi ra dùng đúng hiệu ứng của lần chọn **cuối cùng**, cả GIF lẫn tiếng |
| G7 | Vào màn camera, **từ chối** quyền camera lần đầu | Hiện Toast từ chối, không crash, không đứng lại ở màn đen (tự quay về màn danh sách) |
| G7b | Back, vào camera lại | Hệ thống **vẫn hỏi quyền** lần thứ 2 |
| G7c | Từ chối lần 2, Back, vào camera lại lần nữa | **Không còn dialog nào** — đây là hành vi đúng của Android, không phải bug (xem ghi chú dưới). App phải hiện hướng dẫn mở Settings, tuyệt đối không im lặng hoặc kẹt ở màn đen |
| G7d | Vào Settings hệ thống cấp lại quyền Camera → quay lại app | Camera hoạt động bình thường (trùng ca A3) |

> **Về G7c — hành vi hệ thống, không phải lỗi app:** từ Android 11 (API 30), sau **2 lần từ chối**, hệ thống chuyển quyền sang trạng thái *permanently denied*: `launch()` vẫn chạy, callback vẫn trả kết quả "denied", nhưng **không dialog nào hiện ra**. Không có API nào bắt hệ thống hỏi lại được — chỉ người dùng tự cấp trong Settings. Vì vậy app bắt buộc phải phân biệt 3 trạng thái bằng `shouldShowRequestPermissionRationale()`:
>
> | `checkSelfPermission` | `shouldShowRationale` | Nghĩa | Xử lý |
> |---|---|---|---|
> | GRANTED | — | có quyền | chạy camera |
> | DENIED | `true` | còn hỏi lại được | giải thích lý do rồi `launch()` lại |
> | DENIED | `false` (sau khi đã hỏi) | từ chối vĩnh viễn | dialog + nút mở `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` |
>
> ⚠️ `shouldShowRationale` cũng trả `false` **trước khi hỏi lần nào** (app vừa cài). Nên chỉ dùng nó để kết luận "từ chối vĩnh viễn" khi đang ở **trong callback kết quả** (chắc chắn vừa hỏi xong), hoặc kèm một cờ "đã từng hỏi" lưu ở `SharedPreferences`.
>
> **Reset để test lại từ đầu:** `adb shell pm reset-permissions` (toàn máy) hoặc `adb shell pm revoke com.example.handar android.permission.CAMERA` (chỉ app này) — không cần gỡ cài đặt.

## H. Vòng đời Fragment & rò rỉ tài nguyên (từ Phase B)

> Nhóm này bắt các bug **không làm app crash ngay** — chúng tích tụ dần rồi mới nổ. Đọc kỹ cột "Dấu hiệu hỏng" vì kỳ vọng ở đây thường là "không có gì xảy ra".

| # | Bước | Kỳ vọng | Dấu hiệu hỏng |
|---|---|---|---|
| H1 | Đang quay video → bấm **Back** | Không crash. File mp4 vừa quay vẫn mở và phát được | Crash `NullPointerException` từ thread ghi hình = thread nền còn chạm View đã chết. File hỏng/0 byte = recorder không được `stop()` |
| H2 | Vào camera → Back → vào lại → **bấm Record ngay** | Ghi hình bình thường, có đủ frame | `RejectedExecutionException` trong log = executor bị shutdown nhưng không tạo lại |
| H3 | Vào camera → Back → về màn danh sách, quan sát **chấm/đèn chỉ báo camera** của hệ thống | Chỉ báo tắt trong vòng 1-2s | Còn sáng = camera chưa unbind (dùng `this` thay vì `viewLifecycleOwner` ở `bindToLifecycle`) |
| H4 | Vào camera → nhấn **Home** → mở lại app | Preview trở lại bình thường, hiệu ứng vẫn đúng | Preview đen hoặc mất hiệu ứng |
| H5 | Vào camera → **tắt màn hình** → bật lại → mở khoá | Như H4 | |
| H6 | Vào/ra màn camera 5 lần, sau đó đưa tay vào khung hình | Hiệu ứng phát **đúng 1 lần** cho mỗi cử chỉ | Nghe tiếng chồng nhau nhiều lớp = nhiều collector `HandLandmarkerProvider.results` cùng chạy (dùng `lifecycleScope` thay vì `viewLifecycleOwner.lifecycleScope`) |
| H7 | Vào/ra màn camera 10 lần, mở **Android Studio Profiler → Memory**, bấm "Force GC" rồi xem heap | Heap trở về xấp xỉ mức ban đầu | Heap tăng đều sau mỗi vòng = leak view/bitmap (thiếu dòng gán `null` trong `onDestroyView`) |
| H8 | Vào camera, Back **thật nhanh** ngay khi màn vừa hiện (chưa kịp thấy preview) | Không crash | NPE trong `startCamera` = callback của `ProcessCameraProvider` về sau khi View đã huỷ, thiếu `_binding ?: return@addListener` |
| H9 | Quay 1 clip dài (~60s) → bấm Stop → **Back ngay** trong lúc đang lưu | Không crash. Video vẫn được lưu đầy đủ, phát được | Crash trong callback `stop {}` = callback đụng `binding`/`requireContext()` sau khi detach |
| H10 | Đang quay → nhấn Home → đợi 10s → mở lại app | Không crash; hoặc video dừng sạch sẽ, hoặc tiếp tục quay bình thường — miễn là file không hỏng | |
| H11 | *(từ Phase C)* Quay xong → sang màn Preview → Back → chọn hiệu ứng → vào camera lại | Log `LC_Camera` hiện đủ `onAttach → onCreate → onCreateView`, tức fragment được tạo mới hoàn toàn | Thiếu `onAttach/onCreate` = camera vẫn nằm trong back stack → `popUpToInclusive` chưa đúng, camera không được giải phóng |
| H12 | *(từ Phase C)* Quay xong → sang màn Preview → ở lại đó 30s, quan sát chỉ báo camera của hệ thống + heap trong Profiler | Chỉ báo camera **tắt**; heap không còn giữ bitmap full-size của camera | Còn sáng / heap không giảm = camera fragment chưa bị pop, hoặc `latestCameraBitmap` chưa được null hoá |

> ⚠️ H11-H12 giả định action `cameraRecord → recordedPreview` khai `popUpTo="@id/cameraRecordFragment"` + `popUpToInclusive="true"` (phương án đã chốt). Nếu về sau đổi cách khai `popUpTo`, `CameraRecordFragment` sẽ nằm lại trong back stack — khi đó phải test thêm: state debounce (`lastStateId`, `pendingState`) có được reset khi quay lại không, và `latestCameraBitmap`/`latestHandResult` có bị giữ lì trong RAM không.

### Công cụ hỗ trợ cho mục H

- **Profiler (H7):** Android Studio → View → Tool Windows → Profiler → chọn tiến trình → tab Memory. Bấm biểu tượng thùng rác (Force GC) trước mỗi lần đọc số, nếu không số liệu sẽ nhiễu vì rác chưa được dọn.
- **LeakCanary (khuyến nghị bật từ Phase C):** thêm `debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")`. Không cần viết thêm dòng code nào — nó tự phát hiện Fragment/View bị leak và hiện notification kèm chuỗi tham chiếu chỉ thẳng ra field nào đang giữ. Thay thế được phần lớn công sức của H7.
- **Kiểm tra nhanh bằng adb:** `adb shell dumpsys meminfo com.example.handar` — so số `TOTAL PSS` trước và sau vòng lặp vào/ra 10 lần.
- **Log vòng đời:** tạm override `onCreate/onCreateView/onViewCreated/onDestroyView/onDestroy` với một dòng `Log.d("LC", ...)` để nhìn thấy thứ tự thật khi test G5, H4, H5. Nhớ gỡ trước khi commit.

---

## Cách ghi kết quả

Với mỗi dòng test, đánh dấu: ✅ Pass / ❌ Fail / ⚠️ Pass có lưu ý. Nếu Fail, ghi lại: model máy, số liệu `VideoStatsLogger` (nếu có), và mô tả hiện tượng — quay lại đúng phase liên quan trong `HandAr_Refactor_Plan.md` (mục A-F) hoặc `HandAr_App_Expansion_Plan.md` (mục G-H) để xử lý tiếp.

Riêng mục **G-H**, khi Fail hãy đối chiếu với `Fragment_Review_Checklist.md` trước khi sửa — mỗi kiểu hỏng ở hai mục này đều ứng với đúng một mục trong checklist đó.
