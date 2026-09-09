package com.example.handar.ui.effectlist

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.handar.R
import com.example.handar.databinding.FragmentEffectListBinding

class EffectListFragment : Fragment() {
    private var _binding: FragmentEffectListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("LC_EffectList", "onCreateView")
        _binding = FragmentEffectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("LC_EffectList", "onViewCreated")
        binding.btnEffectList.setOnClickListener {
            findNavController().navigate(
                R.id.action_effectList_to_cameraRecord,
                bundleOf("effectId" to "happy_cat")
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("LC_EffectList", "onDestroyView")
        _binding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d("LC_EffectList", "onAttach")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LC_EffectList", "onCreate")
    }
    
    override fun onStart() {
        super.onStart()
        Log.d("LC_EffectList", "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("LC_EffectList", "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d("LC_EffectList", "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("LC_EffectList", "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LC_EffectList", "onDestroy")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d("LC_EffectList", "onDetach")
    }
}