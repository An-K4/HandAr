package com.example.handar.ui.effectpreview

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.handar.R
import com.example.handar.databinding.FragmentEffectPreviewBinding
import com.example.handar.effect.EffectRepository
import com.example.handar.effect.model.EffectDefinition
import com.example.handar.utils.applySystemBarsInsetsMargin

class EffectPreviewFragment : Fragment() {

    companion object {
        // TẠM: chưa có video/gif minh hoạ cho từng effect, dùng ảnh động có sẵn để dựng UI trước.
        // khi có media thật: khai vào EffectDefinition (vd previewRes) và bỏ hằng này.
        private val DEMO_PREVIEW_RES = R.drawable.black_hole
    }

    private val args: EffectPreviewFragmentArgs by navArgs()

    private var _binding: FragmentEffectPreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var effect: EffectDefinition

    // giữ riêng để start/stop theo vòng đời (onStart/onStop). AnimatedImageDrawable giữ callback về ImageView
    // nên PHẢI null hoá ở onDestroyView, nếu không sẽ giữ cả cây view.
    private var previewDrawable: AnimatedImageDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        effect = EffectRepository.findById(args.effectId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEffectPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutPreviewTopBar.root.applySystemBarsInsetsMargin(top = true)
        binding.btnCreate.applySystemBarsInsetsMargin(bottom = true)

        binding.layoutPreviewTopBar.textEffectName.text = effect.displayName
        binding.layoutPreviewTopBar.btnBack.setOnClickListener { goBack() }
        binding.btnCreate.setOnClickListener { create() }

        loadPreviewMedia()
    }

    override fun onStart() {
        super.onStart()
        previewDrawable?.start()
    }

    override fun onStop() {
        super.onStop()
        previewDrawable?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewDrawable?.stop()
        previewDrawable = null
        binding.imgEffectPreview.setImageDrawable(null)
        _binding = null
    }

    /**
     * decode ảnh động (webp/gif) thành AnimatedImageDrawable, loop vô hạn. việc start() để onStart lo
     * (onStart luôn chạy sau onViewCreated) nên animation tự dừng khi màn không còn nhìn thấy.
     * decode lỗi thì để trống, nền đen của root lộ ra chứ không crash.
     */
    private fun loadPreviewMedia() {
        val drawable = runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, DEMO_PREVIEW_RES))
        }.getOrNull()

        binding.imgEffectPreview.setImageDrawable(drawable)
        previewDrawable = (drawable as? AnimatedImageDrawable)?.apply {
            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
    }

    /**
     * cả goBack() lẫn create() đều chốt cửa bằng currentDestination: bấm đúp thì lần 2 có thể chạy khi màn này
     * đã bị điều hướng đi — popBackStack() lần 2 sẽ pop luôn màn phía dưới, navigate() lần 2 sẽ ném
     * IllegalArgumentException.
     */
    private fun goBack() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.effectPreviewFragment) return
        nav.popBackStack()
    }

    private fun create() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.effectPreviewFragment) return
        nav.navigate(EffectPreviewFragmentDirections.actionEffectPreviewToCameraRecord(effect.id))
    }
}
