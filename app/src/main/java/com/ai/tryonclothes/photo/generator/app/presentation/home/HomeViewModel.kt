package com.ai.tryonclothes.photo.generator.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.tryonclothes.photo.generator.app.core.Clock
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import com.ai.tryonclothes.photo.generator.app.domain.repository.LookRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class BannerKind { RUNNING, READY, FAILED }

data class HomeBanner(val kind: BannerKind, val jobId: String)

data class HomeUiState(
    val recentLooks: List<SavedLook> = emptyList(),
    val banner: HomeBanner? = null,
    val backendConfigured: Boolean = true,
)

class HomeViewModel(
    jobs: TryOnJobRepository,
    looks: LookRepository,
    clock: Clock,
    backendConfigured: Boolean,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(jobs.latestJob, looks.looks) { job, saved ->
        val recent = job?.takeIf { clock.now() - it.updatedAt < BANNER_MAX_AGE_MILLIS }
        val banner = when {
            recent == null -> null
            recent.status.isActive -> HomeBanner(BannerKind.RUNNING, recent.id)
            recent.status == JobStatus.COMPLETED && saved.none { it.id == recent.id } -> HomeBanner(BannerKind.READY, recent.id)
            recent.status == JobStatus.FAILED -> HomeBanner(BannerKind.FAILED, recent.id)
            else -> null
        }
        HomeUiState(saved.take(RECENT_COUNT), banner, backendConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(backendConfigured = backendConfigured))

    private companion object {
        const val RECENT_COUNT = 10
        const val BANNER_MAX_AGE_MILLIS = 6L * 60 * 60 * 1000
    }
}
