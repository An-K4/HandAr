# Kịch bản Test Thủ công — HandAr (sau refactor Phase 0-5)

> Test trên ít nhất 2 máy: 1 máy khá mới (đại diện hiệu năng tốt) + 1 máy cũ/yếu (đại diện giới hạn phần cứng).
> Ghi lại kết quả từng mục — mục nào FAIL thì note rõ hiện tượng để quay lại phase tương ứng.

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

## F. Edge case & độ bền

| # | Bước | Kỳ vọng |
|---|---|---|
| F1 | Bấm Record → Stop → Record → Stop liên tục 5 lần nhanh | Không crash, không leak `MediaCodec` (app không bị chậm dần/out-of-memory sau nhiều lần) |
| F2 | Bấm Stop ngay lập tức sau khi vừa bấm Record (chưa có frame nào kịp vẽ) | Không crash, file vẫn được tạo (có thể rất ngắn/gần như trống), `effectClock` không lỗi khi chưa từng `start()` |
| F3 | Xoay điện thoại ngang trong lúc đang quay (nếu app hỗ trợ xoay màn hình) | Không crash — nếu app khóa hướng dọc thì bỏ qua case này |
| F4 | Quay khi pin yếu / máy đang nóng (throttling) | FPS có thể giảm tạm thời nhưng không crash, video vẫn phát được bình thường |
| F5 | Kiểm tra dung lượng bộ nhớ trong (`Environment.DIRECTORY_MOVIES` của app) sau nhiều lần quay | File cũ không bị ghi đè/mất, tên file luôn unique (dựa theo timestamp) |

---

## Cách ghi kết quả

Với mỗi dòng test, đánh dấu: ✅ Pass / ❌ Fail / ⚠️ Pass có lưu ý. Nếu Fail, ghi lại: model máy, số liệu `VideoStatsLogger` (nếu có), và mô tả hiện tượng — quay lại đúng phase liên quan trong `HandAr_Refactor_Plan.md` để xử lý tiếp.
