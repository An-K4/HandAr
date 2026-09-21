package com.example.handar.ui.effectlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.handar.databinding.FragmentEffectListBinding
import com.example.handar.effect.EffectRepository
import com.example.handar.utils.applySystemBarsInsetsPadding

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
        binding.recyclerEffect.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerEffect.addItemDecoration(EffectGridSpacingDecoration(requireContext()))
        binding.recyclerEffect.adapter = adapter
        // paddingBottom trong xml tính chiều cao thanh nav cong + inset động của thanh nav hệ thống ở đây
        // để hàng cuối luôn cuộn lên được trên cả hai thanh, không bị che mất.
        binding.recyclerEffect.applySystemBarsInsetsPadding(
            left = false, top = false, right = false, bottom = true
        )

        // tìm kiếm theo tên. list đơn giản ít phần tử nên filter thẳng qua EffectRepository.findByName.
        // lastQuery giữ vai trò "distinct": chỉ lọc lại khi có thay đổi.
        var lastQuery: String? = null
        binding.searchBar.editSearchQuery.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty()
            if (query == lastQuery) return@addTextChangedListener
            lastQuery = query
            adapter.updateItems(EffectRepository.findByName(query))
        }
    }
}