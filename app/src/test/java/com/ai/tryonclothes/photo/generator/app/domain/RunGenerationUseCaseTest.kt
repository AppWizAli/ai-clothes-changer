package com.ai.tryonclothes.photo.generator.app.domain

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteState
import com.ai.tryonclothes.photo.generator.app.domain.usecase.RunGenerationUseCase
import com.ai.tryonclothes.photo.generator.app.fakes.FakeResultStore
import com.ai.tryonclothes.photo.generator.app.fakes.InMemoryJobRepository
import com.ai.tryonclothes.photo.generator.app.fakes.ScriptedProvider
import com.ai.tryonclothes.photo.generator.app.fakes.TestClock
import com.ai.tryonclothes.photo.generator.app.fakes.failure
import com.ai.tryonclothes.photo.generator.app.fakes.queuedJob
import com.ai.tryonclothes.photo.generator.app.fakes.remote
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunGenerationUseCaseTest {
    @get:Rule val tmp = TemporaryFolder()

    private val jobs = InMemoryJobRepository()
    private val provider = ScriptedProvider()
    private val clock = TestClock()
    private lateinit var results: FakeResultStore
    private lateinit var person: File
    private lateinit var garment: File

    @Before fun setUp() {
        person = tmp.newFile("person.jpg").apply { writeText("p") }
        garment = tmp.newFile("garment.jpg").apply { writeText("g") }
        results = FakeResultStore(tmp.newFolder("results"))
    }

    private fun useCase(deadline: Long = 60_000) = RunGenerationUseCase(
        jobs, provider, results, clock, pause = { clock.advance(it) }, pollIntervalMillis = 1_000, deadlineMillis = deadline,
    )

    private suspend fun enqueueJob() = queuedJob(person = person, garment = garment).also { jobs.insert(it) }

    @Test fun happyPathSubmitsOncePollsAndCompletesWithARealFile() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.QUEUED))
        provider.statusResponses += Outcome.Success(remote(RemoteState.PROCESSING, progress = 40))
        provider.statusResponses += Outcome.Success(remote(RemoteState.SUCCEEDED, url = "/r.jpg"))

        useCase()(job.id)

        val done = jobs.get(job.id)!!
        assertEquals(JobStatus.COMPLETED, done.status)
        assertEquals(100, done.progress)
        assertEquals(listOf(job.id), provider.submitKeys) // job id is the idempotency key
        assertTrue(File(done.resultPath!!).isFile)
        assertNull(done.error)
    }

    @Test fun submitFailureMarksJobFailedAndRetryable() = runTest {
        val job = enqueueJob()
        provider.submitResponses += failure(AppError.Offline)

        useCase()(job.id)

        val failed = jobs.get(job.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals(AppError.Offline, failed.error)
        assertNull(failed.remoteId)
    }

    @Test fun retryAfterAcceptedSubmissionResumesPollingAndNeverResubmits() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.PROCESSING, id = "r-7"))
        // Polling dies with a non-retryable error after the provider already accepted (and billed) the job.
        provider.statusResponses += failure(AppError.Rejected("boom"))
        useCase()(job.id)
        val failed = jobs.get(job.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals("r-7", failed.remoteId)

        // Simulate RetryTryOnUseCase: same job id, back to QUEUED, remoteId retained.
        jobs.update(job.id) { it.copy(status = JobStatus.QUEUED, error = null) }
        provider.statusResponses += Outcome.Success(remote(RemoteState.SUCCEEDED, id = "r-7", url = "/x"))
        useCase()(job.id)

        assertEquals(1, provider.submitKeys.size)
        assertEquals(JobStatus.COMPLETED, jobs.get(job.id)!!.status)
    }

    @Test fun resubmittingAfterAnUnansweredSubmitReusesTheSameIdempotencyKey() = runTest {
        val job = enqueueJob()
        provider.submitResponses += failure(AppError.Timeout)
        useCase()(job.id)
        jobs.update(job.id) { it.copy(status = JobStatus.QUEUED, error = null) }
        provider.submitResponses += Outcome.Success(remote(RemoteState.SUCCEEDED, url = "/x"))
        useCase()(job.id)

        assertEquals(listOf(job.id, job.id), provider.submitKeys)
        assertEquals(JobStatus.COMPLETED, jobs.get(job.id)!!.status)
    }

    @Test fun transientPollingErrorsAreToleratedUpToALimit() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.QUEUED))
        repeat(3) { provider.statusResponses += failure(AppError.Offline) }
        provider.statusResponses += Outcome.Success(remote(RemoteState.SUCCEEDED, url = "/x"))

        useCase()(job.id)

        assertEquals(JobStatus.COMPLETED, jobs.get(job.id)!!.status)
    }

    @Test fun persistentPollingErrorsFailTheJobKeepingItsRemoteId() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.QUEUED, id = "r-3"))
        repeat(4) { provider.statusResponses += failure(AppError.Offline) }

        useCase()(job.id)

        val failed = jobs.get(job.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals("r-3", failed.remoteId)
    }

    @Test fun providerReportedFailureBecomesProviderFailed() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.QUEUED))
        provider.statusResponses += Outcome.Success(remote(RemoteState.FAILED, message = "no person detected"))

        useCase()(job.id)

        val error = jobs.get(job.id)!!.error as AppError.ProviderFailed
        assertEquals("no person detected", error.detail)
    }

    @Test fun neverEndingGenerationTimesOut() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.QUEUED))
        repeat(200) { provider.statusResponses += Outcome.Success(remote(RemoteState.PROCESSING)) }

        useCase(deadline = 10_000)(job.id)

        assertEquals(AppError.Timeout, jobs.get(job.id)!!.error)
    }

    @Test fun aDownloadThatIsNotAnImageIsNotReportedAsSuccess() = runTest {
        val job = enqueueJob()
        results.validImage = false
        provider.submitResponses += Outcome.Success(remote(RemoteState.SUCCEEDED, url = "/x"))

        useCase()(job.id)

        val failed = jobs.get(job.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals(AppError.InvalidResult, failed.error)
        assertNull(failed.resultPath)
        assertFalse(results.newResultFile(job.id).exists()) // junk download is removed
    }

    @Test fun downloadFailureFailsTheJob() = runTest {
        val job = enqueueJob()
        provider.downloadResponse = failure(AppError.Offline)
        provider.submitResponses += Outcome.Success(remote(RemoteState.SUCCEEDED, url = "/x"))

        useCase()(job.id)

        assertEquals(AppError.Offline, jobs.get(job.id)!!.error)
    }

    @Test fun aConcurrentCancellationWinsOverLateProgress() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.QUEUED))
        provider.statusResponses += Outcome.Success(remote(RemoteState.PROCESSING, progress = 10))
        // The user cancels while the first status request is in flight.
        provider.onStatus = {
            jobs.update(job.id) { it.copy(status = JobStatus.CANCELLED, error = AppError.Cancelled) }
        }

        useCase()(job.id)

        assertEquals(JobStatus.CANCELLED, jobs.get(job.id)!!.status)
        assertEquals(1, provider.statusCalls.size) // stopped polling
    }

    @Test fun cancellationDuringDownloadDiscardsTheResult() = runTest {
        val job = enqueueJob()
        provider.submitResponses += Outcome.Success(remote(RemoteState.SUCCEEDED, url = "/x"))
        // The user cancels after the file was written but before the job is marked complete.
        provider.onDownload = {
            jobs.update(job.id) { it.copy(status = JobStatus.CANCELLED, error = AppError.Cancelled) }
        }

        useCase()(job.id)

        val stored = jobs.get(job.id)!!
        assertEquals(JobStatus.CANCELLED, stored.status)
        assertNull(stored.resultPath)
        assertFalse(results.newResultFile(job.id).exists())
    }

    @Test fun aJobCancelledBeforeItStartsNeverReachesTheProvider() = runTest {
        val job = enqueueJob()
        jobs.update(job.id) { it.copy(status = JobStatus.CANCELLED, error = AppError.Cancelled) }

        useCase()(job.id)

        assertTrue(provider.submitKeys.isEmpty())
    }

    @Test fun missingInputFilesFailWithStorageError() = runTest {
        val job = enqueueJob()
        person.delete()

        useCase()(job.id)

        assertTrue(jobs.get(job.id)!!.error is AppError.Storage)
        assertTrue(provider.submitKeys.isEmpty())
    }

    @Test fun finishedJobsAreNotRunAgain() = runTest {
        val job = enqueueJob()
        jobs.update(job.id) { it.copy(status = JobStatus.COMPLETED) }

        useCase()(job.id)

        assertTrue(provider.submitKeys.isEmpty())
    }

}
