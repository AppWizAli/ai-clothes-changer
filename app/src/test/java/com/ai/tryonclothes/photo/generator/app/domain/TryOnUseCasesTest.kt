package com.ai.tryonclothes.photo.generator.app.domain

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.Selection
import com.ai.tryonclothes.photo.generator.app.domain.model.StoredImage
import com.ai.tryonclothes.photo.generator.app.domain.usecase.CancelTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.domain.usecase.RetryTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.domain.usecase.StartTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.fakes.InMemoryJobRepository
import com.ai.tryonclothes.photo.generator.app.fakes.InMemorySelection
import com.ai.tryonclothes.photo.generator.app.fakes.RecordingScheduler
import com.ai.tryonclothes.photo.generator.app.fakes.ScriptedProvider
import com.ai.tryonclothes.photo.generator.app.fakes.SequentialIds
import com.ai.tryonclothes.photo.generator.app.fakes.TestClock
import com.ai.tryonclothes.photo.generator.app.fakes.queuedJob
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TryOnUseCasesTest {
    private val jobs = InMemoryJobRepository()
    private val scheduler = RecordingScheduler()
    private val clock = TestClock()
    private val image = StoredImage("/x.jpg", 800, 1000, 1000)
    private val ready = Selection(person = image, garment = image, category = GarmentCategory.DRESSES)

    private fun start(selection: Selection = ready, configured: Boolean = true) =
        StartTryOnUseCase(InMemorySelection(selection), jobs, scheduler, clock, SequentialIds(), isBackendConfigured = { configured })

    @Test fun startCreatesAndSchedulesExactlyOneJob() = runTest {
        val result = start()() as Outcome.Success
        assertEquals("job-1", result.value)
        assertEquals(listOf("job-1"), scheduler.enqueued)
        val job = jobs.get("job-1")!!
        assertEquals(JobStatus.QUEUED, job.status)
        assertEquals(GarmentCategory.DRESSES, job.category)
    }

    @Test fun doubleTapReturnsTheRunningJobInsteadOfStartingAnother() = runTest {
        val useCase = start()
        val first = useCase() as Outcome.Success
        val second = useCase() as Outcome.Success
        assertEquals(first.value, second.value)
        assertEquals(1, jobs.all().size)
        assertEquals(1, scheduler.enqueued.size)
    }

    @Test fun startWithoutBackendFailsHonestlyAndCreatesNothing() = runTest {
        val result = start(configured = false)()
        assertEquals(AppError.NotConfigured, (result as Outcome.Failure).error)
        assertTrue(jobs.all().isEmpty())
        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test fun startRequiresBothImages() = runTest {
        assertEquals(AppError.MissingInput, (start(Selection(person = image))() as Outcome.Failure).error)
        assertEquals(AppError.MissingInput, (start(Selection(garment = image))() as Outcome.Failure).error)
        assertTrue(jobs.all().isEmpty())
    }

    @Test fun cancelMarksJobCancelledStopsWorkAndTellsTheProviderOnlyIfItAcceptedIt() = runTest {
        val provider = ScriptedProvider()
        jobs.insert(queuedJob("a", File("p"), File("g")).copy(status = JobStatus.GENERATING, remoteId = "r-5"))
        jobs.insert(queuedJob("b", File("p"), File("g"), clock = 5).copy(status = JobStatus.UPLOADING))
        val cancel = CancelTryOnUseCase(jobs, scheduler, provider, clock)

        cancel("a"); cancel("b")

        assertEquals(JobStatus.CANCELLED, jobs.get("a")!!.status)
        assertEquals(JobStatus.CANCELLED, jobs.get("b")!!.status)
        assertEquals(listOf("a", "b"), scheduler.cancelled)
        assertEquals(listOf("r-5"), provider.cancelCalls)
    }

    @Test fun cancellingAFinishedJobDoesNothing() = runTest {
        jobs.insert(queuedJob("a", File("p"), File("g")).copy(status = JobStatus.COMPLETED))
        CancelTryOnUseCase(jobs, scheduler, ScriptedProvider(), clock)("a")
        assertEquals(JobStatus.COMPLETED, jobs.get("a")!!.status)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test fun retryRequeuesTheSameJobAndKeepsItsRemoteId() = runTest {
        jobs.insert(queuedJob("a", File("p"), File("g")).copy(status = JobStatus.FAILED, remoteId = "r-1", error = AppError.Offline))
        val result = RetryTryOnUseCase(jobs, scheduler, clock)("a")

        assertTrue(result is Outcome.Success)
        val job = jobs.get("a")!!
        assertEquals(JobStatus.QUEUED, job.status)
        assertEquals("r-1", job.remoteId)
        assertEquals(null, job.error)
        assertEquals(listOf("a"), scheduler.enqueued)
    }

    @Test fun retryDoesNothingForAJobThatIsStillRunning() = runTest {
        jobs.insert(queuedJob("a", File("p"), File("g")).copy(status = JobStatus.GENERATING))
        val result = RetryTryOnUseCase(jobs, scheduler, clock)("a")
        assertTrue(result is Outcome.Failure)
        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test fun retryDoesNotStartASecondJobWhileAnotherIsRunning() = runTest {
        jobs.insert(queuedJob("old", File("p"), File("g")).copy(status = JobStatus.FAILED))
        jobs.insert(queuedJob("busy", File("p"), File("g"), clock = 9).copy(status = JobStatus.GENERATING))
        RetryTryOnUseCase(jobs, scheduler, clock)("old")
        assertEquals(JobStatus.FAILED, jobs.get("old")!!.status)
        assertTrue(scheduler.enqueued.isEmpty())
    }
}
