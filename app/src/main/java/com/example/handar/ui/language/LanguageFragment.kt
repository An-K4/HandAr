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
import com.example.handar.utils.applySystemBarsInsetsPadding

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
        binding.root.applySystemBarsInsetsPadding()

        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTag.startsWith("vi")) {
            binding.radioLangVi.isChecked = true
        } else {
            binding.radioLangEn.isChecked = true
        }

        binding.btnDone.setOnClickListener {
            val tag = if (binding.radioGroupLanguage.checkedRadioButtonId == R.id.radio_lang_vi) {
                "vi"
            } else {
                "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}