# Kịch bản Test Thủ công — HandAr (sau refactor Phase 0-5 + Phase A-B)

> Test trên ít nhất 2 máy: 1 máy khá mới (đại diện hiệu năng tốt) + 1 máy cũ/yếu (đại diện giới hạn phần cứng).
> Ghi lại kết quả từng mục — mục nào FAIL thì note rõ hiện tượng để quay lại phase tương ứng.
>
> **Mục A-F**: kiểm tra pipeline ghi hình (có từ Phase 0-5, không được phép hỏng sau mỗi lần refactor).
> **Mục G-H**: thêm từ Phase B — điều hướng giữa màn hình và vòng đời/leak của Fragment. Đây là lớp bug mới xuất hiện kể từ khi app có nhiều màn; chúng **không** biểu hiện ở lần chạy đầu tiên mà chỉ lộ ra sau nhiều lần vào/ra màn, nên phải test riêng.
> **Mục I**: thêm từ đợt mở rộng `Gestures` (1 tay + 2 tay) — xem chi tiết công thức/lý do tại `Camera_X_Hand_Landmarker.md` Mục 11. Chạy mục này sau bất kỳ lần nào sửa `GestureRecognizer.kt`/`GestureUtils.kt`/`EffectRepository.kt`.
> **Mục J-L**: thêm từ Phase M (hiệu ứng procedural/canvas, `SizeSource`/`AnchorSource`/`handedness`, và race condition `AnimatedGifVisual` khi ghi hình). Chạy sau bất kỳ lần nào sửa `HandFrame.kt`, `EffectScope.kt`, `ProceduralVisual.kt`, `OverlayView.kt` (phần `setResult`/`drawFrame`), hoặc thêm effect procedural/anchor-size mới.
> **Mục M**: Tia sét & Dragon Ball — 2 chỗ rủi ro suy luận (góc xoay tia, ngưỡng cổ tay chụm) chưa từng chạy trên máy thật.
>
> Chạy đầy đủ A-H sau mỗi phase từ Phase B trở đi. Chạy Mục I sau mỗi lần thêm/sửa cử chỉ. Chạy Mục J-L sau mỗi lần đụng tới hạ tầng Phase M nói trên.

---

## A. Quyền & khởi động app

| # | Bước | Kỳ vọng |
|---|---|---|
| A1 | Gỡ cài đặt app cũ, cài lại bản mới, mở app → đi hết luồng splash (~5s) → chào mừng → onboarding 1–3 → khảo sát 1–2 tới **màn xin quyền** | Trước màn xin quyền **không** hiện popup quyền nào. Ở màn xin quyền, switch Camera đang tắt. Bật switch **Camera** → hiện đúng 1 popup Camera; Android 13+ bật switch Thông báo → hiện popup Thông báo riêng. **Không** có popup xin quyền Mic ở bất kỳ bước nào |
| A1b | **Chạy trên máy Android < 13** (hoặc emulator API 32), vào màn xin quyền | Card "Allow notification" **không hiện** (bị ẩn cả dòng, không phải chỉ tắt switch). Card Camera nằm ngay trên nút bắt đầu, khoảng cách cân đối như khi có 2 card — không bị tụt sát nút hay hở một mảng trống |
| A2 | Ở màn xin quyền: bật switch Camera rồi **từ chối** popup (1 lần) | Switch tự trả về tắt, **không** có dialog nào, không crash. Bấm lại switch → popup hiện lại bình thường |
| A2b | Từ chối tiếp cho tới khi Android chuyển sang *permanently denied* (Android 11+: từ chối 2 lần, hoặc tick "Don't ask again") | Hiện `PermissionDeniedDialog`: tiêu đề "Cần cấp quyền" (nội dung trung tính, dùng chung cho màn xin quyền lẫn màn camera), 2 nút **Đóng** / **Cài đặt** (đúng bố cục `dialog_confirm.xml`, có font Noto Serif, không phải dialog trắng mặc định của hệ thống). Bấm ra ngoài dialog **không** đóng được |
| A2c | Ở dialog A2b bấm **Đóng** | Dialog đóng, vẫn ở màn xin quyền, switch tắt, không điều hướng đi đâu |
| A2d | Ở dialog A2b bấm **Cài đặt** | Mở đúng màn App info của Magic Hand (không phải màn Settings tổng). Bật quyền Camera rồi back về app → switch Camera **tự bật** mà không cần thao tác gì thêm |
| A2e | Lặp A2b–A2d với quyền **Thông báo** trên Android 13+ | Dialog hiện đúng nội dung về Thông báo (không phải nội dung Máy ảnh); nút Cài đặt cũng mở App info; quay lại thì switch Thông báo tự cập nhật |
| A3 | Vào Settings hệ thống → cấp lại quyền Camera → mở lại app | App hoạt động bình thường, live preview hiện đúng |

## B. Live preview (chưa ghi hình)

| # | Bước | Kỳ vọng |
|---|---|---|
| B1 | Mở app, chọn effect `fire_ball`, đưa tay vào khung hình, nắm tay (✊) | GIF lửa nhỏ hiện đúng vị trí lòng bàn tay, tiếng lửa cháy phát ra loa |
| B2 | Xoè tay (✋) | Đổi sang hoạt ảnh bùng lửa to (burst rồi chuyển loop), tiếng bùng lửa phát ra loa |
| B3 | Đưa tay ra khỏi khung hình | GIF biến mất, không còn tiếng phát tiếp |
| B4 | Di chuyển tay lại gần/xa camera | Kích thước GIF co giãn theo đúng khoảng cách tay-camera |

## C. Ghi hình — luồng cơ bản

| # | Bước | Kỳ vọng |
|---|---|---|
| C1 | Bấm Record khi tay đang **không** có cử chỉ nào active | Bắt đầu ghi bình thường, icon đổi thành "stop" |
| C2 | Trong lúc ghi, đổi cử chỉ tay 2-3 lần | Mỗi lần đổi, hiệu ứng âm thanh + GIF live đều đúng như phần B |
| C3 | Bấm Stop | Nút Record **bị disable tạm thời**, sau 1-2s bật lại + Toast "Đã lưu video" hiện ra |
| C4 | Mở video vừa quay | Phát được bình thường, không lỗi file |
| C5 | *(UI màn quay mới)* Bấm Record | Ngay lập tức top bar (back + tên effect) và 2 nút Effect/Action biến mất; chỉ còn nút stop **đứng nguyên vị trí** nút record (không dịch ngang/dọc). Sau frame đầu tiên, đồng hồ hiện ngay trên nút stop (chữ trắng, nền đen mờ) và nút stop vẫn không nhúc nhích |
| C6 | Bấm stop trong vòng < 1s sau khi bấm Record | Toast "không thể dừng ngay…", vẫn tiếp tục quay, UI vẫn ở trạng thái ẩn, đồng hồ vẫn chạy |
| C7 | Bấm stop bình thường, nhìn màn hình trong 1–2s chờ lưu | **Không** thấy top bar hay nút Effect/Action hiện lại (không nháy UI) trước khi sang màn xem lại |
| C8 | Chưa quay: bấm nút back trên top bar | Quay về màn trước, giống back hệ thống (G3) |
| C9 | Đang quay: bấm back hệ thống | Dừng + lưu, sang màn xem lại kèm Toast "Đã lưu video" (không pop thẳng, không mất video) — trùng H1 |
| C10 | Kiểm tra bố cục trên ít nhất 2 cỡ màn (ví dụ ~360dp và ≥ 411dp) + font scale lớn | Tên effect luôn căn giữa; nút Effect/Action cách mép 16dp; nút record ở giữa; tâm nút viền trùng tâm nút record; nhãn không bị cắt (nhãn dài quá thì hiện "…") |
| C11 | Effect có thumbnail lỗi / không có (đổi tạm `thumbnailRes = 0`) | Nút Effect hiện nền đen trơn có viền trắng, không crash |

## D. Kịch bản đặc biệt — bug đã fix, cần test lại để chống regression

