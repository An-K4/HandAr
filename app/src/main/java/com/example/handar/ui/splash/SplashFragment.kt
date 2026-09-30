package com.example.handar.ui.splash

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentSplashBinding
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SplashViewModel by viewModels()

    // ObjectAnimator chạy vô hạn tới khi bị cancel, và nó giữ tham chiếu tới ProgressBar. Bản gốc
    // không giữ tham chiếu nên không huỷ được ở onDestroyView — xem ghi chú fix ở mốc 6.1.
    private var loadingBarAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.layoutSplashLoading.applySystemBarsInsetsPadding()

        bindLoadingHintByLocale()
        setupLoadingBar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ready.collect {
                    // Chốt cửa currentDestination thay cho `isAdded` của bản gốc: mạnh hơn, và đồng bộ
                    // với các màn khác. action_splash_to_welcome có popUpToInclusive nên navigate lần
                    // hai sẽ ném IllegalArgumentException.
                    val nav = findNavController()
                    if (nav.currentDestination?.id != R.id.splashFragment) return@collect
                    nav.navigate(R.id.action_splash_to_welcome)
                }
            }
        }
    }

    private fun bindLoadingHintByLocale() {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (!appLocales.isEmpty) {
            appLocales.toLanguageTags()
        } else {
            resources.configuration.locales[0].toLanguageTag()
        }
        binding.tvSplashLoadingHint.text = getString(R.string.splash_loading_hint)
        Log.d("SplashFragment", "Hiển thị splash theo locale: $currentTag")
    }

    private fun setupLoadingBar() {
        val barWidth = (resources.displayMetrics.widthPixels * 0.75f).toInt()
        binding.progressSplashLoading.updateLayoutParams { width = barWidth }

        // Tiếp tục từ đúng chỗ đang dở thay vì chạy lại từ 0: cần cho trường hợp view bị tạo lại
        // giữa splash (đổi font scale hệ thống chẳng hạn) — đồng hồ chờ nằm ở VM nên nó ĐẾM TIẾP,
        // nếu thanh loading reset về 0 với duration đủ SPLASH_DELAY_MS thì nó mới chạy được một phần
        // là đã chuyển màn.
        val remainingMs = viewModel.remainingMs
        val startProgress = (100 - remainingMs * 100 / SplashViewModel.SPLASH_DELAY_MS).toInt()

        if (remainingMs <= 0L) {
            // Hết thời gian rồi (sự kiện ready đang trên đường tới): đặt thẳng 100, không animate
            // với duration 0.
            binding.progressSplashLoading.progress = 100
            return
        }

        loadingBarAnimator = ObjectAnimator.ofInt(
            binding.progressSplashLoading, "progress", startProgress, 100
        ).apply {
            // Tuyến tính, KHÔNG để mặc định AccelerateDecelerateInterpolator: startProgress ở trên
            // tính tuyến tính theo thời gian, nếu animator chạy theo đường cong ease thì hai bên lệch
            // — giữa quãng, thời gian đi 66% nhưng đường cong đã ở ~72%, nên lúc view bị tạo lại thanh
            // giật lùi. Thanh tiến độ tuyến tính cũng đúng bản chất hơn.
            interpolator = LinearInterpolator()
            duration = remainingMs
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadingBarAnimator?.cancel()
        loadingBarAnimator = null
        _binding = null
    }
}