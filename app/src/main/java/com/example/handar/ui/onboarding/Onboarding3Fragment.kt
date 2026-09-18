package com.example.handar.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentOnboarding3Binding
import com.example.handar.utils.applySystemBarsInsetsPadding

class Onboarding3Fragment : Fragment() {
    private var _binding: FragmentOnboarding3Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applySystemBarsInsetsPadding()

        binding.btnNext.setOnClickListener {
            findNavController().navigate(R.id.action_onboarding3_to_survey)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}