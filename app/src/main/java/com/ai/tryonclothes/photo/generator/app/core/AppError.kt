package com.ai.tryonclothes.photo.generator.app.core

/**
 * Every failure the user can see. [code] is persisted with a failed job so the right message
 * can be shown after the process restarts; [retryable] drives whether a Retry action is offered.
 */
sealed class AppError(val code: String, val retryable: Boolean, val detail: String? = null) {
    data object NotConfigured : AppError("not_configured", retryable = false)
    data object MissingInput : AppError("missing_input", retryable = false)
    data object Offline : AppError("offline", retryable = true)
    data object Timeout : AppError("timeout", retryable = true)
    class RateLimited(val retryAfterSeconds: Long?) : AppError("rate_limited", retryable = true, detail = retryAfterSeconds?.toString())
    class Server(detail: String?) : AppError("server", retryable = true, detail = detail)
    class Rejected(detail: String?) : AppError("rejected", retryable = false, detail = detail)
    class ProviderFailed(detail: String?) : AppError("provider_failed", retryable = true, detail = detail)
    class InvalidImage(val problem: ImageProblem) : AppError("invalid_image_${problem.name}", retryable = false)
    data object InvalidResult : AppError("invalid_result", retryable = true)
    class Storage(detail: String?) : AppError("storage", retryable = false, detail = detail)
    data object Cancelled : AppError("cancelled", retryable = true)
    class Unknown(detail: String?) : AppError("unknown", retryable = true, detail = detail)

    companion object {
        /** Rebuilds an error from persisted [code] and [detail]. Unrecognised codes degrade to [Unknown]. */
        fun fromCode(code: String, detail: String?): AppError = when {
            code == NotConfigured.code -> NotConfigured
            code == MissingInput.code -> MissingInput
            code == Offline.code -> Offline
            code == Timeout.code -> Timeout
            code == "rate_limited" -> RateLimited(detail?.toLongOrNull())
            code == "server" -> Server(detail)
            code == "rejected" -> Rejected(detail)
            code == "provider_failed" -> ProviderFailed(detail)
            code == InvalidResult.code -> InvalidResult
            code == "storage" -> Storage(detail)
            code == Cancelled.code -> Cancelled
            code.startsWith("invalid_image_") ->
                ImageProblem.entries.firstOrNull { code.endsWith(it.name) }?.let(::InvalidImage) ?: Unknown(detail)
            else -> Unknown(detail)
        }
    }
}

enum class ImageProblem { UNSUPPORTED_TYPE, FILE_TOO_LARGE, TOO_SMALL, TOO_LARGE_DIMENSIONS, UNREADABLE }

/** Explicit success/failure result so failures are never swallowed by catch-all handlers. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
