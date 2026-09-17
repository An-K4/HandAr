package com.example.handar.effect

class StrokeModel {
    private val lock = Any()
    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private var count = 0

    /** tăng mỗi lần dữ liệu đổi (thêm hoặc xoá điểm) — StrokeVisual dựa vào đây để biết có cần dựng lại Path không. */
    @Volatile
    var version = 0
        private set

    fun addPoint(normX: Float, normY: Float) {
        synchronized(lock) {
            if (count > 0) {
                val dx = normX - xs[count - 1]
                val dy = normY - ys[count - 1]
                if (dx * dx + dy * dy < MIN_DIST_NORM * MIN_DIST_NORM) return
            }
            if (count == xs.size) {
                if (count >= MAX_POINTS) {
                    System.arraycopy(xs, 1, xs, 0, count - 1)
                    System.arraycopy(ys, 1, ys, 0, count - 1)
                    count--
                } else {
                    xs = xs.copyOf(xs.size * 2)
                    ys = ys.copyOf(ys.size * 2)
                }
            }
            xs[count] = normX
            ys[count] = normY
            count++
            version++
        }
    }

    fun clear() {
        synchronized(lock) {
            count = 0
            version++
        }
    }

    fun withPoints(block: (xs: FloatArray, ys: FloatArray, count: Int) -> Unit) {
        synchronized(lock) { block(xs, ys, count) }
    }

    private companion object {
        const val INITIAL_CAPACITY = 256
        const val MAX_POINTS = 2000
        const val MIN_DIST_NORM = 0.005f
    }
}