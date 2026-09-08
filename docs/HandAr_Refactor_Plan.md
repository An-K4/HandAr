# Kế hoạch Refactor & Fix — HandAr Video Recording Pipeline

> Tổng hợp toàn bộ phát hiện từ buổi review + số liệu đo thật + tham khảo `google/grafika`.
> Mục tiêu cuối: video mượt (≥25fps thật), dung lượng hợp lý, không giật khung đầu, không lỗi âm thanh, UI phản hồi nhanh.

---

## 0. Quyết định kiến trúc cần chốt trước (ảnh hưởng toàn bộ plan bên dưới)

### 0.1 — Có ghi mic vào video hay không?

Nhiều app "magic hand effect" cùng thể loại trên store **không ghi mic**, chỉ bake sẵn tiếng hiệu ứng (SFX). Lý do: tránh xung đột với tính năng thêm nhạc của TikTok/Reels sau khi upload, nội dung dạng gag ngắn không cần thoại thật, giảm friction xin quyền `RECORD_AUDIO`, và **loại bỏ hoàn toàn lớp bug rò rỉ loa→mic** (Bug 2 đã gặp).

| Phương án | Ưu điểm | Nhược điểm |
|---|---|---|
| **A. Giữ mic** (hiện tại) | Ghi được phản ứng/giọng nói thật của người dùng, cảm giác chân thực | Phải xử lý rò rỉ loa→mic, đồng bộ PTS 2 nguồn audio, xin quyền mic, pipeline phức tạp hơn |
| **B. Bỏ hẳn mic**, chỉ ghi hiệu ứng | Kiến trúc audio đơn giản hẳn (chỉ 1 nguồn PCM là hiệu ứng), hết bug rò rỉ loa, hết vấn đề đồng bộ 2 track, không cần quyền `RECORD_AUDIO`, `stop()` nhanh hơn (ít việc encode hơn) | Mất khả năng ghi phản ứng/giọng người dùng |
| **C. Hybrid** — toggle bật/tắt mic cho người dùng chọn | Linh hoạt, chiều được cả 2 nhu cầu | Tốn công sức làm UI + vẫn phải giữ toàn bộ độ phức tạp của phương án A khi mic bật |

**→ Cần bạn chốt trước khi làm Phase 3 (audio) bên dưới**, vì hướng sửa Bug "âm thanh chèn đôi" khác hẳn nhau tùy phương án chọn.

---

## 1. Tổng hợp bug & vấn đề đã phát hiện

| # | Vấn đề | Vị trí | Trạng thái |
|---|---|---|---|
| 1 | Trigger hiệu ứng bị mất khi gesture xảy ra trước lúc bấm Record | `AudioMixer`/`MainActivity` | ✅ Đã fix (đồng bộ theo elapsed time) |
| 2 | Code thừa: comment, log debug, import không dùng | Toàn bộ project | ✅ Đã dọn |
| 3 | `stop()` chặn UI thread khi finalize video dài | `VideoRecorder.stop()` | ✅ Đã fix (chạy nền + callback) |
| 4 | Không có công cụ đo số liệu thật | — | ✅ Đã thêm `VideoStatsLogger` |
| 5 | Resolution ghi hình = nguyên kích thước màn hình (1080×2100, > FullHD) | `MainActivity.toggleRecording()` | 🔲 Chưa fix — **Phase 1** |
| 6 | Bitrate hard-code 4Mbps, không theo resolution | `VideoEncoderWrapper` | 🔲 Chưa fix — **Phase 2** |
| 7 | `OverlayView` tính scale/offset theo kích thước View màn hình, không theo canvas ghi hình thật → sẽ vỡ hình khi đổi resolution | `OverlayView.kt` | 🔲 Đã có hướng sửa, chưa áp dụng — **đi kèm Phase 1** |
| 8 | FPS thực tế đo được chỉ 13.5→18.7, dù khai báo 25 — do pipeline camera/vẽ/encode chạy dồn 1 thread | `MainActivity.startCamera()` analyzer | 🔲 Chưa fix — **Phase 4** |
| 9 | Vài frame đầu video bị đơ (PTS baseline sai — lấy tại lúc bấm Record thay vì lúc frame đầu tiên thật sự được đẩy vào) | `VideoRecorder.start()`/`drainVideoEncoder()` | 🔲 Chưa fix — **Phase 5** |
| 10 | Âm thanh hiệu ứng bị chèn 2 lần, lệch nhẹ — do mic thu lại tiếng phát ra loa ngoài (đã xác nhận bằng test tắt loa) | Kiến trúc audio: `SoundEffectPlayer` (loa) + `MicReader`+`AudioMixer` (mic) cùng chạy song song | 🔲 Chưa fix — **Phase 3**, phụ thuộc quyết định mục 0.1 |

---

## 2. Bài học rút ra từ `google/grafika` (repo mẫu chính thức của Google cho MediaCodec+Surface+Muxer)

