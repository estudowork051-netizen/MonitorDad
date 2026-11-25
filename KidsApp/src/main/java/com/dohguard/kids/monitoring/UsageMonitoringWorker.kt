package com.dohguard.kids.monitoring

import android.content.Context
import androidx.work.*
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.sync.UsageSyncRepository
import java.util.concurrent.TimeUnit

/**
 * Worker do Android WorkManager para coleta periódica e econômica de telemetria de uso.
 * Executa respeitando restrições de economia de bateria (sem loops ou wake-locks contínuos).
 */
class UsageMonitoringWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            UsageSyncRepository.init(applicationContext)
            RemoteControlRepository.init(applicationContext)
            UsageStatsCollector.refreshTelemetryIfPermitted(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "doh_guard_usage_monitoring_work"

        /**
         * Agenda a coleta periódica das estatísticas com intervalo de 15 minutos (mínimo suportado pelo WorkManager).
         */
        fun enqueuePeriodicMonitoring(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<UsageMonitoringWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }

        /**
         * Executa uma coleta imediata em segundo plano.
         */
        fun enqueueImmediateSync(context: Context) {
            val oneTimeWork = OneTimeWorkRequestBuilder<UsageMonitoringWorker>()
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeWork)
        }
    }
}
