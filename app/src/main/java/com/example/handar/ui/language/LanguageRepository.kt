package com.example.handar.ui.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * **Không cần `Context`** — `AppCompatDelegate` là API tĩnh. Khác `VideoRepository`/`FavouriteManager`/
 * `SurveyRepository`: đây là cố ý, không phải quên truyền Context.
 */
class LanguageRepository {
    /**
     * Ngôn ngữ ĐANG hiển thị, đã quy về "vi"/"en".
     *
     * ⚠️ `getApplicationLocales()` chỉ có giá trị sau khi user (hoặc app) đã gọi `setApplicationLocales`.
     * Chưa chọn lần nào thì nó **rỗng** (app đang theo ngôn ngữ hệ thống) — máy tiếng Việt vẫn trả "",
     * nên nếu chỉ đọc nó thì màn Ngôn ngữ luôn tick English. Rỗng thì rơi về `Locale.getDefault()`,
     * chính là ngôn ngữ mà resource đang được chọn theo.
     */
    fun currentTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val language = if (!appLocales.isEmpty) appLocales[0]?.language else Locale.getDefault().language
        return if (language == "vi") "vi" else "en"
    }

    /**
     * ⚠️ Lời gọi này làm **Activity bị tạo lại**. Xem ghi chú ở [LanguageViewModel].
     */
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
