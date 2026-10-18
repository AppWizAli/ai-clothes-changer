package com.ai.tryonclothes.photo.generator.app.data

import android.content.Context
import java.io.File

/** One place that decides where every kind of file lives, and how long it is expected to live. */
class AppDirs(context: Context) {
    /** Normalised photos/garments chosen by the user. Pruned by the orphan sweep. */
    val work = File(context.filesDir, "work").apply { mkdirs() }
    val jobs = File(context.filesDir, "jobs").apply { mkdirs() }
    /** Generated images that have not been saved yet. */
    val results = File(context.filesDir, "results").apply { mkdirs() }
    /** Saved looks: the only permanent user content. */
    val looks = File(context.filesDir, "looks").apply { mkdirs() }
    val captures = File(context.cacheDir, "capture").apply { mkdirs() }
    val incoming = File(context.cacheDir, "incoming").apply { mkdirs() }
}

/** Writes through a temp file so a crash can never leave a half-written file under the real name. */
internal fun File.writeAtomically(write: (File) -> Unit) {
    val tmp = File(parentFile, "$name.tmp")
    try {
        write(tmp)
        if (!tmp.renameTo(this)) throw java.io.IOException("Could not move ${tmp.name} into place")
    } finally {
        if (tmp.exists()) tmp.delete()
    }
}

internal fun File.sizeRecursive(): Long = if (isFile) length() else listFiles()?.sumOf { it.sizeRecursive() } ?: 0L
