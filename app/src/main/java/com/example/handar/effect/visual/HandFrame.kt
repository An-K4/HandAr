package com.example.handar.effect.visual

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

enum class HandSide { Left, Right, Unknown }

class HandFrame {
    var hands: List<List<NormalizedLandmark>> = emptyList()
    var handedness: List<HandSide> = emptyList()

    var cx = 0f
    var cy = 0f
    var r = 0f
    var elapsedMs = 0L

    /**
     * Bitmap camera của CHÍNH frame đang vẽ (đã xoay đứng, chưa mirror, kích thước `imgWidth × imgHeight`),
     * do `OverlayView.drawFrame()` gán mỗi frame; null nếu không có. Chỉ ĐỌC — không `recycle()`, không sửa:
     * bitmap này còn được MediaPipe, vòng ghi hình và Fragment dùng chung. Không lưu lại giữa các lần `draw()`.
     */
    var cameraFrame: Bitmap? = null

    /**
     * true nếu frame này đang vẽ cho VIDEO ghi hình (do `OverlayView.drawFrame()` gán), false = vẽ live.
     * Live và recording có 2 object HandFrame riêng. Dùng khi visual cần cư xử khác nhau (vd. Finger Frame chỉ
     * ngoại suy vị trí ở live, vì video đã dùng đúng bitmap của frame đó).
     */
    var forRecording = false

    private var mirrorX = true
    private var imgWidth = 1
    private var imgHeight = 1
    private var localScale = 1f
    private var localScaleOffsetX = 0f
    private var localScaleOffsetY = 0f

    fun setProjection(
        mirrorX: Boolean,
        imgWidth: Int,
        imgHeight: Int,
        localScale: Float,
        localScaleOffsetX: Float,
        localScaleOffsetY: Float,
    ) {
        this.mirrorX = mirrorX
        this.imgWidth = imgWidth
        this.imgHeight = imgHeight
        this.localScale = localScale
        this.localScaleOffsetX = localScaleOffsetX
        this.localScaleOffsetY = localScaleOffsetY
    }

    fun px(normX: Float): Float {
        val nx = if (mirrorX) 1f - normX else normX
        return nx * imgWidth * localScale + localScaleOffsetX
    }

    fun py(normY: Float): Float = normY * imgHeight * localScale + localScaleOffsetY

    fun px(lm: NormalizedLandmark): Float = px(lm.x())
    fun py(lm: NormalizedLandmark): Float = py(lm.y())

    /**
     * Ma trận vẽ [cameraFrame] lên canvas đang vẽ, cùng phép chiếu với px()/py() (mirror + center-crop).
     * Chỉ gọi trong `draw()`, sau khi `OverlayView.drawFrame()` đã gọi `setProjection()` (xem Camera_X_Hand_Landmarker.md 13.5).
     * Tương đương `bmpMatrix` của vòng ghi hình trong `CameraRecordFragment.startRecordingFrameLoop()`.
     */
    fun cameraMatrix(out: Matrix) {
        if (mirrorX) {
            out.setScale(-localScale, localScale)
            out.postTranslate(imgWidth * localScale + localScaleOffsetX, localScaleOffsetY)
        } else {
            out.setScale(localScale, localScale)
            out.postTranslate(localScaleOffsetX, localScaleOffsetY)
        }
    }
}