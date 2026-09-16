# Quy chuẩn Asset — PNG / GIF / Sprite Sheet / WAV

> Tài liệu này rút ra trực tiếp từ cách `EffectVisual`, `AudioMixer`, `AudioEncoderWrapper` xử lý file
> trong code hiện tại (`effect/StaticImageVisual.kt`, `effect/AnimatedGifVisual.kt`,
> `effect/SpriteSheetVisual.kt`, `utils/AudioUtils.kt`, `wrapper/AudioEncoderWrapper.kt`). Mỗi quy tắc
> dưới đây gắn với 1 dòng code cụ thể — nếu sau này code đổi, tài liệu này phải cập nhật theo, không
> phải quy ước áp đặt từ bên ngoài.

---

## 0. Quy tắc đặt tên chung (áp dụng mọi file `drawable/` và `raw/`)

Đây là ràng buộc của Android resource system, không phải của HandAr, nhưng vi phạm sẽ khiến project
**không build được** chứ không chỉ lỗi runtime:

- Chỉ chữ thường, chữ số, dấu gạch dưới (`_`). Không dấu cách, không gạch ngang, không chữ hoa.
- Không bắt đầu bằng chữ số.
- Tên phải mô tả đúng effect + trạng thái, khớp cách đặt trong `EffectRepository`, ví dụ:
  `happy_happy_happy_cat.gif`, `egg_cracked.png`, `stranger_things_monster.png`.

---

## 1. PNG tĩnh — dùng cho `EffectAsset.StaticImage`

`StaticImageVisual` decode bằng `BitmapFactory.decodeResource` (giữ nguyên độ phân giải gốc), rồi scale
để vừa bán kính `r` lúc vẽ — không có bước resize/nén nào ở code, ảnh nặng bao nhiêu là load nguyên bấy
nhiêu vào RAM.

| Thuộc tính | Yêu cầu | Vì sao |
|---|---|---|
| Định dạng file | `.png`, có kênh alpha (RGBA) | Hiệu ứng vẽ đè lên video preview trong suốt; ảnh không có alpha sẽ hiện nền trắng/đen hình chữ nhật đè lên tay |
| Kích thước cạnh dài | **512–768px** | `draw()` scale theo `r` (bán kính lòng bàn tay trên khung hình, thường 100–300px). Ảnh gốc lớn hơn nhiều lần không tăng chất lượng hiển thị nhưng tốn RAM decode — mỗi `EffectDefinition` load ảnh 1 lần và giữ suốt vòng đời effect |
| Tỉ lệ khung | Vuông hoặc gần vuông (1:1 → 4:3) | `draw()` scale theo `max(width, height)` rồi giữ nguyên tỉ lệ khung hình gốc — ảnh quá dẹt (ví dụ 16:9) sẽ bị thu nhỏ quá mức theo cạnh dài, phần còn lại trông bé so với bán kính bàn tay |
| Nền | Trong suốt xung quanh chủ thể, không viền cứng | Ảnh được vẽ đè trực tiếp lên video, viền hình chữ nhật/nền đặc sẽ lộ rõ |

**Checklist trước khi thêm:** mở ảnh trong trình xem hỗ trợ alpha (không phải trình xem ảnh mặc định
Windows đôi khi hiện nền trắng giả), xác nhận nền thật sự trong suốt.

---

## 2. GIF động — dùng cho `EffectAsset.AnimatedGif`

Đây là loại asset có nhiều ràng buộc ẩn nhất vì `AnimatedGifVisual` render GIF vào **1 buffer cố định
256×256** (`GIF_BUFFER_SIZE = 256`) mỗi frame, dùng `ImageDecoder` với `ALLOCATOR_SOFTWARE` bắt buộc
(ghi chú trong `HandAr_Plan.md` Phần I mục 7: đây là "cạm bẫy" đã gặp và cố định để tránh crash).