| # | Bug gốc | Cách test | Kỳ vọng sau fix |
|---|---|---|---|
| D1 | Trigger hiệu ứng bị mất nếu gesture xảy ra **trước** khi bấm Record | Xòe/nắm tay tạo hiệu ứng **trước**, đợi 1-2s rồi mới bấm Record | Video quay được phải có tiếng+GIF hiệu ứng đó ngay từ đầu, không cần đổi cử chỉ mới có |
| D2 | UI bị đơ khi bấm Stop với clip dài | Quay 1 clip ~60-90s, bấm Stop | Nút Record disable ngay lập tức, KHÔNG bị treo/đơ toàn màn hình trong lúc chờ lưu |
| D3 | Frame đầu video bị đơ/đứng hình | Quay clip bất kỳ, phát lại, tua về đúng 00:00 | Video chuyển động ngay từ khung đầu tiên, không có đoạn đứng hình/đơ ở đầu |
| D4 | Âm thanh hiệu ứng chèn đôi (do mic thu tiếng loa) | Bật loa ngoài to, quay 1 clip có đổi cử chỉ vài lần | Chỉ nghe đúng 1 lớp tiếng hiệu ứng, không có tiếng vọng/lệch nhịp thứ 2 |
| D5 | GIF chồng GIF (ghosting) do lỗi mirror-scale trong recordingFrameThread | Quay 1 clip ~20-30s, di chuyển tay qua lại nhiều vị trí | Video chỉ hiện đúng 1 lớp GIF tại đúng vị trí tay hiện tại, không có vệt bóng/ảnh cũ chồng lên |
| D6 | Camera trong video bị lật ngược (mất mirror) | So sánh video ghi được với live preview lúc quay (cùng 1 cử chỉ/góc tay) | Chiều camera trong video phải khớp với chiều đã thấy lúc live (mirror đúng) |
| D7 | ANR khi mở camera / đổi effect — `HandLandmarker.createFromOptions()` (bên trong `HandLandmarkerProvider.getOrCreate()`) chạy đồng bộ trên main thread, GPU delegate build EGL context/shader mất tới vài giây → treo UI thread | Từ `effectListFragment`, bấm liên tiếp vào **nhiều effect khác nhau xen kẽ 1 tay/2 tay** (ví dụ `fire_ball` → `black_hole` → `earth` → `gojo`) thật nhanh, mỗi lần đều vào tới màn camera | Không bao giờ hiện dialog "App không phản hồi" (ANR), preview camera hiện gần như ngay lập tức mỗi lần; hiệu ứng AR có thể xuất hiện chậm hơn preview đúng 1 nhịp (do model đang build nền) nhưng không giật/đơ UI trong lúc đó |
| D8 | Race khi thoát màn camera ngay lúc `HandLandmarker` đang được tạo nền | Mở 1 effect, bấm Back **thật nhanh** (trong lúc AR chưa kịp hiện lên) — lặp lại vài lần liên tiếp với các effect khác nhau | Không crash, không log lỗi liên quan `HandLandmarker`/coroutine; quay lại màn trước bình thường |
| D9 | Loading overlay che khoảng chờ tạo `HandLandmarker` | Mở camera bình thường vài lần liên tiếp, để ý ngay khung hình đầu tiên; trong lúc loading thử bấm vào vùng nút Effect/Record/Action/Back (in-app) | Overlay đen hiện **ngay lập tức, không nhấp nháy** kể cả khi setup xong gần như tức thời (giữ tối thiểu 500ms); che kín cả top bar lẫn bottom bar nên không bấm được gì trong lúc đó; Back **hệ thống** (nút/gesture) vẫn thoát bình thường vì xử lý ở `OnBackPressedCallback`, không phụ thuộc thứ tự vẽ view |
| D10 | Fallback `Delegate.GPU` → `Delegate.CPU` khi GPU init quá 5s hoặc máy RAM thấp (`HandLandmarkerProvider.createWithFallback`) | Không cần máy yếu: đã bắt được fallback thật trên máy test (log `HandLandmarkerProvider W GPU init qua 5s, chuyen ve CPU cho phan con lai cua phien`, 29/09/2026) — kiểm tra: sau dòng log này, đổi sang effect khác trong cùng phiên | Các lần tạo `HandLandmarker` sau đó phải dùng thẳng CPU (không có thêm log warning GPU timeout nào nữa, không bị chờ thêm 5s) — do cờ `forcedCpuForSession` nhớ cho cả phiên app. Nhãn hiển thị trong `DelegatePerfLogger` (nếu có gắn lại để đo) sẽ KHÔNG tự đổi theo delegate thực tế (xem `AGENTS.md` mục 5) |

## E. Số liệu & hiệu năng (dùng `VideoStatsLogger`/`logRecordingStats`, chỉ chạy bản Debug — **hiện không còn gắn sẵn trong code**, phải gắn lại lời gọi trong `stopRecordingAndGoToPreview()` trước khi test mục này, xem `AGENTS.md` mục 5)

| # | Bước | Kỳ vọng |
|---|---|---|
| E1 | Quay 3 clip: ~5s, ~20s, ~60s trên máy tốt | Resolution theo tỉ lệ **toàn màn hình** của máy đó (short side 720; ví dụ máy 1080×2340 → `720×1560`), Bitrate quanh **~2.5-3 Mbps**, Avg FPS **≥ 23-24** ở cả 3 độ dài |
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

