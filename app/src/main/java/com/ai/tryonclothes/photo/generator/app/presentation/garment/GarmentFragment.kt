package com.ai.tryonclothes.photo.generator.app.presentation.garment

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil3.load
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.databinding.FragmentGarmentBinding
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.presentation.common.ImportEvent
import com.ai.tryonclothes.photo.generator.app.presentation.common.appViewModels
import com.ai.tryonclothes.photo.generator.app.presentation.common.launchWhenStarted
import com.ai.tryonclothes.photo.generator.app.presentation.common.show
import com.ai.tryonclothes.photo.generator.app.presentation.common.toMessage
import java.io.File
import kotlinx.coroutines.launch

class GarmentFragment : Fragment(R.layout.fragment_garment) {
    private val viewModel: GarmentViewModel by appViewModels { app, saved ->
        GarmentViewModel(app.images, app.selection, app.startTryOn, saved)
    }

    private val pickGarment = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.importer.onPicked(it) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { viewModel.importer.onCaptureResult(it) }

    private val chipCategories = mapOf(
        R.id.chip_tops to GarmentCategory.TOPS,
        R.id.chip_bottoms to GarmentCategory.BOTTOMS,
        R.id.chip_dresses to GarmentCategory.DRESSES,
        R.id.chip_outerwear to GarmentCategory.OUTERWEAR,
        R.id.chip_full to GarmentCategory.FULL_OUTFIT,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGarmentBinding.bind(view)
        binding.topBar.title.setText(R.string.garment_title)
        binding.topBar.step.setText(R.string.step_two_of_two)
        binding.topBar.back.setOnClickListener { findNavController().popBackStack() }

        binding.gallery.setOnClickListener { pickGarment.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        binding.camera.setOnClickListener { viewModel.importer.requestCapture() }
        binding.generate.setOnClickListener { viewModel.generate() }
        binding.categories.setOnCheckedStateChangeListener { _, checkedIds ->
            chipCategories[checkedIds.firstOrNull()]?.let(viewModel::onCategorySelected)
        }

        launchWhenStarted {
            launch {
                viewModel.uiState.collect { state ->
                    binding.preview.show(state.garment != null)
                    state.garment?.let { binding.preview.load(File(it.path)) }
                    binding.emptyState.show(state.garment == null && !state.validating)
                    binding.progress.show(state.validating)
                    binding.gallery.isEnabled = !state.validating
                    binding.camera.isEnabled = !state.validating
                    binding.gallery.setText(if (state.garment == null) R.string.garment_import else R.string.garment_replace)
                    binding.generate.isEnabled = state.canGenerate
                    chipCategories.entries.firstOrNull { it.value == state.category }?.let { binding.categories.check(it.key) }
                }
            }
            launch {
                viewModel.importer.events.collect { event ->
                    when (event) {
                        is ImportEvent.LaunchCamera -> launchCamera(event.uri)
                        is ImportEvent.Failed -> toast(event.error.toMessage(requireContext()))
                    }
                }
            }
            launch {
                viewModel.events.collect { event ->
                    when (event) {
                        is GarmentEvent.OpenGeneration ->
                            findNavController().navigate(R.id.generationFragment, bundleOf("jobId" to event.jobId))
                        is GarmentEvent.Failed -> toast(event.error.toMessage(requireContext()))
                    }
                }
            }
        }
    }

    private fun launchCamera(uri: String) {
        try {
            takePhoto.launch(Uri.parse(uri))
        } catch (e: android.content.ActivityNotFoundException) {
            toast(getString(R.string.camera_unavailable))
        }
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
}
