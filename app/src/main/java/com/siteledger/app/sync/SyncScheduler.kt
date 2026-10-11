package com.siteledger.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Repositories call this after a successful local save so uploads start promptly. */
fun interface SyncTrigger {
    fun requestSync()
}

/**
 * Durable scheduling for the outbox. Unique work with a network constraint means repeated
 * save events never stack workers; a running sync that still sees pending rows re-schedules
 * itself through [SyncWorker]. See blueprint section 04, local save protocol step 4.
 */
class SyncScheduler(context: Context) : SyncTrigger {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun requestSync() = schedule()

    fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(SyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
