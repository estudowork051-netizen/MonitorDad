package com.dohguard.shared.model

/**
 * Representa os dados e o status do dispositivo monitorado (ex: Tablet da Criança).
 */
data class DeviceInfo(
    val deviceId: String,
    val childName: String,
    val deviceName: String,
    val model: String,
    val osVersion: String,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val isOnline: Boolean,
    val isPaused: Boolean,
    val lastSyncFormatted: String,
    val storageUsedGb: Double,
    val storageTotalGb: Double
) {
    companion object {
        val DEMO_TABLET = DeviceInfo(
            deviceId = "tablet-lucas-01",
            childName = "Lucas",
            deviceName = "Tablet do Lucas",
            model = "Samsung Galaxy Tab A8",
            osVersion = "Android 13 (One UI 5.0)",
            batteryPercentage = 82,
            isCharging = true,
            isOnline = true,
            isPaused = false,
            lastSyncFormatted = "há 2 minutos",
            storageUsedGb = 24.5,
            storageTotalGb = 64.0
        )
    }
}
