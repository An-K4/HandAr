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
        private val ANSWERS = listOf(
            R.drawable.room_teleport_thumbnail to R.string.survey_2_answer_1,
            R.drawable.black_hole_thumbnail to R.string.survey_2_answer_2,
            R.drawable.gojo_thumbnail to R.string.survey_2_answer_3,
            R.drawable.monster_thumbnail to R.string.survey_2_answer_4
        )
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
