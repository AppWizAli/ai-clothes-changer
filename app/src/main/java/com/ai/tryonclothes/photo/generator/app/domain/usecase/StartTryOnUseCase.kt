package com.ai.tryonclothes.photo.generator.app.domain.usecase

import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Clock
import com.ai.tryonclothes.photo.generator.app.core.IdGenerator
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.GenerationScheduler
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import kotlinx.coroutines.flow.first

/** Creates a generation job from the current selection. Tapping twice returns the running job instead of a second one. */
class StartTryOnUseCase(
    private val selection: SelectionRepository,
    private val jobs: TryOnJobRepository,
    private val scheduler: GenerationScheduler,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val isBackendConfigured: () -> Boolean = { AppConfig.isBackendConfigured },
) {
    suspend operator fun invoke(): Outcome<String> {
        if (!isBackendConfigured()) return Outcome.Failure(AppError.NotConfigured)
        jobs.activeJob.first()?.let { return Outcome.Success(it.id) }
        val current = selection.selection.first()
        val person = current.person ?: return Outcome.Failure(AppError.MissingInput)
        val garment = current.garment ?: return Outcome.Failure(AppError.MissingInput)
        val now = clock.now()
        val job = TryOnJob(
            id = ids.newId(),
            personPath = person.path,
            garmentPath = garment.path,
            category = current.category,
            status = JobStatus.QUEUED,
            createdAt = now,
            updatedAt = now,
        )
        jobs.insert(job)
        scheduler.enqueue(job.id)
        return Outcome.Success(job.id)
    }
}
