package com.ai.tryonclothes.photo.generator.app.domain.repository

import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.model.ImageSlot
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import com.ai.tryonclothes.photo.generator.app.domain.model.Selection
import com.ai.tryonclothes.photo.generator.app.domain.model.StorageUsage
import com.ai.tryonclothes.photo.generator.app.domain.model.StoredImage
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    /** Validates the image at [sourceUri] and stores a normalised copy for [slot], replacing the previous one. */
    suspend fun importImage(sourceUri: String, slot: ImageSlot): Outcome<StoredImage>
    /** A fresh, writable location for a camera capture. Returns the content URI as a string. */
    suspend fun createCaptureTarget(): Outcome<String>
}

interface SelectionRepository {
    val selection: Flow<Selection>
    suspend fun setImage(slot: ImageSlot, image: StoredImage)
    suspend fun setCategory(category: GarmentCategory)
    /** Forgets the garment (and its file) but keeps the person photo so another outfit can be tried. */
    suspend fun clearGarment()
    suspend fun clearAll()
}

interface TryOnJobRepository {
    fun observe(jobId: String): Flow<TryOnJob?>
    /** The newest job that is still running, if any. */
    val activeJob: Flow<TryOnJob?>
    /** The newest job in any state, so Home can offer to resume or review it. */
    val latestJob: Flow<TryOnJob?>
    suspend fun get(jobId: String): TryOnJob?
    suspend fun all(): List<TryOnJob>
    suspend fun insert(job: TryOnJob)
    /**
     * Atomically applies [transform] to the stored job and returns the updated job. Returns null when the job
     * does not exist or [transform] returned null (a veto) and nothing was changed. Workers rely on the veto
     * to avoid overwriting a concurrent cancellation, and on the null result to know they must stop.
     */
    suspend fun update(jobId: String, transform: (TryOnJob) -> TryOnJob?): TryOnJob?
    suspend fun delete(jobId: String)
}

interface LookRepository {
    val looks: Flow<List<SavedLook>>
    suspend fun get(lookId: String): SavedLook?
    /** Copies a completed result into permanent storage. Succeeds only once the file is fully written. */
    suspend fun save(job: TryOnJob): Outcome<SavedLook>
    suspend fun delete(lookId: String): Outcome<Unit>
    suspend fun deleteAll(): Outcome<Unit>
}

interface StorageRepository {
    suspend fun usage(): StorageUsage
    suspend fun clearCache(): Outcome<Unit>
    /** Removes files nothing refers to and trims old finished jobs. Safe to run at any time. */
    suspend fun sweep()
}
