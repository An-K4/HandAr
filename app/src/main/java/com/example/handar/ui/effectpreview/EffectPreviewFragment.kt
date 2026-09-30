package com.example.handar.ui.effectpreview

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.handar.R
import com.example.handar.databinding.FragmentEffectPreviewBinding
import com.example.handar.utils.applySystemBarsInsetsMargin
import kotlinx.coroutines.launch

class EffectPreviewFragment : Fragment() {

    companion object {
        // TẠM: chưa có video/gif minh hoạ cho từng effect, dùng ảnh động có sẵn để dựng UI trước.
        // khi có media thật: khai vào EffectDefinition (vd previewRes) và bỏ hằng này.
        private val DEMO_PREVIEW_RES = R.drawable.black_hole_portal
    }

    private val args: EffectPreviewFragmentArgs by navArgs()

    private var _binding: FragmentEffectPreviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EffectPreviewViewModel by viewModels {
        EffectPreviewViewModel.factory(args.effectId)
    }

    // giữ riêng để start/stop theo vòng đời (onStart/onStop). AnimatedImageDrawable giữ callback về ImageView
    // nên phải null hoá ở onDestroyView, nếu không sẽ giữ cả cây view.
    private var previewDrawable: AnimatedImageDrawable? = null

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

        binding.layoutPreviewTopBar.textEffectName.text = viewModel.effect.displayName
        binding.layoutPreviewTopBar.btnBack.setOnClickListener { viewModel.onBackClicked() }
        binding.btnCreate.setOnClickListener { viewModel.onCreateClicked() }

        loadPreviewMedia()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }
    }

    /**
     * Chốt cửa currentDestination giữ nguyên ở Fragment, cùng lý do đã ghi cho goBack()/create() bản
     * gốc: bấm đúp thì lần 2 có thể chạy khi màn này đã bị điều hướng đi — popBackStack() lần 2 sẽ
     * pop luôn màn phía dưới, navigate() lần 2 sẽ ném IllegalArgumentException.
     */
    private fun handleEvent(event: EffectPreviewViewModel.Event) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.effectPreviewFragment) return
        when (event) {
            EffectPreviewViewModel.Event.GoBack -> nav.popBackStack()
            EffectPreviewViewModel.Event.CreateVideo -> nav.navigate(
                EffectPreviewFragmentDirections.actionEffectPreviewToCameraRecord(viewModel.effect.id)
            )
        }
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
}
