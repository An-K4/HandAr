package com.example.handar.ui.effectpicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.example.handar.R
import com.example.handar.databinding.FragmentEffectPickerBinding
import com.example.handar.effect.EffectRepository
import com.example.handar.ui.widget.GridSpacingItemDecoration
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.applySystemBarsInsetsPadding

/**
 * màn chọn effect mở từ nút effect của màn quay (CameraRecordFragment).
 * màn này không nằm trong MainActivity.destinationsWithMainChrome nên top bar/bottom nav chung tự ẩn,
 * top bar riêng nằm trong fragment_effect_picker.xml.
 */
class EffectPickerFragment : Fragment() {

    private val args: EffectPickerFragmentArgs by navArgs()

    private var _binding: FragmentEffectPickerBinding? = null
    private val binding get() = _binding!!

    // effect đang được chọn (chưa xác nhận). null = chưa chọn gì (currentEffectId rỗng/không có trong
    // repository, ví dụ khi vào màn camera mà chưa có effect nào). để ở field của instance, không phải View.
    private var selectedEffectId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedEffectId = args.currentEffectId.takeIf { id -> EffectRepository.all.any { it.id == id } }
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

        val adapter = EffectPickerAdapter(EffectRepository.all, selectedEffectId) { effect ->
            selectedEffectId = effect.id
            renderConfirmEnabled()
        }
        binding.recyclerEffectPicker.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerEffectPicker.addItemDecoration(GridSpacingItemDecoration(requireContext()))
        binding.recyclerEffectPicker.adapter = adapter

        binding.btnPickerBack.setOnClickListener { cancel() }
        binding.btnPickerConfirm.setOnClickListener { confirm() }
        renderConfirmEnabled()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // chưa chọn gì thì tick bị khoá + mờ đi (không có gì để xác nhận)
    private fun renderConfirmEnabled() {
        val enabled = selectedEffectId != null
        binding.btnPickerConfirm.isEnabled = enabled
        binding.btnPickerConfirm.alpha = if (enabled) 1f else 0.4f
    }

    /**
     * cả cancel() lẫn confirm() đều chốt cửa bằng currentDestination: bấm 2 lần liên tiếp thì lần 2 có thể
     * chạy khi màn này đã bị điều hướng đi. với popBackStack() lần 2 sẽ pop luôn cả camera phía dưới,
     * với navigate() thì ném IllegalArgumentException (cùng bẫy đã ghi ở stop {} của màn quay).
     */
    private fun cancel() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.effectPickerFragment) return
        nav.popBackStack()
    }

    private fun confirm() {
        val id = selectedEffectId ?: return
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.effectPickerFragment) return

        if (id == args.currentEffectId) {
            nav.popBackStack()
        } else {
            nav.navigate(EffectPickerFragmentDirections.actionEffectPickerToCameraRecord(id))
        }
    }
}
