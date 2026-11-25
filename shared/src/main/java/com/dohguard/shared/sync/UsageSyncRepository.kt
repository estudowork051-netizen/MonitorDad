package com.dohguard.shared.sync

import android.content.Context
import android.content.SharedPreferences
import com.dohguard.shared.model.AppUsageInfo
import com.dohguard.shared.model.SyncStatus
import com.dohguard.shared.model.TelemetryPayload
import com.dohguard.shared.model.UsageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositório central de telemetria e sincronização de estatísticas entre Parent e Kids.
 * Gerencia cache local offline (SharedPreferences), histórico, sincronização manual
 * e estados de conectividade, diferenciando dados demonstrativos de telemetria real.
 */
object UsageSyncRepository {

    private const val PREFS_NAME = "monitordad_usage_cache"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_LAST_SYNC_TS = "last_sync_timestamp"
    private const val KEY_IS_DEMO = "is_demo_data"

    private var prefs: SharedPreferences? = null

    private val _telemetryState = MutableStateFlow<TelemetryPayload?>(null)
    val telemetryState: StateFlow<TelemetryPayload?> = _telemetryState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        val initialSyncTs = System.currentTimeMillis() - 1000 * 60 * 2
        val initialSummary = UsageSummary.fromAppsList(
            apps = AppUsageInfo.DEMO_APPS,
            lastSyncTimestamp = initialSyncTs,
            deviceId = "tablet-lucas-01",
            deviceName = "Tablet do Lucas",
            isDemoData = true
        )
        _telemetryState.value = TelemetryPayload(
            deviceId = "tablet-lucas-01",
            deviceName = "Tablet do Lucas",
            timestamp = initialSyncTs,
            isPermissionGranted = true,
            summary = initialSummary,
            apps = AppUsageInfo.DEMO_APPS
        )
    }

    /**
     * Inicializa o cache local persistente com o Context da aplicação.
     * Seguro para chamar múltiplas vezes (ex: onCreate das Activities / Worker).
     */
    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val cachedTs = sp.getLong(KEY_LAST_SYNC_TS, 0L)
        if (cachedTs > 0L) {
            val cachedDeviceId = sp.getString(KEY_DEVICE_ID, "tablet-lucas-01") ?: "tablet-lucas-01"
            val cachedDeviceName = sp.getString(KEY_DEVICE_NAME, "Tablet do Lucas") ?: "Tablet do Lucas"
            val cachedIsDemo = sp.getBoolean(KEY_IS_DEMO, true)
            val currentApps = _telemetryState.value?.apps ?: AppUsageInfo.DEMO_APPS
            val restoredSummary = UsageSummary.fromAppsList(
                apps = currentApps,
                lastSyncTimestamp = cachedTs,
                deviceId = cachedDeviceId,
                deviceName = cachedDeviceName,
                isDemoData = cachedIsDemo
            )
            _telemetryState.value = TelemetryPayload(
                deviceId = cachedDeviceId,
                deviceName = cachedDeviceName,
                timestamp = cachedTs,
                isPermissionGranted = _telemetryState.value?.isPermissionGranted ?: true,
                summary = restoredSummary,
                apps = currentApps
            )
        }
    }

    /**
     * Atualiza a telemetria com novos dados reais coletados pelo serviço do Kids
     * e persiste os metadados de sincronização no cache local.
     */
    fun updateTelemetry(payload: TelemetryPayload) {
        val normalizedSummary = payload.summary.copy(
            deviceId = payload.deviceId,
            deviceName = payload.deviceName,
            lastSyncTimestamp = payload.timestamp,
            isDemoData = false
        )
        val normalizedPayload = payload.copy(summary = normalizedSummary)
        _telemetryState.value = normalizedPayload
        saveToLocalCache(normalizedPayload, isDemo = false)
    }

    /**
     * Executa a sincronização manual de estatísticas de uso.
     */
    suspend fun triggerManualSync(): Result<TelemetryPayload> {
        _isSyncing.value = true
        return try {
            // Simulação de latência de rede/transporte seguro
            kotlinx.coroutines.delay(400)

            val current = _telemetryState.value
            val updatedTimestamp = System.currentTimeMillis()
            val apps = current?.apps ?: AppUsageInfo.DEMO_APPS
            val deviceId = current?.deviceId ?: "tablet-lucas-01"
            val deviceName = current?.deviceName ?: "Tablet do Lucas"
            val isDemo = current?.summary?.isDemoData ?: true

            val updatedSummary = UsageSummary.fromAppsList(
                apps = apps,
                lastSyncTimestamp = updatedTimestamp,
                deviceId = deviceId,
                deviceName = deviceName,
                isDemoData = isDemo
            ).copy(syncStatus = SyncStatus.ONLINE)

            val newPayload = TelemetryPayload(
                deviceId = deviceId,
                deviceName = deviceName,
                timestamp = updatedTimestamp,
                isPermissionGranted = current?.isPermissionGranted ?: true,
                summary = updatedSummary,
                apps = apps
            )

            _telemetryState.value = newPayload
            saveToLocalCache(newPayload, isDemo = isDemo)
            Result.success(newPayload)
        } catch (e: Exception) {
            val current = _telemetryState.value
            if (current != null) {
                _telemetryState.value = current.copy(
                    summary = current.summary.copy(syncStatus = SyncStatus.OFFLINE)
                )
            }
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    private fun saveToLocalCache(payload: TelemetryPayload, isDemo: Boolean) {
        prefs?.edit()
            ?.putString(KEY_DEVICE_ID, payload.deviceId)
            ?.putString(KEY_DEVICE_NAME, payload.deviceName)
            ?.putLong(KEY_LAST_SYNC_TS, payload.timestamp)
            ?.putBoolean(KEY_IS_DEMO, isDemo)
            ?.apply()
    }
}
