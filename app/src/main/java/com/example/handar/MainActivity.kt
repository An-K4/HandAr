package com.example.handar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnRecord: ImageButton
    private var handLandmarker: HandLandmarker? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private var videoRecorder: VideoRecorder? = null
    private var latestHandResult: HandLandmarkerResult? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupMediaPipe()
            startCamera()
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        previewView = findViewById(R.id.preview)
        overlayView = findViewById(R.id.overlay)
        btnRecord = findViewById(R.id.btn_toggle_record)

        btnRecord.setOnClickListener { view ->
            toggleRecording()

            if (videoRecorder?.isRecording == true) {
                view.setBackgroundResource(R.drawable.ic_stop_record)
            } else {
                view.setBackgroundResource(R.drawable.ic_record)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupMediaPipe()
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        backgroundExecutor.shutdown()
    }

    private fun setupMediaPipe() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(Delegate.CPU)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, inputImage ->
                runOnUiThread {
                    latestHandResult = result
                    overlayView.setResult(result, inputImage.width, inputImage.height)
                }
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyze = ImageAnalysis.Builder()
                .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(backgroundExecutor) { imageProxy ->
                        val rotationDegree = imageProxy.imageInfo.rotationDegrees
                        val bitmap = imageProxy.toBitmap()

                        val rotatedBitmap = if (rotationDegree != 0) {
                            val matrix = Matrix().apply { postRotate(rotationDegree.toFloat()) }
                            Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height,
                                matrix,
                                true
                            )
                        } else {
                            bitmap
                        }

                        val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()
                        val timestamp = SystemClock.uptimeMillis()

                        videoRecorder?.pushFrame { canvas ->
                            val recW = canvas.width.toFloat()
                            val recH = canvas.height.toFloat()
                            val bmpMatrix = Matrix().apply {
                                val s = max(recW / rotatedBitmap.width, recH / rotatedBitmap.height)
                                postScale(s, s)
                                postTranslate(
                                    (recW - rotatedBitmap.width * s) / 2f,
                                    (recH - rotatedBitmap.height * s) / 2f
                                )
                            }

                            canvas.drawBitmap(rotatedBitmap, bmpMatrix, null)
                            latestHandResult?.let { overlayView.drawHandEffects(canvas, it, false) }
                        }

                        handLandmarker?.detectAsync(mpImage, timestamp)
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analyze)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun toggleRecording() {
        if (videoRecorder?.isRecording == true) {
            videoRecorder?.stop()
            videoRecorder = null
            Toast.makeText(this, "Đã lưu video", Toast.LENGTH_SHORT).show()
        } else {
            videoRecorder = VideoRecorder(
                this,
                overlayView.width,
                overlayView.height,
                25
            ).apply { start() }
        }
    }
}