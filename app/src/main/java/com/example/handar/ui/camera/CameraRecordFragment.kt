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
import com.example.handar.OverlayView
import com.example.handar.R
import com.example.handar.audio.BgmPlayer
import com.example.handar.audio.SoundEffectPlayer
import com.example.handar.databinding.FragmentCameraRecordBinding
import com.example.handar.effect.EffectRepository
import com.example.handar.effect.HandLandmarkerProvider
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.effect.model.StateMode
import com.example.handar.recording.VideoRecorder
import com.example.handar.ui.camera.CameraRecordFragment.Companion.LOADING_OVERLAY_HINT_DELAY_MS
import com.example.handar.ui.camera.CameraRecordFragment.Companion.LOADING_OVERLAY_MIN_VISIBLE_MS
import com.example.handar.ui.widget.PermissionDeniedDialog
import com.example.handar.ui.widget.clipRoundedCorners
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.loadWavPcm
import com.example.handar.utils.openAppSettings
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class CameraRecordFragment : Fragment() {
    companion object {
        private const val MIN_RECORD_DURATION_MS = 1000L

        // Loading overlay khi HandLandmarker đang được tạo nền, xem docs/CameraLoading_Fallback_Plan.md
        private const val LOADING_OVERLAY_MIN_VISIBLE_MS = 500L
        private const val LOADING_OVERLAY_HINT_DELAY_MS = 3500L
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

    private var currentEffect: EffectDefinition? = null
    private lateinit var statePcmMap: Map<String, ShortArray>

    @Volatile
    private var latestCameraBitmap: Bitmap? = null

    // Loading overlay khi HandLandmarker đang được tạo nền, xem docs/CameraLoading_Fallback_Plan.md
    private var loadingHintJob: Job? = null
    private var loadingShownAtMs: Long = 0L

    private var recordingFrameThread: Thread? = null

    // State debounce cử chỉ + activeEffect: xem GestureStateMachine.
    private val gestureStateMachine = GestureStateMachine()

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

    private lateinit var soundEffectPlayer: SoundEffectPlayer

    private var bgmPlayer: BgmPlayer? = null
    private var bgmPcm: ShortArray? = null

    private var permissionDeniedDialog: PermissionDeniedDialog? = null

    // người dùng vừa được đẩy sang màn cài đặt quyền: khi quay lại app phải kiểm tra quyền lần nữa.
    private var awaitingSettingsResult = false

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
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentEffect = args.effectId.takeIf { it.isNotEmpty() }?.let { EffectRepository.findByIdOrNull(it) }
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
        resetGestureState()

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)

        // inset áp lên 2 khối bọc ngoài, không áp lên từng nút
        binding.layoutCameraTopBar.root.applySystemBarsInsetsMargin(top = true)
        binding.layoutCameraBottomContainer.applySystemBarsInsetsMargin(bottom = true)

        backgroundExecutor = Executors.newSingleThreadExecutor()
        soundEffectPlayer = SoundEffectPlayer(
            requireContext(),
            currentEffect?.states.orEmpty().mapNotNull { it.soundRes }
        )
        statePcmMap = currentEffect?.states.orEmpty().mapNotNull { state ->
            state.soundRes?.let { state.id to loadWavPcm(requireContext(), it) }
        }.toMap()

        currentEffect?.bgm?.let { bgm ->
            bgmPcm = loadWavPcm(requireContext(), bgm.resId)
            bgmPlayer = BgmPlayer(requireContext(), bgm.resId, bgm.gainPercent)
            bgmPlayer?.startFromBeginning()
        }

        with(binding) {
            overlayView = overlay
            currentEffect?.let { overlay.setEffect(it) }
            if (currentEffect?.background != null) {
                binding.preview.visibility = View.INVISIBLE
            }

            bindEffectInfo(currentEffect)
            layoutCameraTopBar.btnBack.setOnClickListener { navigateBack() }
            btnEffect.setOnClickListener { openEffectPicker() }

            val hasEffect = currentEffect != null
            layoutCameraActionColumn.visibility = if (hasEffect) View.VISIBLE else View.INVISIBLE
            btnAction.setOnClickListener {
                currentEffect?.let { effect -> GestureGuideDialog(requireContext(), effect).show() }
            }

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

        // quay lại từ màn cài đặt quyền: bật được thì chạy tiếp, không thì thoát màn.
        if (awaitingSettingsResult) {
            awaitingSettingsResult = false
            if (hasCameraPermission()) {
                setupMediaPipe()
                startCamera()
            } else {
                findNavController().popBackStack()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        bgmPlayer?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        permissionDeniedDialog?.dismiss()
        permissionDeniedDialog = null
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
        // camera nằm lại back stack khi sang màn chọn effect (instance sống, view chết): không để bitmap và kết quả
        // detect nặng nằm lì trong RAM suốt thời gian đó (Fragment_Review_Checklist mục 0)
        latestHandResult = null
        latestCameraBitmap = null
        _binding = null
    }

    private fun navigateBack() {
        if (videoRecorder?.isRecording == true) {
            stopRecordingAndGoToPreview(ignoreMinDuration = true, showSavedToast = true)
        } else {
            findNavController().popBackStack()
        }
    }

    /**
     * mở màn chọn effect. camera nằm lại trong back stack nên instance này sống tiếp
     * và có thể được quay lại → xem resetGestureState().
     * nút này bị ẩn khi đang ghi hình nên không thể bấm giữa lúc quay; còn chốt cửa
     * currentDestination là để chống bấm đúp (navigate lần 2 sẽ ném IllegalArgumentException).
     */
    private fun openEffectPicker() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.cameraRecordFragment) return
        nav.navigate(CameraRecordFragmentDirections.actionCameraRecordToEffectPicker(currentEffect?.id ?: ""))
    }

    /**
     * reset state nhận diện cử chỉ mỗi khi view được tạo (kể cả khi quay lại từ màn chọn effect trên cùng instance).
     * không reset thì: cử chỉ đang giơ lúc quay lại bị debounce coi là “đã kích hoạt rồi” nên không phát lại tiếng;
     * và activeEffect cũ (loa live đã bị release) sẽ bị cài vào video nếu bấm Record ngay sau đó.
     */
    private fun resetGestureState() {
        gestureStateMachine.reset()
    }

    private fun bindEffectInfo(effect: EffectDefinition?) {
        val context = requireContext()
        binding.layoutCameraTopBar.textEffectName.text = effect?.displayName.orEmpty()

        val thumbnail = effect?.thumbnailRes?.takeIf { it != 0 }?.let { res ->
            runCatching { ContextCompat.getDrawable(context, res) }.getOrNull()
        }
        with(binding.imgEffectThumbnail) {
            clipRoundedCorners(resources.getDimension(R.dimen.card_corner_radius))
            setImageDrawable(thumbnail) // fallback nền đen của btn_effect
        }
    }

    private fun hideChromeWhileRecording() {
        with(binding) {
            layoutCameraTopBar.root.visibility = View.GONE
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

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionDeniedDialog() {
        val ctx = context ?: return
        if (permissionDeniedDialog?.isShowing == true) return

        permissionDeniedDialog = PermissionDeniedDialog(
            context = ctx,
            onExit = { findNavController().popBackStack() },
            onOpenSettings = {
                awaitingSettingsResult = true
                ctx.openAppSettings()
            },
        ).also { it.show() }
    }

    private fun setupMediaPipe() {
        // FIX ANR: HandLandmarker.createFromOptions() (bên trong getOrCreate) có thể mất từ vài trăm ms
        // tới vài giây (đặc biệt Delegate.GPU phải build EGL context + compile shader) — trước đây gọi
        // đồng bộ ngay trên main thread nên treo UI thread, gây ANR khi mở camera / đổi effect.
        // Chuyển việc tạo model sang backgroundExecutor (đúng thread đang dùng cho detectAsync), chỉ quay
        // lại main thread để gán field + lắng nghe kết quả. startCamera() không cần đợi vì detectAsync()
        // đã dùng handLandmarker?.  — preview vẫn hiện ngay, hiệu ứng AR chỉ "vào" chậm 1 nhịp.
        val appContext = requireContext()
        val numHands = currentEffect?.requiredNumHands ?: 1

        showLoadingOverlay()

        viewLifecycleOwner.lifecycleScope.launch {
            val landmarker = withContext(backgroundExecutor.asCoroutineDispatcher()) {
                HandLandmarkerProvider.getOrCreate(appContext, numHands)
            }
            handLandmarker = landmarker
            hideLoadingOverlayAfterMinDuration()

            HandLandmarkerProvider.results.collect { (result, inputImage) ->
                latestHandResult = result

                overlayView?.setResult(result, inputImage.width, inputImage.height)
                handleGesture(result)
            }
        }
    }

    /**
     * Hiện lớp phủ loading NGAY LẬP TỨC (không trễ) — vào màn là thấy loading luôn, tránh 1 nhịp
     * "trống" mà người dùng có thể táy máy bấm nút trong lúc đó. Disable luôn nút effect/record/
     * action (Back vẫn bấm được). Nếu vẫn đang chờ sau [LOADING_OVERLAY_HINT_DELAY_MS], hiện thêm
     * dòng chữ phụ để người dùng không tưởng app bị đơ (case có thật từ khi có fallback GPU→CPU
     * timeout 5s). Xem [hideLoadingOverlayAfterMinDuration] cho phần đảm bảo hiện tối thiểu
     * [LOADING_OVERLAY_MIN_VISIBLE_MS] để không bị nhấp nháy khi setup xong gần như ngay lập tức.
     */
    private fun showLoadingOverlay() {
        loadingHintJob?.cancel()
        loadingShownAtMs = SystemClock.uptimeMillis()

        val b = _binding ?: return
        b.layoutCameraLoading.visibility = View.VISIBLE
        b.tvCameraLoadingHint.visibility = View.GONE
        setCameraControlsEnabled(false)

        loadingHintJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(LOADING_OVERLAY_HINT_DELAY_MS)
            _binding?.tvCameraLoadingHint?.visibility = View.VISIBLE
        }
    }

    /**
     * Đợi đủ [LOADING_OVERLAY_MIN_VISIBLE_MS] tính từ lúc [showLoadingOverlay] rồi mới ẩn + bật lại
     * nút — nếu setup xong ngay lập tức thì vẫn giữ overlay đủ nửa giây thay vì tắt-mở nhấp nháy.
     * Là suspend fun, gọi trong coroutine của setupMediaPipe() nên delay() ở đây không chặn UI.
     */
    private suspend fun hideLoadingOverlayAfterMinDuration() {
        val elapsedMs = SystemClock.uptimeMillis() - loadingShownAtMs
        val remainingMs = LOADING_OVERLAY_MIN_VISIBLE_MS - elapsedMs
        if (remainingMs > 0) delay(remainingMs)

        loadingHintJob?.cancel()
        loadingHintJob = null
        val b = _binding ?: return
        b.layoutCameraLoading.visibility = View.GONE
        setCameraControlsEnabled(true)
    }

    private fun setCameraControlsEnabled(enabled: Boolean) {
        val b = _binding ?: return
        b.btnEffect.isEnabled = enabled
        b.btnToggleRecord.isEnabled = enabled
        b.btnAction.isEnabled = enabled
    }

    private fun handleGesture(result: HandLandmarkerResult) {
        val effect = currentEffect ?: return
        val hands = result.landmarks()
        // firstOrNull trên list rỗng cũng trả null, nên hai nhánh "không có tay" và "không state nào
        // khớp" của bản gốc gộp được vào một đường matchedState == null.
        val matchedState = if (hands.isEmpty()) null else effect.states.firstOrNull { it.gesture.recognize(hands) }

        // Tên khác `result` để không che tham số HandLandmarkerResult của hàm.
        val gestureResult = gestureStateMachine.onMatchedState(
            matchedStateId = matchedState?.id,
            soundRes = matchedState?.soundRes,
            momentaryClearsOnNull = effect.stateMode == StateMode.Momentary
        )
        when (gestureResult) {
            is GestureStateMachine.Result.Activate -> {
                val pcm = statePcmMap[gestureResult.stateId] ?: return
                gestureStateMachine.setActiveEffect(pcm, SystemClock.elapsedRealtime())
                videoRecorder?.audioMixer?.triggerEffect(pcm)
                soundEffectPlayer.playForSound(gestureResult.soundRes)
            }

            GestureStateMachine.Result.Clear -> {
                soundEffectPlayer.stopEffect()
                videoRecorder?.audioMixer?.triggerEffect(null)
            }

            GestureStateMachine.Result.NoChange -> Unit
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
                audioMixer.setBgm(bgmPcm, currentEffect?.bgm?.gainPercent ?: 50)
                audioMixer.resetBgmPos()
                start()
                gestureStateMachine.activeEffect?.let { effect ->
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

        recordingFrameThread = Thread {
            while (videoRecorder?.isRecording == true) {
                val frameStartNs = System.nanoTime()
                val bitmap = latestCameraBitmap
                val handResult = latestHandResult

                val effectHasBackground = currentEffect?.background != null || currentEffect?.states.orEmpty().any { it.background != null }
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
                val elapsedMs = workNs / 1_000_000
                val sleepMs = (intervalMs - elapsedMs).coerceAtLeast(0)
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }
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
        binding.layoutCameraTopBar.btnBack.isEnabled = false // tránh bấm back lần 2 khi đang dừng: lúc này isRecording đã false nên sẽ pop thẳng, mất video

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
            if (showSavedToast) {
                Toast.makeText(requireContext(), getString(R.string.recording_saved_on_back), Toast.LENGTH_SHORT).show()
            }
            val action = CameraRecordFragmentDirections.actionCameraRecordToRecordedPreview(path, currentEffect?.id ?: "")
            nav.navigate(action)
        }
    }
}