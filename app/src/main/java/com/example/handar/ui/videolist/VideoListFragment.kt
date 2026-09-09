package com.example.handar.ui.videolist

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handar.databinding.FragmentVideoListBinding
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

        val rv = binding.recyclerVideos
        val baseTop = rv.paddingTop
        val baseBottom = rv.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rv) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }

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