package com.example.handar.ui.player

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.example.handar.databinding.FragmentVideoPlayerBinding
import java.io.File

@OptIn(UnstableApi::class)
class VideoPlayerFragment : Fragment() {
    companion object {
        private const val KEY_PLAYBACK_POSITION = "playback_position"
    }

    private var _binding: FragmentVideoPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
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

        player = ExoPlayer.Builder(requireContext()).build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("VideoPlayer", "Lỗi phát video: ${error.errorCodeName}", error)
                    val ctx = context ?: return
                    Toast.makeText(ctx, getString(R.string.can_not_play_video), Toast.LENGTH_SHORT)
                        .show()
                }
            })
            binding.playerView.player = p
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            p.seekTo(playbackPosition)
            p.prepare()
            p.playWhenReady = true
        }

        binding.playerView.keepScreenOn = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playbackPosition = player?.currentPosition ?: 0L
        binding.playerView.player = null
        player?.release()
        player = null
        _binding = null
    }
}