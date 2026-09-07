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
import com.example.handar.utils.isPalmOpen
import com.example.handar.utils.loadWavPcm
import com.example.handar.utils.logRecordingStats
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
    companion object {
        private const val MIN_RECORD_DURATION_MS = 1000L
        private const val DEBOUNCE_MS = 200L
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnRecord: ImageButton
    private var handLandmarker: HandLandmarker? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private var videoRecorder: VideoRecorder? = null
    private var latestHandResult: HandLandmarkerResult? = null

    @Volatile
    private var latestCameraBitmap: Bitmap? = null
    private var recordingFrameThread: Thread? = null
    private var lastPalmOpen: Boolean? = null
    private var pendingState: Boolean? = null
    private var pendingStateSince = 0L
    private var recordStartUiTimeMs = 0L

    private data class ActiveEffect(val pcm: ShortArray, val startedAtMs: Long)

    @Volatile
    private var activeEffect: ActiveEffect? = null
    private val happy3SoundPcm: ShortArray by lazy { loadWavPcm(this, R.raw.happy_happy_happy_cat) }
    private val bananaCryingSoundPcm: ShortArray by lazy {
        loadWavPcm(
            this,
            R.raw.banana_cat_crying
        )
    }
    private lateinit var soundEffectPlayer: SoundEffectPlayer

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permission ->
        val cameraGranted = permission[Manifest.permission.CAMERA] ?: false

        if (cameraGranted) {
            setupMediaPipe()
            startCamera()
        } else {
            Toast.makeText(this, "Quyền truy cập camera bị từ chối.", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundEffectPlayer = SoundEffectPlayer(this)
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

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA
        )

        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            setupMediaPipe()
            startCamera()
        } else {
            requestPermissionsLauncher.launch(notGranted.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        backgroundExecutor.shutdown()
        soundEffectPlayer.release()
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

                    val landmark = result.landmarks().firstOrNull()
                    if (landmark == null) {
                        soundEffectPlayer.stopEffect()
                        videoRecorder?.audioMixer?.triggerEffect(null)
                        activeEffect = null
                        lastPalmOpen = null
                        pendingState = null
                        pendingStateSince = 0
                        return@runOnUiThread
                    }

                    val wrist = landmark[0]
                    val open = isPalmOpen(landmark, wrist)
                    val now = SystemClock.uptimeMillis()

                    if (open != pendingState) {
                        pendingState = open
                        pendingStateSince = now
                    } else if (lastPalmOpen != open && now - pendingStateSince >= DEBOUNCE_MS) {
                        val pcm = if (open) happy3SoundPcm else bananaCryingSoundPcm
                        activeEffect = ActiveEffect(pcm, SystemClock.elapsedRealtime())
                        videoRecorder?.audioMixer?.triggerEffect(pcm)
                        soundEffectPlayer.playForGesture(open)
                        lastPalmOpen = open
                    }
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

                        latestCameraBitmap = rotatedBitmap

                        val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()
                        val timestamp = SystemClock.uptimeMillis()
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
            val elapsed = SystemClock.elapsedRealtime() - recordStartUiTimeMs
            if (elapsed < MIN_RECORD_DURATION_MS) {
                Toast.makeText(this, "Không thể dừng ngay sau khi bắt đầu ghi", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            val recorderToStop = videoRecorder
            videoRecorder = null
            btnRecord.isEnabled = false

            recorderToStop?.stop {
                btnRecord.isEnabled = true
                Toast.makeText(this, "Đã lưu video", Toast.LENGTH_SHORT).show()
                recorderToStop.outputFile?.let { file ->
                    logRecordingStats(this, file)
                }
            }
        } else {
            recordStartUiTimeMs = SystemClock.elapsedRealtime()
            val (recW, recH) = computeRecordingSize(overlayView.width, overlayView.height)
            videoRecorder = VideoRecorder(
                this,
                recW,
                recH,
                25
            ).apply {
                start()
                activeEffect?.let { effect ->
                    val elapsedMs = SystemClock.elapsedRealtime() - effect.startedAtMs
                    val elapsedSamples = (elapsedMs * 44_100L / 1000L).toInt()
                    audioMixer.triggerEffect(effect.pcm, startPos = elapsedSamples)
                }
            }

            startRecordingFrameLoop(25)
        }
    }

    private fun computeRecordingSize(
        viewWidth: Int,
        viewHeight: Int,
        targetShortSide: Int = 720
    ): Pair<Int, Int> {
        if (viewWidth <= 0 || viewHeight <= 0) return viewWidth to viewHeight
        val shortSide = minOf(viewWidth, viewHeight)
        if (shortSide <= targetShortSide) return viewWidth to viewHeight

        val scale = targetShortSide.toFloat() / shortSide
        val newWidth = (viewWidth * scale).toInt().let { it - it % 2 }
        val newHeight = (viewHeight * scale).toInt().let { it - it % 2 }
        return newWidth to newHeight
    }

    private fun startRecordingFrameLoop(fps: Int) {
        val intervalMs = 1000L / fps
        recordingFrameThread = Thread {
            while (videoRecorder?.isRecording == true) {
                val frameStartNs = System.nanoTime()
                val bitmap = latestCameraBitmap
                val handResult = latestHandResult

                if (bitmap != null) {
                    videoRecorder?.pushFrame { canvas ->
                        val recW = canvas.width.toFloat()
                        val recH = canvas.height.toFloat()
                        val w = bitmap.width.toFloat()
                        val h = bitmap.height.toFloat()

                        val s = max(recW / w, recH / h)
                        val offsetX = (recW - w * s) / 2f
                        val offsetY = (recH - h * s) / 2f

                        val bmpMatrix = Matrix().apply {
                            setScale(-s, s)
                            postTranslate(w * s + offsetX, offsetY)
                        }
                        canvas.drawBitmap(bitmap, bmpMatrix, null)

                        handResult?.let {
                            overlayView.drawHandEffects(
                                canvas,
                                it,
                                mirrorX = true,
                                forRecording = true
                            )
                        }
                    }
                }

                val elapsedMs = (System.nanoTime() - frameStartNs) / 1_000_000
                val sleepMs = (intervalMs - elapsedMs).coerceAtLeast(0)
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }
        }.apply { start() }
    }
}