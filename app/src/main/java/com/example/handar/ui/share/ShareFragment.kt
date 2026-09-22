package com.example.handar.ui.share

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
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
import com.example.handar.utils.applySystemBarsInsetsMargin
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

        setupPlayer()

        binding.btnHome.setOnClickListener { goHome() }
        binding.btnExpand.setOnClickListener { showFullscreen() }
        binding.btnCollapse.setOnClickListener { showCard() }
        binding.btnBackFullscreen.setOnClickListener { showCard() }
        binding.btnTryAgain.setOnClickListener { tryAgain() }

        // TODO: impl logic mở app tương ứng để share (kế hoạch mục 7.4) — commit riêng.
        binding.btnFacebook.setOnClickListener { }
        binding.btnInstagram.setOnClickListener { }
        binding.btnTiktok.setOnClickListener { }
        binding.btnYoutube.setOnClickListener { }

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
        // icon play giữa card chỉ hiện khi video KHÔNG đang phát (đang buffer/pause/ended) — ẩn ngay khi
        // đã vào phát thật (đúng yêu cầu "vào là phát luôn thì ẩn icon đi"), không cần tự theo dõi state thủ công.
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _binding?.playIndicator?.visibility = if (isPlaying) View.GONE else View.VISIBLE
            }
        })
        binding.playerView.player = p
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

    // dời player_view (giữ nguyên player đang phát, không tạo lại) sang container toàn màn hình.
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

    // dời player_view về lại card thu gọn, chèn ở index 0 để nằm DƯỚI icon play trang trí + nút expand
    // (đúng thứ tự z-order khai trong fragment_share.xml lúc đầu).
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
