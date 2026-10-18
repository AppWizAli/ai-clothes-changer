package com.ai.tryonclothes.photo.generator.app.domain.model

import com.ai.tryonclothes.photo.generator.app.core.AppError

/** An image that passed validation and was normalised (rotated, downscaled, JPEG) for upload. */
data class StoredImage(val path: String, val width: Int, val height: Int, val sizeBytes: Long)

enum class ImageSlot { PERSON, GARMENT }

enum class GarmentCategory { TOPS, BOTTOMS, DRESSES, OUTERWEAR, FULL_OUTFIT }

enum class JobStatus {
    /** Persisted, waiting for the worker to start. */
    QUEUED,
    UPLOADING,
    GENERATING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean get() = this == QUEUED || this == UPLOADING || this == GENERATING
}

/**
 * One generation request. [id] is stable for the life of the job and doubles as the idempotency key,
 * so retrying a timed-out submission can never create a second paid generation.
 */
data class TryOnJob(
    val id: String,
    val personPath: String,
    val garmentPath: String,
    val category: GarmentCategory,
    val status: JobStatus,
    /** Provider-assigned id; once set, retries resume polling instead of re-submitting. */
    val remoteId: String? = null,
    val progress: Int? = null,
    val resultPath: String? = null,
    val error: AppError? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SavedLook(
    val id: String,
    val imagePath: String,
    val originalPath: String,
    val createdAt: Long,
)

/** The user's in-progress choices, kept so leaving the flow never loses them. */
data class Selection(val person: StoredImage? = null, val garment: StoredImage? = null, val category: GarmentCategory = GarmentCategory.TOPS)

/** Local-only space usage shown in Settings. */
data class StorageUsage(val looksBytes: Long, val cacheBytes: Long, val lookCount: Int)
