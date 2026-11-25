package com.dohguard.shared.remote

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistência local das preferências de conexão (Automático / Wi-Fi local / Internet),
 * estado do assistente inicial e lista de dispositivos vinculados (sem armazenar segredos).
 */
object ConnectionSettingsRepository {

    private const val PREFS_NAME = "monitordad_connection_settings"
    private const val KEY_MODE = "transport_mode"
    private const val KEY_LOCAL_DISCOVERY = "local_discovery_enabled"
    private const val KEY_RELAY_URL = "relay_base_url"
    private const val KEY_ENV_LABEL = "environment_label"
    private const val KEY_WIZARD_DONE = "setup_wizard_done"
    private const val KEY_LINKED_DEVICES = "linked_devices_json"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun loadSettings(): ConnectionSettings {
        val sp = prefs ?: return ConnectionSettings()
        val modeName = sp.getString(KEY_MODE, TransportMode.AUTO.name) ?: TransportMode.AUTO.name
        val mode = TransportMode.entries.firstOrNull { it.name == modeName } ?: TransportMode.AUTO
        return ConnectionSettings(
            mode = mode,
            localDiscoveryEnabled = sp.getBoolean(KEY_LOCAL_DISCOVERY, true),
            relayBaseUrl = sp.getString(KEY_RELAY_URL, "") ?: "",
            environmentLabel = sp.getString(KEY_ENV_LABEL, "Produção") ?: "Produção"
        )
    }

    fun saveSettings(settings: ConnectionSettings) {
        prefs?.edit()
            ?.putString(KEY_MODE, settings.mode.name)
            ?.putBoolean(KEY_LOCAL_DISCOVERY, settings.localDiscoveryEnabled)
            ?.putString(KEY_RELAY_URL, settings.relayBaseUrl.trim())
            ?.putString(KEY_ENV_LABEL, settings.environmentLabel.trim().ifBlank { "Produção" })
            ?.apply()
    }

    fun isWizardCompleted(): Boolean = prefs?.getBoolean(KEY_WIZARD_DONE, false) ?: false

    fun markWizardCompleted(completed: Boolean = true) {
        prefs?.edit()?.putBoolean(KEY_WIZARD_DONE, completed)?.apply()
    }

    fun loadLinkedDevices(): List<LinkedDeviceRecord> {
        val raw = prefs?.getString(KEY_LINKED_DEVICES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val transportName = obj.optString("lastTransport", ActiveTransportType.NONE.name)
                    add(
                        LinkedDeviceRecord(
                            deviceId = obj.getString("deviceId"),
                            deviceName = obj.optString("deviceName", "Tablet MonitorDad"),
                            parentId = obj.optString("parentId", "parent-hod-01"),
                            pairedAtMillis = obj.optLong("pairedAtMillis", 0L),
                            lastSeenMillis = obj.optLong("lastSeenMillis", 0L),
                            lastTransport = ActiveTransportType.entries.firstOrNull { it.name == transportName }
                                ?: ActiveTransportType.NONE,
                            revoked = obj.optBoolean("revoked", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveLinkedDevices(devices: List<LinkedDeviceRecord>) {
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("deviceId", d.deviceId)
                    put("deviceName", d.deviceName)
                    put("parentId", d.parentId)
                    put("pairedAtMillis", d.pairedAtMillis)
                    put("lastSeenMillis", d.lastSeenMillis)
                    put("lastTransport", d.lastTransport.name)
                    put("revoked", d.revoked)
                }
            )
        }
        prefs?.edit()?.putString(KEY_LINKED_DEVICES, arr.toString())?.apply()
    }
}
