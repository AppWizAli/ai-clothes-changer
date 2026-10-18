package com.ai.tryonclothes.photo.generator.app.data.image

import android.graphics.BitmapFactory
import java.io.File

/** Reads image dimensions without decoding pixels. */
object ImageSizeReader {
    fun read(file: File): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
    }
}
