package com.dohguard.shared.model

/**
 * Estado de sincronização e conectividade com o dispositivo monitorado.
 */
enum class SyncStatus(val label: String) {
    ONLINE("Online"),
    OUTDATED("Desatualizado"),
    OFFLINE("Offline")
}

/**
 * Resumo consolidado de utilização diária para visualização do responsável.
 */
data class UsageSummary(
    val totalUsedMinutesToday: Int,
    val totalUsedMinutesYesterday: Int,
    val appsCountUsedToday: Int,
    val mostUsedAppName: String,
    val mostUsedAppMinutes: Int,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.ONLINE,
    val deviceId: String = "tablet-lucas-01",
    val deviceName: String = "Tablet do Lucas",
    val isDemoData: Boolean = false
) {
    val lastSyncFormatted: String
        get() {
            val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(lastSyncTimestamp))
        }

    val formattedTotalToday: String
        get() {
            val h = totalUsedMinutesToday / 60
            val m = totalUsedMinutesToday % 60
            return "${h}h ${m}m"
        }

    val formattedTotalYesterday: String
        get() {
            val h = totalUsedMinutesYesterday / 60
            val m = totalUsedMinutesYesterday % 60
            return "${h}h ${m}m"
        }

    val differenceVsYesterdayFormatted: String
        get() {
            val diff = totalUsedMinutesToday - totalUsedMinutesYesterday
            return when {
                diff > 0 -> "+${diff} min em relação a ontem"
                diff < 0 -> "${diff} min em relação a ontem"
                else -> "Mesmo tempo de ontem"
            }
        }

    val mostUsedFormatted: String
        get() {
            val h = mostUsedAppMinutes / 60
            val m = mostUsedAppMinutes % 60
            val time = if (h > 0) "${h}h ${m}m" else "${m}m"
            return if (mostUsedAppName.isNotBlank()) "$mostUsedAppName ($time)" else "Nenhum"
        }

    companion object {
        fun fromAppsList(
            apps: List<AppUsageInfo>,
            lastSyncTimestamp: Long = System.currentTimeMillis(),
            deviceId: String = "tablet-lucas-01",
            deviceName: String = "Tablet do Lucas",
            isDemoData: Boolean = false
        ): UsageSummary {
            val totalToday = apps.sumOf { it.usedMinutesToday }
            val totalYesterday = apps.sumOf { it.usedMinutesYesterday }
            val usedApps = apps.filter { it.usedMinutesToday > 0 }
            val mostUsed = usedApps.maxByOrNull { it.usedMinutesToday }

            val now = System.currentTimeMillis()
            val diffMs = now - lastSyncTimestamp
            val status = when {
                diffMs < 1000 * 60 * 15 -> SyncStatus.ONLINE       // menos de 15 min
                diffMs < 1000 * 60 * 120 -> SyncStatus.OUTDATED    // entre 15 min e 2h
                else -> SyncStatus.OFFLINE                        // mais de 2h
            }

            return UsageSummary(
                totalUsedMinutesToday = totalToday,
                totalUsedMinutesYesterday = totalYesterday,
                appsCountUsedToday = usedApps.size,
                mostUsedAppName = mostUsed?.appName ?: "Nenhum",
                mostUsedAppMinutes = mostUsed?.usedMinutesToday ?: 0,
                lastSyncTimestamp = lastSyncTimestamp,
                syncStatus = status,
                deviceId = deviceId,
                deviceName = deviceName,
                isDemoData = isDemoData
            )
        }

        val DEMO = UsageSummary(
            totalUsedMinutesToday = 187, // 3h 7m
            totalUsedMinutesYesterday = 220, // 3h 40m
            appsCountUsedToday = 7,
            mostUsedAppName = "YouTube Kids",
            mostUsedAppMinutes = 70,
            lastSyncTimestamp = System.currentTimeMillis() - 1000 * 60 * 2,
            syncStatus = SyncStatus.ONLINE,
            deviceId = "tablet-lucas-01",
            deviceName = "Tablet do Lucas",
            isDemoData = true
        )
    }
}

/**
 * Pacote de telemetria emitido pelo tablet infantil e recebido pelo aplicativo dos pais.
 */
data class TelemetryPayload(
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val isPermissionGranted: Boolean,
    val summary: UsageSummary,
    val apps: List<AppUsageInfo>,
    val currentForegroundPackage: String? = null,
    val currentForegroundAppName: String? = null,
    val currentForegroundStartedAtMillis: Long? = null
)
