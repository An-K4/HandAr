package com.example.handar.ui.effectlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.handar.databinding.FragmentEffectListBinding
import com.example.handar.utils.applySystemBarsInsetsPadding
import com.example.handar.ui.widget.GridSpacingItemDecoration
import kotlinx.coroutines.launch

class EffectListFragment : Fragment() {
    private var _binding: FragmentEffectListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EffectListViewModel by viewModels {
        EffectListViewModel.factory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEffectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = EffectAdapter(
            items = emptyList(),
            favouriteIds = emptySet(),
            onClick = { effect ->
                val action = EffectListFragmentDirections.actionEffectListToEffectPreview(effect.id)
                findNavController().navigate(action)
            },
            onToggleFavourite = { effectId -> viewModel.toggleFavourite(effectId) }
        )
        binding.recyclerEffect.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerEffect.addItemDecoration(GridSpacingItemDecoration(requireContext()))
        binding.recyclerEffect.adapter = adapter
        // paddingBottom trong xml tính chiều cao thanh nav cong + inset động của thanh nav hệ thống ở đây
        // để hàng cuối luôn cuộn lên được trên cả hai thanh, không bị che mất.
        binding.recyclerEffect.applySystemBarsInsetsPadding(
            left = false, top = false, right = false, bottom = true
        )

        // Khôi phục ô tìm kiếm từ VM TRƯỚC khi gắn listener. Cần vì VM sống qua onDestroyView: đi
        // sang EffectPreview rồi back lại thì query còn trong VM nhưng EditText mới inflate là rỗng —
        // không set lại thì danh sách đang lọc mà ô tìm kiếm trống, người dùng không hiểu vì sao.
        // Set trước khi addTextChangedListener nên không tự kích hoạt listener.
        val restoredQuery = viewModel.uiState.value.query
        if (restoredQuery.isNotEmpty()) binding.searchBar.editSearchQuery.setText(restoredQuery)

        // Tìm kiếm theo tên. Việc lọc và việc "distinct" (trước đây là biến lastQuery ở đây) chuyển
        // vào EffectListViewModel.onQueryChanged.
        binding.searchBar.editSearchQuery.addTextChangedListener { editable ->
            viewModel.onQueryChanged(editable?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submit(state.items, state.favouriteIds)
                }
            }
        }
    }
}