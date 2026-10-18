package com.ai.tryonclothes.photo.generator.app.domain.usecase

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Clock
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.provider.GenerationScheduler
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import kotlinx.coroutines.flow.first

/**
 * Re-queues a failed or cancelled job under the SAME id. If the provider already accepted the job the
 * worker resumes polling it; if not, the same idempotency key is re-sent. Either way no duplicate is created.
 */
class RetryTryOnUseCase(
    private val jobs: TryOnJobRepository,
    private val scheduler: GenerationScheduler,
    private val clock: Clock,
) {
    suspend operator fun invoke(jobId: String): Outcome<Unit> {
        val other = jobs.activeJob.first()
        if (other != null && other.id != jobId) return Outcome.Success(Unit)
        val requeued = jobs.update(jobId) { job ->
            if (job.status != JobStatus.FAILED && job.status != JobStatus.CANCELLED) null
            else job.copy(status = JobStatus.QUEUED, error = null, progress = null, updatedAt = clock.now())
        } ?: return Outcome.Failure(AppError.Unknown("This generation can no longer be retried."))
        scheduler.enqueue(requeued.id)
        return Outcome.Success(Unit)
    }
}
