package com.ai.tryonclothes.photo.generator.app.data.remote

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.TryOnProvider
import java.io.File

/** Used when no backend URL was configured at build time. It fails honestly instead of pretending to generate. */
object NotConfiguredTryOnProvider : TryOnProvider {
    override suspend fun submit(idempotencyKey: String, person: File, garment: File, category: GarmentCategory): Outcome<RemoteJob> =
        Outcome.Failure(AppError.NotConfigured)
    override suspend fun status(remoteId: String): Outcome<RemoteJob> = Outcome.Failure(AppError.NotConfigured)
    override suspend fun downloadResult(job: RemoteJob, target: File): Outcome<Unit> = Outcome.Failure(AppError.NotConfigured)
    override suspend fun cancel(remoteId: String): Outcome<Unit> = Outcome.Failure(AppError.NotConfigured)
}
