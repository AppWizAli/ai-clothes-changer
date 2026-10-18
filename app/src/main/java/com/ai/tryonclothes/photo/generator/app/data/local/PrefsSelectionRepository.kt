package com.ai.tryonclothes.photo.generator.app.data.local

import android.content.SharedPreferences
import com.ai.tryonclothes.photo.generator.app.domain.model.GarmentCategory
import com.ai.tryonclothes.photo.generator.app.domain.model.ImageSlot
import com.ai.tryonclothes.photo.generator.app.domain.model.Selection
import com.ai.tryonclothes.photo.generator.app.domain.model.StoredImage
import com.ai.tryonclothes.photo.generator.app.domain.repository.SelectionRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Remembers the chosen photo, garment and category across navigation and process death.
 * Replaced files are not deleted here: a running or retryable job may still reference them,
 * so cleanup is left to the orphan sweep in [com.ai.tryonclothes.photo.generator.app.data.repository.FileStorageRepository].
 */
class PrefsSelectionRepository(private val prefs: SharedPreferences) : SelectionRepository {
    private val state = MutableStateFlow(read())
    override val selection: Flow<Selection> = state

    override suspend fun setImage(slot: ImageSlot, image: StoredImage) {
        write(slot, image)
        state.value = read()
    }

    override suspend fun setCategory(category: GarmentCategory) {
        prefs.edit().putString(KEY_CATEGORY, category.name).apply()
        state.value = read()
    }

    override suspend fun clearGarment() {
        prefs.edit().remove(prefix(ImageSlot.GARMENT)).apply()
        state.value = read()
    }

    override suspend fun clearAll() {
        prefs.edit().clear().apply()
        state.value = read()
    }

    private fun write(slot: ImageSlot, image: StoredImage) {
        prefs.edit()
            .putString(prefix(slot), image.path)
            .putInt(prefix(slot) + "_w", image.width)
            .putInt(prefix(slot) + "_h", image.height)
            .putLong(prefix(slot) + "_s", image.sizeBytes)
            .apply()
    }

    private fun read(): Selection = Selection(
        person = readImage(ImageSlot.PERSON),
        garment = readImage(ImageSlot.GARMENT),
        category = prefs.getString(KEY_CATEGORY, null)?.let { name -> GarmentCategory.entries.firstOrNull { it.name == name } }
            ?: GarmentCategory.TOPS,
    )

    private fun readImage(slot: ImageSlot): StoredImage? {
        val path = prefs.getString(prefix(slot), null) ?: return null
        if (!File(path).isFile) return null // the file was cleaned up; treat as not selected
        return StoredImage(path, prefs.getInt(prefix(slot) + "_w", 0), prefs.getInt(prefix(slot) + "_h", 0), prefs.getLong(prefix(slot) + "_s", 0))
    }

    private fun prefix(slot: ImageSlot) = slot.name.lowercase()

    private companion object { const val KEY_CATEGORY = "category" }
}
