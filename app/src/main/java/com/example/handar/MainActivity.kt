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
    private var lastPalmOpen: Boolean? = null
    private var pendingState: Boolean? = null
    private var pendingStateSince = 0L

    private data class ActiveEffect(val pcm: ShortArray, val startedAtMs: Long)

    @Volatile
    private var activeEffect: ActiveEffect? = null
    private val DEBOUNCE_MS = 200L
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
        val audioGranted = permission[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted) {
            setupMediaPipe()
            startCamera()
        } else {
            Toast.makeText(this, "Quyền truy cập camera bị từ chối.", Toast.LENGTH_SHORT).show()
        }

        if (!audioGranted) Toast.makeText(
            this,
            "Quyền truy cập mic bị từ chối.",
            Toast.LENGTH_SHORT
        ).show()
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
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
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

                        val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()
                        val timestamp = SystemClock.uptimeMillis()

                        videoRecorder?.pushFrame { canvas ->
                            val recW = canvas.width.toFloat()
                            val recH = canvas.height.toFloat()
                            val w = rotatedBitmap.width.toFloat()
                            val h = rotatedBitmap.height.toFloat()

                            val s = max(recW / w, recH / h)
                            val offsetX = (recW - w * s) / 2f
                            val offsetY = (recH - h * s) / 2f

                            val bmpMatrix = Matrix().apply {
                                setScale(-s, s)
                                postTranslate(w * s + offsetX, offsetY)
                            }
                            canvas.drawBitmap(rotatedBitmap, bmpMatrix, null)

                            latestHandResult?.let { latestHandResult ->
                                overlayView.drawHandEffects(
                                    canvas,
                                    latestHandResult,
                                    mirrorX = true,
                                    forRecording = true
                                )
                            }
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
            ).apply {
                start()
                activeEffect?.let { effect ->
                    val elapsedMs = SystemClock.elapsedRealtime() - effect.startedAtMs
                    val elapsedSamples = (elapsedMs * 44_100L / 1000L).toInt()
                    audioMixer.triggerEffect(effect.pcm, startPos = elapsedSamples)
                }
            }
        }
    }
}