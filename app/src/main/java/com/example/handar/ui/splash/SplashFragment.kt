package com.example.handar.ui.splash

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentSplashBinding
import com.example.handar.utils.applySystemBarsInsetsPadding

class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    // TODO: hiện chỉ là delay giả để có hiệu ứng loading. khi có logic thật cần chờ ở splash
    private val splashDelayMs = 5000L
    private var navigateRunnable: Runnable? = null

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

        navigateRunnable = Runnable {
            if (isAdded) findNavController().navigate(R.id.action_splash_to_welcome)
        }
        view.postDelayed(navigateRunnable!!, splashDelayMs)
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

        ObjectAnimator.ofInt(binding.progressSplashLoading, "progress", 0, 100).apply {
            duration = splashDelayMs
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        navigateRunnable?.let { _binding?.root?.removeCallbacks(it) }
        navigateRunnable = null
        _binding = null
    }
}