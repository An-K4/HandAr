package com.example.handar.utils

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Double =
    hypot((a.x() - b.x()).toDouble(), (a.y() - b.y()).toDouble())

private fun crossSign(o: NormalizedLandmark, a: NormalizedLandmark, b: NormalizedLandmark): Double =
    ((a.x() - o.x()).toDouble() * (b.y() - o.y()).toDouble()) - ((a.y() - o.y()).toDouble() * (b.x() - o.x()).toDouble())

private fun isFingerExtended(
    landmark: List<NormalizedLandmark>,
    tip: Int, pip: Int,
    wrist: NormalizedLandmark
): Boolean = distance(landmark[tip], wrist) > distance(landmark[pip], wrist)

fun pointDistance(a: NormalizedLandmark, b: NormalizedLandmark): Double = distance(a, b)

fun palmLength(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark): Double =
    distance(landmark[9], wrist)

/** 4 ngón tay: trỏ, giữa, áp út, út có công thức giống nhau,
 *  tính khoảng cách từ điểm đầu ngón đến cổ tay so sánh với
 *  khoảng cách từ điểm ở giữa ngón tay đến cổ tay,
 *  nếu khoảng cách từ đầu ngón tay đến cổ tay nhỏ hơn -> gập.
 *  ngược lại là duỗi */
fun isIndexExtended(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark) =
    isFingerExtended(landmark, 8, 6, wrist)

fun isMiddleExtended(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark) =
    isFingerExtended(landmark, 12, 10, wrist)

fun isRingExtended(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark) =
    isFingerExtended(landmark, 16, 14, wrist)

fun isPinkyExtended(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark) =
    isFingerExtended(landmark, 20, 18, wrist)

/** công thức ngón cái do khác các ngón còn lại là
 *  gập theo chiều ngang chứ không phải dọc.
 *  do vậy lấy gốc ngón út làm mốc thay vì cổ tay,
 *  sau đó áp dụng cơ chế tương tự 4 ngón kia,
 *  so khoảng cách từ đầu ngón cái đến gốc ngón út với
 *  khoảng cách từ gốc ngón cái đến gốc ngón út.
 *  nếu khoảng cách từ gốc ngón cái đến
 *  gốc ngón út ngắn hơn là duỗi. ngược lại là gập.
 *  công thức này chưa chặt hoàn toàn đối với mọi kiểu tay và
 *  hình dáng tay khi gập ngón cái, sau này có thể thêm
 *  phương pháp đo góc khi gập để chính xác hơn.*/
fun isThumbExtended(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark) =
    distance(landmark[4], landmark[17]) > distance(landmark[2], landmark[17])

/** công thức tính tỉ lệ để xem
 * đầu ngón cái có chạm vào hoặc thu lại gần đầu ngón trỏ không,
 * phù hợp để kiểm tra các cử chỉ như 👌 hoặc 🤏 */
fun thumbIndexPinchRatio(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark): Double {
    val palmLength = palmLength(landmark, wrist)
    if (palmLength == 0.0) return Double.MAX_VALUE
    return distance(landmark[4], landmark[8]) / palmLength
}

fun fingerCurlRatio(
    landmark: List<NormalizedLandmark>,
    mcp: Int,
    pip: Int,
    dip: Int,
    tip: Int
): Double {
    val straight = distance(landmark[mcp], landmark[tip])
    val boneLength =
        distance(landmark[mcp], landmark[pip]) + distance(landmark[pip], landmark[dip]) + distance(
            landmark[dip],
            landmark[tip]
        )

    if (boneLength == 0.0) return 1.0
    return straight / boneLength
}

fun isIndexCurled(landmark: List<NormalizedLandmark>): Boolean =
    fingerCurlRatio(landmark, 5, 6, 7, 8) < 0.88

fun isMiddleCurled(landmark: List<NormalizedLandmark>): Boolean =
    fingerCurlRatio(landmark, 9, 10, 11, 12) < 0.88

fun isRingCurled(landmark: List<NormalizedLandmark>): Boolean =
    fingerCurlRatio(landmark, 13, 14, 15, 16) < 0.88

fun isPinkyCurled(landmark: List<NormalizedLandmark>): Boolean =
    fingerCurlRatio(landmark, 17, 18, 19, 20) < 0.88

fun isPalmOpen(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark): Boolean {
    return isThumbExtended(landmark, wrist) &&
            isIndexExtended(landmark, wrist) &&
            isMiddleExtended(landmark, wrist) &&
            isRingExtended(landmark, wrist) &&
            isPinkyExtended(landmark, wrist)
}

fun isFist(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark): Boolean {
    return !isThumbExtended(landmark, wrist) &&
            !isIndexExtended(landmark, wrist) &&
            !isMiddleExtended(landmark, wrist) &&
            !isRingExtended(landmark, wrist) &&
            !isPinkyExtended(landmark, wrist)
}

fun segmentsCross(
    a: NormalizedLandmark, b: NormalizedLandmark,
    c: NormalizedLandmark, d: NormalizedLandmark
): Boolean {
    val d1 = crossSign(c, d, a)
    val d2 = crossSign(c, d, b)
    val d3 = crossSign(a, b, c)
    val d4 = crossSign(a, b, d)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

fun isPointing(landmark: List<NormalizedLandmark>, wrist: NormalizedLandmark): Boolean =
    isIndexExtended(landmark, wrist) && !isMiddleExtended(landmark, wrist) &&
            !isRingExtended(landmark, wrist) && !isPinkyExtended(landmark, wrist)