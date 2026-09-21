# Kịch bản Test Thủ công — HandAr (sau refactor Phase 0-5 + Phase A-B)

> Test trên ít nhất 2 máy: 1 máy khá mới (đại diện hiệu năng tốt) + 1 máy cũ/yếu (đại diện giới hạn phần cứng).
> Ghi lại kết quả từng mục — mục nào FAIL thì note rõ hiện tượng để quay lại phase tương ứng.
>
> **Mục A-F**: kiểm tra pipeline ghi hình (có từ Phase 0-5, không được phép hỏng sau mỗi lần refactor).
> **Mục G-H**: thêm từ Phase B — điều hướng giữa màn hình và vòng đời/leak của Fragment. Đây là lớp bug mới xuất hiện kể từ khi app có nhiều màn; chúng **không** biểu hiện ở lần chạy đầu tiên mà chỉ lộ ra sau nhiều lần vào/ra màn, nên phải test riêng.
> **Mục I**: thêm từ đợt mở rộng `Gestures` (1 tay + 2 tay) — xem chi tiết công thức/lý do tại `Camera_X_Hand_Landmarker.md` Mục 11. Chạy mục này sau bất kỳ lần nào sửa `GestureRecognizer.kt`/`GestureUtils.kt`/`EffectRepository.kt`.
> **Mục J-L**: thêm từ Phase M (hiệu ứng procedural/canvas, `SizeSource`/`AnchorSource`/`handedness`, và race condition `AnimatedGifVisual` khi ghi hình). Chạy sau bất kỳ lần nào sửa `HandFrame.kt`, `EffectScope.kt`, `ProceduralVisual.kt`, `OverlayView.kt` (phần `setResult`/`drawFrame`), hoặc thêm effect procedural/anchor-size mới.
>
> Chạy đầy đủ A-H sau mỗi phase từ Phase B trở đi. Chạy Mục I sau mỗi lần thêm/sửa cử chỉ. Chạy Mục J-L sau mỗi lần đụng tới hạ tầng Phase M nói trên.

---

## A. Quyền & khởi động app

| # | Bước | Kỳ vọng |
|---|---|---|
| A1 | Gỡ cài đặt app cũ, cài lại bản mới, mở app → đi hết luồng splash → ngôn ngữ → onboarding 1–3 → khảo sát → chào mừng tới **màn xin quyền** | Trước màn xin quyền **không** hiện popup quyền nào. Ở màn xin quyền, 2 switch (Camera, Thông báo) đang tắt (Android < 13: switch Thông báo luôn bật, không có popup). Bật switch **Camera** → hiện đúng 1 popup Camera; Android 13+ bật switch Thông báo → hiện popup Thông báo riêng. **Không** có popup xin quyền Mic ở bất kỳ bước nào |
| A2 | Từ chối quyền Camera | App hiện Toast từ chối, không crash |
| A3 | Vào Settings hệ thống → cấp lại quyền Camera → mở lại app | App hoạt động bình thường, live preview hiện đúng |

## B. Live preview (chưa ghi hình)

| # | Bước | Kỳ vọng |
|---|---|---|
| B1 | Mở app, chọn effect `rock_on_ily`, đưa tay vào khung hình, làm cử chỉ rock on (🤘) | GIF rock on hiện đúng vị trí lòng bàn tay, tiếng rock on phát ra loa |
| B2 | Đổi sang cử chỉ I love you (🤟) | Đổi sang GIF I love you, tiếng tương ứng phát ra loa |
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

