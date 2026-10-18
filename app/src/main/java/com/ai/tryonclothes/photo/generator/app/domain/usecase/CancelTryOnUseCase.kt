package com.ai.tryonclothes.photo.generator.app.domain.usecase

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Clock
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.provider.GenerationScheduler
import com.ai.tryonclothes.photo.generator.app.domain.provider.TryOnProvider
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository

class CancelTryOnUseCase(
    private val jobs: TryOnJobRepository,
    private val scheduler: GenerationScheduler,
    private val provider: TryOnProvider,
    private val clock: Clock,
) {
    suspend operator fun invoke(jobId: String) {
        val cancelled = jobs.update(jobId) { job ->
            if (!job.status.isActive) null
            else job.copy(status = JobStatus.CANCELLED, error = AppError.Cancelled, updatedAt = clock.now())
        } ?: return
        scheduler.cancel(jobId)
        // Best effort: stopping server-side work saves cost, but the local job is already cancelled either way.
        cancelled.remoteId?.let { provider.cancel(it) }
    }
}
