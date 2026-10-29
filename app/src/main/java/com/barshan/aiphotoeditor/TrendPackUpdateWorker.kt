package com.barshan.aiphotoeditor

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Refreshes only small, validated text/rule packs. Photos never leave the device.
 * Work is constrained to unmetered connectivity and is safe to retry.
 */
class TrendPackUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            CaptionCatalogRepository.refresh(applicationContext)
            AiEditRulesRepository.refresh(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object TrendPackUpdateScheduler {
    private const val PERIODIC_WORK = "trend_pack_update_periodic"
    private const val STARTUP_WORK = "trend_pack_update_startup"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val manager = WorkManager.getInstance(context.applicationContext)

        val periodic = PeriodicWorkRequestBuilder<TrendPackUpdateWorker>(15, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        // Also check once after app launch; it waits quietly until unmetered Wi-Fi/network exists.
        val startup = OneTimeWorkRequestBuilder<TrendPackUpdateWorker>()
            .setConstraints(constraints)
            .build()
        manager.enqueueUniqueWork(STARTUP_WORK, ExistingWorkPolicy.KEEP, startup)
    }
}
