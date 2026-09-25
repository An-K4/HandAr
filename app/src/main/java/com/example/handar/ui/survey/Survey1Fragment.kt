package com.example.handar.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentSurvey1Binding
import com.example.handar.databinding.ItemSurveyAnswerBinding
import com.example.handar.utils.applySystemBarsInsetsPadding

class Survey1Fragment : Fragment() {

    companion object {
        private val ANSWERS = listOf(
            R.drawable.ic_tiktok to R.string.survey_1_answer_1,
            R.drawable.friend to R.string.survey_1_answer_2,
            R.drawable.canvas_draw_thumbnail to R.string.survey_1_answer_3,
            R.drawable.question_mark to R.string.survey_1_answer_4
        )
    }

    private var _binding: FragmentSurvey1Binding? = null
    private val binding get() = _binding!!

    private var selectedIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurvey1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applySystemBarsInsetsPadding()

        bindAnswers()

        binding.textSurveySkip.setOnClickListener {
            findNavController().navigate(R.id.action_survey1_to_permission)
        }

        binding.btnSurveyNext.setOnClickListener {
            findNavController().navigate(R.id.action_survey1_to_survey2)
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
            val (iconRes, textRes) = ANSWERS[index]
            row.iconAnswerThumbnail.setImageResource(iconRes)
            row.textAnswerName.text = getString(textRes)
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
