package com.ai.tryonclothes.photo.generator.app

import android.content.Context
import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.SystemClock
import com.ai.tryonclothes.photo.generator.app.core.UuidGenerator
import com.ai.tryonclothes.photo.generator.app.data.AppDirs
import com.ai.tryonclothes.photo.generator.app.data.image.AndroidImageRepository
import com.ai.tryonclothes.photo.generator.app.data.image.AndroidResultStore
import com.ai.tryonclothes.photo.generator.app.data.local.FileLookRepository
import com.ai.tryonclothes.photo.generator.app.data.local.FileTryOnJobRepository
import com.ai.tryonclothes.photo.generator.app.data.local.PrefsSelectionRepository
import com.ai.tryonclothes.photo.generator.app.data.remote.HttpTryOnProvider
import com.ai.tryonclothes.photo.generator.app.data.remote.NotConfiguredTryOnProvider
import com.ai.tryonclothes.photo.generator.app.data.repository.FileStorageRepository
import com.ai.tryonclothes.photo.generator.app.domain.provider.GenerationScheduler
import com.ai.tryonclothes.photo.generator.app.domain.provider.TryOnProvider
import com.ai.tryonclothes.photo.generator.app.domain.usecase.CancelTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.domain.usecase.RetryTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.domain.usecase.RunGenerationUseCase
import com.ai.tryonclothes.photo.generator.app.domain.usecase.SaveLookUseCase
import com.ai.tryonclothes.photo.generator.app.domain.usecase.StartTryOnUseCase
import com.ai.tryonclothes.photo.generator.app.work.WorkManagerGenerationScheduler
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/**
 * Hand-written dependency container. The graph is small and fully visible here, which keeps the project
 * free of annotation processors; swap in a DI framework only if the graph grows enough to justify one.
 */
class AppContainer(private val context: Context) {
    private val io = Dispatchers.IO
    private val dirs = AppDirs(context)

    val selection = PrefsSelectionRepository(context.getSharedPreferences("selection", Context.MODE_PRIVATE))
    val jobs = FileTryOnJobRepository(dirs, io)
    val looks = FileLookRepository(dirs, SystemClock, io)
    val images = AndroidImageRepository(context, dirs, UuidGenerator, io)
    val storage = FileStorageRepository(dirs, jobs, selection, looks, io)

    private val resultStore = AndroidResultStore(dirs)
    private val scheduler: GenerationScheduler = WorkManagerGenerationScheduler(context)

    val provider: TryOnProvider = if (AppConfig.isBackendConfigured) {
        HttpTryOnProvider(
            client = OkHttpClient.Builder()
                .connectTimeout(AppConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(AppConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build(),
            baseUrl = AppConfig.backendUrl,
            installId = installId(),
            io = io,
        )
    } else {
        NotConfiguredTryOnProvider
    }

    val startTryOn = StartTryOnUseCase(selection, jobs, scheduler, SystemClock, UuidGenerator)
    val cancelTryOn = CancelTryOnUseCase(jobs, scheduler, provider, SystemClock)
    val retryTryOn = RetryTryOnUseCase(jobs, scheduler, SystemClock)
    val runGeneration = RunGenerationUseCase(jobs, provider, resultStore, SystemClock)
    val saveLook = SaveLookUseCase(jobs, looks)

    /** A random per-install identifier (not a secret) so the backend can rate-limit per device. */
    private fun installId(): String {
        val prefs = context.getSharedPreferences("install", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    }
}