| Thuộc tính | Yêu cầu | Vì sao |
|---|---|---|
| Định dạng file | `.gif` thật (không phải video đổi đuôi) | `ImageDecoder.decodeDrawable` ép kiểu `as AnimatedImageDrawable` — file không phải GIF hợp lệ sẽ crash lúc load, không phải lỗi mượt |
| Kích thước gốc | **≤ 256×256px**, khuyến nghị đúng 256×256 | Mọi GIF đều bị vẽ vào buffer 256×256 cố định (`renderToBuffer()`), GIF lớn hơn chỉ tốn thời gian decode + bộ nhớ mà không tăng chất lượng hiển thị — GIF nhỏ hơn 256 vẫn ổn (được scale lên) nhưng sẽ hơi mờ |
| Tỉ lệ khung | Vuông (1:1) | `renderToBuffer()` dùng `uniformScale = min(256/w, 256/h)` rồi căn giữa — GIF không vuông sẽ để lại viền trong suốt 2 bên trong buffer, làm chủ thể trông nhỏ hơn thực tế so với bán kính bàn tay |
| Nền | Trong suốt (GIF hỗ trợ alpha nhị phân, không mượt như PNG) | `buffer.eraseColor(Color.TRANSPARENT)` mỗi frame — nếu GIF nguồn có nền đặc (trắng/xanh lá) nó sẽ hiện y nguyên, không tự động xoá được bằng code |
| Số frame / dung lượng | Không giới hạn cứng trong code, nhưng nên **≤ 20 frame, ≤ 500KB** | `renderToBuffer()` chạy lại mỗi lần `draw()` được gọi — tức **mỗi frame video ghi hình** (25fps) lẫn mỗi lần `onDraw` của live preview đều decode lại 1 frame GIF; GIF nặng/nhiều frame làm tăng nguy cơ tụt fps khi ghi hình (đúng loại vấn đề `RecordingPerfLogger` trong `CameraRecordFragment` đang đo) |
| Vòng lặp | Nên tự loop mượt (frame cuối nối liền frame đầu) | `repeatCount = REPEAT_INFINITE` luôn set trong code — GIF không tự loop mượt sẽ bị giật ở mỗi chu kỳ lặp |

**Checklist trước khi thêm:** xuất GIF ở đúng canvas vuông 256×256 ngay từ công cụ tạo (không để công cụ
tự "fit" sai tỉ lệ), kiểm tra dung lượng file, đếm số frame.

---

## 3. Sprite Sheet — dùng cho `EffectAsset.SpriteSheet`

`SpriteSheetVisual.init` có 2 `require()` sẽ **crash ngay khi tạo effect** nếu file không đúng lưới —
đây là loại asset duy nhất tự kiểm tra định dạng bằng exception, nên quy chuẩn ở đây gần như bắt buộc
tuyệt đối, không phải khuyến nghị.

| Thuộc tính | Yêu cầu (bắt buộc — vi phạm là crash) | Vì sao |
|---|---|---|
| Lưới đều | `sheet.width % columns == 0` **và** `sheet.height % rows == 0` | `require()` đầu tiên trong `init` ném exception đúng câu: *"Sheet WxH không chia hết cho lưới CxR — sẽ lệch ô"* |
| Số frame thật ≤ số ô lưới | `frameCount <= columns * rows` | `require()` thứ hai — ô thừa (nếu `frameCount < columns*rows`) được phép, dùng để chừa placeholder, nhưng không được khai `frameCount` vượt quá lưới |
| Thứ tự frame | Trái → phải, trên → dưới (row-major), không có khoảng trống giữa các frame đã dùng | `SpriteSheetVisual.draw()` tính `col = frameIndex % columns`, `row = frameIndex / columns` — code không hỗ trợ sheet có ô "rỗng" xen giữa các frame hợp lệ |
| Kích thước 1 ô | Nên là số chẵn, ví dụ 128×128 hoặc 256×256/ô | Không bắt buộc trong code, nhưng `frameW = sheet.width / columns` là phép chia nguyên — ô lẻ pixel (ví dụ sheet 1000px / 3 cột) sẽ làm `frameW` bị làm tròn xuống, lệch nhẹ theo từng frame |
| `frameDurationMs` khai trong `EffectAsset.SpriteSheet` | Đặt trước khi xuất sheet, không đoán sau | `draw()` tính `frameIndex` theo **đồng hồ hệ thống thật** (`elapsedMs / frameDurationMs % frameCount`), không theo lần gọi `draw()` — animation nhanh/chậm hoàn toàn do đúng con số này, không do code vẽ |

**Checklist trước khi thêm:** ghi rõ ra giấy 4 số `columns`, `rows`, `frameCount`, `frameDurationMs`
**trước khi** xuất file từ công cụ tạo sprite sheet, xuất đúng đủ số ô đó — tránh xuất sheet rồi mới đếm
ngược ra 4 số này (dễ lệch 1 ô so với thực tế).

---

## 4. WAV — dùng cho `soundRes` trong `EffectState`

Đây là loại asset **dễ sai âm thầm nhất**: sai định dạng không hề crash, chỉ làm âm thanh trong video
ghi ra bị rè/nhanh/chậm — vì `loadWavPcm()` không hề kiểm tra hay chuyển đổi định dạng, nó giả định
đúng 1 định dạng cố định và đọc thẳng byte.

