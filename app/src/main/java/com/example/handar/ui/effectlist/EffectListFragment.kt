package com.example.handar.ui.effectlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handar.databinding.FragmentEffectListBinding
import com.example.handar.effect.EffectRepository

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
        val adapter = EffectAdapter(EffectRepository.all) { effect ->
            val action = EffectListFragmentDirections.actionEffectListToCameraRecord(effect.id)
            findNavController().navigate(action)
        }
        binding.recyclerEffect.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEffect.adapter = adapter

        binding.btnVideoList.setOnClickListener {
            val action = EffectListFragmentDirections.actionEffectListToVideoList()
            findNavController().navigate(action)
        }
    }
}