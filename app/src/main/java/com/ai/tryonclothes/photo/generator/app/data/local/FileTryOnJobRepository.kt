package com.ai.tryonclothes.photo.generator.app.data.local

import com.ai.tryonclothes.photo.generator.app.data.AppDirs
import com.ai.tryonclothes.photo.generator.app.data.writeAtomically
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Jobs are one small JSON file each, mirrored in memory. The files make unfinished work recoverable
 * after the process dies; the mutex makes read-modify-write updates atomic.
 */
class FileTryOnJobRepository(
    private val dirs: AppDirs,
    private val io: CoroutineDispatcher,
) : TryOnJobRepository {
    private val state = MutableStateFlow<Map<String, TryOnJob>>(emptyMap())
    private val lock = Mutex()
    private var loaded = false

    override fun observe(jobId: String): Flow<TryOnJob?> = flow {
        ensureLoaded()
        emitAll(state.map { it[jobId] }.distinctUntilChanged())
    }

    override val activeJob: Flow<TryOnJob?> = flow {
        ensureLoaded()
        emitAll(state.map { jobs -> jobs.values.filter { it.status.isActive }.maxByOrNull { it.createdAt } }.distinctUntilChanged())
    }

    override val latestJob: Flow<TryOnJob?> = flow {
        ensureLoaded()
        emitAll(state.map { jobs -> jobs.values.maxByOrNull { it.createdAt } }.distinctUntilChanged())
    }

    override suspend fun get(jobId: String): TryOnJob? {
        ensureLoaded()
        return state.value[jobId]
    }

    override suspend fun all(): List<TryOnJob> {
        ensureLoaded()
        return state.value.values.toList()
    }

    override suspend fun insert(job: TryOnJob) {
        ensureLoaded()
        lock.withLock { persist(job) }
    }

    override suspend fun update(jobId: String, transform: (TryOnJob) -> TryOnJob?): TryOnJob? {
        ensureLoaded()
        return lock.withLock {
            val current = state.value[jobId] ?: return@withLock null
            val next = transform(current) ?: return@withLock null
            if (next != current) persist(next)
            next
        }
    }

    override suspend fun delete(jobId: String) {
        ensureLoaded()
        lock.withLock {
            withContext(io) { File(dirs.jobs, "$jobId.json").delete() }
            state.value = state.value - jobId
        }
    }

    private suspend fun persist(job: TryOnJob) {
        withContext(io) { File(dirs.jobs, "${job.id}.json").writeAtomically { it.writeText(JobJson.encode(job)) } }
        state.value = state.value + (job.id to job)
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            val read = withContext(io) {
                dirs.jobs.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
                    JobJson.decode(file.readText()).also { if (it == null) file.delete() } // drop corrupt records
                }
            }
            state.value = read.associateBy { it.id }
            loaded = true
        }
    }
}
