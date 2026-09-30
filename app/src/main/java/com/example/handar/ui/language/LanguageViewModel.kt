package com.example.handar.ui.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ⚠️ **Đây là màn duy nhất trong app mà ViewModel KHÔNG sống sót qua thao tác của chính nó.**
 * `AppCompatDelegate.setApplicationLocales()` làm Activity bị tạo lại hoàn toàn (không phải kiểu
 * "instance sống, view chết" của back stack), nên Fragment mới + VM mới. Đừng ai đọc code này rồi
 * tưởng `selectedTag` giữ được qua lần đổi ngôn ngữ — nó không, và **không cần**: sau khi tạo lại,
 * [LanguageRepository.currentTag] đọc lại từ hệ thống nên state khởi tạo vẫn đúng.
 *
 * Nếu sau này cần UI không "giật" khi đổi ngôn ngữ (ví dụ đang có animation), phải xử lý ở tầng khác
 * (`android:configChanges` hoặc tương tự) — ViewModel không giải quyết được việc đó.
 */
class LanguageViewModel(private val repository: LanguageRepository) : ViewModel() {

    private val _selectedTag = MutableStateFlow(if (repository.currentTag().startsWith(TAG_VI)) TAG_VI else TAG_EN)
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    fun select(tag: String) {
        if (tag == _selectedTag.value) return
        // Đổi state TRƯỚC khi gọi repository: setApplicationLocales tạo lại Activity ngay, nên nếu
        // gọi repository trước thì dòng gán state có thể không kịp có tác dụng nhìn thấy được.
        _selectedTag.value = tag
        repository.setLanguage(tag)
    }

    companion object {
        const val TAG_EN = "en"
        const val TAG_VI = "vi"

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { LanguageViewModel(LanguageRepository()) }
        }
    }
}
