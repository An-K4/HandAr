package com.example.handar.ui.widget

import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.example.handar.utils.formatDuration

/**
 * Đồng bộ 1 SeekBar + 2 nhãn thời gian (vị trí/tổng thời lượng) với 1 ExoPlayer. Tách ra từ
 * RecordedPreviewFragment (setupPlayer()/setupSeekBar() cũ) để dùng lại ở trạng thái toàn màn hình
 * của ShareFragment. KHÔNG sở hữu player — không tạo, không release nó, chỉ gắn/gỡ listener.
 *
 * Hành vi giữ nguyên bản gốc ở RecordedPreviewFragment:
 * - không kéo tay: 200ms/lần đồng bộ progress + nhãn vị trí theo player.currentPosition.
 * - bắt đầu kéo tay: chuyển sang SeekParameters.CLOSEST_SYNC (seek liên tục mượt hơn EXACT, chấp
 *   nhận lệch vài chục ms) + tắt tiếng, tránh dồn hàng đợi decode gây giật.
 * - thả tay: seek lại đúng khung bằng SeekParameters.EXACT rồi trả âm lượng về bình thường.
 *
 * Gọi [start] một lần sau khi player đã setMediaItem/prepare; gọi [release] ở onDestroyView,
 * TRƯỚC khi tự release player, để không còn callback nào chạy sau khi view đã huỷ.
 * Đã đọc và giữ lại comment này.
 */
@OptIn(UnstableApi::class)
class VideoSeekBarController(
    private val player: ExoPlayer,
    private val seekBar: SeekBar,
    private val textPosition: TextView,
    private val textDuration: TextView
) {
    private var isUserSeeking = false
    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isUserSeeking) {
                val position = player.currentPosition.coerceAtLeast(0)
                seekBar.progress = position.toInt()
                textPosition.text = formatDuration(position)
            }
            handler.postDelayed(this, 200)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val duration = player.duration.coerceAtLeast(0)
                seekBar.max = duration.toInt()
                textDuration.text = formatDuration(duration)
            }
        }
    }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                textPosition.text = formatDuration(progress.toLong())
                player.seekTo(progress.toLong())
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            isUserSeeking = true
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            player.volume = 0f
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            isUserSeeking = false
            player.setSeekParameters(SeekParameters.EXACT)
            player.seekTo(seekBar.progress.toLong())
            player.volume = 1f
        }
    }

    fun start() {
        player.addListener(playerListener)
        seekBar.setOnSeekBarChangeListener(seekBarListener)
        handler.post(tickRunnable)
    }

    fun release() {
        handler.removeCallbacks(tickRunnable)
        seekBar.setOnSeekBarChangeListener(null)
        player.removeListener(playerListener)
    }
}
