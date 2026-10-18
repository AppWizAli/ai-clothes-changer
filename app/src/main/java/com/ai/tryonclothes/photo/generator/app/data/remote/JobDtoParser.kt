package com.ai.tryonclothes.photo.generator.app.data.remote

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteJob
import com.ai.tryonclothes.photo.generator.app.domain.provider.RemoteState
import org.json.JSONException
import org.json.JSONObject

/** Maps the backend's JSON (see docs/BACKEND_API.md) onto the provider-neutral [RemoteJob]. */
object JobDtoParser {
    fun parse(body: String): Outcome<RemoteJob> {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return Outcome.Failure(AppError.Server("The server sent an unreadable response."))
        }
        val id = json.optString("id").ifEmpty { return Outcome.Failure(AppError.Server("The server response had no job id.")) }
        val state = when (json.optString("status").lowercase()) {
            "queued", "pending" -> RemoteState.QUEUED
            "processing", "running" -> RemoteState.PROCESSING
            "succeeded", "completed" -> RemoteState.SUCCEEDED
            "failed" -> RemoteState.FAILED
            "canceled", "cancelled" -> RemoteState.CANCELLED
            else -> return Outcome.Failure(AppError.Server("The server reported an unknown status."))
        }
        val progress = if (json.has("progress") && !json.isNull("progress")) json.optInt("progress").coerceIn(0, 100) else null
        val resultUrl = json.optString("resultUrl").ifEmpty { null }
        if (state == RemoteState.SUCCEEDED && resultUrl == null) {
            return Outcome.Failure(AppError.Server("The server finished the job but gave no result."))
        }
        val message = json.optJSONObject("error")?.optString("message")?.ifEmpty { null }
        return Outcome.Success(RemoteJob(id, state, progress, resultUrl, message))
    }

    /** Extracts a human-readable reason from an error body, if the backend provided one. */
    fun errorMessage(body: String): String? =
        runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()?.ifEmpty { null }
}
