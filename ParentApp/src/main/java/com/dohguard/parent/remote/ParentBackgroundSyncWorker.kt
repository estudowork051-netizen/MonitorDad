package com.dohguard.parent.remote

import android.content.Context
import androidx.work.*
import com.dohguard.parent.notifications.ParentNotificationHelper
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository
import java.util.concurrent.TimeUnit

/**
 * Worker em segundo plano para sincronizar o estado remoto no MonitorDad Parent
 * e disparar notificações locais de novos eventos quando houver conexão.
 */
class ParentBackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        ControlRulesRepository.init(applicationContext)
        UsageSyncRepository.init(applicationContext)
        RemoteControlRepository.init(applicationContext)
        ParentNotificationHelper.ensureChannel(applicationContext)

        val syncResult = RemoteControlRepository.syncParentSession()

        RemoteControlRepository.notifications.value
            .filter { !it.isRead }
            .take(3)
            .forEach { event ->
                ParentNotificationHelper.showNotification(applicationContext, event)
            }

        return if (syncResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "monitordad_parent_remote_sync_periodic"

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ParentBackgroundSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
