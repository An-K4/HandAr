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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class ShareFragment : Fragment() {
    private var _binding: FragmentShareBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var seekController: VideoSeekBarController? = null

    // Trạng thái fullscreen mà cây View HIỆN ĐANG ở, khác với state trong VM. Cần cả hai vì
    // showFullscreen/showCard dời hẳn playerView giữa hai container — chạy lại khi đã đúng chỗ là
    // removeView + addView vô ích. View mới inflate luôn ở dạng card, nên gán lại false ở
    // onViewCreated; nếu VM đang giữ isFullscreen = true (quay lại từ back stack) thì lần collect đầu
    // sẽ tự dựng lại fullscreen.
    private var renderedFullscreen = false

    private val args: ShareFragmentArgs by navArgs()
    private val videoPath: String get() = args.videoPath
    private val effectId: String get() = args.effectId

    private val viewModel: ShareViewModel by viewModels()

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

        // Vào từ màn xem lại sau khi quay (Save) thì báo "lưu thành công"; vào từ menu ⋮ của
        // videoPlayer thì video đã lưu từ trước, tiêu đề chỉ là "Chia sẻ".
        binding.textShareTitle.setText(
            if (args.fromRecordedPreview) R.string.save_successfully else R.string.share
        )

        binding.btnHome.setOnClickListener { viewModel.goHome() }
        binding.btnExpand.setOnClickListener { viewModel.showFullscreen() }
        binding.btnCollapse.setOnClickListener { viewModel.showCard() }
        binding.btnBackFullscreen.setOnClickListener { viewModel.showCard() }
        binding.btnTryAgain.setOnClickListener { viewModel.tryAgain() }

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
                override fun handleOnBackPressed() = viewModel.onBackPressed()
            }
        )

        renderedFullscreen = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    private fun render(state: ShareUiState) {
        if (state.isFullscreen == renderedFullscreen) return
        renderedFullscreen = state.isFullscreen
        if (state.isFullscreen) applyFullscreen() else applyCard()
    }

    // Chốt cửa currentDestination ở lại Fragment, không chuyển vào VM (VM không cầm NavController).
    private fun handleEvent(event: ShareViewModel.Event) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.shareFragment) return
        when (event) {
            ShareViewModel.Event.GoHome -> nav.popBackStack(R.id.effectListFragment, false)
            // shareFragment bị popUpTo gỡ khỏi back stack (khai trong nav_graph) để giữ đúng bất biến
            // "camera luôn nằm ngay trên effectList trong back stack" — giống mọi lối vào camera khác.
            ShareViewModel.Event.TryAgain -> nav.navigate(
                ShareFragmentDirections.actionShareToCameraRecord(effectId)
            )
        }
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

    private fun applyFullscreen() {
        (binding.playerView.parent as? ViewGroup)?.removeView(binding.playerView)
        binding.fullscreenVideoContainer.addView(
            binding.playerView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        binding.groupCard.visibility = View.GONE
        binding.groupFullscreen.visibility = View.VISIBLE
    }

    private fun applyCard() {
        (binding.playerView.parent as? ViewGroup)?.removeView(binding.playerView)
        binding.cardVideoContainer.addView(
            binding.playerView,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        binding.groupFullscreen.visibility = View.GONE
        binding.groupCard.visibility = View.VISIBLE
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
