package com.example.handar.ui.survey

import android.content.Context
import androidx.core.content.edit

/**
 * Lưu câu trả lời khảo sát vào `SharedPreferences`. Cùng khuôn với `FavouriteManager`/`VideoRepository`:
 * `class` nhận `Context` ở constructor (quy ước `MVVM_Migration_Plan.md` mục 0.2).
 *
 * Hai câu trả lời hiện **chưa được dùng vào việc gì** ở phần còn lại của app — mốc 6.2 chỉ đảm bảo
 * chúng không bị mất, không thêm thống kê hay cá nhân hoá nào.
 */
class SurveyRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * [answer2Index] `null` nghĩa là người dùng bấm Skip ở Survey1 nên chưa từng thấy Survey2. Khi đó
     * **xoá** khoá answer_2 thay vì ghi một giá trị mặc định — để dữ liệu phản ánh đúng "đã bỏ qua",
     * không phải "đã chọn đáp án đầu".
     */
    fun save(answer1Index: Int, answer2Index: Int?) {
        prefs.edit {
            putInt(KEY_ANSWER_1, answer1Index)
            if (answer2Index != null) putInt(KEY_ANSWER_2, answer2Index) else remove(KEY_ANSWER_2)
        }
    }

    private companion object {
        const val PREFS_NAME = "survey_answers"
        const val KEY_ANSWER_1 = "answer_1"
        const val KEY_ANSWER_2 = "answer_2"
    }
}