## E. Số liệu & hiệu năng (dùng `VideoStatsLogger`, chỉ chạy bản Debug)

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
| G1 | Mở app (cold start), đi hết luồng: splash (~1s, tự chuyển) → ngôn ngữ → onboarding 1–3 → khảo sát → chào mừng → xin quyền → bấm nút bắt đầu | Bắt đầu ở **splash** (start destination), kết thúc ở màn **danh sách hiệu ứng** (không phải màn camera). Ở danh sách bấm Back thì thoát app, không quay lại xin quyền/chào mừng/khảo sát (mỗi bước `popUpTo` inclusive). Hiện chưa có cờ "đã xem onboarding" nên **mỗi lần** mở app đều chạy lại luồng này |
| G2 | Chọn 1 hiệu ứng → sang màn xem trước → bấm **Create** → vào màn camera | Preview lên bình thường, hiệu ứng hiển thị đúng **hiệu ứng vừa chọn** (không phải hiệu ứng mặc định) |
| G3 | Bấm Back từ màn camera | Quay lại màn danh sách, **không** thoát app (nếu thoát app: thiếu `app:defaultNavHost="true"`) |
| G4 | Back tiếp ở màn danh sách | Thoát app bình thường, không crash |
| G5 | Vào camera → Back → vào lại → Back, **lặp 5 lần** | Cả 5 lần preview đều lên. Lần nào preview đen/không có frame → executor hoặc camera đã bị huỷ sai chỗ (xem `Fragment_Review_Checklist.md` mục 1) |
| G6 | Chọn qua lại nhiều hiệu ứng khác nhau rồi mới bấm Record | Video ghi ra dùng đúng hiệu ứng của lần chọn **cuối cùng**, cả GIF lẫn tiếng |
| G7 | Vào màn camera, **từ chối** quyền camera lần đầu | Hiện Toast từ chối, không crash, không đứng lại ở màn đen (tự quay về màn danh sách) |
| G7b | Back, vào camera lại | Hệ thống **vẫn hỏi quyền** lần thứ 2 |
| G7c | Từ chối lần 2, Back, vào camera lại lần nữa | **Không còn dialog nào** — đây là hành vi đúng của Android, không phải bug (xem ghi chú dưới). App phải hiện hướng dẫn mở Settings, tuyệt đối không im lặng hoặc kẹt ở màn đen |
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
| G19 | Màn danh sách + màn chọn: effect có tên dài (vd `black_background_with_monster`) | Tên chỉ 1 dòng, cắt bằng “…”; ở màn danh sách dấu “…” **không** nằm dưới icon tim |

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
| H13 | *(màn chọn effect — camera **nằm lại** back stack)* Camera → Effect → Back, **lặp 5 lần**, rồi đưa tay vào làm cử chỉ có tiếng | Preview lên cả 5 lần; hiệu ứng + tiếng phát đúng 1 lần cho mỗi cử chỉ | Preview đen = executor/camera bị huỷ sai chỗ. Nhiều lớp tiếng = collector `HandLandmarkerProvider.results` nhân đôi |
| H14 | Camera: giơ cử chỉ có tiếng và **giữ nguyên tay** → bấm Effect → Back (tay vẫn giơ) | Tiếng của cử chỉ **phát lại** sau khoảng 200ms debounce (state đã được reset) | Không có tiếng cho tới khi đổi cử chỉ = thiếu `resetGestureState()` (`lastStateId` cũ còn sót lại) |
| H15 | Camera: giơ cử chỉ có tiếng → bấm Effect → Back → **hạ tay xuống**, bấm Record ngay, quay ~5s, xem video | Video **không** có tiếng/hiệu ứng của cử chỉ cũ (loa live đã release, không được cài vào video) | Video có tiếng cử chỉ cũ ngay từ đầu = `activeEffect` cũ không được reset |
| H16 | Camera → Effect, ở lại màn chọn 30s, quan sát chỉ báo camera hệ thống + heap trong Profiler | Chỉ báo camera **tắt**; heap không giữ bitmap full-size của camera | Còn sáng / heap không giảm = `latestCameraBitmap`/`latestHandResult` chưa được null hoá ở `onDestroyView` |
| H17 | *(màn xem trước)* Danh sách → xem trước → Back, **lặp 10 lần** (bật LeakCanary); rồi vào xem trước, nhấn Home / tắt màn hình | Không có cảnh báo leak `EffectPreviewFragment`/`ImageView`; ảnh động dừng khi màn ẩn và chạy lại khi quay lại | Có leak = `previewDrawable` chưa null hoá ở `onDestroyView` (drawable giữ callback về ImageView) |

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
| I1 | Chọn effect có nhiều state dùng **asset khác nhau rõ rệt** (ví dụ `test_effect_background`: trỏ / peace / 3 ngón / nắm → 4 nền + tiếng khác nhau; hoặc `black_background_with_monster`: xoè → quái vật, nắm → đồng hồ), lần lượt làm đúng từng cử chỉ tương ứng | Đúng gif/tiếng tương ứng hiện ra, không lẫn sang state khác — xác nhận `indexOfFirst`/`firstOrNull` vẫn chọn đúng sau khi đổi sang gọi `recognize(hands)` 1 lần |
| I2 | Chọn `camera_shutter` (effect nhiều state 1 tay dùng chung 1 asset), thử đủ cả 5-6 cử chỉ gắn vào đó | Mỗi cử chỉ đều kích hoạt được hiệu ứng, không có cử chỉ nào "im lặng" |
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

> **Mục I.6 (cũ) đã xoá** — checklist trước đây có một mục kiểm tra cử chỉ 2 tay "ghép hình chữ L thành khung máy ảnh", nhưng gesture đó không còn tồn tại trong `Gestures` (chỉ còn `twoHandsHeart`, `twoHandsCrossedFingers`, `bothHandsPalmOpen`, `bothHandsFist` ở nhóm 2 tay). `camera_shutter` hiện tại kích hoạt bằng 5 cử chỉ **1 tay** (OK sign, peace, thumbs up, rock on, call) — không có gesture 2 tay nào cho hiệu ứng chụp ảnh nữa. Nếu sau này thêm lại kiểu cử chỉ ghép hình, viết lại mục này từ đầu thay vì khôi phục nguyên văn, vì `EffectRepository.kt`/`GestureRecognizer.kt` đã đổi cấu trúc nhiều lần từ lúc mục cũ được viết.

### I.7. Log dọn dẹp

