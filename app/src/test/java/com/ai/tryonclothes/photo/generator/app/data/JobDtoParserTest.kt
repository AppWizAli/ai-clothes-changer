package com.ai.tryonclothes.photo.generator.app.data

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.data.remote.JobDtoParser
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobDtoParserTest {
    private fun ok(body: String) = (JobDtoParser.parse(body) as Outcome.Success).value
    private fun error(body: String) = (JobDtoParser.parse(body) as Outcome.Failure).error

    @Test fun parsesProcessingWithProgress() {
        val job = ok("""{"id":"r1","status":"processing","progress":55}""")
        assertEquals(RemoteState.PROCESSING, job.state)
        assertEquals(55, job.progress)
    }

    @Test fun parsesSuccessWithResultUrl() {
        val job = ok("""{"id":"r1","status":"succeeded","resultUrl":"/v1/files/x.jpg"}""")
        assertEquals(RemoteState.SUCCEEDED, job.state)
        assertEquals("/v1/files/x.jpg", job.resultUrl)
    }

    @Test fun clampsOutOfRangeProgress() = assertEquals(100, ok("""{"id":"r","status":"processing","progress":250}""").progress)

    @Test fun parsesFailureMessage() {
        val job = ok("""{"id":"r1","status":"failed","error":{"message":"no person found"}}""")
        assertEquals(RemoteState.FAILED, job.state)
        assertEquals("no person found", job.failureMessage)
    }

    @Test fun acceptsBothSpellingsOfCanceled() {
        assertEquals(RemoteState.CANCELLED, ok("""{"id":"r","status":"canceled"}""").state)
        assertEquals(RemoteState.CANCELLED, ok("""{"id":"r","status":"cancelled"}""").state)
    }

    @Test fun successWithoutResultIsAnError() = assertTrue(error("""{"id":"r","status":"succeeded"}""") is AppError.Server)
    @Test fun unknownStatusIsAnError() = assertTrue(error("""{"id":"r","status":"teleporting"}""") is AppError.Server)
    @Test fun missingIdIsAnError() = assertTrue(error("""{"status":"queued"}""") is AppError.Server)
    @Test fun garbageIsAnError() = assertTrue(error("<html>502</html>") is AppError.Server)
}
