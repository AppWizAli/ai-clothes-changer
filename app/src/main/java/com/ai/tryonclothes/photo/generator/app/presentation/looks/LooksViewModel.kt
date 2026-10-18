package com.ai.tryonclothes.photo.generator.app.presentation.looks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import com.ai.tryonclothes.photo.generator.app.domain.repository.LookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LooksViewModel(looks: LookRepository) : ViewModel() {
    /** Null while the index is still loading, so the empty state doesn't flash before the data arrives. */
    val looks: StateFlow<List<SavedLook>?> = looks.looks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
