package com.ai.tryonclothes.photo.generator.app.data.local

import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.model.JobStatus
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import com.ai.tryonclothes.photo.generator.app.domain.model.TryOnJob
import org.json.JSONObject

/** Serialisation for persisted jobs and looks. Kept free of I/O so it can be unit tested. */
object JobJson {
    fun encode(job: TryOnJob): String = JSONObject().apply {
        put("id", job.id)
        put("person", job.personPath)
        put("garment", job.garmentPath)
        put("category", job.category.name)
        put("status", job.status.name)
        put("remoteId", job.remoteId ?: JSONObject.NULL)
        put("progress", job.progress ?: JSONObject.NULL)
        put("result", job.resultPath ?: JSONObject.NULL)
        put("errorCode", job.error?.code ?: JSONObject.NULL)
        put("errorDetail", job.error?.detail ?: JSONObject.NULL)
        put("createdAt", job.createdAt)
        put("updatedAt", job.updatedAt)
    }.toString()

    /** Returns null for corrupt or unrecognisable records rather than crashing the app at startup. */
    fun decode(text: String): TryOnJob? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val status = enumOrNull<JobStatus>(json.optString("status")) ?: return null
        val category = enumOrNull<GarmentCategory>(json.optString("category")) ?: return null
        val id = json.optString("id").ifEmpty { return null }
        val errorCode = json.optStringOrNull("errorCode")
        return TryOnJob(
            id = id,
            personPath = json.optString("person"),
            garmentPath = json.optString("garment"),
            category = category,
            status = status,
            remoteId = json.optStringOrNull("remoteId"),
            progress = if (json.isNull("progress")) null else json.optInt("progress"),
            resultPath = json.optStringOrNull("result"),
            error = errorCode?.let { AppError.fromCode(it, json.optStringOrNull("errorDetail")) },
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
        )
    }

    fun encodeLook(look: SavedLook): JSONObject = JSONObject().apply {
        put("id", look.id)
        put("image", look.imagePath)
        put("original", look.originalPath)
        put("createdAt", look.createdAt)
    }

    fun decodeLook(json: JSONObject): SavedLook? {
        val id = json.optString("id").ifEmpty { return null }
        val image = json.optString("image").ifEmpty { return null }
        return SavedLook(id, image, json.optString("original"), json.optLong("createdAt"))
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
}
