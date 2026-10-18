package com.ai.tryonclothes.photo.generator.app.data

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.ImageProblem
import com.ai.tryonclothes.photo.generator.app.data.local.JobJson
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobJsonTest {
    private val job = TryOnJob(
        id = "abc", personPath = "/p.jpg", garmentPath = "/g.jpg", category = GarmentCategory.DRESSES,
        status = JobStatus.GENERATING, remoteId = "r-9", progress = 42, createdAt = 10, updatedAt = 20,
    )

    @Test fun roundTripsAnActiveJob() = assertEquals(job, JobJson.decode(JobJson.encode(job)))

    @Test fun roundTripsFailureWithDetail() {
        val failed = job.copy(status = JobStatus.FAILED, error = AppError.Rejected("blurry"))
        val decoded = JobJson.decode(JobJson.encode(failed))!!
        assertTrue(decoded.error is AppError.Rejected)
        assertEquals("blurry", decoded.error?.detail)
    }

    @Test fun roundTripsImageProblemErrors() {
        val failed = job.copy(status = JobStatus.FAILED, error = AppError.InvalidImage(ImageProblem.TOO_SMALL))
        val error = JobJson.decode(JobJson.encode(failed))!!.error as AppError.InvalidImage
        assertEquals(ImageProblem.TOO_SMALL, error.problem)
    }

    @Test fun roundTripsRateLimitSeconds() {
        val failed = job.copy(status = JobStatus.FAILED, error = AppError.RateLimited(30))
        assertEquals(30L, (JobJson.decode(JobJson.encode(failed))!!.error as AppError.RateLimited).retryAfterSeconds)
    }

    @Test fun nullablesStayNull() {
        val decoded = JobJson.decode(JobJson.encode(job.copy(remoteId = null, progress = null)))!!
        assertNull(decoded.remoteId); assertNull(decoded.progress); assertNull(decoded.resultPath); assertNull(decoded.error)
    }

    @Test fun corruptRecordsAreIgnoredNotFatal() {
        assertNull(JobJson.decode("not json"))
        assertNull(JobJson.decode("{\"id\":\"x\",\"status\":\"BOGUS\",\"category\":\"TOPS\"}"))
        assertNull(JobJson.decode("{\"status\":\"QUEUED\",\"category\":\"TOPS\"}"))
    }

    @Test fun unknownErrorCodesDegradeToUnknown() {
        assertTrue(AppError.fromCode("from_the_future", "x") is AppError.Unknown)
    }

    @Test fun looksRoundTrip() {
        val look = SavedLook("l1", "/i.jpg", "/o.jpg", 99)
        assertEquals(look, JobJson.decodeLook(JobJson.encodeLook(look)))
    }
}
