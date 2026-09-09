package com.example.handar.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.handar.OverlayView
import com.example.handar.R
import com.example.handar.SoundEffectPlayer
import com.example.handar.VideoRecorder
import com.example.handar.databinding.FragmentCameraRecordBinding
import com.example.handar.effect.EffectDefinition
import com.example.handar.effect.EffectRepository
import com.example.handar.effect.HandLandmarkerProvider
import com.example.handar.utils.loadWavPcm
import com.example.handar.utils.logRecordingStats
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class CameraRecordFragment : Fragment() {
    companion object {
        private const val MIN_RECORD_DURATION_MS = 1000L
        private const val DEBOUNCE_MS = 200L
    }

    private var _binding: FragmentCameraRecordBinding? = null
    private val binding get() = _binding!!

    private var overlayView: OverlayView? = null

    private var handLandmarker: HandLandmarker? = null
    private lateinit var backgroundExecutor: ExecutorService

    private var videoRecorder: VideoRecorder? = null
    private var latestHandResult: HandLandmarkerResult? = null

    private lateinit var currentEffect: EffectDefinition
    private lateinit var statePcmMap: Map<String, ShortArray>

    @Volatile
    private var latestCameraBitmap: Bitmap? = null
    private var recordingFrameThread: Thread? = null
    private var lastStateId: String? = null
    private var pendingState: String? = null
    private var pendingStateSince = 0L
    private var recordStartUiTimeMs = 0L

    private data class ActiveEffect(val pcm: ShortArray, val startedAtMs: Long)

    @Volatile
    private var activeEffect: ActiveEffect? = null
    private lateinit var soundEffectPlayer: SoundEffectPlayer

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permission ->
        val cameraGranted = permission[Manifest.permission.CAMERA] ?: false

        if (cameraGranted) {
            setupMediaPipe()
            startCamera()
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                val ctx = context ?: return@registerForActivityResult
                Toast.makeText(ctx, "Quyền truy cập camera bị từ chối", Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.permission_denied))
                    .setMessage(getString(R.string.denied_permission_message))
                    .setPositiveButton(getString(R.string.ok)) { _, _ -> findNavController().popBackStack()}
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LC_Camera", "onCreate")
        currentEffect = EffectRepository.findById(requireArguments().getString("effectId")!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("LC_Camera", "onCreateView")
        _binding = FragmentCameraRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("LC_Camera", "onViewCreated")

        val btn = binding.btnToggleRecord
        val baseMarginBottom = (btn.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(btn) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseMarginBottom + bars.bottom
            }
            insets
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()
        soundEffectPlayer =
            SoundEffectPlayer(requireContext(), currentEffect.states.map { it.soundRes })
        statePcmMap = currentEffect.states.associate {
            it.id to loadWavPcm(requireContext(), it.soundRes)
        }

        with(binding) {
            overlayView = overlay
            overlay.setEffect(currentEffect)
            btnToggleRecord.setOnClickListener { view ->
                toggleRecording()

                if (videoRecorder?.isRecording == true) {
                    view.setBackgroundResource(R.drawable.ic_stop_record)
                } else {
                    view.setBackgroundResource(R.drawable.ic_record)
                }
            }
        }

        checkAndRequestPermission()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("LC_Camera", "onDestroyView")
        val recorder = videoRecorder
        videoRecorder = null
        recordingFrameThread?.join(500)
        recordingFrameThread = null
        recorder?.stop()

        backgroundExecutor.shutdown()
        soundEffectPlayer.release()

        overlayView = null
        handLandmarker = null
        _binding = null
    }

    private fun checkAndRequestPermission() {
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA
        )

        val notGrantedPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isEmpty()) {
            setupMediaPipe()
            startCamera()
        } else {
            requestPermissionLauncher.launch(notGrantedPermissions.toTypedArray())
        }
    }

    private fun setupMediaPipe() {
        handLandmarker =
            HandLandmarkerProvider.getOrCreate(requireContext(), currentEffect.requiredNumHands)

        viewLifecycleOwner.lifecycleScope.launch {
            HandLandmarkerProvider.results.collect { (result, inputImage) ->
                latestHandResult = result

                overlayView?.setResult(result, inputImage.width, inputImage.height)
                handleGesture(result)
            }
        }
    }

    private fun handleGesture(result: HandLandmarkerResult) {
        val landmark = result.landmarks().firstOrNull()
        if (landmark == null) {
            soundEffectPlayer.stopEffect()
            videoRecorder?.audioMixer?.triggerEffect(null)
            activeEffect = null
            lastStateId = null
            pendingState = null
            pendingStateSince = 0
            return
        }

        val matchedState = currentEffect.states.firstOrNull {
            it.gesture.recognize(listOf(landmark))
        } ?: return

        val now = SystemClock.uptimeMillis()
        if (matchedState.id != pendingState) {
            pendingState = matchedState.id
            pendingStateSince = now
        } else if (lastStateId != matchedState.id && now - pendingStateSince >= DEBOUNCE_MS) {
            val pcm = statePcmMap[matchedState.id] ?: return
            activeEffect = ActiveEffect(pcm, SystemClock.elapsedRealtime())
            videoRecorder?.audioMixer?.triggerEffect(pcm)
            soundEffectPlayer.playForSound(matchedState.soundRes)
            lastStateId = matchedState.id
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val b = _binding ?: return@addListener
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(b.preview.surfaceProvider)
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
                            val matrix = Matrix().apply {
                                postRotate(rotationDegree.toFloat())
                            }

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
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, analyze)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun toggleRecording() {
        if (videoRecorder?.isRecording == true) {
            val elapsed = SystemClock.elapsedRealtime() - recordStartUiTimeMs
            if (elapsed < MIN_RECORD_DURATION_MS) {
                Toast.makeText(
                    requireContext(),
                    "Không thể dừng ngay sau khi bắt đầu ghi",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val recorderToStop = videoRecorder
            videoRecorder = null
            binding.btnToggleRecord.isEnabled = false

            recorderToStop?.stop {
                if (!isAdded) return@stop
                val nav = findNavController()
                if (nav.currentDestination?.id != R.id.cameraRecordFragment) return@stop
                val path = recorderToStop.outputFile?.absolutePath ?: return@stop
                context?.let { logRecordingStats(it, File(path)) }
                nav.navigate(
                    R.id.action_cameraRecord_to_recordedPreview,
                    bundleOf("videoPath" to path)
                )
            }
        } else {
            recordStartUiTimeMs = SystemClock.elapsedRealtime()
            val (recW, recH) = computeRecordingSize(binding.overlay.width, binding.overlay.height)
            videoRecorder = VideoRecorder(
                requireContext(),
                recW,
                recH,
                25
            ).apply {
                start()
                activeEffect?.let { effect ->
                    val elapsedMs = SystemClock.elapsedRealtime() - effect.startedAtMs
                    val elapsedSamples = (elapsedMs * 44_100L / 1000L).toInt()
                    audioMixer.triggerEffect(effect.pcm, elapsedSamples)
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
        val overlay = overlayView ?: return
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
                            overlay.drawHandEffects(
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d("LC_Camera", "onAttach")
    }

    override fun onStart() {
        super.onStart()
        Log.d("LC_Camera", "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("LC_Camera", "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d("LC_Camera", "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("LC_Camera", "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LC_Camera", "onDestroy")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d("LC_Camera", "onDetach")
    }
}