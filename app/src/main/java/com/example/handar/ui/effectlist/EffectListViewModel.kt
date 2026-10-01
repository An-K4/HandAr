package com.example.handar.ui.effectlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.handar.effect.EffectRepository
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.utils.FavouriteManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EffectListUiState(
    val items: List<EffectDefinition> = EffectRepository.all,
    val favouriteIds: Set<String> = emptySet(),
    val query: String = ""
)

/**
 * Trạng thái favourite giờ ở đây chứ không rải trong [EffectAdapter] — Adapter không còn tự đọc/ghi
 * `SharedPreferences`. Đây là thay đổi có ý nghĩa thật, không chỉ đổi vỏ, và là chỗ màn Bộ sưu tập
 * sắp tới sẽ dùng chung dữ liệu yêu thích (xem ghi chú ở `MVVM_Migration_Plan.md` mục 6.2).
 *
 * Không đẩy [FavouriteManager] sang `Dispatchers.IO`: đọc là `getStringSet` từ map trong bộ nhớ,
 * ghi là `edit { }` của androidx (mặc định `apply()`, đã bất đồng bộ). Giữ đồng bộ để `toggle` trả
 * kết quả ngay, không cần state trung gian "đang lưu".
 */
class EffectListViewModel(private val favouriteManager: FavouriteManager) : ViewModel() {

    private val _uiState = MutableStateFlow(EffectListUiState())
    val uiState: StateFlow<EffectListUiState> = _uiState.asStateFlow()

    init {
        refreshFavourites()
    }

    /** `query == state.query` thay cho biến `lastQuery` mà Fragment dùng làm "distinct" trước đây. */
    fun onQueryChanged(context: Context, query: String) {
        if (query == _uiState.value.query) return
        _uiState.value = _uiState.value.copy(
            query = query,
            items = EffectRepository.findByName(context, query)
        )
    }

    fun toggleFavourite(effectId: String) {
        val nowFavourite = favouriteManager.toggle(effectId)
        val current = _uiState.value.favouriteIds
        _uiState.value = _uiState.value.copy(
            favouriteIds = if (nowFavourite) current + effectId else current - effectId
        )
    }

    private fun refreshFavourites() {
        val ids = EffectRepository.all
            .map { it.id }
            .filter { favouriteManager.isFavourite(it) }
            .toSet()
        _uiState.value = _uiState.value.copy(favouriteIds = ids)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { EffectListViewModel(FavouriteManager(context.applicationContext)) }
        }
    }
}
