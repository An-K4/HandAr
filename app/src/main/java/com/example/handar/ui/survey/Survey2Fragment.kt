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
import com.example.handar.databinding.FragmentSurvey2Binding
import com.example.handar.databinding.ItemSurveyAnswerBinding
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

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

    // VM dùng chung với Survey1Fragment — xem SurveyViewModel.
    private val viewModel: SurveyViewModel by navGraphViewModels(R.id.nav_graph) {
        SurveyViewModel.factory(requireContext())
    }

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

        // Đánh dấu đã vào màn này: answer2 từ null thành mặc định 0, phân biệt với ca Skip ở Survey1.
        viewModel.onSurvey2Shown()

        val rows = bindAnswers()

        binding.btnSurveyFinish.setOnClickListener {
            viewModel.submit()
            findNavController().navigate(R.id.action_survey2_to_permission)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val selected = state.answer2Index ?: DEFAULT_ANSWER_INDEX
                    rows.forEachIndexed { index, row ->
                        setRowSelected(row, index == selected)
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
            row.root.setOnClickListener { viewModel.selectAnswer2(index) }
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
