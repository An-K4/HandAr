package com.example.handar.ui.videolist

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handar.databinding.FragmentVideoListBinding
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

class VideoListFragment : Fragment() {
    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerVideos.applySystemBarsInsetsPadding(left = false, right = false)

        val adapter = VideoAdapter(emptyList()) { videoItem ->
            val action = VideoListFragmentDirections.actionVideoListToVideoPlayer(videoItem.file.absolutePath)
            findNavController().navigate(action)
        }
        binding.recyclerVideos.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVideos.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progress.isVisible = true
            val items = VideoRepository.loadAll(requireContext())
            binding.progress.isVisible = false
            binding.textEmpty.isVisible = items.isEmpty()
            adapter.submit(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerVideos.adapter = null
        _binding = null
    }
}