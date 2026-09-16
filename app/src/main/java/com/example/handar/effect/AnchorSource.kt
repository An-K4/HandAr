package com.example.handar.effect

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
    TwoHandMidpoint
}