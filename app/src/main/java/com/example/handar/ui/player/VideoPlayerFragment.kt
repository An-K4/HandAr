package com.example.handar.ui.player

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.handar.R
import com.example.handar.databinding.FragmentVideoPlayerBinding
import com.example.handar.ui.widget.ConfirmDialog
import com.example.handar.ui.widget.RenameDialog
import com.example.handar.ui.widget.VideoSeekBarController
import com.example.handar.utils.applySystemBarsInsetsMargin
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class VideoPlayerFragment : Fragment() {
    private var _binding: FragmentVideoPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var seekController: VideoSeekBarController? = null
    private var confirmDeleteDialog: ConfirmDialog? = null

    // Xoá file giờ chạy bất đồng bộ trong VM, nên dialog dismiss XONG TRƯỚC khi Fragment nhận
    // Event.Deleted và popBackStack. Không có cờ này thì setOnDismissListener gọi player.play() và
    // video (đã bị xoá) phát tiếp một nhịp trước khi thoát màn. Cùng dạng với cờ `discarding` ở
    // RecordedPreviewFragment.
    private var deleting = false

    private val args: VideoPlayerFragmentArgs by navArgs()
    private val videoPath: String get() = args.videoPath

    private val viewModel: VideoPlayerViewModel by viewModels {
        VideoPlayerViewModel.factory(videoPath)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.applySystemBarsInsetsMargin(top = true)
        binding.btnMenu.applySystemBarsInsetsMargin(top = true)
        binding.layoutBottomBarPlayer.applySystemBarsInsetsMargin(bottom = true)

        val file = File(videoPath)
        if (!file.exists()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.can_not_play_video),
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
            return
        }

        setupPlayer(file)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnMenu.setOnClickListener { toggleMenu() }
        binding.scrimDropdown.setOnClickListener { closeMenu() }

        binding.itemDelete.setOnClickListener {
            closeMenu()
            showDeleteConfirm()
        }

        binding.itemShare.setOnClickListener {
            closeMenu()
            findNavController().navigate(
                VideoPlayerFragmentDirections.actionVideoPlayerToShare(videoPath, effectId = "")
            )
        }

        binding.itemRename.setOnClickListener {
            closeMenu()
            RenameDialog(requireContext(), file.nameWithoutExtension) { newName -> viewModel.rename(newName) }.show()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.menuDropdown.isVisible) closeMenu() else findNavController().popBackStack()
                }
            }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }
    }

    // Toast và chốt cửa currentDestination ở lại Fragment — VM không cầm Context lẫn NavController.
    private fun handleEvent(event: VideoPlayerViewModel.Event) {
        when (event) {
            VideoPlayerViewModel.Event.Deleted -> exitAfterFileChanged()

            VideoPlayerViewModel.Event.Renamed -> {
                Toast.makeText(requireContext(), getString(R.string.rename_successfully), Toast.LENGTH_SHORT).show()
                exitAfterFileChanged()
            }

            VideoPlayerViewModel.Event.RenameNameExists ->
                Toast.makeText(requireContext(), getString(R.string.video_name_already_exists), Toast.LENGTH_SHORT).show()

            VideoPlayerViewModel.Event.RenameFailed ->
                Toast.makeText(requireContext(), getString(R.string.can_not_rename_video), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Không cần báo gì cho `VideoListFragment`: nó load lại mỗi lần view được tạo, mà pop về đây là
     * tạo lại view. Cờ `KEY_VIDEO_LIST_STALE` cũ đã bỏ — xem `MVVM_Migration_Plan.md` mục 4.
     */
    private fun exitAfterFileChanged() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.videoPlayerFragment) return
        nav.popBackStack()
    }

    private fun setupPlayer(file: File) {
        val p = ExoPlayer.Builder(requireContext()).build()
        player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayer", "Lỗi phát video: ${error.errorCodeName}", error)
                val ctx = context ?: return
                Toast.makeText(ctx, getString(R.string.can_not_play_video), Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _binding?.playIndicator?.visibility = if (isPlaying) View.GONE else View.VISIBLE
            }
        })
        binding.playerView.player = p
        binding.playerView.keepScreenOn = true

        binding.playerView.setOnClickListener {
            if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(0)
                p.play()
            } else if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
        }
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.seekTo(viewModel.playbackPosition)
        p.prepare()
        p.playWhenReady = true

        seekController = VideoSeekBarController(
            player = p,
            seekBar = binding.seekBarPlayer,
            textPosition = binding.textPositionPlayer,
            textDuration = binding.textDurationPlayer
        ).also { it.start() }
    }

    private fun toggleMenu() {
        if (binding.menuDropdown.isVisible) closeMenu() else openMenu()
    }

    private fun showDeleteConfirm() {
        if (confirmDeleteDialog?.isShowing == true) return
        player?.pause()
        confirmDeleteDialog = ConfirmDialog(
            context = requireContext(),
            message = getString(R.string.delete_video_message),
            negativeText = getString(R.string.cancel),
            positiveText = getString(R.string.delete),
            onNegative = { },
            onPositive = { deleteVideoAndExit() }
        ).apply {
            setOnDismissListener { if (!deleting) player?.play() }
            show()
        }
    }

    private fun deleteVideoAndExit() {
        if (findNavController().currentDestination?.id != R.id.videoPlayerFragment) return
        player?.pause()
        deleting = true
        // Xoá file chạy trên Dispatchers.IO trong VM, thoát màn khi nhận Event.Deleted.
        viewModel.delete()
    }

    private fun openMenu() {
        binding.menuDropdown.isVisible = true
        binding.scrimDropdown.isVisible = true
    }

    private fun closeMenu() {
        binding.menuDropdown.isVisible = false
        binding.scrimDropdown.isVisible = false
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
        // Thay cho onSaveInstanceState cũ: onStop luôn chạy trước khi hệ thống lưu state, nên đây là
        // chỗ chắc chắn nhất để đẩy vị trí hiện tại vào SavedStateHandle.
        player?.let { viewModel.playbackPosition = it.currentPosition }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Không dùng `?: 0L` như bản gốc: onStop đã ghi vị trí vào VM rồi, player null ở đây thì
        // giữ nguyên giá trị cũ chứ không xoá về 0.
        player?.let { viewModel.playbackPosition = it.currentPosition }
        seekController?.release()
        seekController = null
        confirmDeleteDialog?.setOnDismissListener(null)
        confirmDeleteDialog?.dismiss()
        confirmDeleteDialog = null
        binding.playerView.player = null
        player?.release()
        player = null
        _binding = null
    }
}
