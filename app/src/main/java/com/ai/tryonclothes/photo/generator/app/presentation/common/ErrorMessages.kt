package com.ai.tryonclothes.photo.generator.app.presentation.common

import android.content.Context
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.ImageProblem

/** Turns a domain error into text the user can act on. */
fun AppError.toMessage(context: Context): String = when (this) {
    AppError.NotConfigured -> context.getString(R.string.error_not_configured)
    AppError.MissingInput -> context.getString(R.string.error_missing_input)
    AppError.Offline -> context.getString(R.string.error_offline)
    AppError.Timeout -> context.getString(R.string.error_timeout)
    is AppError.RateLimited -> retryAfterSeconds?.takeIf { it in 1..600 }
        ?.let { context.resources.getQuantityString(R.plurals.error_rate_limited_seconds, it.toInt(), it.toInt()) }
        ?: context.getString(R.string.error_rate_limited)
    is AppError.Server -> context.getString(R.string.error_server)
    is AppError.Rejected -> detail?.let { context.getString(R.string.error_rejected, it) } ?: context.getString(R.string.error_rejected_plain)
    is AppError.ProviderFailed -> context.getString(R.string.error_provider_failed)
    is AppError.InvalidImage -> context.getString(
        when (problem) {
            ImageProblem.UNSUPPORTED_TYPE -> R.string.error_image_unsupported
            ImageProblem.FILE_TOO_LARGE -> R.string.error_image_too_large_file
            ImageProblem.TOO_SMALL -> R.string.error_image_too_small
            ImageProblem.TOO_LARGE_DIMENSIONS -> R.string.error_image_too_large_dimensions
            ImageProblem.UNREADABLE -> R.string.error_image_unreadable
        },
    )
    AppError.InvalidResult -> context.getString(R.string.error_invalid_result)
    is AppError.Storage -> context.getString(R.string.error_storage)
    AppError.Cancelled -> context.getString(R.string.error_cancelled)
    is AppError.Unknown -> context.getString(R.string.error_unknown)
}
