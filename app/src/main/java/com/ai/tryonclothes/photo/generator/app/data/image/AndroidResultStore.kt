package com.ai.tryonclothes.photo.generator.app.data.image

import android.graphics.BitmapFactory
import com.ai.tryonclothes.photo.generator.app.data.AppDirs
import com.ai.tryonclothes.photo.generator.app.domain.provider.ResultStore
import java.io.File

class AndroidResultStore(private val dirs: AppDirs) : ResultStore {
    override fun newResultFile(jobId: String): File = File(dirs.results, "$jobId.jpg")

    override fun isValidImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType?.startsWith("image/") == true
    }

    override fun delete(file: File) {
        file.delete()
    }
}
