package com.ai.tryonclothes.photo.generator.app.domain.usecase

import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Clock
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteState
import com.ai.tryonclothes.photo.generator.app.domain.provider.ResultStore
import com.ai.tryonclothes.photo.generator.app.domain.provider.TryOnProvider
import com.ai.tryonclothes.photo.generator.app.domain.repository.TryOnJobRepository
import java.io.File
import kotlinx.coroutines.delay

/**
 * Drives one job from QUEUED to a terminal state: submit (once), poll, download, verify.
 * Every state change goes through [TryOnJobRepository.update] so a concurrent cancel always wins.
 */
class RunGenerationUseCase(
    private val jobs: TryOnJobRepository,
    private val provider: TryOnProvider,
    private val results: ResultStore,
    private val clock: Clock,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pollIntervalMillis: Long = AppConfig.POLL_INTERVAL_MILLIS,
    private val deadlineMillis: Long = AppConfig.GENERATION_DEADLINE_MILLIS,
) {
    suspend operator fun invoke(jobId: String) {
        val job = jobs.get(jobId) ?: return
        if (!job.status.isActive) return

        val person = File(job.personPath)
        val garment = File(job.garmentPath)
        if (!person.isFile || !garment.isFile) {
            return fail(jobId, AppError.Storage("The selected images are no longer available."))
        }

        var remote: RemoteJob? = job.remoteId?.let { RemoteJob(it, RemoteState.QUEUED) }
        if (remote == null) {
            if (advance(jobId) { it.copy(status = JobStatus.UPLOADING, progress = null) } == null) return
            when (val submitted = provider.submit(job.id, person, garment, job.category)) {
                is Outcome.Failure -> return fail(jobId, submitted.error)
                is Outcome.Success -> remote = submitted.value
            }
        }
        val accepted = remote
        if (advance(jobId) { it.copy(status = JobStatus.GENERATING, remoteId = accepted.remoteId, progress = accepted.progress) } == null) return
        poll(jobId, accepted)
    }

    private suspend fun poll(jobId: String, first: RemoteJob) {
        val startedAt = clock.now()
        var remote = first
        var transientFailures = 0
        // The submit response may already carry a state; only ask the server when it does not settle things.
        var needsFetch = first.state == RemoteState.QUEUED
        while (true) {
            if (needsFetch) {
                when (val polled = provider.status(remote.remoteId)) {
                    is Outcome.Success -> { remote = polled.value; transientFailures = 0 }
                    is Outcome.Failure -> {
                        if (!polled.error.retryable || ++transientFailures > MAX_TRANSIENT_POLL_FAILURES) return fail(jobId, polled.error)
                        pause(pollIntervalMillis)
                        continue
                    }
                }
            }
            needsFetch = true
            when (remote.state) {
                RemoteState.SUCCEEDED -> return finish(jobId, remote)
                RemoteState.FAILED -> return fail(jobId, AppError.ProviderFailed(remote.failureMessage))
                RemoteState.CANCELLED -> return fail(jobId, AppError.Cancelled)
                RemoteState.QUEUED, RemoteState.PROCESSING -> {
                    val progress = remote.progress
                    if (advance(jobId) { it.copy(progress = progress ?: it.progress) } == null) return
                }
            }
            if (clock.now() - startedAt > deadlineMillis) return fail(jobId, AppError.Timeout)
            pause(pollIntervalMillis)
        }
    }

    private suspend fun finish(jobId: String, remote: RemoteJob) {
        val target = results.newResultFile(jobId)
        when (val downloaded = provider.downloadResult(remote, target)) {
            is Outcome.Failure -> { results.delete(target); return fail(jobId, downloaded.error) }
            is Outcome.Success -> Unit
        }
        if (!results.isValidImage(target)) {
            results.delete(target)
            return fail(jobId, AppError.InvalidResult)
        }
        val stored = advance(jobId) { it.copy(status = JobStatus.COMPLETED, progress = 100, resultPath = target.path, error = null) }
        if (stored == null) results.delete(target) // cancelled while downloading
    }

    private suspend fun fail(jobId: String, error: AppError) {
        advance(jobId) { it.copy(status = JobStatus.FAILED, error = error) }
    }

    /** Applies [change] only while the job is still active, stamping the update time. */
    private suspend fun advance(jobId: String, change: (TryOnJob) -> TryOnJob): TryOnJob? =
        jobs.update(jobId) { if (it.status.isActive) change(it).copy(updatedAt = clock.now()) else null }

    private companion object {
        const val MAX_TRANSIENT_POLL_FAILURES = 3
    }
}
