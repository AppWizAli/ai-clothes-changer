package com.ai.tryonclothes.photo.generator.app.presentation.garment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.model.ImageSlot
import com.ai.tryonclothes.photo.generator.app.domain.model.StoredImage
import com.ai.tryonclothes.photo.generator.app.domain.repository.ImageRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import com.ai.tryonclothes.photo.generator.app.domain.usecase.StartTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.presentation.common.ImageSlotImporter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GarmentUiState(
    val garment: StoredImage? = null,
    val category: GarmentCategory = GarmentCategory.TOPS,
    val hasPhoto: Boolean = false,
    val validating: Boolean = false,
    val starting: Boolean = false,
) {
    val canGenerate: Boolean get() = garment != null && hasPhoto && !validating && !starting
}

sealed interface GarmentEvent {
    data class OpenGeneration(val jobId: String) : GarmentEvent
    data class Failed(val error: AppError) : GarmentEvent
}

class GarmentViewModel(
    images: ImageRepository,
    private val selection: SelectionRepository,
    private val startTryOn: StartTryOnUseCase,
    saved: SavedStateHandle,
) : ViewModel() {
    val importer = ImageSlotImporter(ImageSlot.GARMENT, images, selection, saved, viewModelScope)

    private val starting = MutableStateFlow(false)
    private val _events = Channel<GarmentEvent>(Channel.BUFFERED)
    val events: Flow<GarmentEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<GarmentUiState> = combine(selection.selection, importer.busy, starting) { sel, busy, start ->
        GarmentUiState(sel.garment, sel.category, sel.person != null, busy, start)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarmentUiState())

    fun onCategorySelected(category: GarmentCategory) {
        viewModelScope.launch { selection.setCategory(category) }
    }

    fun generate() {
        if (!uiState.value.canGenerate) return
        starting.value = true // blocks duplicate taps until the job exists
        viewModelScope.launch {
            try {
                when (val result = startTryOn()) {
                    is Outcome.Success -> _events.send(GarmentEvent.OpenGeneration(result.value))
                    is Outcome.Failure -> _events.send(GarmentEvent.Failed(result.error))
                }
            } finally {
                starting.value = false
            }
        }
    }
}
