package com.example.handar.ui.recordedpreview

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.handar.R
import com.example.handar.databinding.FragmentRecordedPreviewBinding
import com.example.handar.ui.widget.ConfirmDialog
import com.example.handar.ui.widget.VideoSeekBarController
import com.example.handar.utils.applySystemBarsInsetsMargin
import java.io.File

@OptIn(UnstableApi::class)
class RecordedPreviewFragment : Fragment() {
    private var _binding: FragmentRecordedPreviewBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var seekController: VideoSeekBarController? = null
    private var confirmDialog: ConfirmDialog? = null

    private val args: RecordedPreviewFragmentArgs by navArgs()
    private val videoPath: String get() = args.videoPath
    private val effectId: String get() = args.effectId

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordedPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutPreviewTopBar.root.applySystemBarsInsetsMargin(top = true)
        binding.layoutBottomContainer.applySystemBarsInsetsMargin(bottom = true)

        binding.layoutPreviewTopBar.textEffectName.text = getString(R.string.preview)
        binding.layoutPreviewTopBar.btnBack.setOnClickListener { showExitConfirm() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = showExitConfirm()
            }
        )

        setupPlayer()

        binding.btnSave.setOnClickListener { save() }
    }

    private fun setupPlayer() {
        val p = ExoPlayer.Builder(requireContext()).build()
        player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("Preview", "Lỗi phát video: ${error.errorCodeName}", error)
                val ctx = context ?: return
                Toast.makeText(ctx, getString(R.string.can_not_play_video), Toast.LENGTH_SHORT).show()
            }
        })
        binding.playerView.player = p
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
        p.prepare()
        p.playWhenReady = true

        seekController = VideoSeekBarController(
            player = p,
            seekBar = binding.seekBar,
            textPosition = binding.textPosition,
            textDuration = binding.textDuration
        ).also { it.start() }

        // design không có nút play/pause riêng: chạm vào video để tạm dừng/tiếp tục.
        // hết video thì chạm để phát lại từ đầu.
        binding.playerView.setOnClickListener {
            if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(0)
                p.play()
            } else {
                p.playWhenReady = !p.playWhenReady
            }
        }
    }

    // tạm dừng video và hỏi xác nhận thoát.
    private fun showExitConfirm() {
        if (confirmDialog?.isShowing == true) return
        player?.pause()
        confirmDialog = ConfirmDialog(
            context = requireContext(),
            message = getString(R.string.exit_without_save_message),
            negativeText = getString(R.string.exit),
            positiveText = getString(R.string.save),
            onNegative = { discardAndExit() },
            onPositive = { save() }
        ).apply {
            setOnDismissListener { player?.play() }
            show()
        }
    }

    // nhấn exit/thoát (không phải dấu x) trong dialog: người dùng cố tình bỏ video chưa lưu → xóa file rồi thoát.
    private fun discardAndExit() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.recordedPreviewFragment) return
        player?.pause()
        File(videoPath).delete()
        nav.popBackStack()
    }

    // video đã lưu sẵn từ lúc dừng ghi, save chỉ đóng vai trò điều hướng sang màn share.
    // popUpTo chính màn này tự gỡ recordedPreview khỏi back stack.
    private fun save() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.recordedPreviewFragment) return
        nav.navigate(RecordedPreviewFragmentDirections.actionRecordedPreviewToShare(videoPath, effectId, fromRecordedPreview = true))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        seekController?.release()
        seekController = null
        confirmDialog?.setOnDismissListener(null)
        confirmDialog?.dismiss()
        confirmDialog = null
        binding.playerView.player = null
        player?.release()
        player = null
        _binding = null
    }
}
