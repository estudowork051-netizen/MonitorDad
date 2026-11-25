package com.dohguard.kids.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.dohguard.kids.permission.UsagePermissionHelper
import com.dohguard.shared.model.*
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.sync.UsageSyncRepository
import java.util.Calendar

/**
 * Coletor oficial de estatísticas de utilização de aplicativos no tablet infantil.
 * Utiliza UsageStatsManager e PackageManager conforme as diretrizes do Android.
 */
class UsageStatsCollector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Coleta as estatísticas completas de utilização dos aplicativos instalados.
     */
    fun collectUsageTelemetry(deviceId: String = "tablet-lucas-01", deviceName: String = "Tablet do Lucas"): TelemetryPayload {
        val hasPermission = UsagePermissionHelper.hasUsageStatsPermission(context)
        val now = System.currentTimeMillis()

        if (!hasPermission || usageStatsManager == null) {
            // Se a permissão não foi concedida, lista os aplicativos instalados com tempo zerado
            // e sinaliza claramente que a permissão é necessária.
            val installedApps = getInstalledLauncherApps().map { (pkg, label, category, isSystem) ->
                AppUsageInfo(
                    packageName = pkg,
                    appName = label,
                    category = category,
                    usedMinutesToday = 0,
                    usedMinutesYesterday = 0,
                    dailyLimitMinutes = 60,
                    isBlocked = false,
                    iconIdentifier = pkg,
                    lastTimeUsedFormatted = "Aguardando permissão",
                    isSystemApp = isSystem,
                    isMonitored = true
                )
            }

            val summary = UsageSummary.fromAppsList(installedApps, now).copy(
                syncStatus = SyncStatus.ONLINE
            )

            return TelemetryPayload(
                deviceId = deviceId,
                deviceName = deviceName,
                timestamp = now,
                isPermissionGranted = false,
                summary = summary,
                apps = installedApps
            )
        }

        // 1. Calcula os intervalos de tempo (Início do dia de hoje e dia de ontem)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startOfYesterday = calendar.timeInMillis
        val endOfYesterday = startOfToday

        // 2. Coleta estatísticas agregadas de hoje e de ontem
        val statsToday = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfToday,
            now
        ).associateBy { it.packageName }

        val statsYesterday = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfYesterday,
            endOfYesterday
        ).associateBy { it.packageName }

        // 3. Coleta sessões detalhadas do dia de hoje via UsageEvents
        val sessionsByPackage = collectTodaySessions(startOfToday, now)

        // 4. Mapeia todos os aplicativos instalados e mescla com as estatísticas
        val installedApps = getInstalledLauncherApps().map { (pkg, label, category, isSystem) ->
            val usageToday = statsToday[pkg]
            val usageYesterday = statsYesterday[pkg]

            val usedMillisToday = usageToday?.totalTimeInForeground ?: 0L
            val usedMillisYesterday = usageYesterday?.totalTimeInForeground ?: 0L

            val usedMinToday = (usedMillisToday / (1000 * 60)).toInt()
            val usedMinYesterday = (usedMillisYesterday / (1000 * 60)).toInt()

            val lastUsedTimestamp = usageToday?.lastTimeUsed ?: 0L
            val lastUsedFormatted = if (lastUsedTimestamp > 0) {
                formatTimestamp(lastUsedTimestamp)
            } else {
                "Não utilizado hoje"
            }

            val sessions = sessionsByPackage[pkg] ?: emptyList()

            AppUsageInfo(
                packageName = pkg,
                appName = label,
                category = category,
                usedMinutesToday = usedMinToday,
                usedMinutesYesterday = usedMinYesterday,
                dailyLimitMinutes = 60,
                isBlocked = false,
                iconIdentifier = pkg,
                lastTimeUsedTimestamp = lastUsedTimestamp,
                lastTimeUsedFormatted = lastUsedFormatted,
                sessionHistory = sessions,
                isSystemApp = isSystem,
                isMonitored = true
            )
        }.sortedByDescending { it.usedMinutesToday }

        val summary = UsageSummary.fromAppsList(installedApps, now)
        val foreground = getCurrentForegroundAppSession(installedApps)

        return TelemetryPayload(
            deviceId = deviceId,
            deviceName = deviceName,
            timestamp = now,
            isPermissionGranted = true,
            summary = summary,
            apps = installedApps,
            currentForegroundPackage = foreground?.packageName,
            currentForegroundAppName = foreground?.appName,
            currentForegroundStartedAtMillis = foreground?.startedAtMillis
        )
    }

    data class ForegroundAppSession(
        val packageName: String,
        val appName: String,
        val startedAtMillis: Long
    )

    /**
     * Identifica o aplicativo atualmente em primeiro plano e o horário de início da sessão.
     */
    fun getCurrentForegroundAppSession(knownApps: List<AppUsageInfo>? = null): ForegroundAppSession? {
        if (!UsagePermissionHelper.hasUsageStatsPermission(context) || usageStatsManager == null) {
            return null
        }

        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - 1000L * 60 * 60 * 2, now)
        val event = UsageEvents.Event()
        var lastForegroundPkg: String? = null
        var lastForegroundTs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == "com.android.systemui") continue

            if (isForegroundEvent(event.eventType)) {
                lastForegroundPkg = pkg
                lastForegroundTs = event.timeStamp
            } else if (isBackgroundEvent(event.eventType) && pkg == lastForegroundPkg && event.timeStamp >= lastForegroundTs) {
                lastForegroundPkg = null
                lastForegroundTs = 0L
            }
        }

        val pkg = lastForegroundPkg ?: return null
        val knownName = knownApps?.firstOrNull { it.packageName == pkg }?.appName
        val resolvedName = knownName ?: runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkg)

        return ForegroundAppSession(
            packageName = pkg,
            appName = resolvedName,
            startedAtMillis = lastForegroundTs
        )
    }

    /**
     * Identifica o pacote do aplicativo atualmente em primeiro plano no tablet.
     */
    fun getCurrentForegroundPackage(): String? = getCurrentForegroundAppSession()?.packageName

    /**
     * Extrai as sessões de uso (início e término) de hoje através dos UsageEvents.
     */
    private fun collectTodaySessions(startTime: Long, endTime: Long): Map<String, List<AppSession>> {
        if (usageStatsManager == null) return emptyMap()

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        val activeSessions = mutableMapOf<String, Long>()
        val completedSessions = mutableMapOf<String, MutableList<AppSession>>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName

            when {
                isForegroundEvent(event.eventType) -> {
                    activeSessions[pkg] = event.timeStamp
                }
                isBackgroundEvent(event.eventType) -> {
                    val sessionStart = activeSessions.remove(pkg)
                    if (sessionStart != null && event.timeStamp > sessionStart) {
                        val durationMs = event.timeStamp - sessionStart
                        val durationMin = (durationMs / (1000 * 60)).toInt()
                        if (durationMin >= 1) { // Registra apenas sessões de pelo menos 1 minuto
                            val list = completedSessions.getOrPut(pkg) { mutableListOf() }
                            list.add(
                                AppSession(
                                    id = "s_${pkg}_${sessionStart}",
                                    startTimeFormatted = formatTimeOnly(sessionStart),
                                    endTimeFormatted = formatTimeOnly(event.timeStamp),
                                    durationMinutes = durationMin,
                                    timestamp = sessionStart
                                )
                            )
                        }
                    }
                }
            }
        }

        return completedSessions
    }

    /**
     * Lista todos os aplicativos instalados com Activity inicializável (Launchable).
     */
    private fun getInstalledLauncherApps(): List<AppItemMetadata> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val result = mutableListOf<AppItemMetadata>()

        for (resolveInfo in resolveInfos) {
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val packageName = appInfo.packageName

            // Ignora o próprio KidsApp
            if (packageName == context.packageName) continue

            val label = resolveInfo.loadLabel(packageManager).toString()
            val category = resolveCategory(appInfo, packageName, label)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            result.add(AppItemMetadata(packageName, label, category, isSystem))
        }

        return result.distinctBy { it.packageName }
    }

    /**
     * Classifica a categoria do aplicativo utilizando APIs do Android e heurísticas de nomes de pacotes.
     */
    private fun resolveCategory(appInfo: ApplicationInfo, pkg: String, label: String): AppCategory {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.VIDEOS
                ApplicationInfo.CATEGORY_SOCIAL,
                ApplicationInfo.CATEGORY_NEWS -> return AppCategory.COMMUNICATION
                ApplicationInfo.CATEGORY_MAPS -> return AppCategory.NAVIGATION
            }
        }

        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()

        return when {
            lowerPkg.contains("game") || lowerPkg.contains("mojang") || lowerPkg.contains("roblox") || lowerLabel.contains("jogo") -> AppCategory.GAMES
            lowerPkg.contains("youtube") || lowerPkg.contains("netflix") || lowerPkg.contains("disney") || lowerPkg.contains("video") || lowerPkg.contains("player") -> AppCategory.VIDEOS
            lowerPkg.contains("duolingo") || lowerPkg.contains("khan") || lowerPkg.contains("education") || lowerPkg.contains("learn") || lowerLabel.contains("escola") -> AppCategory.EDUCATION
            lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") || lowerPkg.contains("message") || lowerPkg.contains("dialer") -> AppCategory.COMMUNICATION
            lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("map") -> AppCategory.NAVIGATION
            else -> AppCategory.OTHER
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "Hoje às $h:$m"
    }

    private fun formatTimeOnly(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$h:$m"
    }

    private fun isForegroundEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun isBackgroundEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
        }
    }

    private data class AppItemMetadata(
        val packageName: String,
        val label: String,
        val category: AppCategory,
        val isSystem: Boolean
    )

    companion object {
        /**
         * Coleta a telemetria real de uso do dispositivo (usando deviceId e deviceName pareados
         * quando disponíveis) e publica no UsageSyncRepository caso a permissão de uso esteja concedida.
         */
        fun refreshTelemetryIfPermitted(context: Context): TelemetryPayload? {
            if (!UsagePermissionHelper.hasUsageStatsPermission(context)) {
                return null
            }
            val creds = RemoteControlRepository.credentials.value
            val deviceId = creds.deviceId.ifBlank { "tablet-lucas-01" }
            val deviceName = creds.deviceName.ifBlank { "Tablet do Lucas" }
            val fresh = UsageStatsCollector(context).collectUsageTelemetry(
                deviceId = deviceId,
                deviceName = deviceName
            )
            UsageSyncRepository.updateTelemetry(fresh)
            return fresh
        }
    }
}