| Thuộc tính | Yêu cầu (bắt buộc) | Vì sao |
|---|---|---|
| Encoding | PCM tuyến tính, **16-bit** | `loadWavPcm()` đọc thẳng phần `data` thành `ShortArray` (2 byte/sample) qua `asShortBuffer()` — file 8-bit hoặc 24-bit/32-bit float sẽ bị đọc sai hoàn toàn (không lỗi, chỉ ra tiếng ồn trắng) |
| Số kênh | **Mono (1 kênh)** | `AudioMixer.mix()` cộng từng sample của mic (mono) với từng sample effect theo đúng chỉ số `i` — file stereo có 2 kênh xen kẽ (L,R,L,R...) sẽ bị hiểu nhầm thành chuỗi mono, làm giai điệu bị "vỡ" ra thành nhiễu the thé. `AudioEncoderWrapper` cũng cấu hình cứng `channelCount = 1` |
| Sample rate | **44100 Hz đúng** | `VideoRecorder` mặc định `sampleRate = 44_100`, không có bước resample nào giữa `loadWavPcm()` → `AudioMixer` → `AudioEncoderWrapper`. WAV thu ở tần số khác (ví dụ 48000Hz, hay giọng nói 16000Hz) sẽ bị phát **nhanh/chậm và lệch cao độ** khi trộn vào video, dù nghe thử trực tiếp trên máy tính vẫn bình thường |
| Header | Chuẩn RIFF/WAVE có chunk `fmt ` + `data` | `loadWavPcm()` tự tìm chunk `data` bằng cách quét byte (`0x61746164`) — file WAV có chunk phụ lạ (ví dụ metadata `LIST`/`bext` do 1 số DAW chèn thêm) vẫn đọc được vì code có bỏ qua chunk không khớp, nhưng để an toàn nên export "WAV chuẩn" (không kèm metadata) từ công cụ edit âm thanh |
| Độ dài | Ngắn, tiếng hiệu ứng (khuyến nghị 0.3–3 giây) | Không phải giới hạn cứng, nhưng `AudioMixer` loop lại (`effectPos = 0` khi hết mảng) nếu hiệu ứng đang active lâu hơn 1 vòng phát — file quá dài sẽ không bao giờ thấy hết trong ngữ cảnh dùng thực tế của app (chạm cử chỉ → phát 1 tiếng ngắn) |

**Checklist trước khi thêm:** export từ Audacity/DAW với đúng 3 thông số **PCM 16-bit / Mono / 44100Hz**
(Audacity: *Tracks → Resample → 44100Hz* rồi *File → Export → WAV (Microsoft) → Signed 16-bit PCM*, và
convert stereo→mono qua *Tracks → Mix → Mix Stereo Down to Mono* nếu file gốc là stereo). Nghe thử lại
file WAV vừa export (không phải file gốc) để chắc bước convert không làm hỏng tiếng.

---

## 5. Nền hiệu ứng — dùng cho `EffectBackground` (`Solid` / `Image` / `Animated`)

Ba renderer trong package `effect/` (`SolidBackgroundRenderer`, `ImageBackgroundRenderer`,
`AnimatedBackgroundRenderer`) đều vẽ **phủ kín toàn bộ canvas theo kiểu center-crop**: tính
`scale = max(canvasW / nguồnW, canvasH / nguồnH)`, canh giữa rồi cắt phần thừa hai bên/trên-dưới.
Khác với ảnh hiệu ứng vẽ theo bán kính bàn tay, nền **không co theo nội dung** — cạnh khung hình
luôn bị cắt trên máy có tỉ lệ màn hình khác với ảnh gốc, nên bố cục quan trọng phải nằm giữa khung.

Mỗi hiệu ứng có nền tạo ra **hai instance renderer riêng** (`liveBackground` và
`recordingBackground` trong `OverlayView.setEffect()`) — ảnh/GIF bị decode **hai lần**, tốn gấp đôi
bộ nhớ và thời gian load so với hiệu ứng không nền. Đây là lý do trực tiếp khiến giới hạn dung
lượng ở bảng dưới nghiêm hơn so với ảnh/GIF hiệu ứng thường.

### 5.1 `Solid` — màu trơn

| Thuộc tính | Yêu cầu | Vì sao |
|---|---|---|
| Cách khai | Khai bằng `colorRes` (id trong `colors.xml`), **không** dùng số hex trần trong `EffectRepository` | `SolidBackgroundRenderer` gọi `ContextCompat.getColor(context, colorRes)` — tham số này bắt buộc là resource id, không phải giá trị màu; truyền nhầm số hex build vẫn qua nhưng `getColor` sẽ ném `Resources.NotFoundException` lúc chạy |

### 5.2 `Image` — ảnh tĩnh

`ImageBackgroundRenderer` decode bằng `BitmapFactory.decodeResource` — giữ nguyên độ phân giải
gốc trong RAM suốt vòng đời effect, không có bước resize.

