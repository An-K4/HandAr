package com.example.handar.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.handar.audio.BgmPlayer
import com.example.handar.OverlayView
import com.example.handar.R
import com.example.handar.audio.SoundEffectPlayer
import com.example.handar.databinding.FragmentCameraRecordBinding
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.EffectRepository
import com.example.handar.effect.HandLandmarkerProvider
import com.example.handar.effect.model.StateMode
import com.example.handar.recording.VideoRecorder
import com.example.handar.ui.widget.clipRoundedCorners
import com.example.handar.utils.RecordingPerfLogger
import com.example.handar.utils.applySystemBarsInsetsMargin
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

    private val args: CameraRecordFragmentArgs by navArgs()

    private var _binding: FragmentCameraRecordBinding? = null
    private val binding get() = _binding!!

    private var backCallback: OnBackPressedCallback? = null

    private var overlayView: OverlayView? = null

    private var handLandmarker: HandLandmarker? = null
    private lateinit var backgroundExecutor: ExecutorService

    private var videoRecorder: VideoRecorder? = null
    private var latestHandResult: HandLandmarkerResult? = null

    private lateinit var currentEffect: EffectDefinition
    private lateinit var statePcmMap: Map<String, ShortArray>

    @Volatile
    private var latestCameraBitmap: Bitmap? = null

    // TẠM: đếm frame camera để đo hiệu năng, gỡ cùng RecordingPerfLogger
    @Volatile
    private var analyzerFrames = 0

    private var recordingFrameThread: Thread? = null
    private var lastStateId: String? = null
    private var pendingState: String? = null
    private var pendingStateSince = 0L

    private var recordStartUiTimeMs = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val b = _binding ?: return
            val elapsedSeconds = (SystemClock.elapsedRealtime() - recordStartUiTimeMs) / 1000
            b.tvRecordingTimer.text = DateUtils.formatElapsedTime(elapsedSeconds)
            timerHandler.postDelayed(this, 200)
        }
    }

    private data class ActiveEffect(val pcm: ShortArray, val startedAtMs: Long)

    @Volatile
    private var activeEffect: ActiveEffect? = null
    private lateinit var soundEffectPlayer: SoundEffectPlayer

    private var bgmPlayer: BgmPlayer? = null
    private var bgmPcm: ShortArray? = null

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
                Toast.makeText(ctx, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.permission_denied))
                    .setMessage(getString(R.string.denied_permission_message))
                    .setPositiveButton(getString(R.string.ok)) { _, _ -> findNavController().popBackStack() }
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentEffect = EffectRepository.findById(args.effectId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)

        // inset áp lên 2 khối bọc ngoài, không áp lên từng nút
        binding.layoutCameraTopBar.applySystemBarsInsetsMargin(top = true)
        binding.layoutCameraBottomContainer.applySystemBarsInsetsMargin(bottom = true)

        backgroundExecutor = Executors.newSingleThreadExecutor()
        soundEffectPlayer = SoundEffectPlayer(
            requireContext(),
            currentEffect.states.mapNotNull { it.soundRes }
        )
        statePcmMap = currentEffect.states.mapNotNull { state ->
            state.soundRes?.let { state.id to loadWavPcm(requireContext(), it) }
        }.toMap()

        currentEffect.bgm?.let { bgm ->
            bgmPcm = loadWavPcm(requireContext(), bgm.resId)
            bgmPlayer = BgmPlayer(requireContext(), bgm.resId, bgm.gainPercent)
            bgmPlayer?.startFromBeginning()
        }

        with(binding) {
            overlayView = overlay
            overlay.setEffect(currentEffect)
            if (currentEffect.background != null) {
                binding.preview.visibility = View.INVISIBLE
            }

            bindEffectInfo(currentEffect)
            btnBack.setOnClickListener { navigateBack() }

            btnToggleRecord.setOnClickListener { view ->
                toggleRecording()

                val isRecording = videoRecorder?.isRecording == true
                view.setBackgroundResource(
                    if (isRecording) R.drawable.ic_stop_record else R.drawable.ic_record
                )
                backCallback?.isEnabled = isRecording
                if (isRecording) hideChromeWhileRecording()
            }
        }

        checkAndRequestPermission()
    }

    override fun onResume() {
        super.onResume()
        if (videoRecorder?.isRecording != true) bgmPlayer?.startFromBeginning()
    }

    override fun onPause() {
        super.onPause()
        bgmPlayer?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopRecordingTimerUI()
        val recorder = videoRecorder
        videoRecorder = null
        recordingFrameThread?.join(500)
        recordingFrameThread = null
        recorder?.stop()

        backgroundExecutor.shutdown()
        soundEffectPlayer.release()
        bgmPlayer?.release()

        overlayView = null
        handLandmarker = null
        _binding = null
    }

    private fun navigateBack() {
        if (videoRecorder?.isRecording == true) {
            stopRecordingAndGoToPreview(ignoreMinDuration = true, showSavedToast = true)
        } else {
            findNavController().popBackStack()
        }
    }

    private fun bindEffectInfo(effect: EffectDefinition?) {
        val context = requireContext()
        binding.textEffectName.text = effect?.displayName.orEmpty()

        val thumbnail = effect?.thumbnailRes?.takeIf { it != 0 }?.let { res ->
            runCatching { ContextCompat.getDrawable(context, res) }.getOrNull()
        }
        with(binding.imgEffectThumbnail) {
            clipRoundedCorners(resources.getDimension(R.dimen.card_corner_radius))
            setImageDrawable(thumbnail) // fallback nền đen của btn_effect
        }
    }

    /**
     * bắt đầu ghi hình thì màn chỉ còn nút stop (+ đồng hồ ngay trên nút): ẩn top bar và 2 cột Effect/Action.
     * không có hàm hiện lại: dừng ghi hợp lệ luôn rời khỏi màn này (sang preview hoặc pop), nên hiện lại
     * trong lúc chờ lưu file chỉ gây nháy UI; còn bấm stop quá sớm ("recording_too_short") thì vẫn đang ghi nên
     * UI đang ẩn là đúng.
     * 2 cột dùng INVISIBLE chứ không phải GONE: chúng là 2 ô weight 1 kẹp 2 bên nút record,
     * GONE sẽ dồn hết chỗ và nút record bị lệch khỏi tâm. (đồng hồ do startRecordingTimerUI/
     * stopRecordingTimerUI tự bật/tắt đúng lúc frame đầu tiên được ghi, không xử lý ở đây.)
     */
    private fun hideChromeWhileRecording() {
        with(binding) {
            layoutCameraTopBar.visibility = View.GONE
            layoutCameraEffectColumn.visibility = View.INVISIBLE
            layoutCameraActionColumn.visibility = View.INVISIBLE
        }
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
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            if (currentEffect.stateMode == StateMode.Momentary) clearActiveEffect()
            return
        }

        val matchedState = currentEffect.states.firstOrNull { it.gesture.recognize(hands) }
        if (matchedState == null) {
            if (currentEffect.stateMode == StateMode.Momentary) clearActiveEffect()
            return
        }

        val now = SystemClock.uptimeMillis()
        if (matchedState.id != pendingState) {
            pendingState = matchedState.id
            pendingStateSince = now
        } else if (lastStateId != matchedState.id && now - pendingStateSince >= DEBOUNCE_MS) {
            lastStateId = matchedState.id

            val soundRes = matchedState.soundRes
            if (soundRes == null) {
                clearActiveEffect(keepPendingState = true)
                return
            }

            val pcm = statePcmMap[matchedState.id] ?: return
            activeEffect = ActiveEffect(pcm, SystemClock.elapsedRealtime())
            videoRecorder?.audioMixer?.triggerEffect(pcm)
            soundEffectPlayer.playForSound(soundRes)
        }
    }

    private fun clearActiveEffect(keepPendingState: Boolean = false) {
        soundEffectPlayer.stopEffect()
        videoRecorder?.audioMixer?.triggerEffect(null)
        activeEffect = null
        lastStateId = null
        if (!keepPendingState) {
            pendingState = null
            pendingStateSince = 0
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val b = _binding ?: return@addListener
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = b.preview.surfaceProvider
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

                        // TẠM: đo hiệu năng
                        if (analyzerFrames == 0) {
                            Log.i(
                                "RecPerf",
                                "bitmap analyzer = ${rotatedBitmap.width}x${rotatedBitmap.height}, " +
                                        "${rotatedBitmap.byteCount / 1024} KB/frame"
                            )
                        }
                        analyzerFrames++

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
            stopRecordingAndGoToPreview(ignoreMinDuration = false)
        } else {
            recordStartUiTimeMs = SystemClock.elapsedRealtime()
            val (recW, recH) = computeRecordingSize(binding.overlay.width, binding.overlay.height)

            videoRecorder = VideoRecorder(
                requireContext(),
                recW,
                recH,
                25
            ).apply {
                onFirstFrame = {
                    startRecordingTimerUI()
                    bgmPlayer?.startFromBeginning()
                }
                audioMixer.setBgm(bgmPcm, currentEffect.bgm?.gainPercent ?: 50)
                audioMixer.resetBgmPos()
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

    private fun startRecordingTimerUI() {
        val b = _binding ?: return
        recordStartUiTimeMs = SystemClock.elapsedRealtime()
        b.tvRecordingTimer.visibility = View.VISIBLE
        timerHandler.post(timerRunnable)
    }

    private fun stopRecordingTimerUI() {
        timerHandler.removeCallbacks(timerRunnable)
        _binding?.tvRecordingTimer?.visibility = View.GONE
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

        // TẠM: đo hiệu năng ghi hình, gỡ cùng RecordingPerfLogger
        val perf = RecordingPerfLogger(requireContext().applicationContext, { analyzerFrames })
        val budgetNs = intervalMs * 1_000_000L

        recordingFrameThread = Thread {
            perf.start()
            while (videoRecorder?.isRecording == true) {
                val frameStartNs = System.nanoTime()
                val bitmap = latestCameraBitmap
                val handResult = latestHandResult

                val effectHasBackground = currentEffect.background != null || currentEffect.states.any { it.background != null }
                if (effectHasBackground || bitmap != null) {
                    videoRecorder?.pushFrame { canvas ->
                        if (!effectHasBackground) {
                            val recW = canvas.width.toFloat()
                            val recH = canvas.height.toFloat()
                            val w = bitmap!!.width.toFloat()
                            val h = bitmap.height.toFloat()

                            val s = max(recW / w, recH / h)
                            val offsetX = (recW - w * s) / 2f
                            val offsetY = (recH - h * s) / 2f

                            val bmpMatrix = Matrix().apply {
                                setScale(-s, s)
                                postTranslate(w * s + offsetX, offsetY)
                            }
                            canvas.drawBitmap(bitmap, bmpMatrix, null)
                        }
                        overlay.drawFrame(canvas, handResult, mirrorX = true, forRecording = true)
                    }
                }

                if (videoRecorder?.writeFailed == true) {
                    timerHandler.post { onLowStorageDuringRecording() }
                    break
                }

                val workNs = System.nanoTime() - frameStartNs
                perf.onFrame(workNs, budgetNs)   // TẠM: đo hiệu năng

                val elapsedMs = workNs / 1_000_000
                val sleepMs = (intervalMs - elapsedMs).coerceAtLeast(0)
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }
            perf.finish()   // TẠM: đo hiệu năng
        }.apply { start() }
    }

    private fun onLowStorageDuringRecording() {
        _binding ?: return
        Toast.makeText(requireContext(), getString(R.string.stopped_low_storage), Toast.LENGTH_LONG)
            .show()
        stopRecordingAndGoToPreview(ignoreMinDuration = true, showSavedToast = false)
    }

    private fun stopRecordingAndGoToPreview(
        ignoreMinDuration: Boolean,
        showSavedToast: Boolean = false
    ) {
        if (!ignoreMinDuration) {
            val elapsed = SystemClock.elapsedRealtime() - recordStartUiTimeMs
            if (elapsed < MIN_RECORD_DURATION_MS) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.recording_too_short),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        stopRecordingTimerUI()
        backCallback?.isEnabled = false

        val recorderToStop = videoRecorder
        videoRecorder = null
        binding.btnToggleRecord.isEnabled = false
        binding.btnBack.isEnabled = false // tránh bấm back lần 2 khi đang dừng: lúc này isRecording đã false nên sẽ pop thẳng, mất video

        recorderToStop?.stop {
            if (!isAdded) return@stop
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.cameraRecordFragment) return@stop

            if (!recorderToStop.hadValidOutput()) {
                recorderToStop.outputFile?.delete()
                nav.popBackStack()
                return@stop
            }

            val path = recorderToStop.outputFile?.absolutePath ?: return@stop
            context?.let { logRecordingStats(it, File(path)) }
            if (showSavedToast) {
                Toast.makeText(requireContext(), getString(R.string.recording_saved_on_back), Toast.LENGTH_SHORT).show()
            }
            val action = CameraRecordFragmentDirections.actionCameraRecordToRecordedPreview(path)
            nav.navigate(action)
        }
    }
}