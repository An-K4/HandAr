package com.example.handar.ui.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * **Không cần `Context`** — `AppCompatDelegate` là API tĩnh. Khác `VideoRepository`/`FavouriteManager`/
 * `SurveyRepository`: đây là cố ý, không phải quên truyền Context.
 */
class LanguageRepository {
    fun currentTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    /**
     * ⚠️ Lời gọi này làm **Activity bị tạo lại**. Xem ghi chú ở [LanguageViewModel].
     */
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
