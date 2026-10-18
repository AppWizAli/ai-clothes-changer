package com.ai.tryonclothes.photo.generator.app.data

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.data.remote.HttpTryOnProvider
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteState
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HttpTryOnProviderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var provider: HttpTryOnProvider
    private lateinit var person: File
    private lateinit var garment: File

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).connectTimeout(1, TimeUnit.SECONDS).build()
        provider = HttpTryOnProvider(client, server.url("/").toString(), "install-1", Dispatchers.Unconfined)
        person = tmp.newFile("person.jpg").apply { writeText("PERSON") }
        garment = tmp.newFile("garment.jpg").apply { writeText("GARMENT") }
    }

    @After fun tearDown() = server.shutdown()

    private suspend fun submit() = provider.submit("key-123", person, garment, GarmentCategory.OUTERWEAR)

    @Test fun submitSendsIdempotencyKeyInstallIdAndBothImages() = runTest {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"id":"r-1","status":"queued"}"""))

        val result = submit() as Outcome.Success

        assertEquals("r-1", result.value.remoteId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/tryon/jobs", request.path)
        assertEquals("key-123", request.getHeader("Idempotency-Key"))
        assertEquals("install-1", request.getHeader("X-Install-Id"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("PERSON") && body.contains("GARMENT") && body.contains("outerwear"))
    }

    @Test fun statusPollsTheJobUrl() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"r-1","status":"processing","progress":30}"""))
        val job = (provider.status("r-1") as Outcome.Success).value
        assertEquals(RemoteState.PROCESSING, job.state)
        assertEquals("/v1/tryon/jobs/r-1", server.takeRequest().path)
    }

    @Test fun rateLimitCarriesRetryAfter() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "17"))
        val error = (submit() as Outcome.Failure).error as AppError.RateLimited
        assertEquals(17L, error.retryAfterSeconds)
        assertTrue(error.retryable)
    }

    @Test fun serverErrorsAreRetryable() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val error = (submit() as Outcome.Failure).error
        assertTrue(error is AppError.Server && error.retryable)
    }

    @Test fun rejectedImagesAreNotRetryableAndKeepTheServerReason() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":{"message":"face not visible"}}"""))
        val error = (submit() as Outcome.Failure).error as AppError.Rejected
        assertEquals("face not visible", error.detail)
        assertEquals(false, error.retryable)
    }

    @Test fun authFailuresAreRejections() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue((submit() as Outcome.Failure).error is AppError.Rejected)
    }

    @Test fun slowServerBecomesTimeout() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(AppError.Timeout, (provider.status("r-1") as Outcome.Failure).error)
    }

    @Test fun droppedConnectionBecomesOffline() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(AppError.Offline, (provider.status("r-1") as Outcome.Failure).error)
    }

    @Test fun unreachableHostBecomesOffline() = runTest {
        server.shutdown()
        assertEquals(AppError.Offline, (provider.status("r-1") as Outcome.Failure).error)
    }

    @Test fun downloadWritesTheResultToDisk() = runTest {
        server.enqueue(MockResponse().setBody("IMAGEBYTES"))
        val target = tmp.newFile("out.jpg")
        val result = provider.downloadResult(RemoteJob("r", RemoteState.SUCCEEDED, resultUrl = "/files/out.jpg"), target)
        assertTrue(result is Outcome.Success)
        assertEquals("IMAGEBYTES", target.readText())
        assertEquals("/files/out.jpg", server.takeRequest().path)
    }

    @Test fun downloadHttpErrorIsReportedNotWritten() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val target = tmp.newFile("out.jpg")
        val result = provider.downloadResult(RemoteJob("r", RemoteState.SUCCEEDED, resultUrl = "/files/out.jpg"), target)
        assertTrue(result is Outcome.Failure)
        assertEquals(0L, target.length())
    }

    @Test fun cancelIssuesADelete() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(provider.cancel("r-1") is Outcome.Success)
        assertEquals("DELETE", server.takeRequest().method)
    }
}
