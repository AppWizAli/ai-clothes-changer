package com.ai.tryonclothes.photo.generator.app.core

import com.ai.tryonclothes.photo.generator.app.BuildConfig

/** Central, non-secret configuration. Secrets never live in the app; see docs/BACKEND_API.md. */
object AppConfig {
    /** Base URL of the try-on backend, injected at build time via the `tryon.backendUrl` property. */
    val backendUrl: String = BuildConfig.TRYON_BACKEND_URL.trim().trimEnd('/')
    val isBackendConfigured: Boolean get() = backendUrl.isNotEmpty()

    // Image rules, enforced before anything is uploaded.
    const val MAX_SOURCE_BYTES = 15L * 1024 * 1024
    const val MIN_EDGE_PX = 256
    const val MAX_EDGE_PX = 12_000
    const val UPLOAD_MAX_EDGE_PX = 1536
    const val UPLOAD_JPEG_QUALITY = 90
    val ACCEPTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    // Network behaviour.
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val UPLOAD_TIMEOUT_SECONDS = 90L
    const val READ_TIMEOUT_SECONDS = 30L
    const val POLL_INTERVAL_MILLIS = 2_000L
    /** Upper bound on how long we keep polling one job before reporting a timeout. */
    const val GENERATION_DEADLINE_MILLIS = 5L * 60 * 1000
}
