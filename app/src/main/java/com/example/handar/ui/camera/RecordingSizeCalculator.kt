package com.example.handar.ui.camera

/**
 * Hàm thuần, không state, không Android API — tách khỏi `CameraRecordFragment` để Fragment chỉ còn
 * phần điều phối. Logic giữ nguyên 100% so với `computeRecordingSize` cũ.
 *
 * Thu kích thước ghi hình về [targetShortSide] ở cạnh ngắn, giữ nguyên tỉ lệ. Kết quả luôn là số
 * chẵn (`it - it % 2`) vì encoder H.264 yêu cầu chiều chia hết cho 2.
 *
 * Kích thước view không hợp lệ (<= 0) hoặc cạnh ngắn đã nhỏ hơn mục tiêu thì trả về nguyên xi,
 * **không** làm chẵn — giữ đúng hành vi bản gốc.
 */
fun computeRecordingSize(
    viewWidth: Int,
    viewHeight: Int,
    targetShortSide: Int = DEFAULT_TARGET_SHORT_SIDE
): Pair<Int, Int> {
    if (viewWidth <= 0 || viewHeight <= 0) return viewWidth to viewHeight
    val shortSide = minOf(viewWidth, viewHeight)
    if (shortSide <= targetShortSide) return viewWidth to viewHeight

    val scale = targetShortSide.toFloat() / shortSide
    val newWidth = (viewWidth * scale).toInt().let { it - it % 2 }
    val newHeight = (viewHeight * scale).toInt().let { it - it % 2 }

    return newWidth to newHeight
}

private const val DEFAULT_TARGET_SHORT_SIDE = 720
