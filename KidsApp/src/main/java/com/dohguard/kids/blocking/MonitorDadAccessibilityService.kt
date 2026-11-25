package com.dohguard.kids.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.dohguard.shared.control.ClockIntegrityGuard
import com.dohguard.shared.control.RuleEvaluationEngine
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.BlockReason
import com.dohguard.shared.remote.NotificationEventType
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository

/**
 * Serviço oficial de Acessibilidade para detectar transições de aplicativos em primeiro plano
 * e aplicar bloqueios automáticos definidos pelo responsável sem polling contínuo.
 */
class MonitorDadAccessibilityService : AccessibilityService() {

    private var lastBlockedPackage: String? = null
    private var lastLaunchMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ControlRulesRepository.init(this)
        UsageSyncRepository.init(this)
        RemoteControlRepository.init(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        if (pkg == packageName) {
            return
        }

        val now = System.currentTimeMillis()
        val rulesState = ControlRulesRepository.rulesState.value
        val telemetry = UsageSyncRepository.telemetryState.value
        val apps = telemetry?.apps.orEmpty()

        val matchedApp = apps.firstOrNull { it.packageName == pkg }
        val category = matchedApp?.category ?: AppCategory.OTHER
        val appUsedMinutes = matchedApp?.usedMinutesToday ?: 0
        val categoryUsedMinutes = apps.filter { it.category == category }.sumOf { it.usedMinutesToday }

        val clockGuard = ClockIntegrityGuard(this)
        val rawTotalMinutes = telemetry?.summary?.totalUsedMinutesToday ?: apps.sumOf { it.usedMinutesToday }
        val safeTotalMinutes = clockGuard.updateAndPersist(rawTotalMinutes).totalMinutes

        val decision = RuleEvaluationEngine.evaluateAppAccess(
            state = rulesState,
            packageName = pkg,
            appCategory = category,
            totalDeviceUsedMinutes = safeTotalMinutes,
            appUsedMinutes = appUsedMinutes,
            categoryUsedMinutes = categoryUsedMinutes,
            nowMillis = now
        )

        if (decision.isBlocked) {
            val isDuplicateTrigger = (pkg == lastBlockedPackage) && (now - lastLaunchMillis < 1200L)
            if (!isDuplicateTrigger) {
                lastBlockedPackage = pkg
                lastLaunchMillis = now
                val appLabel = matchedApp?.appName ?: pkg
                if (decision.reason == BlockReason.DAILY_LIMIT_REACHED) {
                    RemoteControlRepository.recordNotification(
                        type = NotificationEventType.DAILY_LIMIT_REACHED,
                        message = "Limite diário atingido ao tentar abrir $appLabel.",
                        dedupKey = "daily-limit-${now / 60_000L}"
                    )
                } else {
                    RemoteControlRepository.recordNotification(
                        type = NotificationEventType.APP_BLOCKED,
                        message = "$appLabel foi bloqueado (${decision.title}).",
                        dedupKey = "app-block-$pkg-${now / 60_000L}"
                    )
                }
                val intent = Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(BlockOverlayActivity.EXTRA_BLOCK_TITLE, decision.title)
                    putExtra(BlockOverlayActivity.EXTRA_BLOCK_MESSAGE, decision.message)
                    putExtra(BlockOverlayActivity.EXTRA_UNLOCK_TIME, decision.unlockTimeFormatted ?: "")
                    putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, pkg)
                    putExtra(BlockOverlayActivity.EXTRA_BLOCKED_APP_NAME, appLabel)
                }
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {
        // Nenhuma limpeza especial necessária
    }
}
