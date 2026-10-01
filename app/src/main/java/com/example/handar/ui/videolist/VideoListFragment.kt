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

        // Load lại mỗi lần VIEW được tạo, không phải mỗi lần VM được tạo.
        //
        // Lý do: màn này nằm trong back stack gần như suốt phiên (tab Bộ sưu tập) nên VM sống dai hơn
        // view rất nhiều. Mọi đường làm danh sách lệch khỏi thư mục đều phải được phủ, kể cả đường
        // KHÔNG đi qua VideoPlayerFragment — video mới quay đi camera → recordedPreview → share →
        // popBackStack về effectList, không chạm màn này một lần nào. Mọi cách quay lại đây (pop từ
        // player, chuyển tab, back từ camera) đều tạo lại view, nên đây là chỗ duy nhất phủ hết.
        //
        // Đánh đổi đã chấp nhận: quét lại thư mục mỗi lần vào màn. Xem MVVM_Migration_Plan.md mục 4.
        //
        // Đây là nguồn gọi load() DUY NHẤT — VM không có `init { load() }` (có thì mỗi lần vào màn với
        // VM mới sẽ quét thư mục hai lần). Xem comment trong VideoListViewModel.
        viewLifecycleOwner.lifecycleScope.launch { viewModel.load() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerVideos.adapter = null
        _binding = null
    }

}
