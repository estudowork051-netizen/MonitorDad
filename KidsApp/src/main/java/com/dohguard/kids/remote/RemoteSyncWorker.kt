package com.dohguard.kids.remote

import android.content.Context
import androidx.work.*
import com.dohguard.kids.monitoring.UsageStatsCollector
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository
import java.util.concurrent.TimeUnit

/**
 * Worker em segundo plano para manter a comunicação remota do MonitorDad Kids ativa,
 * executar comandos pendentes, enviar confirmações (ACK) e sincronizar regras/telemetria
 * automaticamente sempre que houver conexão com a internet.
 */
class RemoteSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        ControlRulesRepository.init(applicationContext)
        UsageSyncRepository.init(applicationContext)
        RemoteControlRepository.init(applicationContext)

        runCatching {
            UsageStatsCollector.refreshTelemetryIfPermitted(applicationContext)
        }

        val syncResult = RemoteControlRepository.syncKidsSession { cmd ->
            KidsCommandExecutor.execute(applicationContext, cmd)
        }

        return if (syncResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "monitordad_kids_remote_sync_periodic"
        private const val IMMEDIATE_WORK_NAME = "monitordad_kids_remote_sync_immediate"

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RemoteSyncWorker>(
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

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RemoteSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
