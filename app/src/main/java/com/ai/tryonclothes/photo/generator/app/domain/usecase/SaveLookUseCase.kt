package com.ai.tryonclothes.photo.generator.app.domain.usecase

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import com.ai.tryonclothes.photo.generator.app.domain.repository.LookRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository

class SaveLookUseCase(private val jobs: TryOnJobRepository, private val looks: LookRepository) {
    suspend operator fun invoke(jobId: String): Outcome<SavedLook> {
        val job = jobs.get(jobId)
        if (job == null || job.status != JobStatus.COMPLETED || job.resultPath == null) {
            return Outcome.Failure(AppError.Unknown("There is no finished result to save."))
        }
        return looks.save(job)
    }
}
