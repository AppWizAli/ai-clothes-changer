package com.ai.tryonclothes.photo.generator.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.StorageUsage
import com.ai.tryonclothes.photo.generator.app.domain.repository.LookRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.StorageRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object CacheCleared : SettingsEvent
    data object LooksDeleted : SettingsEvent
    data class Failed(val error: AppError) : SettingsEvent
}

class SettingsViewModel(
    private val storage: StorageRepository,
    private val looks: LookRepository,
    val backendHost: String?,
) : ViewModel() {
    private val _usage = MutableStateFlow<StorageUsage?>(null)
    val usage: StateFlow<StorageUsage?> = _usage

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun refresh() {
        viewModelScope.launch { _usage.value = storage.usage() }
    }

    fun clearCache() {
        viewModelScope.launch {
            when (val result = storage.clearCache()) {
                is Outcome.Success -> _events.send(SettingsEvent.CacheCleared)
                is Outcome.Failure -> _events.send(SettingsEvent.Failed(result.error))
            }
            refresh()
        }
    }

    fun deleteAllLooks() {
        viewModelScope.launch {
            when (val result = looks.deleteAll()) {
                is Outcome.Success -> _events.send(SettingsEvent.LooksDeleted)
                is Outcome.Failure -> _events.send(SettingsEvent.Failed(result.error))
            }
            refresh()
        }
    }
}
