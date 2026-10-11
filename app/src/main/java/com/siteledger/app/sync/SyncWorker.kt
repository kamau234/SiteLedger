package com.siteledger.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.siteledger.app.SiteLedgerApp

/**
 * Network-constrained worker that drains the outbox and pulls remote changes.
 * Transient failures use exponential backoff, capped so the phone stops retrying
 * until the next trigger (app launch, save or manual retry) re-arms the work.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SiteLedgerApp ?: return Result.success()
        val container = app.container

        val outcome = try {
            container.syncEngine.sync()
        } catch (t: Throwable) {
            Log.w(TAG, "sync attempt ${runAttemptCount + 1} failed", t)
            return retryOrFail()
        }

        return when (outcome) {
            is SyncOutcome.Success -> {
                Log.i(TAG, "sync done: ${outcome.uploaded} uploaded, ${outcome.applied} applied")
                if (container.database.outboxDao().pendingCount() > 0) {
                    retryOrFail()
                } else {
                    Result.success()
                }
            }
            is SyncOutcome.RetryLater -> {
                Log.i(TAG, "sync deferred: ${outcome.message}")
                retryOrFail()
            }
            is SyncOutcome.Paused -> {
                Log.i(TAG, "sync paused: ${outcome.reason}")
                Result.success()
            }
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        const val UNIQUE_WORK_NAME = "siteledger-sync"
        const val TAG = "siteledger-sync"
        const val MAX_ATTEMPTS = 8
    }
}
