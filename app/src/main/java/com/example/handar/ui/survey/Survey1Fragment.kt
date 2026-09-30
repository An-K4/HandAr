package com.example.handar.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.handar.R
import com.example.handar.databinding.FragmentSurvey1Binding
import com.example.handar.databinding.ItemSurveyAnswerBinding
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

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

    // VM dùng chung với Survey2Fragment — xem SurveyViewModel.
    private val viewModel: SurveyViewModel by navGraphViewModels(R.id.nav_graph) {
        SurveyViewModel.factory(requireContext())
    }

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

        val rows = bindAnswers()

        // Skip = rời cụm Survey nên lưu luôn; answer2 còn null nên Repository xoá khoá answer_2.
        binding.textSurveySkip.setOnClickListener {
            viewModel.submit()
            findNavController().navigate(R.id.action_survey1_to_permission)
        }

        // Next chưa lưu: còn một màn nữa mới rời cụm Survey.
        binding.btnSurveyNext.setOnClickListener {
            findNavController().navigate(R.id.action_survey1_to_survey2)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    rows.forEachIndexed { index, row ->
                        setRowSelected(row, index == state.answer1Index)
                    }
                }
            }
        }
    }

    /** Đổ icon/tên + gắn click. Trạng thái chọn do collector ở trên vẽ, không set ở đây. */
    private fun bindAnswers(): List<ItemSurveyAnswerBinding> {
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
            row.root.setOnClickListener { viewModel.selectAnswer1(index) }
        }
        return rows
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
