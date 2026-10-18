package com.ai.tryonclothes.photo.generator.app.fakes

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Clock
import com.ai.tryonclothes.photo.generator.app.core.IdGenerator
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.model.ImageSlot
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.Selection
import com.ai.tryonclothes.photo.generator.app.domain.model.StoredImage
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.GenerationScheduler
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteState
import com.ai.tryonclothes.photo.generator.app.domain.provider.ResultStore
import com.ai.tryonclothes.photo.generator.app.domain.provider.TryOnProvider
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class TestClock(var time: Long = 1_000L) : Clock {
    override fun now() = time
    fun advance(millis: Long) { time += millis }
}

class SequentialIds(private val prefix: String = "job") : IdGenerator {
    private var next = 1
    override fun newId() = "$prefix-${next++}"
}

class InMemoryJobRepository : TryOnJobRepository {
    val jobs = MutableStateFlow<Map<String, TryOnJob>>(emptyMap())
    override fun observe(jobId: String): Flow<TryOnJob?> = jobs.map { it[jobId] }
    override val activeJob: Flow<TryOnJob?> = jobs.map { m -> m.values.filter { it.status.isActive }.maxByOrNull { it.createdAt } }
    override val latestJob: Flow<TryOnJob?> = jobs.map { m -> m.values.maxByOrNull { it.createdAt } }
    override suspend fun get(jobId: String) = jobs.value[jobId]
    override suspend fun all() = jobs.value.values.toList()
    override suspend fun insert(job: TryOnJob) { jobs.value = jobs.value + (job.id to job) }
    override suspend fun update(jobId: String, transform: (TryOnJob) -> TryOnJob?): TryOnJob? {
        val current = jobs.value[jobId] ?: return null
        val next = transform(current) ?: return null
        jobs.value = jobs.value + (jobId to next)
        return next
    }
    override suspend fun delete(jobId: String) { jobs.value = jobs.value - jobId }
}

class RecordingScheduler : GenerationScheduler {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun enqueue(jobId: String) { enqueued += jobId }
    override fun cancel(jobId: String) { cancelled += jobId }
}

class InMemorySelection(initial: Selection = Selection()) : SelectionRepository {
    private val state = MutableStateFlow(initial)
    override val selection: Flow<Selection> = state
    override suspend fun setImage(slot: ImageSlot, image: StoredImage) {
        state.value = if (slot == ImageSlot.PERSON) state.value.copy(person = image) else state.value.copy(garment = image)
    }
    override suspend fun setCategory(category: GarmentCategory) { state.value = state.value.copy(category = category) }
    override suspend fun clearGarment() { state.value = state.value.copy(garment = null) }
    override suspend fun clearAll() { state.value = Selection() }
}

/** Scripted provider: each call consumes the next queued response and every call is recorded. */
class ScriptedProvider : TryOnProvider {
    val submitResponses = ArrayDeque<Outcome<RemoteJob>>()
    val statusResponses = ArrayDeque<Outcome<RemoteJob>>()
    var downloadResponse: Outcome<Unit> = Outcome.Success(Unit)
    var downloadBytes: ByteArray = byteArrayOf(1, 2, 3)

    val submitKeys = mutableListOf<String>()
    val statusCalls = mutableListOf<String>()
    val cancelCalls = mutableListOf<String>()
    var onStatus: (suspend () -> Unit)? = null
    var onDownload: (suspend () -> Unit)? = null

    override suspend fun submit(idempotencyKey: String, person: File, garment: File, category: GarmentCategory): Outcome<RemoteJob> {
        submitKeys += idempotencyKey
        return submitResponses.removeFirst()
    }

    override suspend fun status(remoteId: String): Outcome<RemoteJob> {
        statusCalls += remoteId
        onStatus?.invoke()
        return statusResponses.removeFirst()
    }

    override suspend fun downloadResult(job: RemoteJob, target: File): Outcome<Unit> {
        if (downloadResponse is Outcome.Success) target.writeBytes(downloadBytes)
        onDownload?.invoke()
        return downloadResponse
    }

    override suspend fun cancel(remoteId: String): Outcome<Unit> {
        cancelCalls += remoteId
        return Outcome.Success(Unit)
    }
}

class FakeResultStore(private val dir: File, var validImage: Boolean = true) : ResultStore {
    override fun newResultFile(jobId: String) = File(dir, "$jobId.jpg")
    override fun isValidImage(file: File) = validImage && file.isFile && file.length() > 0
    override fun delete(file: File) { file.delete() }
}

fun remote(state: RemoteState, id: String = "r-1", progress: Int? = null, url: String? = null, message: String? = null) =
    RemoteJob(id, state, progress, url, message)

fun queuedJob(id: String = "job-1", person: File, garment: File, clock: Long = 1_000L, status: JobStatus = JobStatus.QUEUED) = TryOnJob(
    id = id,
    personPath = person.path,
    garmentPath = garment.path,
    category = GarmentCategory.TOPS,
    status = status,
    createdAt = clock,
    updatedAt = clock,
)

fun failure(error: AppError) = Outcome.Failure(error)
