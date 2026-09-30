package com.example.handar.ui.videolist

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.handar.databinding.FragmentVideoListBinding
import com.example.handar.ui.widget.GridSpacingItemDecoration
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

class VideoListFragment : Fragment() {
    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoListViewModel by viewModels {
        VideoListViewModel.factory(requireContext())
    }

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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.isLoading
                    binding.textEmpty.isVisible = state.isEmpty
                    adapter.submit(state.items)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // VideoPlayerFragment đặt cờ này trước khi popBackStack về đây, sau khi XOÁ hoặc ĐỔI TÊN
        // video. Cả hai đều làm List<VideoItem> trong VideoListViewModel thành cũ: VideoItem giữ
        // File, và onClick truyền file.absolutePath sang player — path cũ sau rename thì player
        // không mở được file.
        // Chỉ load lại khi thật sự có thay đổi, không load lại mỗi lần onResume — để giữ lợi ích
        // "rời màn rồi quay lại không phải quét lại thư mục" của ViewModel.
        // Bước 3 sẽ đổi cờ này sang sự kiện của VideoPlayerViewModel.
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        if (handle.get<Boolean>(KEY_VIDEO_LIST_STALE) == true) {
            handle.remove<Boolean>(KEY_VIDEO_LIST_STALE)
            viewModel.load()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerVideos.adapter = null
        _binding = null
    }

    companion object {
        /**
         * Cờ `VideoPlayerFragment` đặt vào `savedStateHandle` của entry này khi danh sách video
         * không còn khớp với thư mục nữa (xoá hoặc đổi tên).
         */
        const val KEY_VIDEO_LIST_STALE = "video_deleted"
    }
}