| Thuộc tính | Yêu cầu | Vì sao |
|---|---|---|
| Định dạng file | WebP, đặt trong `res/drawable-nodpi/` | `drawable-nodpi` để Android không tự chọn theo mật độ màn hình rồi nạp nhầm bản chất lượng thấp; WebP giảm dung lượng APK so với PNG cho ảnh nền không cần alpha |
| Tỉ lệ khung | ~9:19.5 (dọc), khớp tỉ lệ camera app đang khoá `portrait` | Center-crop scale theo cạnh nào hụt nhiều hơn — ảnh lệch xa tỉ lệ máy thật sẽ bị cắt mất phần lớn, đặc biệt trên màn hình rất dài |
| Cạnh ngắn | ≥ 720px | Dưới ngưỡng này ảnh bị phóng to (scale > 1) để phủ kín canvas ghi hình 720p, lộ rõ vỡ hạt |
| Nền | Không cần alpha — nền phủ kín toàn khung, phía sau không có gì để lộ ra | Khác với `StaticImage`/`AnimatedGif` ở mục 1–2 (vẽ đè lên camera, bắt buộc alpha) |

### 5.3 `Animated` — ảnh động toàn màn hình

`AnimatedBackgroundRenderer` dùng `ImageDecoder` với `ALLOCATOR_SOFTWARE` (cùng lý do bắt buộc đã
ghi ở mục 2 cho GIF hiệu ứng), decode ra `AnimatedImageDrawable`, vẽ vào buffer đúng **kích thước
gốc** của ảnh (không ép về 256×256 như GIF hiệu ứng) rồi mới center-crop buffer đó lên canvas.
Vì vậy ảnh nền động **nặng hơn nhiều lần** so với GIF hiệu ứng bàn tay ở mục 2 — nó chiếm cả màn
hình thay vì một vòng tròn quanh tay, và không có giới hạn buffer cố định nào trong code giúp
"ghìm" chi phí lại.

| Thuộc tính | Yêu cầu | Vì sao |
|---|---|---|
| Độ phân giải | ≤ 720p | Buffer trong `AnimatedBackgroundRenderer` = đúng kích thước gốc ảnh, không giới hạn cứng như buffer GIF 256×256 ở mục 2 — ảnh gốc lớn hơn 720p chỉ tốn RAM/CPU decode mỗi frame, không tăng chất lượng vì cuối cùng vẫn bị scale xuống canvas ghi hình |
| Độ dài loop | ≤ 3 giây | `drawable` set `repeatCount = REPEAT_INFINITE` và tự tính frame theo đồng hồ hệ thống tại thời điểm `draw()` — loop dài làm điểm nối lặp lại thưa, người xem dễ nhận ra giật hơn loop ngắn mượt |
| Dung lượng file | ≤ 1.5 MB | Nền động bị decode **hai lần** (live + recording, xem ghi chú đầu mục 5) — cùng một ngưỡng dung lượng như GIF hiệu ứng sẽ tốn gấp đôi bộ nhớ thực tế so với hiệu ứng thường |

**Checklist trước khi thêm:** với `Animated`, đo thử `RecPerf` sau khi thêm hiệu ứng mới có nền —
đây là loại asset duy nhất bị decode hai lần cùng lúc, nên chi phí thực tế cao hơn cảm giác "chỉ
là một GIF" khi nhìn file gốc.

---

## 6. Bảng tóm tắt nhanh (dán khi làm asset)

| Loại | Định dạng | Kích thước/Thông số | Nền/Kênh |
|---|---|---|---|
| Ảnh tĩnh (hiệu ứng) | `.png` | 512–768px, vuông/gần vuông | Alpha trong suốt |
| GIF (hiệu ứng) | `.gif` | ≤256×256, vuông, ≤20 frame, ≤500KB | Alpha trong suốt |
| Sprite sheet | `.png` | Lưới chia hết, ô đồng đều, ghi rõ columns/rows/frameCount/frameDurationMs | Alpha trong suốt |
| Âm thanh | `.wav` | PCM 16-bit, Mono, 44100Hz, 0.3–3s | — |
| Nền — Solid | `colors.xml` | — | Không dùng hex trần |
| Nền — Image | `.webp`, `drawable-nodpi/` | ~9:19.5, cạnh ngắn ≥720px | Không cần alpha |
| Nền — Animated | ảnh động (WebP/GIF động) | ≤720p, loop ≤3s, ≤1.5MB | Không cần alpha, decode 2 lần |

> Nếu sau này thêm loại `EffectAsset` mới (ví dụ Lottie như đề cập trong
> `HandAr_Plan.md` Phần I), bổ sung thêm 1 mục vào file này theo đúng format trên: nêu yêu cầu +
> trích dẫn dòng code gây ra yêu cầu đó — tránh quy tắc "nghe nói vậy" không có căn cứ trong code thật.
