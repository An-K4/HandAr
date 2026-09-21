package com.example.handar.ui.videolist

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.handar.databinding.FragmentVideoListBinding
import com.example.handar.ui.widget.GridSpacingItemDecoration
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

        val adapter = VideoAdapter(emptyList()) { videoItem ->
            val action = VideoListFragmentDirections.actionVideoListToVideoPlayer(videoItem.file.absolutePath)
            findNavController().navigate(action)
        }
        binding.recyclerVideos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerVideos.addItemDecoration(GridSpacingItemDecoration(requireContext()))
        binding.recyclerVideos.adapter = adapter
        // áp padding giống recycler_effect ở EffectListFragment.
        binding.recyclerVideos.applySystemBarsInsetsPadding(
            left = false, top = false, right = false, bottom = true
        )

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
