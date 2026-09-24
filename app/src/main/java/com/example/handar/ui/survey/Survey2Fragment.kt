package com.example.handar.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentSurvey2Binding
import com.example.handar.databinding.ItemSurveyAnswerBinding
import com.example.handar.utils.applySystemBarsInsetsPadding

class Survey2Fragment : Fragment() {

    companion object {
        // TẠM: chưa có ảnh riêng cho từng đáp án, dùng chung 1 ảnh có sẵn để dựng ui
        private val DEMO_ANSWER_THUMBNAIL_RES = R.drawable.fire_ball_thumbnail
    }

    private var _binding: FragmentSurvey2Binding? = null
    private val binding get() = _binding!!

    private var selectedIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurvey2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applySystemBarsInsetsPadding()

        bindAnswers()

        binding.textSurveySkip.setOnClickListener {
            findNavController().navigate(R.id.action_survey2_to_permission)
        }

        binding.btnSurveyFinish.setOnClickListener {
            findNavController().navigate(R.id.action_survey2_to_permission)
        }
    }

    private fun bindAnswers() {
        val rows = listOf(
            binding.itemSurveyAnswer1,
            binding.itemSurveyAnswer2,
            binding.itemSurveyAnswer3,
            binding.itemSurveyAnswer4
        )

        rows.forEachIndexed { index, row ->
            row.iconAnswerThumbnail.setImageResource(DEMO_ANSWER_THUMBNAIL_RES)
            row.textAnswerName.text = getString(R.string.survey_answer_placeholder, index + 1)
            setRowSelected(row, index == selectedIndex)

            row.root.setOnClickListener {
                if (index == selectedIndex) return@setOnClickListener
                setRowSelected(rows[selectedIndex], false)
                selectedIndex = index
                setRowSelected(row, true)
            }
        }
    }

    private fun setRowSelected(row: ItemSurveyAnswerBinding, selected: Boolean) {
        row.root.isSelected = selected
        row.checkboxAnswer.setImageResource(
            if (selected) R.drawable.ic_checkbox_circle_checked
            else R.drawable.ic_checkbox_circle_unchecked
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