1. **Tách hẳn luồng "hiển thị live" khỏi luồng "ghi/encode".** Grafika dùng 1 thread riêng chỉ để encode (`TextureMovieEncoder`, có `Handler`/`Looper` riêng), nhận tín hiệu "có frame mới" từ renderer thread qua message, không tự vẽ/encode ngay trên thread render. → **Đối chiếu với app hiện tại:** `backgroundExecutor` (1 thread) đang gánh hết: đọc camera → xoay bitmap → vẽ+encode+drain → chạy AI landmark — đây chính là nguyên nhân bottleneck fps (mục #8).

2. **"Giữ frame mới nhất, cho phép rớt frame cũ" khi consumer xử lý không kịp.** `SurfaceTexture` của Grafika chỉ giữ đúng 1 frame mới nhất; nếu encoder thread chưa rảnh, frame cũ bị ghi đè chứ không xếp hàng đợi → không bao giờ bị dồn ứ (backlog), video vẫn đúng tốc độ thời gian thực dù ít frame hơn.

3. **PTS luôn neo vào frame đầu tiên thực sự được encode, không neo vào thời điểm bấm nút.** Pattern chuẩn: `timeDiff = now - lastFrameTime; if (lastFrameTime chưa từng có) đừng cộng dồn; total += timeDiff`. Frame đầu tiên luôn là mốc 0.

4. **`drainEncoder()` được gọi ngay trước khi submit frame mới**, không đợi đến cuối — tránh encoder bị nghẽn do buffer đầy.

**Kết luận:** app không cần bê nguyên kiến trúc OpenGL của Grafika (quá phức tạp so với nhu cầu), nhưng nên học đúng 2 nguyên lý (1) và (3) — áp dụng bằng Kotlin thread/Handler đơn giản, không cần OpenGL.

---

## 3. Thứ tự thực hiện đề xuất

```
Phase 0  → Chốt quyết định mic (mục 0.1)
Phase 1  → Giới hạn resolution HD + fix OverlayView scale (đã soạn sẵn, chờ áp dụng)
Phase 2  → Bitrate động theo resolution
Phase 3  → Fix bug âm thanh chèn đôi (hướng phụ thuộc Phase 0)
Phase 4  → Tách thread camera/AI khỏi thread vẽ+encode (áp dụng bài học Grafika #1, #2)
Phase 5  → Fix PTS baseline (frame đầu bị đơ) — áp dụng bài học Grafika #3, #4
Phase 6  → Test toàn diện + đo lại số liệu bằng VideoStatsLogger
```

**Vì sao thứ tự này:**
- Phase 1-2 độc lập, ít rủi ro, đã có hướng dẫn sẵn — nên làm trước để "ăn điểm nhanh" với mentor.
- Phase 3 cần chốt Phase 0 trước, và nên làm trước Phase 4-5 vì nếu bỏ mic (phương án B), Phase 5 (PTS) sẽ **đơn giản đi đáng kể** (không cần đồng bộ 2 track audio/video, chỉ còn track hiệu ứng thuần).
- Phase 4 (tách thread) là thay đổi kiến trúc lớn nhất, rủi ro cao nhất — nên làm sau cùng khi các phần khác đã ổn định, và nên làm trước Phase 5 vì cách tách thread mới sẽ quyết định *nơi nào* trong code là "lúc frame đầu tiên thực sự được đẩy vào" (ảnh hưởng trực tiếp cách fix Phase 5).
- Phase 6 luôn ở cuối, dùng lại đúng công cụ đã xây (`VideoStatsLogger`) để so sánh trước/sau.

---

## 4. Chi tiết từng Phase

### Phase 1 — Giới hạn resolution HD + fix OverlayView
- Viết hàm tính resolution HD giữ tỉ lệ khung hình gốc (dựa số liệu thật: `1080×2100` → cap short-side về `720` → `720×1400`).
- Áp dụng vào `toggleRecording()` thay cho `overlayView.width/height` trực tiếp.
- **Bắt buộc đi kèm:** fix `OverlayView.drawHandEffects()` để tính scale/offset theo canvas thực tế đang vẽ (đã soạn sẵn ở buổi trước — xem lại phần trao đổi "Bước 1.1").
- Lưu ý: width/height truyền vào `MediaFormat` nên là số chẵn (làm tròn `- it % 2`), một số encoder yêu cầu.

### Phase 2 — Bitrate động theo resolution
- Công thức tham khảo (rule-of-thumb H.264): `bitrate = width * height * fps * bitsPerPixel`, với `bitsPerPixel` khoảng `0.07–0.1` tùy độ nét mong muốn.
- Với resolution `720×1400 @25fps`: bitrate ước tính ≈ **1.8–2.5 Mbps** (thấp hơn nhiều so với 4Mbps hard-code hiện tại, hợp lý vì resolution đã giảm gần 1 nửa).
- Sửa `VideoEncoderWrapper` để nhận `bitrate` làm tham số thay vì hard-code.

### Phase 3 — Fix bug âm thanh chèn đôi
**Nếu chọn phương án A (giữ mic):**
- Cách đơn giản nhất: khi đang trong lúc ghi hình, **giảm/tắt âm lượng phát ra loa ngoài** của `SoundEffectPlayer` (vẫn phát qua `AudioMixer` để ghi vào video), chỉ bật lại loa khi không ghi hình (để live/preview vẫn nghe được). Có thể dùng `AudioManager` để route qua tai nghe nếu cắm, hoặc đơn giản là set volume `SoundPool.play(..., leftVolume=0, rightVolume=0, ...)` trong lúc `isRecording == true`.
- Cách triệt để hơn (phức tạp hơn): dùng `AcousticEchoCanceler`/`NoiseSuppressor` (Android built-in audio effects) gắn vào `AudioRecord` session — nhưng không phải máy nào cũng hỗ trợ tốt, cần test kỹ.

**Nếu chọn phương án B (bỏ mic):**
- Xóa hẳn `MicReader`, bỏ `hasAudio`/`audioEncoder` luồng mic, audio track trong video chỉ còn từ `AudioMixer` (thuần hiệu ứng, không mic).
- Bug này biến mất hoàn toàn theo thiết kế, không cần code fix riêng.
- Bỏ luôn quyền `RECORD_AUDIO` trong `AndroidManifest.xml` nếu không dùng cho việc gì khác.

### Phase 4 — Tách thread camera/AI khỏi thread vẽ+encode (áp dụng bài học Grafika)
- Camera thread (`backgroundExecutor` hiện tại): chỉ đọc ảnh, xoay, chạy `detectAsync`, và ghi kết quả (bitmap mới nhất + hand result mới nhất) vào biến `@Volatile` dùng chung — **không tự gọi `pushFrame()` nữa**.
- Thread encode riêng (mới, dùng `HandlerThread` hoặc vòng lặp `Thread` đơn giản chạy theo nhịp mong muốn ~25fps): liên tục lấy "dữ liệu mới nhất" đang có, vẽ vào canvas encoder, gọi `drainVideoEncoder()`. Nếu camera thread chưa kịp cập nhật, dùng tạm dữ liệu cũ (đúng tinh thần "giữ frame mới nhất" của Grafika).
- Rủi ro cần test kỹ: race condition khi đọc biến `@Volatile` giữa 2 thread — đảm bảo đọc/ghi đúng loại dữ liệu immutable (ví dụ chụp lại `Bitmap` reference tại 1 thời điểm, không sửa bitmap đang được thread kia dùng).

### Phase 5 — Fix PTS baseline (frame đầu bị đơ)
- Dời việc lấy `recordStartTimeNs` từ `start()` sang **lần đầu tiên frame thực sự được đẩy vào canvas encoder** (lazy-init, giống pattern `timeLast >= 0` của Grafika).
- Nếu vẫn giữ mic (phương án A): cần 1 baseline **chung** cho cả audio lẫn video — ví dụ biến `recordingOriginNs` được set đúng 1 lần, tại thời điểm sớm nhất giữa "frame video đầu tiên" và "chunk audio đầu tiên", cả 2 track đều trừ theo đúng mốc này khi tính PTS.
- Nếu bỏ mic (phương án B): chỉ còn 1 track audio (hiệu ứng), việc đồng bộ đơn giản hơn nhiều — có thể dùng chung luôn baseline của video.

### Phase 6 — Test & đo lại
Checklist tối thiểu:
- [ ] Quay clip ngắn (~5s), trung (~30s), dài (~70s) — xác nhận resolution/bitrate/size đúng công thức Phase 1-2.
- [ ] Xem lại avg FPS qua `VideoStatsLogger` — mục tiêu ≥ 23-25fps sau Phase 4.
- [ ] Xem frame đầu video — không còn hiện tượng đơ/đứng hình.
- [ ] Nếu giữ mic: xác nhận không còn tiếng chèn đôi khi bật loa bình thường (test cả khi đeo tai nghe và khi dùng loa ngoài).
- [ ] Đổi cử chỉ tay trong lúc quay — xác nhận hiệu ứng vẫn trigger đúng, không bị lệch vị trí GIF (do Phase 1's OverlayView fix).
- [ ] Bấm Record → Stop nhanh liên tục vài lần — xác nhận không crash, không leak `MediaCodec` (do nút bị disable đúng lúc từ fix trước đó).

---

## 5. Việc cần bạn quyết định trước khi bắt đầu code

1. **Chọn phương án mic:** A / B / C (mục 0.1).
2. **Xác nhận thứ tự Phase** ở mục 3 có hợp lý với bạn không, hay muốn đổi ưu tiên (ví dụ làm Phase 4 threading trước vì đó là thay đổi "nặng" nhất, làm sớm để có nhiều thời gian test).
