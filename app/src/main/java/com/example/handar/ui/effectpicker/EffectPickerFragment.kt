package com.example.handar.ui.effectpicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.example.handar.R
import com.example.handar.databinding.FragmentEffectPickerBinding
import com.example.handar.effect.EffectRepository
import com.example.handar.ui.widget.GridSpacingItemDecoration
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

/**
 * màn chọn effect mở từ nút effect của màn quay (CameraRecordFragment).
 * màn này không nằm trong MainActivity.destinationsWithMainChrome nên top bar/bottom nav chung tự ẩn,
 * top bar riêng nằm trong fragment_effect_picker.xml.
 */
class EffectPickerFragment : Fragment() {

    private val args: EffectPickerFragmentArgs by navArgs()

    private var _binding: FragmentEffectPickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EffectPickerViewModel by viewModels {
        EffectPickerViewModel.factory(args.currentEffectId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEffectPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutPickerTopBar.applySystemBarsInsetsMargin(top = true)
        binding.recyclerEffectPicker.applySystemBarsInsetsPadding(
            left = false, top = false, right = false, bottom = true
        )

        val adapter = EffectPickerAdapter(EffectRepository.all) { effect ->
            viewModel.select(effect.id)
        }
        binding.recyclerEffectPicker.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerEffectPicker.addItemDecoration(GridSpacingItemDecoration(requireContext()))
        binding.recyclerEffectPicker.adapter = adapter

        binding.btnPickerBack.setOnClickListener { viewModel.onBackClicked() }
        binding.btnPickerConfirm.setOnClickListener { viewModel.onConfirmClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedEffectId.collect { id ->
                        adapter.setSelectedId(id)
                        renderConfirmEnabled(id)
                    }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // chưa chọn gì thì tick bị khoá + mờ đi (không có gì để xác nhận)
    private fun renderConfirmEnabled(selectedEffectId: String?) {
        val enabled = selectedEffectId != null
        binding.btnPickerConfirm.isEnabled = enabled
        binding.btnPickerConfirm.alpha = if (enabled) 1f else 0.4f
    }

    /**
     * Chốt cửa currentDestination giữ nguyên ở Fragment, cùng lý do bản gốc đã ghi: bấm 2 lần liên
     * tiếp thì lần 2 có thể chạy khi màn này đã bị điều hướng đi — popBackStack() lần 2 sẽ pop luôn cả
     * camera phía dưới, navigate() lần 2 thì ném IllegalArgumentException (cùng bẫy đã ghi ở stop {}
     * của màn quay).
     */
    private fun handleEvent(event: EffectPickerViewModel.Event) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.effectPickerFragment) return
        when (event) {
            EffectPickerViewModel.Event.Close -> nav.popBackStack()
            is EffectPickerViewModel.Event.OpenPreview -> nav.navigate(
                EffectPickerFragmentDirections.actionEffectPickerToEffectPreview(event.effectId)
            )
        }
    }
}
