package com.example.handar.effect.model

/** quyết định tâm asset cho một state sắp vẽ là ở đâu. */
enum class AnchorSource {
    /** khớp ngón giữa (9).
     *  mang cảm giác cân đối ở giữa bàn tay hơn so với
     *  trung điểm của đầu ngón giữa và cổ tay
     *  (kiểm chứng bằng mắt thường)
     *  - hành vi mặc định. */
    PalmCenter,

    /** trung điểm ngón cái (4) và ngón trỏ (8). ví dụ dùng cho hiệu ứng "Trái đất". */
    PinchMidpoint,

    /** đầu ngón trỏ (8). ví dụ dùng cho hiệu ứng "Gojo". */
    IndexFingertip,

    /** trung điểm tâm hai bàn tay. ví dụ dùng cho "Cổng dịch chuyển/Hố đen". */
    TwoHandMidpoint,

    /** trung điểm 2 cổ tay (landmark 0).
     *  ví dụ dùng cho hiệu ứng "Dragon Ball" state kamehameha: khi 2 cổ tay chụm sát nhau,
     *  "cổ tay" mô tả đúng hành động chụm tay hơn là khớp giữa ngón giữa. */
    TwoWristMidpoint
}