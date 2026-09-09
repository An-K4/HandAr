package com.example.handar.ui.preview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentRecordedPreviewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RecordedPreviewFragment : Fragment() {
    private var _binding: FragmentRecordedPreviewBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private lateinit var videoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoPath = requireArguments().getString("videoPath")!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordedPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val row = binding.buttonRow
        val basePaddingBottom = row.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(row) { v, insets ->
            val bar = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(bottom = basePaddingBottom + bar.bottom)
            insets
        }

        player = ExoPlayer.Builder(requireContext()).build().also { p ->
            p.addListener(object : Player.Listener{
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("Preview", "Lỗi phát video: ${error.errorCodeName}", error)
                    val ctx = context ?: return
                    Toast.makeText(ctx, "Không phát được video", Toast.LENGTH_SHORT).show()
                }
            })
            binding.playerView.player = p
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            p.prepare()
            p.playWhenReady = true
        }

        binding.btnDone.setOnClickListener { v ->
            findNavController().popBackStack()
        }

        binding.btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_confirm))
                .setMessage(getString(R.string.delete_warning))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteVideo()}
                .show()
        }

        binding.btnShare.setOnClickListener {
            val ctx = requireContext()
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                File(videoPath)
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }
    }

    private fun deleteVideo() {
        player?.stop()
        val isDeleted = File(videoPath).delete()
        if (isDeleted) {
            findNavController().popBackStack()
        } else {
            val ctx = context ?: return
            Toast.makeText(ctx, "Không thể xóa video", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.playerView.player = null
        player?.release()
        player = null
        _binding = null
    }
}