package com.ai.tryonclothes.photo.generator.app.presentation.generation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import com.ai.tryonclothes.photo.generator.app.domain.usecase.CancelTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.domain.usecase.RetryTryOnUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Stage { QUEUED, UPLOADING, GENERATING }

sealed interface GenerationUiState {
    data object Loading : GenerationUiState
    data object Missing : GenerationUiState
    data class Running(val stage: Stage, val progress: Int?) : GenerationUiState
    data class Failed(val error: AppError) : GenerationUiState
    data class Done(val jobId: String) : GenerationUiState
}

/**
 * Pure view of the persisted job. The job itself is driven by a background worker, so this screen can be
 * left, killed and reopened at any time and simply re-attaches to whatever state the job is in.
 */
class GenerationViewModel(
    savedState: SavedStateHandle,
    jobs: TryOnJobRepository,
    private val cancelTryOn: CancelTryOnUseCase,
    private val retryTryOn: RetryTryOnUseCase,
) : ViewModel() {
    private val jobId: String = checkNotNull(savedState["jobId"]) { "GenerationFragment requires a jobId argument" }

    val uiState: StateFlow<GenerationUiState> = jobs.observe(jobId)
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenerationUiState.Loading)

    fun cancel() {
        viewModelScope.launch { cancelTryOn(jobId) }
    }

    fun retry() {
        viewModelScope.launch { retryTryOn(jobId) }
    }

    private fun TryOnJob?.toUiState(): GenerationUiState = when {
        this == null -> GenerationUiState.Missing
        status == JobStatus.QUEUED -> GenerationUiState.Running(Stage.QUEUED, progress)
        status == JobStatus.UPLOADING -> GenerationUiState.Running(Stage.UPLOADING, progress)
        status == JobStatus.GENERATING -> GenerationUiState.Running(Stage.GENERATING, progress)
        status == JobStatus.COMPLETED -> GenerationUiState.Done(id)
        else -> GenerationUiState.Failed(error ?: AppError.Cancelled)
    }

}
