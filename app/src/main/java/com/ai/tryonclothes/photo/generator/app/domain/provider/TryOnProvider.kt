package com.ai.tryonclothes.photo.generator.app.domain.provider

import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import java.io.File

/**
 * The seam between the app and whichever AI service performs the try-on. Everything provider-specific
 * (URLs, DTOs, auth, status vocabulary) lives behind this interface.
 */
interface TryOnProvider {
    /** Starts a generation. Implementations must treat [idempotencyKey] as idempotent server-side. */
    suspend fun submit(idempotencyKey: String, person: File, garment: File, category: GarmentCategory): Outcome<RemoteJob>

    suspend fun status(remoteId: String): Outcome<RemoteJob>

    /** Downloads the finished image to [target]. */
    suspend fun downloadResult(job: RemoteJob, target: File): Outcome<Unit>

    /** Best-effort cancellation; the local job is already marked cancelled regardless of the outcome. */
    suspend fun cancel(remoteId: String): Outcome<Unit>
}

data class RemoteJob(
    val remoteId: String,
    val state: RemoteState,
    val progress: Int? = null,
    val resultUrl: String? = null,
    val failureMessage: String? = null,
)

enum class RemoteState { QUEUED, PROCESSING, SUCCEEDED, FAILED, CANCELLED }
