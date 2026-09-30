package com.example.handar.ui.language

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
import com.example.handar.R
import com.example.handar.databinding.FragmentLanguageBinding
import com.example.handar.databinding.ItemLanguageOptionBinding
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

// chỉ hỗ trợ 2 ngôn ngữ (en/vi) nên hiện tại không cần recyclerview.
class LanguageFragment : Fragment() {
    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LanguageViewModel by viewModels { LanguageViewModel.factory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutLanguageTopBar.applySystemBarsInsetsMargin(top = true)
        binding.scrollLanguage.applySystemBarsInsetsPadding(
            left = false, top = false, right = false, bottom = true
        )

        bindRow(
            binding.itemLanguageEn,
            R.drawable.ic_english,
            getString(R.string.language_name_english)
        ) { viewModel.select(LanguageViewModel.TAG_EN) }

        bindRow(
            binding.itemLanguageVi,
            R.drawable.ic_vietnamese,
            getString(R.string.language_name_vietnamese)
        ) { viewModel.select(LanguageViewModel.TAG_VI) }

        binding.btnLanguageBack.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedTag.collect { tag ->
                    val viSelected = tag == LanguageViewModel.TAG_VI
                    setRowSelected(binding.itemLanguageEn, !viSelected)
                    setRowSelected(binding.itemLanguageVi, viSelected)
                }
            }
        }
    }

    /**
     * Đổ icon/tên + gắn click vào 1 dòng item_language_option.xml đã include. Trạng thái chọn do
     * collector ở trên vẽ, không set ở đây — và click không tự chốt "đang chọn rồi thì bỏ qua" nữa,
     * việc đó chuyển vào LanguageViewModel.select.
     */
    private fun bindRow(
        row: ItemLanguageOptionBinding,
        iconRes: Int,
        name: String,
        onSelect: () -> Unit
    ) {
        row.iconLanguageFlag.setImageResource(iconRes)
        row.textLanguageName.text = name
        row.root.setOnClickListener { onSelect() }
    }

    private fun setRowSelected(row: ItemLanguageOptionBinding, selected: Boolean) {
        row.root.isSelected = selected

        row.checkboxLanguage.setImageResource(
            if (selected) R.drawable.ic_checkbox_circle_checked
            else R.drawable.ic_checkbox_circle_unchecked
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
