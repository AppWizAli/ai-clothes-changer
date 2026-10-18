package com.ai.tryonclothes.photo.generator.app.data.local

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Clock
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.data.AppDirs
import com.ai.tryonclothes.photo.generator.app.data.writeAtomically
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import com.ai.tryonclothes.photo.generator.app.domain.repository.LookRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Saved looks live in app-private storage: an index of small records plus image files. Only metadata
 * is held in memory; images are decoded lazily by the UI, so a large collection stays cheap to list.
 */
class FileLookRepository(
    private val dirs: AppDirs,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : LookRepository {
    private val indexFile = File(dirs.looks, "index.json")
    private val state = MutableStateFlow<List<SavedLook>>(emptyList())
    private val lock = Mutex()
    private var loaded = false

    override val looks: Flow<List<SavedLook>> = flow {
        ensureLoaded()
        emitAll(state)
    }

    override suspend fun get(lookId: String): SavedLook? {
        ensureLoaded()
        return state.value.firstOrNull { it.id == lookId }
    }

    override suspend fun save(job: TryOnJob): Outcome<SavedLook> {
        ensureLoaded()
        val result = job.resultPath?.let(::File)?.takeIf { it.isFile }
            ?: return Outcome.Failure(AppError.Storage("The generated image is no longer available."))
        return lock.withLock {
            // The look id is the job id, so saving the same result twice is a no-op rather than a duplicate.
            state.value.firstOrNull { it.id == job.id }?.let { return@withLock Outcome.Success(it) }
            withContext(io) {
                val image = File(dirs.looks, "${job.id}.jpg")
                val original = File(dirs.looks, "${job.id}-original.jpg")
                try {
                    image.writeAtomically { result.copyTo(it, overwrite = true) }
                    File(job.personPath).takeIf { it.isFile }?.let { src -> original.writeAtomically { src.copyTo(it, overwrite = true) } }
                    val look = SavedLook(job.id, image.path, original.takeIf { it.isFile }?.path.orEmpty(), clock.now())
                    writeIndex(listOf(look) + state.value)
                    state.value = listOf(look) + state.value
                    Outcome.Success(look)
                } catch (e: IOException) {
                    image.delete(); original.delete()
                    Outcome.Failure(AppError.Storage(e.message))
                }
            }
        }
    }

    override suspend fun delete(lookId: String): Outcome<Unit> {
        ensureLoaded()
        return lock.withLock {
            val look = state.value.firstOrNull { it.id == lookId } ?: return@withLock Outcome.Success(Unit)
            withContext(io) {
                try {
                    val remaining = state.value - look
                    writeIndex(remaining)
                    File(look.imagePath).delete()
                    if (look.originalPath.isNotEmpty()) File(look.originalPath).delete()
                    state.value = remaining
                    Outcome.Success(Unit)
                } catch (e: IOException) {
                    Outcome.Failure(AppError.Storage(e.message))
                }
            }
        }
    }

    override suspend fun deleteAll(): Outcome<Unit> {
        ensureLoaded()
        return lock.withLock {
            withContext(io) {
                try {
                    writeIndex(emptyList())
                    state.value.forEach { look ->
                        File(look.imagePath).delete()
                        if (look.originalPath.isNotEmpty()) File(look.originalPath).delete()
                    }
                    state.value = emptyList()
                    Outcome.Success(Unit)
                } catch (e: IOException) {
                    Outcome.Failure(AppError.Storage(e.message))
                }
            }
        }
    }

    private fun writeIndex(list: List<SavedLook>) {
        val array = JSONArray().also { a -> list.forEach { a.put(JobJson.encodeLook(it)) } }
        indexFile.writeAtomically { it.writeText(array.toString()) }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            state.value = withContext(io) {
                if (!indexFile.isFile) emptyList()
                else runCatching { JSONArray(indexFile.readText()) }.getOrNull()?.let { array ->
                    (0 until array.length()).mapNotNull { JobJson.decodeLook(array.getJSONObject(it)) }
                        .filter { File(it.imagePath).isFile } // ignore entries whose image was lost
                        .sortedByDescending { it.createdAt }
                }.orEmpty()
            }
            loaded = true
        }
    }
}
