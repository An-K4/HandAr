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
