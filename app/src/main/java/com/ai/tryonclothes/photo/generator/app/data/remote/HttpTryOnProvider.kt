package com.ai.tryonclothes.photo.generator.app.data.remote

import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.TryOnProvider
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response

/**
 * Talks to a backend implementing docs/BACKEND_API.md. The backend holds the AI-provider credentials;
 * this class only knows how to submit images, poll status and fetch the result.
 */
class HttpTryOnProvider(
    private val client: OkHttpClient,
    baseUrl: String,
    private val installId: String,
    private val io: CoroutineDispatcher,
) : TryOnProvider {
    private val base = baseUrl.trimEnd('/').toHttpUrl()
    private val uploadClient = client.newBuilder().callTimeout(AppConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    override suspend fun submit(idempotencyKey: String, person: File, garment: File, category: GarmentCategory): Outcome<RemoteJob> {
        val jpeg = "image/jpeg".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("category", category.name.lowercase())
            .addFormDataPart("person", person.name, person.asRequestBody(jpeg))
            .addFormDataPart("garment", garment.name, garment.asRequestBody(jpeg))
            .build()
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments("v1/tryon/jobs").build())
            .header("Idempotency-Key", idempotencyKey)
            .header("X-Install-Id", installId)
            .post(body)
            .build()
        return execute(uploadClient, request, parse = JobDtoParser::parse)
    }

    override suspend fun status(remoteId: String): Outcome<RemoteJob> = execute(client, jobRequest(remoteId).get().build(), JobDtoParser::parse)

    override suspend fun cancel(remoteId: String): Outcome<Unit> =
        execute(client, jobRequest(remoteId).delete().build()) { Outcome.Success(Unit) }

    override suspend fun downloadResult(job: RemoteJob, target: File): Outcome<Unit> {
        val url = job.resultUrl?.let(base::resolve) ?: return Outcome.Failure(AppError.InvalidResult)
        val request = Request.Builder().url(url).header("X-Install-Id", installId).get().build()
        return try {
            val response = client.newCall(request).await()
            response.use {
                mapHttpFailure(it)?.let { error -> return Outcome.Failure(error) }
                val source = it.body ?: return Outcome.Failure(AppError.InvalidResult)
                withContext(io) { source.byteStream().use { input -> target.outputStream().use { out -> input.copyTo(out) } } }
                Outcome.Success(Unit)
            }
        } catch (e: IOException) {
            Outcome.Failure(mapIo(e))
        }
    }

    private fun jobRequest(remoteId: String) = Request.Builder()
        .url(base.newBuilder().addPathSegments("v1/tryon/jobs").addPathSegment(remoteId).build())
        .header("X-Install-Id", installId)

    private suspend fun <T> execute(http: OkHttpClient, request: Request, parse: (String) -> Outcome<T>): Outcome<T> = try {
        http.newCall(request).await().use { response ->
            val text = withContext(io) { response.body?.string().orEmpty() }
            mapHttpFailure(response, text)?.let { Outcome.Failure(it) } ?: parse(text)
        }
    } catch (e: IOException) {
        Outcome.Failure(mapIo(e))
    }

    private fun mapHttpFailure(response: Response, body: String? = null): AppError? {
        if (response.isSuccessful) return null
        val reason = body?.let(JobDtoParser::errorMessage)
        return when (val code = response.code) {
            429 -> AppError.RateLimited(response.header("Retry-After")?.toLongOrNull())
            401, 403 -> AppError.Rejected(reason ?: "The server did not accept this request.")
            400, 413, 415, 422 -> AppError.Rejected(reason ?: "The server could not use these images.")
            404 -> AppError.ProviderFailed(reason ?: "The generation could not be found on the server.")
            in 500..599 -> AppError.Server(reason ?: "Server error $code")
            else -> AppError.Server("Unexpected response $code")
        }
    }

    private fun mapIo(e: IOException): AppError = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> AppError.Offline
        is InterruptedIOException -> AppError.Timeout // OkHttp reports connect/read/call timeouts this way
        else -> AppError.Offline
    }
}

/** Suspends until the call completes and cancels the HTTP call if the coroutine is cancelled. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
}
