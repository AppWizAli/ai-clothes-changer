package com.ai.tryonclothes.photo.generator.app.presentation.common

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.ImageSlot
import com.ai.tryonclothes.photo.generator.app.domain.repository.ImageRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ImportEvent {
    data class LaunchCamera(val uri: String) : ImportEvent
    data class Failed(val error: AppError) : ImportEvent
}

/**
 * Shared behaviour for "get an image into a slot": gallery pick, camera capture, validation and
 * persistence. One import runs at a time, and a pending camera URI survives process death
 * (the camera app can cause the system to kill us while it is in the foreground).
 */
class ImageSlotImporter(
    private val slot: ImageSlot,
    private val images: ImageRepository,
    private val selection: SelectionRepository,
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    fun onPicked(uri: Uri?) {
        if (uri != null) import(uri.toString())
    }

    fun requestCapture() {
        if (_busy.value) return
        scope.launch {
            when (val target = images.createCaptureTarget()) {
                is Outcome.Success -> {
                    saved[KEY_PENDING_CAPTURE] = target.value
                    _events.send(ImportEvent.LaunchCamera(target.value))
                }
                is Outcome.Failure -> _events.send(ImportEvent.Failed(target.error))
            }
        }
    }

    fun onCaptureResult(success: Boolean) {
        val uri = saved.get<String>(KEY_PENDING_CAPTURE)
        saved.remove<String>(KEY_PENDING_CAPTURE)
        if (success && uri != null) import(uri)
    }

    private fun import(uri: String) {
        if (_busy.value) return // ignore double taps while an import is running
        _busy.value = true
        scope.launch {
            try {
                when (val result = images.importImage(uri, slot)) {
                    is Outcome.Success -> selection.setImage(slot, result.value)
                    is Outcome.Failure -> _events.send(ImportEvent.Failed(result.error))
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object { const val KEY_PENDING_CAPTURE = "pending_capture" }
}
