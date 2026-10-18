package com.ai.tryonclothes.photo.generator.app.presentation.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.data.image.ImageSizeReader
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.repository.LookRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import com.ai.tryonclothes.photo.generator.app.domain.usecase.SaveLookUseCase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResultUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val originalPath: String? = null,
    val resultPath: String? = null,
    /** Width / height of the real generated image, so it is never stretched or cropped by the container. */
    val aspectRatio: Float = DEFAULT_ASPECT,
    val saved: Boolean = false,
    val saving: Boolean = false,
) {
    companion object { const val DEFAULT_ASPECT = 3f / 4f }
}

sealed interface ResultEvent {
    data object Saved : ResultEvent
    data object Deleted : ResultEvent
    data object OpenOutfitStep : ResultEvent
    data class Failed(val error: AppError) : ResultEvent
}

/** Shows either a fresh job result (jobId) or a saved look (lookId); both share one screen. */
class ResultViewModel(
    savedState: SavedStateHandle,
    private val jobs: TryOnJobRepository,
    private val looks: LookRepository,
    private val selection: SelectionRepository,
    private val saveLook: SaveLookUseCase,
) : ViewModel() {
    private val jobId: String = savedState.get<String>("jobId").orEmpty()
    private val lookId: String = savedState.get<String>("lookId").orEmpty()
    /** A saved look reuses the id of the job that produced it. */
    private val savedLookId = lookId.ifEmpty { jobId }

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState

    private val _events = Channel<ResultEvent>(Channel.BUFFERED)
    val events: Flow<ResultEvent> = _events.receiveAsFlow()

    val resultFile: File? get() = _uiState.value.resultPath?.let(::File)

    init {
        viewModelScope.launch { load() }
    }

    fun save() {
        val current = _uiState.value
        if (current.saving || current.saved || jobId.isEmpty()) return
        _uiState.value = current.copy(saving = true)
        viewModelScope.launch {
            when (val result = saveLook(jobId)) {
                // "Saved" is only reported after the file has actually been written.
                is Outcome.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, saved = true)
                    _events.send(ResultEvent.Saved)
                }
                is Outcome.Failure -> {
                    _uiState.value = _uiState.value.copy(saving = false)
                    _events.send(ResultEvent.Failed(result.error))
                }
            }
        }
    }

    fun deleteLook() {
        viewModelScope.launch {
            when (val result = looks.delete(savedLookId)) {
                is Outcome.Success -> _events.send(ResultEvent.Deleted)
                is Outcome.Failure -> _events.send(ResultEvent.Failed(result.error))
            }
        }
    }

    /** Keeps the person photo, drops the garment, and sends the user back to pick another outfit. */
    fun tryAnotherOutfit() {
        viewModelScope.launch {
            selection.clearGarment()
            _events.send(ResultEvent.OpenOutfitStep)
        }
    }

    private suspend fun load() {
        val loaded = withContext(Dispatchers.IO) {
            if (lookId.isNotEmpty()) {
                val look = looks.get(lookId)
                if (look != null && File(look.imagePath).isFile) {
                    Triple(look.originalPath.ifEmpty { null }, look.imagePath, true)
                } else {
                    null
                }
            } else {
                val job = jobs.get(jobId)
                val result = job?.resultPath
                if (job != null && job.status == JobStatus.COMPLETED && result != null && File(result).isFile) {
                    Triple(job.personPath, result, looks.get(job.id) != null)
                } else {
                    null
                }
            }
        }
        if (loaded == null) {
            _uiState.value = ResultUiState(loading = false, missing = true)
            return
        }
        val (original, result, saved) = loaded
        val size = withContext(Dispatchers.IO) { ImageSizeReader.read(File(result)) }
        _uiState.value = ResultUiState(
            loading = false,
            originalPath = original,
            resultPath = result,
            aspectRatio = size?.let { (w, h) -> w.toFloat() / h } ?: ResultUiState.DEFAULT_ASPECT,
            saved = saved,
        )
    }
}
