package com.ai.tryonclothes.photo.generator.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.AppError
import com.ai.tryonclothes.photo.generator.app.core.IdGenerator
import com.ai.tryonclothes.photo.generator.app.core.ImageProblem
import com.ai.tryonclothes.photo.generator.app.core.Outcome
import com.ai.tryonclothes.photo.generator.app.data.AppDirs
import com.ai.tryonclothes.photo.generator.app.data.writeAtomically
import com.ai.tryonclothes.photo.generator.app.domain.model.ImageSlot
import com.ai.tryonclothes.photo.generator.app.domain.model.StoredImage
import com.ai.tryonclothes.photo.generator.app.domain.repository.ImageRepository
import com.ai.tryonclothes.photo.generator.app.domain.validation.ImageValidator
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Turns whatever the picker or camera hands us into a validated, upright, size-bounded JPEG.
 * The original is never kept: only the normalised copy that will actually be uploaded.
 */
class AndroidImageRepository(
    private val context: Context,
    private val dirs: AppDirs,
    private val ids: IdGenerator,
    private val io: CoroutineDispatcher,
) : ImageRepository {

    override suspend fun importImage(sourceUri: String, slot: ImageSlot): Outcome<StoredImage> = withContext(io) {
        val incoming = File(dirs.incoming, ids.newId())
        try {
            copyWithLimit(Uri.parse(sourceUri), incoming)?.let { return@withContext Outcome.Failure(AppError.InvalidImage(it)) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(incoming.path, bounds)
            ImageValidator.validate(bounds.outMimeType, incoming.length(), bounds.outWidth, bounds.outHeight)
                ?.let { return@withContext Outcome.Failure(AppError.InvalidImage(it)) }

            val target = File(dirs.work, "${slot.name.lowercase()}-${ids.newId()}.jpg")
            val stored = normalise(incoming, bounds, target)
                ?: return@withContext Outcome.Failure(AppError.InvalidImage(ImageProblem.UNREADABLE))
            Outcome.Success(stored)
        } catch (e: FileNotFoundException) {
            Outcome.Failure(AppError.InvalidImage(ImageProblem.UNREADABLE))
        } catch (e: SecurityException) {
            Outcome.Failure(AppError.InvalidImage(ImageProblem.UNREADABLE))
        } catch (e: IOException) {
            Outcome.Failure(AppError.Storage(e.message))
        } finally {
            incoming.delete()
        }
    }

    override suspend fun createCaptureTarget(): Outcome<String> = withContext(io) {
        try {
            dirs.captures.listFiles()?.forEach { it.delete() }
            val file = File(dirs.captures, "capture-${ids.newId()}.jpg")
            if (!file.createNewFile()) throw IOException("Could not create capture file")
            Outcome.Success(FileProvider.getUriForFile(context, fileProviderAuthority(context), file).toString())
        } catch (e: IOException) {
            Outcome.Failure(AppError.Storage(e.message))
        }
    }

    /** Copies the source into [target], returning a problem if it is larger than the allowed size. */
    private fun copyWithLimit(source: Uri, target: File): ImageProblem? {
        val input = context.contentResolver.openInputStream(source) ?: throw FileNotFoundException(source.toString())
        input.use { stream ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > AppConfig.MAX_SOURCE_BYTES) return ImageProblem.FILE_TOO_LARGE
                    out.write(buffer, 0, read)
                }
            }
        }
        return null
    }

    private fun normalise(source: File, bounds: BitmapFactory.Options, target: File): StoredImage? {
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= AppConfig.UPLOAD_MAX_EDGE_PX) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val matrix = Matrix()
        when (ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        val scale = minOf(1f, AppConfig.UPLOAD_MAX_EDGE_PX.toFloat() / maxOf(decoded.width, decoded.height))
        matrix.postScale(scale, scale)

        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // JPEG has no alpha: flatten onto white so transparent PNGs do not turn black.
        val flat = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(oriented, 0f, 0f, null) }
        try {
            target.writeAtomically { tmp ->
                tmp.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, AppConfig.UPLOAD_JPEG_QUALITY, it) }
            }
            return StoredImage(target.path, flat.width, flat.height, target.length())
        } finally {
            if (decoded !== oriented) decoded.recycle()
            if (oriented !== flat) oriented.recycle()
            flat.recycle()
        }
    }

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
        fun fileProviderAuthority(context: Context) = "${context.packageName}.fileprovider"
    }
}
