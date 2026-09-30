package com.example.handar.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentSettingsBinding
import com.example.handar.databinding.ItemSettingsOptionBinding
import com.example.handar.utils.applySystemBarsInsetsMargin
import com.example.handar.utils.applySystemBarsInsetsPadding
import kotlinx.coroutines.launch

/**
 * màn hiện tại chỉ item language có logic điều hướng (sang language fragment);
 * các item còn lại mới chỉ đổ ui, chưa gắn logic — sẽ bổ sung sau.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutSettingsTopBar.applySystemBarsInsetsMargin(top = true)
        binding.scrollSettings.applySystemBarsInsetsPadding(
            left = false, top = false, right = false, bottom = true
        )

        bindRow(
            binding.itemLanguage,
            iconRes = R.drawable.ic_language,
            titleRes = R.string.settings_item_language,
            subtitleRes = R.string.settings_language_subtitle,
            showArrow = true
        )
        bindRow(binding.itemRateUs, R.drawable.ic_rate, R.string.settings_item_rate_us)
        bindRow(binding.itemShareApp, R.drawable.ic_share, R.string.settings_item_share_app)
        bindRow(binding.itemFeedback, R.drawable.ic_feedback, R.string.settings_item_feedback)
        bindRow(binding.itemAboutApp, R.drawable.ic_about_app, R.string.settings_item_about_app)
        bindRow(binding.itemPrivacyPolicy, R.drawable.ic_privacy_policy, R.string.settings_item_privacy_policy)

        binding.btnSettingsBack.setOnClickListener { findNavController().navigateUp() }
        // Ngôn ngữ là điều hướng nội bộ app nên gọi thẳng, không qua VM (quy ước 0.2) — giữ y bản gốc.
        binding.itemLanguage.root.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_language)
        }

        binding.itemRateUs.root.setOnClickListener { viewModel.onRateUsClicked() }
        binding.itemShareApp.root.setOnClickListener { viewModel.onShareAppClicked() }
        binding.itemFeedback.root.setOnClickListener { viewModel.onFeedbackClicked() }
        binding.itemAboutApp.root.setOnClickListener { viewModel.onAboutAppClicked() }
        binding.itemPrivacyPolicy.root.setOnClickListener { viewModel.onPrivacyPolicyClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }
    }

    /**
     * 5 nhánh dưới đây **cố ý còn trống**: mốc 6.4 chỉ chuyển MVVM, chưa nối logic thật. Khi nối, viết
     * intent vào đúng nhánh tương ứng — listener và đường sự kiện đã có sẵn, không phải sửa gì thêm.
     */
    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.OpenPlayStore -> Unit       // TODO: mở trang app trên Play Store
            SettingsEvent.ShareApp -> Unit            // TODO: share sheet với link app
            SettingsEvent.SendFeedback -> Unit        // TODO: mailto tới hòm thư góp ý
            SettingsEvent.OpenAboutApp -> Unit        // TODO: màn/dialog Về ứng dụng
            SettingsEvent.OpenPrivacyPolicy -> Unit   // TODO: mở URL chính sách bảo mật
        }
    }

    /** đổ icon/tiêu đề (+ phụ đề/mũi tên tuỳ chọn) vào 1 dòng item_settings_option.xml đã include. */
    private fun bindRow(
        row: ItemSettingsOptionBinding,
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int? = null,
        showArrow: Boolean = false
    ) {
        row.iconOption.setImageResource(iconRes)
        row.textOptionTitle.setText(titleRes)
        if (subtitleRes != null) {
            row.textOptionSubtitle.setText(subtitleRes)
            row.textOptionSubtitle.visibility = View.VISIBLE
        } else {
            row.textOptionSubtitle.visibility = View.GONE
        }
        row.iconOptionTrailing.visibility = if (showArrow) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