> **Số liệu tham chiếu đã đo** (2026-09-09, sau Phase C) — Bitrate ✅, E3 ✅, FPS ⚠️ ở biên nhưng **không phải regression**:
>
> | Bản | Resolution | Avg FPS (2 clip) | Bitrate |
> |---|---|---|---|
> | `main` (Phase C) | 720×1560 | 23.97 / 22.74 → **23.4** | ~2.92 Mbps |
> | `41fda0a` (Phase A) | 720×1400 | 22.96 / 24.04 → **23.5** | ~2.64 Mbps |
>
> Chênh 0.1 fps, nằm trong dao động nội bộ mỗi nhóm (~1.1 fps) → **Phase B/C không làm chậm pipeline**. Đáng chú ý: `main` giữ nguyên FPS trong khi mỗi frame vẽ nhiều hơn 11% điểm ảnh.
>
> Hai clip đo trước đó cho 20.9 / 22.3 fps là clip **dài hơn** (46.5s và 55.8s, quay liên tục) → nhiều khả năng do throttling nhiệt. Khi so sánh hiệu năng, luôn để máy nguội giữa các lần đo.
>
> ⚠️ **Mẹo quan trọng khi so hai bản:** đừng tin thứ tự mình nhớ, hãy tìm một trường trong log dùng làm **nhãn phân biệt tự động**. Ở đây `Resolution` làm việc đó: 720×1400 = bản còn insets padding (Phase A), 720×1560 = bản edge-to-edge (từ Phase B).
>
> **Ghi nhận thay đổi hành vi:** bỏ `setOnApplyWindowInsetsListener` ở Phase B khiến `OverlayView` tràn hết màn hình, kéo theo `computeRecordingSize()` cho ra khung cao hơn (2100 → 2340px thật, tức +240px = chiều cao thanh trạng thái + thanh điều hướng). Video ghi ra vì vậy khớp với preview tràn viền, đổi lại file nặng thêm ~11%. Đây là hệ quả ngoài dự kiến của một thay đổi tưởng chỉ thuộc về giao diện — nếu sau này muốn quay đúng khung "an toàn", phải chỉnh `computeRecordingSize` chứ không phải chỉnh insets.
>
> Nếu muốn FPS cao hơn ngưỡng, đòn bẩy nằm ở **khối lượng vẽ mỗi frame** (hạ độ phân giải ghi, giảm chi phí vẽ hiệu ứng, hoặc `Delegate.GPU` cho MediaPipe) — không nằm ở kiến trúc màn hình.

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
| G1 | Mở app (cold start), đi hết luồng: splash (~5s, tự chuyển) → chào mừng → onboarding 1–3 → khảo sát 1–2 → xin quyền → bấm nút bắt đầu | Bắt đầu ở **splash** (start destination), kết thúc ở màn **danh sách hiệu ứng** (không phải màn camera). Ở danh sách bấm Back thì thoát app, không quay lại xin quyền/chào mừng/khảo sát (mỗi bước `popUpTo` inclusive). Hiện chưa có cờ "đã xem onboarding" nên **mỗi lần** mở app đều chạy lại luồng này |
| G2 | Chọn 1 hiệu ứng → sang màn xem trước → bấm **Create** → vào màn camera | Preview lên bình thường, hiệu ứng hiển thị đúng **hiệu ứng vừa chọn** (không phải hiệu ứng mặc định) |
| G3 | Bấm Back từ màn camera | Quay lại màn danh sách, **không** thoát app (nếu thoát app: thiếu `app:defaultNavHost="true"`) |
| G4 | Back tiếp ở màn danh sách | Thoát app bình thường, không crash |
| G5 | Vào camera → Back → vào lại → Back, **lặp 5 lần** | Cả 5 lần preview đều lên. Lần nào preview đen/không có frame → executor hoặc camera đã bị huỷ sai chỗ (xem `Fragment_Review_Checklist.md` mục 1) |
| G6 | Chọn qua lại nhiều hiệu ứng khác nhau rồi mới bấm Record | Video ghi ra dùng đúng hiệu ứng của lần chọn **cuối cùng**, cả GIF lẫn tiếng |
| G7 | Vào màn camera, **từ chối** quyền camera lần đầu | Hiện Toast từ chối, không crash, không đứng lại ở màn đen (tự quay về màn danh sách) |
| G7b | Back, vào camera lại | Hệ thống **vẫn hỏi quyền** lần thứ 2 |
| G7c | Từ chối lần 2, Back, vào camera lại lần nữa | Hệ thống **không** hiện popup quyền nữa (hành vi đúng của Android, không phải bug — xem ghi chú dưới), nhưng app phải hiện `PermissionDeniedDialog` 2 nút **Thoát** / **Cài đặt**. Tuyệt đối không im lặng, không kẹt ở màn đen |
| G7c1 | Ở dialog G7c bấm **Thoát** | Về màn trước (danh sách hiệu ứng / Home), không kẹt lại màn camera đen |
| G7c2 | Ở dialog G7c bấm **Cài đặt**, KHÔNG bật quyền, bấm back về app | App tự thoát màn camera về màn trước (không đứng ở màn đen chờ mãi) |
| G7c3 | Ở dialog G7c bấm **Cài đặt**, bật quyền Camera, bấm back về app | Camera khởi động ngay tại màn đang đứng, không cần vào lại màn camera |
| G7c4 | Ở dialog G7c, xoay máy / bật recent apps rồi quay lại khi dialog đang mở | Không crash, không `WindowLeaked` trong logcat |
| G7d | Vào Settings hệ thống cấp lại quyền Camera → quay lại app | Camera hoạt động bình thường (trùng ca A3) |
| G8 | *(màn chọn effect)* Ở màn camera bấm nút **Effect** | Mở màn "Template": top bar riêng (back, tiêu đề có gạch chân, tick cyan) và lưới 2 cột; **top bar/bottom nav chung không hiện**; effect đang dùng có viền cyan, các item khác không viền |
| G9 | Ở màn chọn: chọn 1 effect **khác** → bấm tick | Sang **màn xem trước** đúng effect vừa chọn (tên trên top bar). Bấm **Create** → camera mới đúng effect đó (tên trên top bar, thumbnail nút Effect, hiệu ứng + tiếng đúng). Bấm Back ở camera mới thì về **màn danh sách**, không quay lại màn xem trước, màn chọn hay camera cũ |
| G10 | Ở màn chọn: không đổi gì → bấm tick | Quay về camera cũ giữ nguyên effect (không tạo lại camera mới) |
| G11 | Ở màn chọn: chọn effect khác rồi **huỷ** bằng nút back trên top bar; lặp lại bằng back hệ thống | Cả 2 cách đều về camera cũ với effect **cũ** (lựa chọn bị bỏ) |
| G12 | Ở màn chọn: bấm đúp thật nhanh nút tick, rồi lặp lại với nút back | Không crash, không pop luôn cả camera bên dưới (chỉ đi đúng 1 bước) |
| G13 | Ở màn chọn: chọn qua lại nhiều item liên tiếp | Viền chuyển đúng sang item vừa chọn, không nháy cả item, luôn đúng 1 item có viền; lưới căn đều lề 16dp, item tỉ lệ đều trên mọi cỡ màn |
| G14 | *(màn xem trước)* Ở màn danh sách bấm 1 effect | Mở màn xem trước: ảnh động phủ kín màn, top bar (back + tên **đúng effect vừa chọn**), nút Create góc dưới phải không bị thanh điều hướng che; top bar/bottom nav chung không hiện |
| G15 | Ở màn xem trước bấm Back (nút trên top bar; rồi lặp lại bằng back hệ thống) | Về màn danh sách, không crash |
| G16 | Xem trước → Create → camera → Back | Camera đúng effect; Back về **màn danh sách** (màn xem trước đã bị gỡ khỏi back stack) |
| G17 | Camera → Effect → chọn effect khác → tick → ở màn xem trước bấm Back | Về **màn danh sách** (camera cũ + màn chọn đã bị gỡ — cố ý, xem `nav_graph.xml`), không quay về camera cũ |
| G18 | Ở màn xem trước: bấm đúp thật nhanh nút Create, rồi lặp lại với nút back | Không crash (không `IllegalArgumentException`), không pop luôn màn phía dưới — chỉ đi đúng 1 bước |
| G19 | Màn danh sách + màn chọn: effect có tên dài (vd `dragon_ball` — “Chưởng năng lượng Dragon Ball”) | Tên chỉ 1 dòng, cắt bằng “…”; ở màn danh sách dấu “…” **không** nằm dưới icon tim |
| G20 | *(nút camera bottom nav)* Ở tab Home bấm nút camera giữa bottom nav | Vào camera không effect: preview lên, nút Effect nền đen, cột Action ẩn nhưng nút Record vẫn đúng tâm; quay được video thường. Back về Home |
| G21 | Như G20 nhưng bấm từ tab Collection, rồi Back | Về tab Collection (không nhảy sang Home) |
| G22 | Từ camera không effect: Effect → chọn effect → tick → Create → Back (làm 1 lần từ Home, 1 lần từ Collection) | Ghi lại màn thật sự hiện ra. Kỳ vọng theo bất biến là về danh sách hiệu ứng; nếu ca từ Collection cho kết quả khác thì bất biến đã bị phá (xem `AGENTS.md` mục 5, bullet "Camera không effect") |

