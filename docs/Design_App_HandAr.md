# Tài liệu thiết kế ứng dụng HandAr

**Ngày:** 14 tháng 9 năm 2026

## 1. App mẫu tham khảo

- [Hand Magic: Sign Effects AR](https://play.google.com/store/apps/details?id=com.dt.handmagic.signeffects)
- [Hand Magic – AR Control](https://play.google.com/store/apps/details?id=com.handmagic.gesture.areffects)
- [Magic Hand: AR Magic Control](https://play.google.com/store/apps/details?id=com.hand.control.magic.ar.tracking.maker)

## 2. Danh sách chức năng chính của app

- Nhận diện cử chỉ tay (1 hoặc 2 tay) theo thời gian thực qua camera.
- Nhiều hiệu ứng AR (lửa, tia sét, hào quang, particle...) gắn theo cử chỉ, co giãn theo khoảng cách tay.
- Chọn hiệu ứng / mẫu (template) trước khi quay.
- Ghi video trực tiếp trong app.
- Xem lại video ngay sau khi quay.
- Lưu video vào thư viện (bộ sưu tập) trong app.
- Chia sẻ video ra mạng xã hội / ứng dụng khác.
- Xóa video.
- Xem lại các video đã lưu trước đó cùng các tùy chọn đổi tên/chia sẻ/xóa.

## 3. Danh sách màn hình

### 3.1 Màn hình hiện có

1. **Chọn hiệu ứng** – màn khởi động, chọn 1 trong các hiệu ứng có sẵn.
2. **Quay video** – camera + nhận diện tay + ghi hình.
3. **Xem lại sau khi quay** – nút xong / xoá / chia sẻ.
4. **Danh sách video** – thư viện video đã quay.
5. **Phát video** – trình phát video sau khi chọn từ thư viện.

### 3.2 Màn còn thiếu – có thể phát triển thêm theo mức độ ưu tiên

| Màn đề xuất | Lý do / nội dung |
|---|---|
| Trang chủ / Onboarding | Giới thiệu ngắn lần đầu mở app (2-3 màn hình) |
| Chi tiết hiệu ứng / Hướng dẫn cử chỉ | Xem trước hướng dẫn cử chỉ có thể thực hiện trước khi vào quay |
| Mở khoá hiệu ứng / Premium | Một số hiệu ứng khoá, xem quảng cáo hoặc mua để mở khóa thêm nhiều hiệu ứng mới |
| Cài đặt | Ngôn ngữ, giới thiệu phiên bản |

## 4. Danh sách hiệu ứng đề xuất

| Tên hiệu ứng | Số tay | Mô tả / cử chỉ kích hoạt |
|---|---:|---|
| Cầu lửa | 1 tay | Nắm tay → quả cầu lửa nhỏ. Xòe tay → cầu lửa bùng lên to hơn |
| Vòng khiên năng lượng | 1 tay | Xoè tay có vòng tròn khiên phát sáng bao quanh lòng bàn tay. → nắm tay lại vòng tròn thu lại và biến mất |
| Tia sét | 1 tay | Duỗi ngón tay → tia sét/điện phóng ra từ các đầu ngón tay đang duỗi thẳng |
| Chưởng năng lượng Dragon Ball | 1-2 tay | 1 tay hiện quả cầu năng lượng. Chụm 2 cổ tay lại → quả cầu năng lượng kamehameha to và xoáy nhanh hơn. |
| Gojo | 1-2 tay | Chỉ ngón trỏ → quả cầu năng lượng nhỏ màu xanh ở đầu ngón tay. 2 tay chỉ ngón trỏ → quả cầu năng lượng nhỏ ở đầu ngón tay, 1 bên màu xanh 1 bên màu đỏ. Chạm 2 đầu ngón trỏ vào nhau → hiệu ứng 2 quả cầu xanh đỏ hòa vào nhau theo vòng tròn, sau đó thay thế bằng quả cầu năng lượng nhỏ màu tím. |
| Quái vật | 1 tay | Xòe tay → quái vật hiện ra theo tay với tiếng kêu gào. Nắm tay → quái vật biến mất, hiệu ứng sóng âm lan tỏa ra xung quanh. |
| Dịch chuyển tức thời giữa các phòng | 1 tay | Giơ ngón trỏ làm số 1 → background phòng khách (tiếng TV). Giơ ngón trỏ + ngón giữa làm số 2 → background phòng tắm (tiếng nước chảy). Giơ ngón trỏ + ngón giữa + ngón áp út làm số 3 → background phòng bếp (bát đũa lạch cạch). Nắm tay → background phòng ngủ (tiếng ngáy). |
| Trái Đất | 1 tay | Hình ảnh tĩnh của trái đất di chuyển theo tay và phóng to thu nhỏ dựa trên khoảng cách của ngón cái và ngón trỏ. |
| Cổng dịch chuyển/Hố đen | 2 tay | 2 tay xòe ra trong màn hình → cổng xoáy không gian/hố đen với hiệu ứng âm thanh ở giữa 2 tay, to nhỏ theo khoảng cách 2 tay. |
| Vẽ canvas | 1 tay | Duỗi ngón trỏ di chuyển trên màn hình nền đen → điểm nhỏ màu trắng được vẽ trên đường ngón tay đi qua. Nắm tay để xóa tất cả điểm trắng và phát âm thanh tiếng xé giấy hoặc tiếng tẩy xóa. |

## 5. Bảng asset cần chuẩn bị cho 10 hiệu ứng

**Ngày chốt:** 21 tháng 9 năm 2026, tổng hợp từ khảo sát asset + `EffectRepository`/`effect/catalog/` hiện tại. Khi khảo sát khác với mô tả ở mục 4, **khảo sát là bản mới nhất** (ví dụ Vòng khiên chỉ cần 1 file, Quái vật bỏ tiếng gào).

### 5.1 Quy ước đặt tên

- **Ảnh:** mọi ảnh (tĩnh và động) dùng `.webp`. Thư mục: các asset hiện có đang nằm trong `res/drawable/`, trong khi `Asset_Format_Guidelines.md` và `AGENTS.md` ghi `res/drawable-nodpi/` (thư mục này có sẵn nhưng đang trống), chưa thống nhất. Mẫu tên: `<effect_id>_<trạng thái hoặc vai trò>.webp`, ví dụ `fire_ball_small.webp`.
- **Thumbnail:** `<effect_id>_thumbnail.webp` (đúng mẫu 4 file đã có).
- **Nền ảnh tĩnh:** `<effect_id>_bg[_<tên>].webp`.
- **Âm thanh:** `.wav` đặt trong `res/raw/`, mẫu `<effect_id>_<trạng thái hoặc vai trò>.wav`; nhạc nền là `<effect_id>_bgm.wav`. Thông số bắt buộc: PCM 16-bit, mono, 44100Hz, 0.3–3 giây (theo `Asset_Format_Guidelines.md`). Tiếng lặp liên tục phải là file lặp mượt (đầu nối đuôi không bị giật).
- Chỉ chữ thường, số và dấu gạch dưới. Tên trùng nhau giữa `drawable/` và `raw/` là được (hai không gian tên riêng).
- Ký hiệu: **(lặp)** = ảnh động chạy vòng, **(1 lần)** = ảnh động chạy một lần (`oneShot = true`), **(mới)** = chưa có file, **(đổi tên)** = đã có file, cần đổi tên.

### 5.2 Bảng chính

| Hiệu ứng (`id`) | Số tay | Asset hình ảnh | Asset âm thanh | Cử chỉ hỗ trợ | Nền riêng | Ghi chú code |
|---|---:|---|---|---|---|---|
| **Cầu lửa** (`fire_ball`) | 1 | `fire_ball_small.webp` (lặp, mới): lửa nhỏ<br>`fire_ball_big.webp` (lặp, mới): lửa bùng to<br>`fire_ball_burst.webp` (1 lần, mới): chuyển từ nhỏ sang to<br>`fire_ball_thumbnail.webp` (đã có) | `fire_ball_burn.wav` (mới): lửa cháy, khi nắm tay<br>`fire_ball_burst.wav` (mới): bùng lên, khi xòe tay | Nắm tay (`singleHandFist`) → lửa nhỏ<br>Xòe tay (`singleHandPalmOpen`) → bùng to | Không (camera thật) | Chưa có code. Cần visual dạng Procedural (mẫu `GojoVisual`, chứa `AnimatedGifVisual` con) để phát `burst` một lần rồi chuyển sang `big`. |
| **Vòng khiên năng lượng** (`magic_shield`) | 1 | `magic_shield.webp` (lặp, mới): vòng khiên phát sáng, dùng cho cả lúc hiện và lúc thu lại<br>`magic_shield_thumbnail.webp` (đã có) | Không có tiếng hiệu ứng<br>`magic_shield_bgm.wav` (mới): nhạc nền | Xòe tay (`singleHandPalmOpen`) → khiên bao quanh lòng bàn tay<br>Nắm tay (`singleHandFist`) → khiên thu nhỏ dần rồi tắt hẳn | Không (camera thật) | Chưa có code. Thu nhỏ do code làm (scale theo thời gian), không cần file riêng. Momentary chỉ vẽ khi còn khớp cử chỉ, nên cần visual Procedural tự chạy hết animation thu nhỏ. |
| **Tia sét** (`lightning`) | 1 | `lightning_bolt.webp` (lặp, mới): 1 tia sét, code nhân bản và xoay theo hướng từng ngón<br>`lightning_thumbnail.webp` (đã có) | `lightning_zap.wav` (mới): tiếng điện xẹt, lặp khi đang có sét | ≥1 ngón duỗi, **không tính ngón cái** (trỏ, giữa, áp út, út) → vẽ tia sét ở đầu từng ngón đang duỗi<br>Cử chỉ mới: `Gestures.anyFingerExtendedNoThumb` | Không (camera thật) | Chưa có code. Visual Procedural duyệt từng ngón bằng `isIndexExtended`/`isMiddleExtended`/`isRingExtended`/`isPinkyExtended` (đã có trong `GestureUtils`), giống cách `GojoVisual` duyệt từng tay, nên "ngón nào duỗi thì ngón đó có sét" làm được. |
| **Chưởng năng lượng Dragon Ball** (`dragon_ball`) | 1–2 (khai `requiredNumHands = 2`) | `dragon_ball_energy.webp` (lặp, mới): dùng cho cả quả cầu thường và kamehameha, code tăng cỡ và tốc độ xoay khi chụm cổ tay<br>`dragon_ball_thumbnail.webp` (mới) | `dragon_ball_charge.wav` (mới): tụ khí, khi 1 tay hiện quả cầu<br>Không có tiếng kamehameha | 1 tay xòe (`singleHandPalmOpen`) → quả cầu năng lượng<br>2 cổ tay chụm + cả 2 tay xòe → kamehameha<br>Cử chỉ mới: `Gestures.twoHandsWristsTogetherOpen` | Không (camera thật) | Chưa có code. State kamehameha phải khai **trước** state 1 tay (thứ tự khai báo là độ ưu tiên). Neo giữa 2 cổ tay (landmark 0), khác `TwoHandMidpoint` hiện dùng landmark 9. Kích thước cố định lớn hơn, không dùng `TwoHandDistance` vì cổ tay chụm thì khoảng cách gần bằng 0. |
| **Gojo** (`gojo`) | 1–2 (khai `requiredNumHands = 2`) | `gojo_blue_ball.webp` (mới, thay `blue_ball.webp`): cầu xanh<br>`gojo_red_ball.webp` (mới, thay `red_ball.webp`): cầu đỏ<br>`gojo_merge.webp` (1 lần, mới): xanh và đỏ hòa vào nhau<br>`gojo_purple_ball.webp` (tĩnh, mới): cầu tím<br>`gojo_thumbnail.webp` (mới) | Không có tiếng hiệu ứng<br>`gojo_bgm.wav` (mới): nhạc nền | Chỉ ngón trỏ (`anyHandPointing`) → cầu ở đầu ngón trỏ (tay trái xanh, tay phải đỏ)<br>Chạm 2 đầu ngón trỏ → hòa nhập rồi thành cầu tím | Không (camera thật) | Code đã có (`GojoVisual`). Chỉ thay ảnh tạm (`stranger_things_monster`, `stranger_things_clock`) bằng asset mới. `gojo_merge.webp` không nằm trong khảo sát nhưng code đang cần, nên đã thêm vào. |
| **Quái vật** (`monster`, đổi id từ `black_background_with_monster`) | 1 | `monster_appear.webp` (tĩnh, đổi tên từ `stranger_things_monster.webp`): quái vật<br>`monster_wave.webp` (1 lần, mới): sóng âm lan tỏa<br>`monster_thumbnail.webp` (mới) | `monster_bgm.wav` (mới): nhạc nền, dùng bản mới thay vào khi test (không dùng lại `stranger_things_clock.wav`)<br>**Bỏ** tiếng gào `stranger_things_monster.wav`; sóng âm không có tiếng | Xòe tay (`singleHandPalmOpen`) → quái vật hiện theo tay<br>Nắm tay (`singleHandFist`) → quái vật biến mất, sóng âm lan ra | `monster_bg.webp` (ảnh tĩnh, mới), thay nền động tạm `happy_happy_happy_cat.webp` | Code đã có, cần sửa: state `monster` đặt `soundRes = null`, state nắm tay đổi từ GIF tạm sang `monster_wave.webp` (1 lần), đổi nền. `bgm` vẫn có, dùng nhạc nền mới thay vào khi test. |
| **Dịch chuyển tức thời giữa các phòng** (`room_teleport`, đổi id từ `test_effect_background`) | 1 | `room_teleport_bg_living_room.webp` (tĩnh, mới)<br>`room_teleport_bg_bathroom.webp` (tĩnh, mới)<br>`room_teleport_bg_kitchen.webp` (tĩnh, mới)<br>`room_teleport_bg_bedroom.webp` (tĩnh, mới)<br>`room_teleport_character.webp` (tĩnh, có alpha, mới): nhân vật hoạt hình bám theo tay<br>`room_teleport_thumbnail.webp` (mới) | `room_teleport_living_room.wav` (mới, lặp): TV<br>`room_teleport_bathroom.wav` (mới, lặp): nước chảy<br>`room_teleport_kitchen.wav` (mới, lặp): bát đũa<br>`room_teleport_bedroom.wav` (mới, lặp): tiếng ngáy | Số 1 (`singleHandPointing`) → phòng khách<br>Số 2 (`singleHandPeaceSign`) → phòng tắm<br>Số 3 (`singleHandThreeFingers`) → phòng bếp<br>Nắm tay (`singleHandFist`) → phòng ngủ<br>Chế độ `StateMode.Latched` | Ảnh tĩnh theo từng state (4 phòng)<br>Nền mặc định trước khi chọn phòng: đen (như hiện tại) | Code đã có (`TestEffectBackgroundEffect`), cần thay asset và thêm `room_teleport_character.webp` làm `asset` của mỗi state. Nhân vật dùng chung cho cả 4 phòng. Hiện người thật trên nền cần tách người khỏi nền (segmentation), stack hiện chỉ có HandLandmarker nên chưa làm. Tiếng lặp: `AudioMixer` đã tự lặp trong video, còn đường phát live qua `SoundEffectPlayer` cần kiểm tra đã lặp chưa. Không có nhạc nền (đã bỏ), chỉ có tiếng riêng từng phòng. |
| **Trái Đất** (`earth`) | 1 | `earth_planet.webp` (tĩnh, đổi tên từ `earth.webp`)<br>Thumbnail: dùng lại `earth_planet.webp` như hiện tại | Không có tiếng hiệu ứng<br>`earth_bgm.wav` (mới): nhạc nền | Có tay + nhón ngón cái và ngón trỏ (`anyHandPresent`) → to/nhỏ theo khoảng cách nhón (`SizeSource.PinchDistance`, `AnchorSource.PinchMidpoint`) | Không (camera thật) | Code đã có, chỉ thêm `bgm`. |
| **Cổng dịch chuyển / Hố đen** (`black_hole`) | 2 | `black_hole_portal.webp` (lặp, đổi tên từ `black_hole.webp`)<br>`black_hole_thumbnail.webp` (đã có) | `black_hole_loop.wav` (mới, lặp): tiếng ở giữa 2 tay | 2 tay xòe (`bothHandsPalmOpen`) → cổng xoáy giữa 2 tay, to nhỏ theo khoảng cách 2 tay (`TwoHandDistance`, `TwoHandMidpoint`) | `black_hole_bg.webp` (ảnh tĩnh, mới) | Code đã có, cần sửa: cử chỉ hiện là `anyHandPresent`, đổi thành `bothHandsPalmOpen` cho khớp Design; thêm `background`. |
| **Vẽ canvas** (`canvas_draw`) | 1 | Không cần ảnh hiệu ứng (vẽ bằng code, chấm trắng)<br>`canvas_draw_thumbnail.webp` (mới) | `canvas_draw_clear.wav` (đổi tên từ `paper_tear.wav`): tiếng xé giấy khi xóa nét | Chỉ ngón trỏ (`singleHandPointing`) → vẽ nét theo đầu ngón trỏ<br>Nắm tay (`singleHandFist`) → xóa nét + phát tiếng<br>Bất kỳ (`anyHandPresent`) → chỉ hiện khung xương | Nền đen (`EffectBackground.Solid(R.color.black)`, đã có) | Code đã có, chỉ đổi tên file âm thanh. |

### 5.3 Asset đã có cần đổi tên

| File hiện tại | Tên mới | Ghi chú |
|---|---|---|
| `earth.webp` | `earth_planet.webp` | |
| `black_hole.webp` | `black_hole_portal.webp` | |
| `stranger_things_monster.webp` | `monster_appear.webp` | Hiện còn làm ảnh tạm ở Gojo, Vẽ canvas, Thay đổi nền |
| `stranger_things_clock.wav` | Không đổi tên | Không dùng cho Quái vật nữa (nhạc nền mới thay vào), vẫn là tiếng của state "clock" trong hiệu ứng thay đổi nền |
| `paper_tear.wav` | `canvas_draw_clear.wav` | |
| `blue_ball.webp`, `red_ball.webp` | Thay bằng `gojo_blue_ball.webp`, `gojo_red_ball.webp` | Làm mới nên không đổi tên, xóa file cũ sau khi thay |
| `fire_ball_thumbnail.webp`, `lightning_thumbnail.webp`, `magic_shield_thumbnail.webp`, `black_hole_thumbnail.webp` | Giữ nguyên | Đã đúng mẫu `<effect_id>_thumbnail` |

Trước khi đổi tên hoặc xóa file nào, kiểm tra hết chỗ tham chiếu `R.drawable.*` / `R.raw.*` (các file `stranger_things_*` còn được dùng ở nhiều chỗ). Các hiệu ứng test cũ (`rock_on_ily`, `camera_shutter`, `absolute_cinema_two_hand`, `heart_or_cross`) không nằm trong bảng và giữ nguyên asset của chúng.

### 5.4 Tổng hợp

| Loại | Số file cần làm mới |
|---|---:|
| Ảnh hiệu ứng và nền | 18 |
| Thumbnail mới (`dragon_ball`, `gojo`, `monster`, `room_teleport`, `canvas_draw`) | 5 |
| Âm thanh (tiếng hiệu ứng + nhạc nền) | 13 |
| Cử chỉ mới thêm vào `Gestures` | 2 (`anyFingerExtendedNoThumb`, `twoHandsWristsTogetherOpen`) |

Các hiệu ứng cần viết mới hoàn toàn: Cầu lửa, Vòng khiên, Tia sét, Dragon Ball (4 hiệu ứng thay dần các hiệu ứng test theo `AGENTS.md`).
