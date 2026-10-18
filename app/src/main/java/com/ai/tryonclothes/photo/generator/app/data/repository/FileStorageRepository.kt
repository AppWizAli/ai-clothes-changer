package com.ai.tryonclothes.photo.generator.app.data.repository

import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.data.AppDirs
import com.ai.tryonclothes.photo.generator.app.data.sizeRecursive
import com.ai.tryonclothes.photo.generator.app.domain.model.StorageUsage
import com.ai.tryonclothes.photo.generator.app.domain.repository.LookRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.StorageRepository
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reports usage and removes temporary data without ever touching saved looks or work in progress. */
class FileStorageRepository(
    private val dirs: AppDirs,
    private val jobs: TryOnJobRepository,
    private val selection: SelectionRepository,
    private val looks: LookRepository,
    private val io: CoroutineDispatcher,
) : StorageRepository {

    override suspend fun usage(): StorageUsage = withContext(io) {
        val lookList = looks.looks.first()
        StorageUsage(
            looksBytes = lookList.sumOf { File(it.imagePath).length() + if (it.originalPath.isEmpty()) 0 else File(it.originalPath).length() },
            cacheBytes = listOf(dirs.work, dirs.results, dirs.captures, dirs.incoming).sumOf { it.sizeRecursive() },
            lookCount = lookList.size,
        )
    }

    /** Settings action: drop every finished generation and temporary file. Running jobs and the current selection survive. */
    override suspend fun clearCache(): Outcome<Unit> = withContext(io) {
        dirs.captures.listFiles()?.forEach { it.delete() }
        dirs.incoming.listFiles()?.forEach { it.delete() }
        jobs.all().filterNot { it.status.isActive }.forEach { jobs.delete(it.id) }
        deleteUnreferenced()
        Outcome.Success(Unit)
    }

    /** Startup housekeeping: keep recent finished jobs (so results survive a restart) and trim the rest. */
    override suspend fun sweep() = withContext(io) {
        jobs.all().filterNot { it.status.isActive }
            .sortedByDescending { it.updatedAt }
            .drop(MAX_FINISHED_JOBS_KEPT)
            .forEach { jobs.delete(it.id) }
        deleteUnreferenced()
    }

    private suspend fun deleteUnreferenced() {
        val current = selection.selection.first()
        val referenced = buildSet {
            current.person?.let { add(it.path) }
            current.garment?.let { add(it.path) }
            jobs.all().forEach { job ->
                add(job.personPath)
                add(job.garmentPath)
                job.resultPath?.let(::add)
            }
        }
        listOf(dirs.work, dirs.results).forEach { dir ->
            dir.listFiles()?.filter { it.path !in referenced }?.forEach { it.delete() }
        }
    }

    private companion object { const val MAX_FINISHED_JOBS_KEPT = 10 }
}
