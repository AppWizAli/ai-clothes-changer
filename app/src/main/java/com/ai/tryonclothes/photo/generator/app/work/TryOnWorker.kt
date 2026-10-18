package com.ai.tryonclothes.photo.generator.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ai.tryonclothes.photo.generator.app.TryOnApp
import com.ai.tryonclothes.photo.generator.app.domain.provider.GenerationScheduler

/** Runs a job in the background so generation continues when the user leaves the screen or the app. */
class TryOnWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        (applicationContext as TryOnApp).container.runGeneration(jobId)
        return Result.success()
    }

    companion object { const val KEY_JOB_ID = "job_id" }
}

class WorkManagerGenerationScheduler(context: Context) : GenerationScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun enqueue(jobId: String) {
        val request = OneTimeWorkRequestBuilder<TryOnWorker>().setInputData(workDataOf(TryOnWorker.KEY_JOB_ID to jobId)).build()
        workManager.enqueueUniqueWork(uniqueName(jobId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(jobId: String) {
        workManager.cancelUniqueWork(uniqueName(jobId))
    }

    private fun uniqueName(jobId: String) = "tryon-$jobId"
}
