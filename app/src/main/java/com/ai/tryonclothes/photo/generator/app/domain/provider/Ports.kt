package com.ai.tryonclothes.photo.generator.app.domain.provider

import java.io.File

/** Hands generation work to a background executor that survives process death. */
interface GenerationScheduler {
    fun enqueue(jobId: String)
    fun cancel(jobId: String)
}

/** Where generated images are written and how they are checked. */
interface ResultStore {
    fun newResultFile(jobId: String): File
    /** True only if [file] decodes as a real image. A provider returning HTML or garbage must not count as success. */
    fun isValidImage(file: File): Boolean
    fun delete(file: File)
}
