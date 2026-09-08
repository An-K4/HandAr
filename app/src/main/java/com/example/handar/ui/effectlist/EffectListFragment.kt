package com.example.handar.ui.effectlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentEffectListBinding

class EffectListFragment : Fragment() {
    private var _binding: FragmentEffectListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEffectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnEffectList.setOnClickListener {
            findNavController().navigate(
                R.id.action_effectList_to_cameraRecord,
                bundleOf("effectId" to "happy_cat")
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}