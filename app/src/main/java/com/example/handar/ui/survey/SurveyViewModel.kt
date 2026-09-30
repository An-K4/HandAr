package com.example.handar.ui.survey

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SurveyUiState(
    val answer1Index: Int = DEFAULT_ANSWER_INDEX,
    /** `null` = chưa từng thấy Survey2 (bấm Skip ở Survey1). */
    val answer2Index: Int? = null
)

const val DEFAULT_ANSWER_INDEX = 0

/**
 * Dùng **chung** cho `Survey1Fragment` và `Survey2Fragment` qua `navGraphViewModels(R.id.nav_graph)`.
 * Hai màn luôn đi liền nhau và cùng bị `popUpTo` gỡ cùng lúc (xem `nav_graph.xml`), nên state cần
 * sống qua việc đi từ màn 1 sang màn 2 — điều một VM scope-Fragment không làm được.
 *
 * ⚠️ Scope `nav_graph` là scope **rộng nhất** (nav graph gốc), nên VM này sống tới hết phiên làm việc
 * của `NavController` chứ không bị clear ngay khi rời cụm Survey. Chấp nhận vì state chỉ là 2 số
 * `Int`; nếu sau này cụm Survey giữ dữ liệu lớn thì tách nested graph riêng cho nó.
 */
class SurveyViewModel(private val repository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    fun selectAnswer1(index: Int) {
        if (index == _uiState.value.answer1Index) return
        _uiState.value = _uiState.value.copy(answer1Index = index)
    }

    fun selectAnswer2(index: Int) {
        if (index == _uiState.value.answer2Index) return
        _uiState.value = _uiState.value.copy(answer2Index = index)
    }

    /**
     * Survey2 gọi khi hiện ra. Bản gốc có `selectedIndex = 0` sẵn, tức vào màn rồi bấm Hoàn tất mà
     * không chạm gì thì đáp án là dòng đầu — giữ đúng hành vi đó, đồng thời phân biệt được với ca
     * Skip (chưa từng vào màn này nên [SurveyUiState.answer2Index] vẫn `null`).
     */
    fun onSurvey2Shown() {
        if (_uiState.value.answer2Index == null) {
            _uiState.value = _uiState.value.copy(answer2Index = DEFAULT_ANSWER_INDEX)
        }
    }

    /** Gọi ở đúng 2 chỗ rời cụm Survey: `textSurveySkip` (Survey1) và `btnSurveyFinish` (Survey2). */
    fun submit() {
        val state = _uiState.value
        repository.save(state.answer1Index, state.answer2Index)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { SurveyViewModel(SurveyRepository(context.applicationContext)) }
        }
    }
}
