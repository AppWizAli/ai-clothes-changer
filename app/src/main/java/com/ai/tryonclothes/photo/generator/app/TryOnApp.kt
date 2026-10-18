package com.ai.tryonclothes.photo.generator.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TryOnApp : Application() {
    val container by lazy { AppContainer(this) }

    /** Lives as long as the process; used for work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { container.storage.sweep() }
    }
}