> **Về G7c — hành vi hệ thống, không phải lỗi app:** từ Android 11 (API 30), sau **2 lần từ chối**, hệ thống chuyển quyền sang trạng thái *permanently denied*: `launch()` vẫn chạy, callback vẫn trả kết quả "denied", nhưng **không dialog nào hiện ra**. Không có API nào bắt hệ thống hỏi lại được — chỉ người dùng tự cấp trong Settings. Vì vậy app bắt buộc phải phân biệt 3 trạng thái bằng `shouldShowRequestPermissionRationale()`:
>
> | `checkSelfPermission` | `shouldShowRationale` | Nghĩa | Xử lý |
> |---|---|---|---|
> | GRANTED | — | có quyền | chạy camera |
> | DENIED | `true` | còn hỏi lại được | giải thích lý do rồi `launch()` lại |
> | DENIED | `false` (sau khi đã hỏi) | từ chối vĩnh viễn | `PermissionDeniedDialog` + nút Cài đặt gọi `Context.openAppSettings()` (`utils/PermissionUtils.kt` → `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) |
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
| H13 | *(màn chọn effect — camera **nằm lại** back stack)* Camera → Effect → Back, **lặp 5 lần**, rồi đưa tay vào làm cử chỉ có tiếng | Preview lên cả 5 lần; hiệu ứng + tiếng phát đúng 1 lần cho mỗi cử chỉ | Preview đen = executor/camera bị huỷ sai chỗ. Nhiều lớp tiếng = collector `HandLandmarkerProvider.results` nhân đôi |
| H14 | Camera: giơ cử chỉ có tiếng và **giữ nguyên tay** → bấm Effect → Back (tay vẫn giơ) | Tiếng của cử chỉ **phát lại** sau khoảng 200ms debounce (state đã được reset) | Không có tiếng cho tới khi đổi cử chỉ = thiếu `resetGestureState()` (`lastStateId` cũ còn sót lại) |
| H15 | Camera: giơ cử chỉ có tiếng → bấm Effect → Back → **hạ tay xuống**, bấm Record ngay, quay ~5s, xem video | Video **không** có tiếng/hiệu ứng của cử chỉ cũ (loa live đã release, không được cài vào video) | Video có tiếng cử chỉ cũ ngay từ đầu = `activeEffect` cũ không được reset |
| H16 | Camera → Effect, ở lại màn chọn 30s, quan sát chỉ báo camera hệ thống + heap trong Profiler | Chỉ báo camera **tắt**; heap không giữ bitmap full-size của camera | Còn sáng / heap không giảm = `latestCameraBitmap`/`latestHandResult` chưa được null hoá ở `onDestroyView` |
| H17 | *(màn xem trước)* Danh sách → xem trước → Back, **lặp 10 lần** (bật LeakCanary); rồi vào xem trước, nhấn Home / tắt màn hình | Không có cảnh báo leak `EffectPreviewFragment`/`ImageView`; ảnh động dừng khi màn ẩn và chạy lại khi quay lại | Có leak = `previewDrawable` chưa null hoá ở `onDestroyView` (drawable giữ callback về ImageView) |
| H18 | *(mốc 5.4)* Camera → Effect → Back, **lặp 5 lần**, rồi giơ cử chỉ có tiếng và quay 1 clip | Tiếng đúng ở live và trong video, đúng thời điểm | `statePcmMap` giờ decode 1 lần theo vòng đời `CameraRecordViewModel` thay vì mỗi `onViewCreated`; sai/rỗng = alias `statePcmMap` hoặc factory VM có vấn đề |

> ⚠️ H11-H12 giả định action `cameraRecord → recordedPreview` khai `popUpTo="@id/cameraRecordFragment"` + `popUpToInclusive="true"` (phương án đã chốt). Nếu về sau đổi cách khai `popUpTo`, `CameraRecordFragment` sẽ nằm lại trong back stack — khi đó phải test thêm: state debounce (`lastStateId`, `pendingState`) có được reset khi quay lại không, và `latestCameraBitmap`/`latestHandResult` có bị giữ lì trong RAM không. **Đã có trường hợp thật:** action `cameraRecord → effectPicker` cố ý không `popUpTo` nên camera nằm lại — xem H13–H16.

### Công cụ hỗ trợ cho mục H

- **Profiler (H7):** Android Studio → View → Tool Windows → Profiler → chọn tiến trình → tab Memory. Bấm biểu tượng thùng rác (Force GC) trước mỗi lần đọc số, nếu không số liệu sẽ nhiễu vì rác chưa được dọn.
- **LeakCanary (khuyến nghị bật từ Phase C):** thêm `debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")`. Không cần viết thêm dòng code nào — nó tự phát hiện Fragment/View bị leak và hiện notification kèm chuỗi tham chiếu chỉ thẳng ra field nào đang giữ. Thay thế được phần lớn công sức của H7.
- **Kiểm tra nhanh bằng adb:** `adb shell dumpsys meminfo com.example.handar` — so số `TOTAL PSS` trước và sau vòng lặp vào/ra 10 lần.
- **Log vòng đời:** tạm override `onCreate/onCreateView/onViewCreated/onDestroyView/onDestroy` với một dòng `Log.d("LC", ...)` để nhìn thấy thứ tự thật khi test G5, H4, H5. Nhớ gỡ trước khi commit.

---

## I. Cử chỉ tĩnh 1 & 2 tay (từ đợt mở rộng `Gestures`)

> Dùng bảng gán `EffectDefinition` hiện có để test — không cần effect riêng cho từng mục. Nếu ID effect/state đã đổi tên so với lúc viết bảng này, cứ ánh xạ theo đúng cử chỉ, không cần khớp chính xác tên.

### I.1. Hồi quy 1 tay — xác nhận kiến trúc mới (Mục 11.1) không làm hỏng cử chỉ cũ

| # | Bước | Kỳ vọng |
|---|---|---|
| I1 | Chọn effect có nhiều state dùng **asset khác nhau rõ rệt** (ví dụ `room_teleport`: trỏ / peace / 3 ngón / nắm → 4 nền + tiếng khác nhau; hoặc `monster`: xoè → quái vật hiện, nắm → sóng âm), lần lượt làm đúng từng cử chỉ tương ứng | Đúng gif/tiếng tương ứng hiện ra, không lẫn sang state khác — xác nhận `indexOfFirst`/`firstOrNull` vẫn chọn đúng sau khi đổi sang gọi `recognize(hands)` 1 lần |
| I2 | *(đã xoá — effect test `camera_shutter` dùng cho mục này không còn tồn tại; 5 cử chỉ từng gắn ở đó nay test theo cách tạm ở mục I.10)* | |
| I3 | Với 1 effect 1 tay bất kỳ, đưa **2 tay** vào khung hình cùng lúc, chỉ 1 tay làm đúng cử chỉ | Hiệu ứng vẫn kích hoạt bình thường (state 1 tay chỉ cần `hands.firstOrNull()` khớp, không bị tay thứ 2 cản) |

### I.2. Mirror & neo vị trí (Mục 11.2–11.3 — chống regression bug `normMidY`)

| # | Bước | Kỳ vọng |
|---|---|---|
| I4 | 1 tay, kéo tay chậm từ **mép trái sang mép phải** khung hình, giữ nguyên độ cao | Hiệu ứng bám theo tay **mượt suốt toàn bộ chiều ngang**, không dính lại ở 1 vùng cố định (triệu chứng bug gốc: chỉ đúng khi tay phía trái) |
| I5 | 2 tay cùng lúc (bất kỳ cử chỉ 2 tay nào đang có), kéo cả 2 tay ra xa nhau rồi lại gần nhau | Điểm neo (trung điểm) và bán kính (trung bình) di chuyển mượt theo đúng giữa 2 tay, không giật cục |
| I6 | Lặp lại I4 **trong lúc đang ghi hình** (`forRecording = true`), xem lại video sau khi quay | Video ghi ra cũng bám mượt toàn bộ chiều ngang giống hệt live preview — không chỉ test live mà bỏ qua nhánh recording |

### I.3. Cử chỉ 2 tay tĩnh — trạng thái đối xứng (dễ nhất)

| # | Bước | Kỳ vọng |
|---|---|---|
| I7 | Cả 2 tay cùng xoè (`bothHandsPalmOpen`) | Hiệu ứng 2-tay-xoè kích hoạt |
| I8 | Cả 2 tay cùng nắm (`bothHandsFist`) | Hiệu ứng 2-tay-nắm kích hoạt |
| I9 | Chỉ 1 tay xoè, tay kia nắm hoặc không có trong khung hình | **Không** hiệu ứng 2 tay nào kích hoạt — xác nhận guard `hands.size >= 2` chặn đúng, không lỡ khớp khi chỉ 1 tay |

### I.4. 🫶 Trái tim 2 tay (Mục 11.7a — nhiều giới hạn đã biết)

| # | Bước | Kỳ vọng |
|---|---|---|
| I10 | Làm dấu tim đúng chuẩn, 2 tay hướng tương đối đối diện camera | Hiệu ứng tim kích hoạt ổn định, không chập chờn |
| I11 | Giữ nguyên dấu tim, nghiêng dần 2 tay ra khỏi hướng đối diện camera | Hiệu ứng có thể **mất dần** ở góc nghiêng lớn — đây là **giới hạn đã biết** (đo trên hình chiếu 2D), không phải bug, không cần báo Fail nếu đúng hiện tượng này |
| I12 | Làm hình tam giác bằng ngón trỏ+cái duỗi thẳng (không phải dấu tim) | **Không** kích hoạt hiệu ứng tim — xác nhận `fingerCurlRatio`/curl check vẫn chặn đúng hình giả |

### I.5. ❌ Dấu X — 2 ngón bắt chéo (Mục 11.7b)

| # | Bước | Kỳ vọng |
|---|---|---|
| I13 | Bắt chéo 2 ngón trỏ | Hiệu ứng X kích hoạt |
| I14 | Bắt chéo 2 ngón út (thay vì trỏ) | Hiệu ứng X **vẫn** kích hoạt — xác nhận tổng quát hoá sang "1 trong 4 loại ngón" hoạt động đúng |
| I15 | 2 ngón trỏ chỉ **gần nhau**, chưa thực sự bắt chéo qua | **Không** kích hoạt — xác nhận `segmentsCross` phân biệt đúng "cắt qua" với "ở gần" |

> **Mục I.6 (cũ) đã xoá** — checklist trước đây có một mục kiểm tra cử chỉ 2 tay "ghép hình chữ L thành khung máy ảnh", nhưng gesture đó không còn tồn tại trong `Gestures` (chỉ còn `twoHandsHeart`, `twoHandsCrossedFingers`, `bothHandsPalmOpen`, `bothHandsFist` ở nhóm 2 tay). `camera_shutter` (effect dùng 5 cử chỉ 1 tay: OK sign, peace, thumbs up, rock on, call) đã bị xoá hoàn toàn ở đợt thay 10 hiệu ứng thật — 5 cử chỉ đó nay "mồ côi", test theo cách tạm ở mục I.10. Nếu sau này thêm lại kiểu cử chỉ ghép hình, viết lại mục này từ đầu thay vì khôi phục nguyên văn, vì `EffectRepository.kt`/`GestureRecognizer.kt` đã đổi cấu trúc nhiều lần từ lúc mục cũ được viết.

### I.7. Log dọn dẹp

| # | Bước | Kỳ vọng |
|---|---|---|
| I19 | Grep `Log.d` trong `GestureRecognizer.kt`/`GestureUtils.kt` sau khi hoàn tất debug 1 cử chỉ mới | Không còn dòng nào sót lại — log chạy mỗi frame (25-30 lần/giây) không gây lỗi chức năng nhưng ảnh hưởng hiệu năng nếu để sót trong bản release |

### I.8. Nắm tay / xòe tay — đủ 5 điều kiện, không suy luận qua phủ định hay đếm ngưỡng (Phase M)

> Chống regression cho bug: `singleHandFist`/`bothHandsFist` từng định nghĩa bằng `!isPalmOpen(...)`, khiến cử chỉ trỏ tay cũng bị tính nhầm là nắm tay; đồng thời `isPalmOpen` cũ (ngưỡng ≥3/4) cũng tính nhầm cử chỉ "3 ngón" là xòe tay.

| # | Bước | Kỳ vọng |
|---|---|---|
| I20 | Chỉ ngón trỏ (☝️) trên effect dùng `singleHandFist` (vd `monster` — không dùng `canvas_draw` vì chỉ tay chính là state `stroke` của nó) | KHÔNG bị nhận nhầm là nắm tay |
| I21 | Ba ngón (trỏ+giữa+áp út duỗi) trên effect dùng `singleHandPalmOpen` (vd `monster`) | KHÔNG bị nhận nhầm là xòe tay |
| I22 | Nắm tay thật (cả 5 ngón kể cả ngón cái gập) | Vẫn kích hoạt đúng `singleHandFist` — xác nhận không bị thắt quá chặt tới mức không trigger được |
| I23 | Xòe tay thật (cả 5 ngón kể cả ngón cái duỗi) | Vẫn kích hoạt đúng `singleHandPalmOpen` — nếu KHÓ trigger hơn hẳn trước đây (đặc biệt do ngón cái), xem ghi chú "công thức ngón cái chưa chặt hoàn toàn" ở `GestureUtils.kt`, cân nhắc nới ngưỡng riêng cho ngón cái thay vì quay lại kiểu đếm cũ |

### I.9. Cử chỉ catch-all (`anyHandPresent`) — thứ tự ưu tiên

| # | Bước | Kỳ vọng |
|---|---|---|
| I24 | Effect `canvas_draw`: đưa tay vào khung hình nhưng KHÔNG làm cử chỉ trỏ/nắm nào | Chỉ khung xương hiện, không nét vẽ, không tiếng — xác nhận state `idle_skeleton` (catch-all) hoạt động đúng vai trò mặc định |
| I25 | Effect `canvas_draw`: chỉ ngón trỏ (state `stroke`) | Nét vẽ + khung xương cùng hiện — xác nhận state cụ thể vẫn được ưu tiên trước catch-all nhờ đúng thứ tự khai báo trong `states` |
| I26 | Bất kỳ effect nào khác dùng `anyHandPresent`/gesture luôn-đúng tương tự làm catch-all trong tương lai | Đảm bảo state đó luôn được khai **cuối cùng** trong danh sách `states` — nếu đặt trước, nó sẽ che mất mọi cử chỉ khác |

### I.10. Cử chỉ đang “mồ côi” — vẫn còn trong `Gestures` nhưng chưa effect nào dùng

> Bối cảnh: sau đợt thay 4 hiệu ứng test cũ (`rock_on_ily`, `camera_shutter`,
> `absolute_cinema_two_hand`, `heart_or_cross`) bằng 4 hiệu ứng thật (Cầu lửa, Vòng khiên, Tia
> sét, Dragon Ball), 8 cử chỉ sau không còn `EffectState` nào gán tới nữa (xem docstring đầu
> `EffectRepository.kt`): `singleHandOkSign`, `singleHandThumbsUp`, `singleHandCall`,
> `singleHandRockOn`, `singleHandILoveYou`, `bothHandsFist`, `twoHandsHeart`,
> `twoHandsCrossedFingers`. Công thức của chúng trong `GestureUtils.kt` **không đổi**, chỉ là
> mất chỗ kích hoạt qua UI thật để quan sát — không thể test như các mục I khác (chọn 1
> effect có sẵn rồi làm đúng cử chỉ).
>
> **Cách test tạm thời** (không commit thay đổi này): mở 1 file `effect/catalog/*.kt` của hiệu
> ứng 1 tay bất kỳ (ví dụ `MagicShieldEffect.kt`), đổi tạm `gesture = Gestures.xxx` của 1
> state sang đúng cử chỉ muốn test, build/cài lại, làm cử chỉ đó trên effect vừa sửa, quan
> sát hiệu ứng có kích hoạt đúng không, rồi **`git checkout`** lại file đó trước khi chuyển
> sang cử chỉ tiếp theo. Với 3 cử chỉ 2 tay (`bothHandsFist`, `twoHandsHeart`,
> `twoHandsCrossedFingers`) đổi tạm trên 1 hiệu ứng đã khai `requiredNumHands = 2` (ví dụ
> `BlackHoleEffect.kt`) để `HandLandmarkerProvider` khởi động đúng chế độ 2 tay.
>
> ⚠️ Vì cùng lý do, các ca I8 (`bothHandsFist`), I10–I12 (`twoHandsHeart`) và I13–I15
> (`twoHandsCrossedFingers`) ở trên hiện cũng không có effect thật để quan sát — làm theo cách gán
> tạm này, đối chiếu với I32–I34 bên dưới.

| # | Cử chỉ | Bước | Kỳ vọng |
|---|---|---|---|
| I27 | `singleHandOkSign` (👌) | Gán tạm vào 1 state 1 tay, làm dấu OK | Kích hoạt đúng; không nhầm với cử chỉ 3 ngón hoặc nắm tay (công thức dùng `thumbIndexPinchRatio`, khác `isThumbExtended` của các cử chỉ khác — xem `Code_Walkthrough.md` mục 2) |
| I28 | `singleHandThumbsUp` (👍) | Tương tự | Kích hoạt đúng; thử thêm cử chỉ nắm tay thường (không giơ ngón cái) để xác nhận KHÔNG bị nhầm — công thức `isThumbExtended` được comment là chưa chặt, đây là cơ hội duy nhất để phát hiện sai từ lúc 4 hiệu ứng test cũ bị gỡ |
| I29 | `singleHandCall` (🤙) | Tương tự | Kích hoạt đúng, phân biệt được với `singleHandRockOn` (I30) dù cả 2 đều giơ ngón cái + 1 ngón khác |
| I30 | `singleHandRockOn` (🤘) | Tương tự | Kích hoạt đúng, phân biệt được với I29 |
| I31 | `singleHandILoveYou` (🤟) | Tương tự | Kích hoạt đúng, không bị nhầm với `singleHandRockOn` (cả 2 gần giống, khác ở ngón cái) |
| I32 | `bothHandsFist` (✊✊) | Gán tạm vào effect 2 tay, cả 2 tay cùng nắm | Kích hoạt đúng; chỉ 1 tay nắm tay kia xòe/vắng → KHÔNG kích hoạt (guard `hands.size >= 2 && hands.all { ... }` — xem I9) |
| I33 | `twoHandsHeart` (🫶) | Tương tự, làm dấu tim 2 tay | Áp dụng đúng giới hạn đã biết ở I10–I12 (nghiêng tay có thể mất hiệu ứng, hình tam giác bằng ngón thẳng không được kích hoạt) |
| I34 | `twoHandsCrossedFingers` (❌) | Tương tự, bắt chéo 2 ngón bất kỳ trong 4 cặp (trỏ/giữa/áp út/út) | Áp dụng đúng giới hạn đã biết ở I13–I15 (bắt chéo thật mới tính, chỉ để gần nhau thì không) |

---

## J. Hiệu ứng Procedural / mô hình dùng chung giữa live-recording (Phase M)

> Riêng cho hiệu ứng dựng bằng code (`EffectAsset.Procedural`) — vd "Vẽ canvas". Chạy sau bất kỳ lần nào sửa `HandFrame.kt`, `EffectScope.kt`, `ProceduralVisual.kt`, hoặc bất kỳ `EffectVisual` procedural nào.

| # | Bước | Kỳ vọng |
|---|---|---|
| J1 | Chỉ ngón trỏ di chuyển vẽ 1 nét, xem live | Nét bám đúng đầu ngón trỏ, mượt, không giật |
| J2 | Nắm tay lại | Nét biến mất ngay (model bị clear) |
| J3 | Vẽ nét mới sau khi xóa | Nét mới vẽ đúng, không dính tàn dư nét cũ |
| J4 | **Vẽ nét → đợi vài giây → bấm Record → dừng → xem video** | Video phải có nét đã vẽ **trước** khi bấm Record, ngay từ khung đầu tiên (giống nguyên tắc D1, áp dụng cho model dùng chung qua `EffectScope`) |
| J5 | **Vẽ → xóa (nắm tay) → vẽ lại → bấm Record → dừng → xem video** | Video **chỉ** có nét vẽ **sau lần xóa gần nhất** — nét đã xóa KHÔNG được hiện lại trong video (bug thật đã gặp: xóa chỉ tác động lên `StrokeModel` phía live nếu code viết sai chỗ gọi `setActive`) |
| J6 | Vẽ 1 nét dài băng ngang toàn bộ khung hình, bấm Record giữa chừng lúc đang vẽ, tiếp tục vẽ nốt, dừng, xem video | Nét trong video liền mạch, không đứt đoạn hay lệch hình dạng so với nét đã thấy lúc live (xác nhận model theo tọa độ normalized dùng đúng, không lệch tỉ lệ giữa 2 canvas) |
| J7 | Vào camera → Back → vào lại effect `canvas_draw` | `StrokeModel` phải **rỗng** lúc vào lại — xác nhận `EffectScope` được tạo mới mỗi lần `setEffect()`, không dùng chung với phiên trước |

## K. Anchor/Size source & handedness — Trái đất, Hố đen, Gojo (Phase M)

| # | Bước | Kỳ vọng |
|---|---|---|
| K1 | Effect "Trái đất": chụm ngón cái+trỏ lại gần rồi tách xa | Kích thước ảnh to/nhỏ theo đúng khoảng cách 2 đầu ngón, tâm ảnh luôn ở trung điểm 2 ngón, KHÔNG theo độ mở cả bàn tay như hiệu ứng khác |
| K1b | Effect "Trái đất": bấm nút Action ở màn camera | Dialog có đúng 1 dòng "Chụm ngón cái và trỏ" (EN: "Pinch Thumb & Index") với icon chụm, không phải "Giơ tay" |
| K2 | Effect "Hố đen": 2 tay, kéo ra xa/lại gần nhau | Kích thước hiệu ứng to/nhỏ theo khoảng cách 2 tay, tâm hiệu ứng luôn ở trung điểm 2 tay |
| K3 | Effect "Hố đen": chỉ đưa 1 tay vào khung hình | Không crash (nhánh `else` phải có giá trị mặc định hợp lệ, không index-out-of-bounds khi truy `hands[1]`) |
| K4 | Effect "Gojo": chỉ ngón trỏ mỗi tay, đưa cả 2 tay vào khung hình, KHÔNG bắt chéo tay | Tay bên trái người dùng nhìn thấy ra quả cầu 1 màu, tay phải ra màu còn lại — đúng màu như thiết kế, KHÔNG bị đảo màu |
| K5 | Effect "Gojo": bắt chéo 2 tay qua nhau (tay trái đưa sang phải, tay phải đưa sang trái) | Màu quả cầu vẫn bám đúng theo **tay thật** (trái/phải theo người dùng), KHÔNG đổi màu theo vị trí trái/phải trên màn hình — đây là phép thử trực tiếp cho phần đảo `handedness` theo `mirrorX` |
| K6 | Effect "Gojo": chạm 2 đầu ngón trỏ vào nhau | Phát hoạt ảnh hợp nhất, xong chuyển sang ảnh tĩnh quả cầu tím |
| K7 | Lặp lại K4-K6 **trong lúc đang ghi hình**, xem lại video | Kết quả giống hệt live — đặc biệt màu tay không bị đảo trong video dù đã qua `mirrorX` |

## L. Race điều kiện thread — AnimatedGifVisual trong lúc ghi hình (Phase M, chống regression)

> Riêng cho bug đã fix: `setActive()` từng gọi `drawable.start()/stop()` trực tiếp từ main thread trong khi `draw()` của bản ghi chạy trên thread ghi hình — sửa bằng cờ `desiredActive` áp dụng trong `renderToBuffer()`. Test này **chỉ** lộ ra khi đang ghi hình thật, xem live không đủ.

| # | Bước | Kỳ vọng |
|---|---|---|
| L1 | Chọn effect dùng `AnimatedGif` (`fire_ball`, `magic_shield`, hay `black_hole`), bấm Record, đổi cử chỉ qua lại liên tục ~10 lần trong 20-30s | Video mượt, KHÔNG có khung hình đứng/giật cục đúng lúc đổi cử chỉ |
| L2 | Effect "Gojo": chạm 2 tay để trigger hoạt ảnh hợp nhất **trong lúc đang quay**, lặp lại vài lần | Hoạt ảnh phát trọn vẹn mỗi lần trong video, không bị đứng hình/giật ở khung đầu hoạt ảnh |
| L3 | Bất kỳ effect `AnimatedGif` nào: quay 1 clip dài (~60s), đổi cử chỉ liên tục suốt clip | App không crash, không ANR — nếu crash log có `IllegalStateException`/liên quan `AnimatedImageDrawable`, đây là regression của đúng race đã sửa |

## M. Tia sét & Dragon Ball — 2 chỗ rủi ro suy luận chưa từng chạy thật

> Chạy sau bất kỳ lần nào sửa `LightningVisual.kt`, `KamehamehaVisual.kt`, `DragonBallEffect.kt`, `Gesture.kt` (`anyFingerExtended`, `twoHandsWristsTogetherOpen`), `GestureUtils.kt` (`isThumbExtendedStrict`/`isThumbCurled`) hoặc nhánh `TwoWristMidpoint` trong `OverlayView.drawFrame()`. Hai số liệu suy luận: góc xoay tia sét `computeAngleDeg()` và ngưỡng cổ tay chụm `WRIST_TOGETHER_RATIO_THRESHOLD = 0.6` (khoảng cách 2 cổ tay / `palmLength` trung bình).

### M.1. Tia sét (`lightning`)

| # | Bước | Kỳ vọng | Nếu sai thì chỉnh |
|---|---|---|---|
| M1 | Chỉ duỗi ngón trỏ, hướng thẳng lên trên | 1 tia sét chân đặt đúng đầu ngón, mũi hướng lên, cùng chiều ngón tay | Tia ngược 180° → đổi dấu `computeAngleDeg()`; bị lật gương → đổi dấu `dx` |
| M2 | Xoay cổ tay để ngón trỏ chỉ ngang trái, ngang phải, xuống dưới | Tia luôn nằm dọc theo ngón tay (pip → tip) ở cả 4 hướng, không lệch nhất quán | Lệch cùng 1 góc ở mọi hướng = sai hằng số; lệch khác nhau theo hướng = sai công thức `atan2` |
| M3 | Xòe cả bàn tay (5 ngón) | Đúng **5** tia, mỗi tia ở đầu đúng 1 ngón — **kể cả ngón cái**. Tia ngón cái ngắn hơn 4 tia kia (`THUMB_LENGTH_SCALE` 0.85 vs 1.15) | Không có tia ngón cái → xem `isThumbExtendedStrict`; tia ngón cái rung/đảo hướng → kiểm tra `pip = 2` (MCP), không phải 3 |
| M3b | Duỗi lần lượt 1, 2, 3, 4 ngón (trỏ/giữa/áp út/út), ngón cái gập vào lòng bàn tay | Đúng số tia = số ngón đang duỗi, **không** có tia thừa mọc ra từ ngón cái đang gập | Có tia thừa → ngưỡng `isThumbCurled` (0.88) quá lỏng |
| M4 | Chỉ giơ ngón cái (👍, 4 ngón kia gập) | Có **đúng 1** tia, ở đầu ngón cái, hướng dọc theo ngón cái. Đây là thay đổi so với bản cũ (trước đây cố ý bỏ qua ngón cái) | |
| M4b | Nắm tay hoàn toàn (✊) | KHÔNG có tia nào, tiếng tắt | |
| M5 | Đưa **2 tay** vào khung, mỗi tay duỗi số ngón khác nhau | Cả 2 tay đều có tia, đúng số ngón của từng tay. **Ca này trước đây không thể pass** vì effect khai `requiredNumHands = 1` nên MediaPipe chỉ trả về 1 tay — nay đã là 2 | Chỉ 1 tay có tia → kiểm tra `requiredNumHands` và `HandLandmarkerProvider.getOrCreate` có tạo lại detector khi đổi effect không |
| M5b | 2 tay ở **2 khoảng cách khác nhau** với camera (1 tay gần, 1 tay xa) | Tia của mỗi tay co giãn theo **chính tay đó**, không phải trung bình 2 tay (tay gần tia không bị ngắn hụt, tay xa tia không bị dài quá) | Sai → `handRadiusPx` đang không được dùng, rơi về `frame.r` |
| M5c | Hạ 1 tay ra khỏi khung khi tay kia vẫn đang duỗi ngón | Tia của tay còn lại vẫn giữ nguyên, tiếng không tắt (`hands.any`, không phải `hands.all`) | |
| M6 | Tay gần/xa camera | Độ dài tia co giãn theo bán kính lòng bàn tay của chính tay đó (~1.15×, ngón cái ~0.85×), không quá ngắn/dài bất thường | Đổi `BOLT_LENGTH_SCALE` / `THUMB_LENGTH_SCALE` |
| M7 | Lặp M1–M3 **trong lúc đang ghi hình**, xem lại video | Video khớp live: cùng hướng tia, không bị lật/lệch (canvas ghi hình dùng `frame.px/py` với mirror khác live) | |
| M8 | Tiếng điện xẹt | Lặp mượt khi còn tia, dừng khi hạ tay/nắm tay | |

### M.2. Dragon Ball (`dragon_ball`)

| # | Bước | Kỳ vọng | Nếu sai thì chỉnh |
|---|---|---|---|
| M9 | 1 tay xòe (không đưa tay còn lại vào) | Quả cầu năng lượng nhỏ bám lòng bàn tay + tiếng tụ khí (state `charge`) | |
| M10 | Đưa tay thứ 2 vào, 2 tay cùng xòe, cách xa nhau rồi **chụm 2 cổ tay** lại gần | Chuyển sang kamehameha: quả cầu **to hơn (~2.2×)** và xoáy nhanh hơn, neo ở trung điểm 2 cổ tay, không còn tiếng tụ khí | Ghi lại khoảng cách cổ tay thực tế khi cảm thấy “đã chụm” mà không kích hoạt → tăng `WRIST_TOGETHER_RATIO_THRESHOLD` (0.6) |
| M11 | 2 tay xòe nhưng cổ tay **cách xa** nhau (đứng rộng bằng vai) | KHÔNG vào kamehameha, vẫn là quả cầu nhỏ 1 tay | Ngưỡng quá lỏng → giảm `WRIST_TOGETHER_RATIO_THRESHOLD` |
| M12 | Chụm 2 cổ tay nhưng 1 tay nắm hoặc chỉ còn ít ngón | KHÔNG vào kamehameha (đòi cả 2 tay `isPalmOpen`) | |
| M13 | Chụm cổ tay rồi tách ra, lặp 5 lần nhanh | Chuyển qua lại đúng state mỗi lần; tiếng tụ khí phát lại khi về state `charge` (debounce 200ms), không phát chồng | |
| M14 | Chụm cổ tay để kamehameha → quan sát vị trí quả cầu khi 2 tay di chuyển | Quả cầu luôn ở giữa 2 cổ tay (landmark 0), không nhảy về giữa 2 khớp ngón giữa | Kiểm tra nhánh `TwoWristMidpoint` trong `OverlayView.drawFrame()` |
| M15 | Lặp M9–M10 **trong lúc đang ghi hình**, xem lại video | Video khớp live: chuyển state đúng lúc, kích thước/xoáy giống live | |

---

## Cách ghi kết quả

## N. Danh sách video & trình phát (thêm 30/09/2026 — sau khi chuyển MVVM)

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| N1 | Quay 3 video → vào danh sách | Đủ 3 item, thumbnail đúng, sắp theo mới nhất trước |
| N2 | Vào danh sách → mở 1 video → Back ra | Danh sách **quét lại** (có vòng loading) và khớp thư mục. Ca này từng kỳ vọng ngược lại ("không quét lại") — đã đổi 01/10/2026 vì cơ chế cờ không phủ hết, xem `MVVM_Migration_Plan.md` mục 4 |
| N2b | Quay 1 video mới → sang màn chia sẻ → về Trang chủ → mở tab **Bộ sưu tập** (màn này ĐANG nằm trong back stack từ trước) | Video vừa quay **hiện lên ngay**. Đây là bug đã sập: VM sống dai hơn view, video mới quay không đi qua `VideoPlayerFragment` nên không có cờ nào được đặt |
| N3 | Mở video → xoá → về danh sách | Item đã xoá **biến mất ngay** (`load()` ở `onViewCreated`; cơ chế cờ cũ đã xoá 01/10/2026) |
| N4 | Mở video → đổi tên thành công → về danh sách → bấm vào video vừa đổi tên | **Phát được**. Đây là bug đã sập một lần: `VideoItem` giữ `File`, cache cũ mang path cũ (xem `MVVM_Migration_Plan.md` mục 4) |
| N5 | Đổi tên trùng tên file khác | Toast "đã tồn tại", **ở lại** màn player |
| N6 | Đổi tên để trống | Im lặng, ở lại màn, **không** Toast |
| N7 | Đổi tên y như tên cũ | Im lặng, ở lại màn (ca kế hoạch từng định gộp sai thành "thành công") |
| N8 | Phát giữa video → nhấn Home → mở lại app | Seek đúng vị trí cũ (`SavedStateHandle`) |
| N9 | Danh sách trống (chưa quay video nào) | Hiện đúng empty state, không crash |

## O. Xem lại sau khi quay & chia sẻ

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| O1 | Quay xong → màn xem lại → Back → chọn **Exit** | Video bị xoá, thoát màn; **không** phát thêm một nhịp trước khi thoát (cờ `discarding`) |
| O2 | Màn xem lại → Back → chọn **Save** | Sang màn chia sẻ |
| O3 | Màn xem lại → nút Save trực tiếp | Sang màn chia sẻ |
| O4 | Màn chia sẻ: expand → collapse → nút back fullscreen | `playerView` dời đúng giữa 2 container, không mất tiếng, không reset vị trí phát |
| O5 | Màn chia sẻ đang fullscreen → nhấn Home → mở lại app | Vẫn ở fullscreen (`ShareViewModel` giữ `isFullscreen`) |
| O6 | Back hệ thống ở fullscreen / ở card | Fullscreen → thu nhỏ; card → về danh sách hiệu ứng |
| O7 | Nút "Thử lại" (chỉ hiện khi vào từ màn xem lại) | Sang camera đúng effect |
| O7b | Chia sẻ từ menu ⋮ của màn player (không qua màn xem lại) | Tiêu đề top bar là **"Chia sẻ"**, không phải "Lưu thành công"; nút Thử lại ẩn. Vào từ màn xem lại sau khi quay thì vẫn là "Lưu thành công" |
| O8 | 4 nút MXH với app đích **chưa cài** | Không crash, có phản hồi hợp lý |

## P. Danh sách hiệu ứng, yêu thích, tìm kiếm, chọn effect

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| P1 | Bấm tim vài effect → kill app → mở lại | Vẫn còn yêu thích. `FavouriteManager` đổi từ `object` sang `class` nhưng `PREFS_NAME`/`KEY_IDS` không đổi nên **dữ liệu cũ phải đọc được** |
| P2 | Gõ tìm kiếm → vào màn xem trước → Back ra | Ô tìm kiếm **và** danh sách khớp nhau (không phải danh sách đang lọc mà ô trống) |
| P3 | Bấm tim trong lúc đang lọc tìm kiếm | Icon đổi đúng item, danh sách lọc không bị reset |
| P4 | Màn chọn effect: chọn qua lại nhiều item liên tiếp | Viền cyan chuyển đúng, **không nháy cả lưới** (`PAYLOAD_SELECTION` vẫn còn sau khi Adapter đổi API) |
| P5 | Vào camera **chưa có effect** → mở picker | Nút tick mờ, bấm không ăn |
| P6 | Màn chọn: chọn lại đúng effect đang dùng → bấm tick | Chỉ đóng màn, **không** sang màn xem trước |
| P7 | Đổi ngôn ngữ vi ↔ en ở Settings → xem danh sách hiệu ứng, màn chọn, màn xem trước, top bar camera | Tên 10 hiệu ứng đổi theo ngôn ngữ ở cả 4 nơi; chữ tên ở danh sách hiệu ứng cỡ 10sp, không bị cắt xấu |
| P8 | Ngôn ngữ en: gõ "fire" / ngôn ngữ vi: gõ "lửa" ở ô tìm kiếm | Lọc đúng theo tên đang hiển thị (không khớp theo tên ngôn ngữ kia) |

## Q. Riêng cho MVVM — state sống qua cái gì

> Mục quan trọng nhất sau refactor: nó test đúng thứ mà ViewModel hứa, và đúng thứ dễ hỏng âm thầm.

| Ca | Nội dung | Kỳ vọng |
|---|---|---|
| Q1 | Bật **Developer options → Don't keep activities**, chạy lại toàn bộ mục N, O, P | Không crash; state khôi phục đúng ở chỗ dùng `SavedStateHandle` (N8) |
| Q2 | Ở màn player đang phát → `adb shell am kill com.example.handar` → mở lại từ recents | Quay lại đúng màn, `playbackPosition` khôi phục |
| Q3 | Đổi **font scale** hệ thống trong lúc đang ở từng màn có VM | View tạo lại nhưng state giữ nguyên; màn chia sẻ giữ đúng fullscreen (O5) |
| Q4 | Đổi **ngôn ngữ** ở màn Language | Activity tạo lại → **VM KHÔNG sống sót, đây là đúng**. Kỳ vọng là UI đúng sau khi tạo lại (dòng ngôn ngữ mới được tick), không phải "state giữ nguyên" |
| Q5 | Vào/ra từng màn có VM 5 lần → Profiler → Force GC | Heap về xấp xỉ ban đầu. VM sống lâu hơn view nên mục này bắt leak kiểu mới |
| Q6 | Camera: vào màn chọn effect rồi Back, **lặp 5 lần**, đo thời gian từ lúc Back tới lúc preview lên | Nhanh hơn rõ rệt so với trước mốc 5.4 (không decode lại WAV). Không thấy khác biệt → thử effect có nhiều state có tiếng |
| Q7 | Splash: `adb shell settings put system font_scale 1.3` giữa lúc splash đang đếm (tạm nâng `SPLASH_DELAY_MS` lên `20000L` để có chỗ thao tác) | Tổng thời gian tới welcome **không cộng thêm** (đếm tiếp, không reset), và thanh loading **tiếp tục từ chỗ đang dở** chứ không về 0 |
| Q8 | Khảo sát: chọn đáp án ở Survey1 → Next → chọn ở Survey2 → Back về Survey1 | Đáp án Survey1 vẫn được tick đúng (VM dùng chung qua `navGraphViewModels`) |
| Q9 | Khảo sát: chọn đáp án Survey1 → bấm **Skip** | Vào màn xin quyền. Kiểm `SharedPreferences` `survey_answers`: có `answer_1`, **không có** `answer_2` |
| Q10 | Khảo sát: đi hết Survey1 → Survey2 → Hoàn tất mà không chạm đáp án nào ở Survey2 | `answer_2` = 0 (dòng đầu, đúng như bản gốc mặc định chọn dòng đầu) |
| Q11 | Màn xin quyền: bật switch Camera rồi thoát app ngay khi popup còn hiện, rồi cấp quyền và quay lại | Không crash (callback hệ thống về sau khi Fragment detach — đã chốt `context == null` ở `handleDenial`) |
| Q13 | Màn xin quyền: bật switch Camera rồi **từ chối** popup | Switch **tự trả về tắt**. Đây là regression đã sập một lần: `StateFlow` không emit khi giá trị không đổi nên switch nằm lại ở bật (xem `MVVM_Migration_Plan.md` mốc 6.5) |
| Q14 | Màn xin quyền: quyền Camera **đã cấp**, bấm switch để tắt | Switch trả về bật (Android không cho app tự thu hồi quyền) |
| Q12 | Màn cài đặt: bấm lần lượt 5 mục Đánh giá / Chia sẻ / Góp ý / Về ứng dụng / Chính sách | **Không có gì xảy ra và không crash** — 5 nhánh còn TODO là cố ý (mốc 6.4 chỉ chuyển MVVM). Mục Ngôn ngữ vẫn mở màn Language bình thường |

Với mỗi dòng test, đánh dấu: ✅ Pass / ❌ Fail / ⚠️ Pass có lưu ý. Nếu Fail, ghi lại: model máy, số liệu `VideoStatsLogger` (nếu có), và mô tả hiện tượng — quay lại đúng phase liên quan trong `HandAr_Refactor_Plan.md` (Phase 0–5) hoặc `HandAr_Plan.md` (Phase A–N) để xử lý tiếp.

Riêng mục **G-H**, khi Fail hãy đối chiếu với `Fragment_Review_Checklist.md` trước khi sửa — mỗi kiểu hỏng ở hai mục này đều ứng với đúng một mục trong checklist đó.

Riêng mục **N-Q**, khi Fail hãy đối chiếu với `MVVM_Migration_Plan.md` trước khi sửa: mỗi ca ở 4 mục
này ứng với đúng một quyết định hoặc một bẫy đã ghi lại trong kế hoạch đó (mục 4 cho N4, 3.2/3.3 cho
O1, 3.4 cho O4/O5, 4.2 cho P2, 4.4 cho P4, 5.4 cho Q6, 6.1 cho Q7, 6.2 cho Q8-Q10, 6.3 cho Q4,
6.5 cho Q11, 6.4 cho Q12).

Riêng mục **I**, khi Fail hãy đối chiếu với `Camera_X_Hand_Landmarker.md` Mục 11 (nhất là Mục 11.9 — Quy trình chẩn đoán cử chỉ 2 tay) trước khi sửa — phần lớn các lỗi đã gặp khi làm cử chỉ mới đều phù hợp với 1 trong 7 bước chuẩn đoán đã đúc kết ở đó.
