package com.ai.tryonclothes.photo.generator.app.presentation.result

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.databinding.FragmentResultBinding
import com.ai.tryonclothes.photo.generator.app.presentation.common.appViewModels
import com.ai.tryonclothes.photo.generator.app.presentation.common.launchWhenStarted
import com.ai.tryonclothes.photo.generator.app.presentation.common.shareImage
import com.ai.tryonclothes.photo.generator.app.presentation.common.show
import com.ai.tryonclothes.photo.generator.app.presentation.common.toMessage
import java.io.File
import kotlinx.coroutines.launch

class ResultFragment : Fragment(R.layout.fragment_result) {
    private val viewModel: ResultViewModel by appViewModels { app, saved ->
        ResultViewModel(saved, app.jobs, app.looks, app.selection, app.saveLook)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentResultBinding.bind(view)
        binding.topBar.title.setText(R.string.result_title)
        binding.topBar.step.show(false)
        binding.topBar.back.setOnClickListener { findNavController().popBackStack() }

        binding.save.setOnClickListener { viewModel.save() }
        binding.share.setOnClickListener {
            viewModel.resultFile?.let { requireContext().shareImage(it, getString(R.string.share)) }
        }
        binding.tryAnother.setOnClickListener { viewModel.tryAnotherOutfit() }
        binding.delete.setOnClickListener { confirmDelete() }

        launchWhenStarted {
            launch {
                viewModel.uiState.collect { state ->
                    binding.content.show(!state.loading && !state.missing)
                    binding.missing.show(state.missing)
                    binding.loading.show(state.loading)
                    if (state.resultPath != null) {
                        val params = binding.frame.layoutParams as ConstraintLayout.LayoutParams
                        val ratio = "${state.aspectRatio}"
                        if (params.dimensionRatio != ratio) {
                            params.dimensionRatio = ratio
                            binding.frame.layoutParams = params
                        }
                        binding.compare.setImages(state.originalPath?.let(::File), File(state.resultPath))
                    }
                    binding.save.isEnabled = !state.saved && !state.saving
                    binding.save.setText(if (state.saved) R.string.saved_look else R.string.save_look)
                    binding.save.setIconResource(if (state.saved) R.drawable.ic_check else R.drawable.ic_download)
                    binding.delete.show(state.saved)
                }
            }
            launch {
                viewModel.events.collect { event ->
                    when (event) {
                        ResultEvent.Saved -> toast(getString(R.string.result_saved_message))
                        ResultEvent.Deleted -> findNavController().popBackStack()
                        ResultEvent.OpenOutfitStep -> findNavController().navigate(
                            R.id.garmentFragment,
                            bundleOf(),
                            navOptions { popUpTo(R.id.homeFragment) },
                        )
                        is ResultEvent.Failed -> toast(event.error.toMessage(requireContext()))
                    }
                }
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_look_title)
            .setMessage(R.string.delete_look_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteLook() }
            .show()
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}
