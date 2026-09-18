package com.example.handar.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentSplashBinding
import com.example.handar.utils.applySystemBarsInsetsPadding

class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private val splashDelayMs = 1000L
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
        binding.root.applySystemBarsInsetsPadding()

        navigateRunnable = Runnable {
            if (isAdded) findNavController().navigate(R.id.action_splash_to_language)
        }
        view.postDelayed(navigateRunnable!!, splashDelayMs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        navigateRunnable?.let { _binding?.root?.removeCallbacks(it) }
        navigateRunnable = null
        _binding = null
    }
}