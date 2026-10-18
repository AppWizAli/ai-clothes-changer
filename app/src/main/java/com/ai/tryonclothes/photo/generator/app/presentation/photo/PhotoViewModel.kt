package com.ai.tryonclothes.photo.generator.app.presentation.photo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.tryonclothes.photo.generator.app.domain.model.ImageSlot
import com.ai.tryonclothes.photo.generator.app.domain.model.StoredImage
import com.ai.tryonclothes.photo.generator.app.domain.repository.ImageRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import com.ai.tryonclothes.photo.generator.app.presentation.common.ImageSlotImporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PhotoUiState(val photo: StoredImage? = null, val validating: Boolean = false) {
    val canContinue: Boolean get() = photo != null && !validating
}

class PhotoViewModel(
    images: ImageRepository,
    selection: SelectionRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    val importer = ImageSlotImporter(ImageSlot.PERSON, images, selection, saved, viewModelScope)

    val uiState: StateFlow<PhotoUiState> = combine(selection.selection.map { it.person }, importer.busy) { photo, busy ->
        PhotoUiState(photo, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoUiState())
}
