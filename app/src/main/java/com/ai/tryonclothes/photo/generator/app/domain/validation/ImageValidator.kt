package com.ai.tryonclothes.photo.generator.app.domain.validation

import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.ImageProblem

/** Pure validation rules, applied before any image is processed or uploaded. */
object ImageValidator {
    fun validate(mimeType: String?, sizeBytes: Long, width: Int, height: Int): ImageProblem? = when {
        width <= 0 || height <= 0 -> ImageProblem.UNREADABLE
        mimeType == null || mimeType !in AppConfig.ACCEPTED_MIME_TYPES -> ImageProblem.UNSUPPORTED_TYPE
        sizeBytes > AppConfig.MAX_SOURCE_BYTES -> ImageProblem.FILE_TOO_LARGE
        minOf(width, height) < AppConfig.MIN_EDGE_PX -> ImageProblem.TOO_SMALL
        maxOf(width, height) > AppConfig.MAX_EDGE_PX -> ImageProblem.TOO_LARGE_DIMENSIONS
        else -> null
    }
}
