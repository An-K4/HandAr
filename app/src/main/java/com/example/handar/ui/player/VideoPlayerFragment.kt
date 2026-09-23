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
import java.io.File

@OptIn(UnstableApi::class)
class VideoPlayerFragment : Fragment() {
    companion object {
        private const val KEY_PLAYBACK_POSITION = "playback_position"
    }

    private var _binding: FragmentVideoPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var seekController: VideoSeekBarController? = null
    private var confirmDeleteDialog: ConfirmDialog? = null
    private var playbackPosition = 0L
    private val args: VideoPlayerFragmentArgs by navArgs()
    private val videoPath: String get() = args.videoPath

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackPosition = savedInstanceState?.getLong(KEY_PLAYBACK_POSITION) ?: 0L
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_PLAYBACK_POSITION, player?.currentPosition ?: playbackPosition)
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
            RenameDialog(requireContext(), file.nameWithoutExtension) { newName -> renameVideo(newName) }.show()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.menuDropdown.isVisible) closeMenu() else findNavController().popBackStack()
                }
            }
        )
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
        p.seekTo(playbackPosition)
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
            setOnDismissListener { player?.play() }
            show()
        }
    }

    private fun deleteVideoAndExit() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.videoPlayerFragment) return
        player?.pause()
        File(videoPath).delete()
        nav.popBackStack()
    }

    private fun renameVideo(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.videoPlayerFragment) return
        val oldFile = File(videoPath)
        val newFile = File(oldFile.parentFile, "$trimmed.${oldFile.extension}")

        if (newFile == oldFile) return

        if (newFile.exists()) {
            Toast.makeText(requireContext(), getString(R.string.video_name_already_exists), Toast.LENGTH_SHORT).show()
            return
        }

        if (oldFile.renameTo(newFile)) {
            Toast.makeText(requireContext(), getString(R.string.rename_successfully), Toast.LENGTH_SHORT).show()
            nav.popBackStack()
        } else {
            Toast.makeText(requireContext(), getString(R.string.can_not_rename_video), Toast.LENGTH_SHORT).show()
        }
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playbackPosition = player?.currentPosition ?: 0L
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
