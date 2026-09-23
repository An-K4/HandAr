package com.example.handar.ui.share

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.handar.R
import com.example.handar.databinding.FragmentShareBinding
import com.example.handar.ui.widget.VideoSeekBarController
import com.example.handar.ui.widget.clipRoundedCorners
import com.example.handar.utils.SocialTarget
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.shareVideoToSocialApp
import java.io.File

@OptIn(UnstableApi::class)
class ShareFragment : Fragment() {
    private var _binding: FragmentShareBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var seekController: VideoSeekBarController? = null
    private var isFullscreen = false

    private val args: ShareFragmentArgs by navArgs()
    private val videoPath: String get() = args.videoPath
    private val effectId: String get() = args.effectId

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textShareTitle.applySystemBarsInsetsMargin(top = true)
        binding.layoutSocialIcons.applySystemBarsInsetsMargin(bottom = true)
        binding.layoutFullscreenBottomBar.applySystemBarsInsetsMargin(bottom = true)

        // android:clipToOutline="true" trong xml chỉ có hiệu lực từ api 31. gọi lại bằng code để đảm bảo bị cắt đúng trên
        // cả máy api < 31. Chỉ clip card_video_container (không clip fullscreen_video_container).
        binding.cardVideoContainer.clipRoundedCorners(resources.getDimension(R.dimen.card_corner_radius))

        setupPlayer()

        binding.btnTryAgain.isVisible = args.fromRecordedPreview

        binding.btnHome.setOnClickListener { goHome() }
        binding.btnExpand.setOnClickListener { showFullscreen() }
        binding.btnCollapse.setOnClickListener { showCard() }
        binding.btnBackFullscreen.setOnClickListener { showCard() }
        binding.btnTryAgain.setOnClickListener { tryAgain() }

        val caption = getString(R.string.share_caption_template)
        binding.btnFacebook.setOnClickListener {
            shareVideoToSocialApp(requireContext(), SocialTarget.FACEBOOK, File(videoPath), caption)
        }
        binding.btnInstagram.setOnClickListener {
            shareVideoToSocialApp(requireContext(), SocialTarget.INSTAGRAM, File(videoPath), caption)
        }
        binding.btnTiktok.setOnClickListener {
            shareVideoToSocialApp(requireContext(), SocialTarget.TIKTOK, File(videoPath), caption)
        }
        binding.btnYoutube.setOnClickListener {
            shareVideoToSocialApp(requireContext(), SocialTarget.YOUTUBE, File(videoPath), caption)
        }

        // hệ thống: đang fullscreen thì thu nhỏ trước (cùng hành vi nút back/thu nhỏ trong ảnh 2),
        // đang ở card thì về thẳng home — theo đúng xác nhận của người dùng cho câu hỏi 2/3.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isFullscreen) showCard() else goHome()
                }
            }
        )
    }

    private fun setupPlayer() {
        val p = ExoPlayer.Builder(requireContext()).build()
        player = p
        p.repeatMode = Player.REPEAT_MODE_ONE
        // icon play giữa card chỉ hiện khi video đang không phát (buffer/pause/ended).
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _binding?.playIndicator?.visibility = if (isPlaying) View.GONE else View.VISIBLE
            }
        })
        binding.playerView.player = p
        // bấm vào video để tạm dừng/phát tiếp. p.isPlaying thay vì so playWhenReady vì nó
        // tính cả trạng thái buffer/suppress của ExoPlayer, tránh lệch với trạng thái phát thật.
        // listener đặt thẳng trên player_view (không phải card_video_container) nên vẫn hoạt
        // động đúng khi player_view được dời sang fullscreen_video_container.
        binding.playerView.setOnClickListener {
            if (p.isPlaying) p.pause() else p.play()
        }
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
        p.prepare()
        p.playWhenReady = true

        seekController = VideoSeekBarController(
            player = p,
            seekBar = binding.seekBarShare,
            textPosition = binding.textPositionShare,
            textDuration = binding.textDurationShare
        ).also { it.start() }
    }

    private fun showFullscreen() {
        if (isFullscreen) return
        isFullscreen = true
        (binding.playerView.parent as? ViewGroup)?.removeView(binding.playerView)
        binding.fullscreenVideoContainer.addView(
            binding.playerView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        binding.groupCard.visibility = View.GONE
        binding.groupFullscreen.visibility = View.VISIBLE
    }

    private fun showCard() {
        if (!isFullscreen) return
        isFullscreen = false
        (binding.playerView.parent as? ViewGroup)?.removeView(binding.playerView)
        binding.cardVideoContainer.addView(
            binding.playerView,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        binding.groupFullscreen.visibility = View.GONE
        binding.groupCard.visibility = View.VISIBLE
    }

    private fun goHome() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.shareFragment) return
        nav.popBackStack(R.id.effectListFragment, false)
    }

    // shareFragment bị popUpTo gỡ khỏi back stack (khai trong nav_graph) để giữ đúng bất biến
    // "camera luôn nằm ngay trên effectList trong back stack" — giống mọi lối vào camera khác.
    private fun tryAgain() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.shareFragment) return
        nav.navigate(ShareFragmentDirections.actionShareToCameraRecord(effectId))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        seekController?.release()
        seekController = null
        binding.playerView.player = null
        player?.release()
        player = null
        _binding = null
    }
}
