package com.ai.tryonclothes.photo.generator.app.presentation.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.SystemClock
import com.ai.tryonclothes.photo.generator.app.databinding.FragmentHomeBinding
import com.ai.tryonclothes.photo.generator.app.presentation.common.appViewModels
import com.ai.tryonclothes.photo.generator.app.presentation.common.launchWhenStarted
import com.ai.tryonclothes.photo.generator.app.presentation.common.show
import com.ai.tryonclothes.photo.generator.app.presentation.looks.LookAdapter

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val viewModel: HomeViewModel by appViewModels { app, _ ->
        HomeViewModel(app.jobs, app.looks, SystemClock, AppConfig.isBackendConfigured)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        val adapter = LookAdapter(R.layout.item_look_strip) { look ->
            findNavController().navigate(R.id.resultFragment, bundleOf("lookId" to look.id))
        }
        binding.recent.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recent.adapter = adapter
        binding.start.setOnClickListener { findNavController().navigate(R.id.photoFragment) }

        launchWhenStarted {
            viewModel.uiState.collect { state ->
                adapter.submitList(state.recentLooks)
                binding.recentEmpty.show(state.recentLooks.isEmpty())
                binding.recent.show(state.recentLooks.isNotEmpty())
                binding.notConfigured.show(!state.backendConfigured)

                val banner = state.banner
                binding.banner.show(banner != null)
                if (banner != null) {
                    binding.bannerText.setText(
                        when (banner.kind) {
                            BannerKind.RUNNING -> R.string.home_continue_running
                            BannerKind.READY -> R.string.home_continue_ready
                            BannerKind.FAILED -> R.string.home_continue_failed
                        },
                    )
                    binding.banner.setOnClickListener {
                        findNavController().navigate(R.id.generationFragment, bundleOf("jobId" to banner.jobId))
                    }
                }
            }
        }
    }
}
