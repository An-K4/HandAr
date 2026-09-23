package com.example.handar.ui.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentLanguageBinding
import com.example.handar.databinding.ItemLanguageOptionBinding
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.applySystemBarsInsetsPadding

// chỉ hỗ trợ 2 ngôn ngữ (en/vi) nên hiện tại không cần recyclerview.
class LanguageFragment : Fragment() {
    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

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

        val isViSelected = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .startsWith("vi")

        bindRow(
            binding.itemLanguageEn,
            R.drawable.ic_english,
            getString(R.string.language_name_english),
            selected = !isViSelected
        ) { selectLanguage("en") }

        bindRow(
            binding.itemLanguageVi,
            R.drawable.ic_vietnamese,
            getString(R.string.language_name_vietnamese),
            selected = isViSelected
        ) { selectLanguage("vi") }

        binding.btnLanguageBack.setOnClickListener { findNavController().navigateUp() }
    }

    /** đổ icon/tên + trạng thái chọn ban đầu vào 1 dòng item_language_option.xml đã include. */
    private fun bindRow(
        row: ItemLanguageOptionBinding,
        iconRes: Int,
        name: String,
        selected: Boolean,
        onSelect: () -> Unit
    ) {
        row.iconLanguageFlag.setImageResource(iconRes)
        row.textLanguageName.text = name
        row.root.isSelected = selected

        row.checkboxLanguage.setImageResource(
            if (selected) R.drawable.ic_checkbox_circle_checked
            else R.drawable.ic_checkbox_circle_unchecked
        )

        row.root.setOnClickListener {
            if (!row.root.isSelected) onSelect()
        }
    }


    private fun selectLanguage(tag: String) {
        val selectingVi = tag == "vi"
        setRowSelected(binding.itemLanguageEn, !selectingVi)
        setRowSelected(binding.itemLanguageVi, selectingVi)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
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
