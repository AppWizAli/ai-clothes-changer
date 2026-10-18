package com.ai.tryonclothes.photo.generator.app.presentation.generation

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.databinding.FragmentGenerationBinding
import com.ai.tryonclothes.photo.generator.app.presentation.common.appViewModels
import com.ai.tryonclothes.photo.generator.app.presentation.common.launchWhenStarted
import com.ai.tryonclothes.photo.generator.app.presentation.common.show
import com.ai.tryonclothes.photo.generator.app.presentation.common.toMessage

class GenerationFragment : Fragment(R.layout.fragment_generation) {
    private val viewModel: GenerationViewModel by appViewModels { app, saved ->
        GenerationViewModel(saved, app.jobs, app.cancelTryOn, app.retryTryOn)
    }
    private var openedResult = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGenerationBinding.bind(view)
        binding.topBar.title.setText(R.string.generation_title)
        binding.topBar.step.show(false)
        binding.topBar.back.setOnClickListener { findNavController().popBackStack() }
        binding.cancel.setOnClickListener { viewModel.cancel() }
        binding.retry.setOnClickListener { viewModel.retry() }
        binding.changeOutfit.setOnClickListener { findNavController().popBackStack() }

        launchWhenStarted {
            viewModel.uiState.collect { state ->
                val running = state as? GenerationUiState.Running
                val failed = state as? GenerationUiState.Failed
                binding.runningGroup.show(running != null || state is GenerationUiState.Loading)
                binding.failedGroup.show(failed != null || state is GenerationUiState.Missing)

                if (running != null) {
                    binding.stage.setText(
                        when (running.stage) {
                            Stage.QUEUED -> R.string.generation_queued
                            Stage.UPLOADING -> R.string.generation_uploading
                            Stage.GENERATING -> R.string.generation_generating
                        },
                    )
                    val progress = running.progress
                    if (progress != null && running.stage == Stage.GENERATING) {
                        binding.progress.isIndeterminate = false
                        binding.progress.setProgressCompat(progress, true)
                        binding.percent.text = getString(R.string.generation_progress, progress)
                        binding.percent.show(true)
                    } else {
                        binding.progress.isIndeterminate = true
                        binding.percent.show(false)
                    }
                }
                if (failed != null) {
                    binding.errorMessage.text = failed.error.toMessage(requireContext())
                    binding.retry.show(failed.error.retryable)
                }
                if (state is GenerationUiState.Missing) {
                    binding.errorMessage.setText(R.string.error_unknown)
                    binding.retry.show(false)
                }
                if (state is GenerationUiState.Done && !openedResult) {
                    openedResult = true
                    findNavController().navigate(
                        R.id.resultFragment,
                        bundleOf("jobId" to state.jobId),
                        navOptions { popUpTo(R.id.homeFragment) },
                    )
                }
            }
        }
    }
}
