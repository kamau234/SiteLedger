package com.siteledger.app

import android.app.Application
import com.siteledger.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SiteLedgerApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.authRepository.start(applicationScope)
        // Seeds missing default categories and heals duplicate names on every launch.
        applicationScope.launch {
            runCatching { container.categoryRepository.ensureDefaults() }
        }
        // Schedule sync on app launch when queued changes exist (blueprint section 04).
        applicationScope.launch {
            if (container.database.outboxDao().pendingCount() > 0) {
                container.syncScheduler.schedule()
            }
        }
    }
}
