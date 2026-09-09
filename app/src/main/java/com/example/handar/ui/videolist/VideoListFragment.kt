package com.example.handar.ui.videolist

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handar.R
import com.example.handar.databinding.FragmentEffectListBinding
import com.example.handar.databinding.FragmentVideoListBinding
import kotlinx.coroutines.launch
import java.io.File

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

        val adapter = VideoListAdapter(emptyList()) { videoItem ->
            context?.let { Toast.makeText(it, "Clicked: ${videoItem.file}", Toast.LENGTH_SHORT).show() }
        }
        binding.recyclerVideos.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVideos.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val elapsed = SystemClock.elapsedRealtime()
            binding.progress.isVisible = true
            val items = VideoRepository.loadAll(requireContext())
            binding.progress.isVisible = false
            binding.textEmpty.isVisible = items.isEmpty()
            adapter.submit(items)
            Log.i("VideoListFragment", "Loaded ${items.size} videos in ${SystemClock.elapsedRealtime() - elapsed}ms")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerVideos.adapter = null
        _binding = null
    }
}