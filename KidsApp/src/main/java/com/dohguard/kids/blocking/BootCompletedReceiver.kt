package com.dohguard.kids.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dohguard.kids.monitoring.UsageMonitoringWorker
import com.dohguard.kids.permission.UsagePermissionHelper
import com.dohguard.kids.remote.RemoteSyncWorker
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository

/**
 * Restaura regras persistidas localmente e re-agenda o monitoramento e a reconexão remota
 * após reinicialização ou atualização do aplicativo MonitorDad Kids.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ControlRulesRepository.init(context)
            UsageSyncRepository.init(context)
            RemoteControlRepository.init(context)
            if (UsagePermissionHelper.hasUsageStatsPermission(context)) {
                UsageMonitoringWorker.enqueuePeriodicMonitoring(context)
            }
            RemoteSyncWorker.enqueuePeriodicSync(context)
            RemoteSyncWorker.enqueueImmediateSync(context)
        }
    }
}