| # | Bước | Kỳ vọng |
|---|---|---|
| I19 | Grep `Log.d` trong `GestureRecognizer.kt`/`GestureUtils.kt` sau khi hoàn tất debug 1 cử chỉ mới | Không còn dòng nào sót lại — log chạy mỗi frame (25-30 lần/giây) không gây lỗi chức năng nhưng ảnh hưởng hiệu năng nếu để sót trong bản release |

### I.8. Nắm tay / xòe tay — đủ 5 điều kiện, không suy luận qua phủ định hay đếm ngưỡng (Phase M)

> Chống regression cho bug: `singleHandFist`/`bothHandsFist` từng định nghĩa bằng `!isPalmOpen(...)`, khiến cử chỉ trỏ tay cũng bị tính nhầm là nắm tay; đồng thời `isPalmOpen` cũ (ngưỡng ≥3/4) cũng tính nhầm cử chỉ "3 ngón" là xòe tay.

| # | Bước | Kỳ vọng |
|---|---|---|
| I20 | Chỉ ngón trỏ (☝️) trên effect dùng `singleHandFist` (vd `black_background_with_monster` — không dùng `canvas_draw` vì chỉ tay chính là state `stroke` của nó) | KHÔNG bị nhận nhầm là nắm tay |
| I21 | Ba ngón (trỏ+giữa+áp út duỗi) trên effect dùng `singleHandPalmOpen` (vd `black_background_with_monster`) | KHÔNG bị nhận nhầm là xòe tay |
| I22 | Nắm tay thật (cả 5 ngón kể cả ngón cái gập) | Vẫn kích hoạt đúng `singleHandFist` — xác nhận không bị thắt quá chặt tới mức không trigger được |
| I23 | Xòe tay thật (cả 5 ngón kể cả ngón cái duỗi) | Vẫn kích hoạt đúng `singleHandPalmOpen` — nếu KHÓ trigger hơn hẳn trước đây (đặc biệt do ngón cái), xem ghi chú "công thức ngón cái chưa chặt hoàn toàn" ở `GestureUtils.kt`, cân nhắc nới ngưỡng riêng cho ngón cái thay vì quay lại kiểu đếm cũ |

### I.9. Cử chỉ catch-all (`anyHandPresent`) — thứ tự ưu tiên

| # | Bước | Kỳ vọng |
|---|---|---|
| I24 | Effect `canvas_draw`: đưa tay vào khung hình nhưng KHÔNG làm cử chỉ trỏ/nắm nào | Chỉ khung xương hiện, không nét vẽ, không tiếng — xác nhận state `idle_skeleton` (catch-all) hoạt động đúng vai trò mặc định |
| I25 | Effect `canvas_draw`: chỉ ngón trỏ (state `stroke`) | Nét vẽ + khung xương cùng hiện — xác nhận state cụ thể vẫn được ưu tiên trước catch-all nhờ đúng thứ tự khai báo trong `states` |
| I26 | Bất kỳ effect nào khác dùng `anyHandPresent`/gesture luôn-đúng tương tự làm catch-all trong tương lai | Đảm bảo state đó luôn được khai **cuối cùng** trong danh sách `states` — nếu đặt trước, nó sẽ che mất mọi cử chỉ khác |

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
| L1 | Chọn effect dùng `AnimatedGif` (`rock_on_ily`, `camera_shutter`, hay `black_background_with_monster`), bấm Record, đổi cử chỉ qua lại liên tục ~10 lần trong 20-30s | Video mượt, KHÔNG có khung hình đứng/giật cục đúng lúc đổi cử chỉ |
| L2 | Effect "Gojo": chạm 2 tay để trigger hoạt ảnh hợp nhất **trong lúc đang quay**, lặp lại vài lần | Hoạt ảnh phát trọn vẹn mỗi lần trong video, không bị đứng hình/giật ở khung đầu hoạt ảnh |
| L3 | Bất kỳ effect `AnimatedGif` nào: quay 1 clip dài (~60s), đổi cử chỉ liên tục suốt clip | App không crash, không ANR — nếu crash log có `IllegalStateException`/liên quan `AnimatedImageDrawable`, đây là regression của đúng race đã sửa |

---

## Cách ghi kết quả

Với mỗi dòng test, đánh dấu: ✅ Pass / ❌ Fail / ⚠️ Pass có lưu ý. Nếu Fail, ghi lại: model máy, số liệu `VideoStatsLogger` (nếu có), và mô tả hiện tượng — quay lại đúng phase liên quan trong `HandAr_Refactor_Plan.md` (Phase 0–5) hoặc `HandAr_Plan.md` (Phase A–N) để xử lý tiếp.

Riêng mục **G-H**, khi Fail hãy đối chiếu với `Fragment_Review_Checklist.md` trước khi sửa — mỗi kiểu hỏng ở hai mục này đều ứng với đúng một mục trong checklist đó.

Riêng mục **I**, khi Fail hãy đối chiếu với `Camera_X_Hand_Landmarker.md` Mục 11 (nhất là Mục 11.9 — Quy trình chẩn đoán cử chỉ 2 tay) trước khi sửa — phần lớn các lỗi đã gặp khi làm cử chỉ mới đều phù hợp với 1 trong 7 bước chuẩn đoán đã đúc kết ở đó.
