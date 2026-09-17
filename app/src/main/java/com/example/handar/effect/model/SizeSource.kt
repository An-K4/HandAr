package com.example.handar.effect.model

/** quyết định khoảng cách để tính bán kính r cho 1 state */
enum class SizeSource {
    /** cổ tay (0) tới khớp ngón giữa (9). khoảng cách mặc định */
    PalmRadius,

    /** đầu ngón cái (4) tới đầu ngón trỏ (8). ví dụ dùng cho hiệu ứng "Trái đất". */
    PinchDistance,

    /** khoảng cách tâm hai bàn tay. ví dụ dùng cho hiệu ứng "Cổng dịch chuyển/Hố đen". */
    TwoHandDistance
}