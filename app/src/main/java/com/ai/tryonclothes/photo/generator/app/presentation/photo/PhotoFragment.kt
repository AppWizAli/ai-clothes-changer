package com.ai.tryonclothes.photo.generator.app.presentation.photo

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil3.load
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.databinding.FragmentPhotoBinding
import com.ai.tryonclothes.photo.generator.app.presentation.common.ImportEvent
import com.ai.tryonclothes.photo.generator.app.presentation.common.appViewModels
import com.ai.tryonclothes.photo.generator.app.presentation.common.launchWhenStarted
import com.ai.tryonclothes.photo.generator.app.presentation.common.show
import com.ai.tryonclothes.photo.generator.app.presentation.common.toMessage
import java.io.File
import kotlinx.coroutines.launch

class PhotoFragment : Fragment(R.layout.fragment_photo) {
    private val viewModel: PhotoViewModel by appViewModels { app, saved -> PhotoViewModel(app.images, app.selection, saved) }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.importer.onPicked(it) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { viewModel.importer.onCaptureResult(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPhotoBinding.bind(view)
        binding.topBar.title.setText(R.string.photo_title)
        binding.topBar.step.setText(R.string.step_one_of_two)
        binding.topBar.back.setOnClickListener { findNavController().popBackStack() }

        binding.gallery.setOnClickListener { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        binding.camera.setOnClickListener { viewModel.importer.requestCapture() }
        binding.next.setOnClickListener { findNavController().navigate(R.id.garmentFragment) }

        launchWhenStarted {
            launch {
                viewModel.uiState.collect { state ->
                    binding.preview.show(state.photo != null)
                    state.photo?.let { binding.preview.load(File(it.path)) }
                    binding.emptyState.show(state.photo == null && !state.validating)
                    binding.progress.show(state.validating)
                    binding.validatingLabel.show(state.validating)
                    binding.gallery.isEnabled = !state.validating
                    binding.camera.isEnabled = !state.validating
                    binding.next.isEnabled = state.canContinue
                }
            }
            launch {
                viewModel.importer.events.collect { event ->
                    when (event) {
                        is ImportEvent.LaunchCamera -> launchCamera(event.uri)
                        is ImportEvent.Failed -> Toast.makeText(requireContext(), event.error.toMessage(requireContext()), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun launchCamera(uri: String) {
        try {
            takePhoto.launch(Uri.parse(uri))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
