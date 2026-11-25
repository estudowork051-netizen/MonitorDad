package com.dohguard.shared.remote

import android.content.Context
import android.content.SharedPreferences
import com.dohguard.shared.security.SecureCredentialStorage
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Repositório central de comunicação remota entre MonitorDad Parent e MonitorDad Kids.
 * Coordena fila de comandos, pareamento, confirmação (ACK), sincronização de regras/telemetria,
 * persistência offline e notificações deduplicadas.
 */
object RemoteControlRepository {

    private const val PREFS_NAME = "monitordad_remote_prefs"
    private const val KEY_DEVICE_ID = "paired_device_id"
    private const val KEY_PARENT_ID = "paired_parent_id"
    private const val KEY_DEVICE_NAME = "paired_device_name"
    private const val KEY_RELAY_URL = "relay_base_url"
    private const val KEY_LEGACY_SHARED_SECRET = "paired_shared_secret"
    private const val KEY_PAIRED_AT = "paired_at_millis"
    private const val KEY_IS_PAIRED = "is_paired"
    private const val KEY_COMMANDS_JSON = "remote_commands_json"
    private const val KEY_SEQUENCE = "remote_sequence_counter"
    private const val KEY_SEEN_NONCES = "seen_nonces"
    private const val KEY_SEEN_CMD_IDS = "seen_command_ids"
    private const val KEY_LAST_COMM_MS = "last_comm_millis"
    private const val KEY_LOCAL_HOST = "local_endpoint_host"
    private const val KEY_LOCAL_PORT = "local_endpoint_port"

    private var prefs: SharedPreferences? = null
    private var secureStorage: SecureCredentialStorage? = null
    private val localTransport = LocalHttpTransport()
    private val remoteTransport = HttpsRemoteTransport()
    private val connectionManager = ConnectionManager(
        localTransport = localTransport,
        remoteTransport = remoteTransport,
        localHealthPing = { baseUrl -> localTransport.pingHealth(baseUrl) },
        remoteHealthPing = { baseUrl -> remoteTransport.pingHealth(baseUrl) }
    )
    private var transport: RemoteTransport = connectionManager
    private val engine = RemoteCommandEngine()
    private var provisionedRelayKey: String? = null
    val pairingManager = PairingManager()

    private val _credentials = MutableStateFlow(engine.credentials)
    val credentials: StateFlow<PairedDeviceCredentials> = _credentials.asStateFlow()

    private val _commands = MutableStateFlow<List<RemoteCommand>>(emptyList())
    val commands: StateFlow<List<RemoteCommand>> = _commands.asStateFlow()

    private val _snapshot = MutableStateFlow(
        DeviceRemoteSnapshot(
            deviceId = engine.credentials.deviceId,
            deviceName = engine.credentials.deviceName,
            connectionState = engine.connectionState
        )
    )
    val snapshot: StateFlow<DeviceRemoteSnapshot> = _snapshot.asStateFlow()

    private val _notifications = MutableStateFlow<List<ParentNotificationEvent>>(emptyList())
    val notifications: StateFlow<List<ParentNotificationEvent>> = _notifications.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _connectionSettings = MutableStateFlow(ConnectionSettings())
    val connectionSettings: StateFlow<ConnectionSettings> = _connectionSettings.asStateFlow()

    private val _activeTransport = MutableStateFlow(ActiveTransportType.NONE)
    val activeTransport: StateFlow<ActiveTransportType> = _activeTransport.asStateFlow()

    private val _localEndpoint = MutableStateFlow<LocalEndpointInfo?>(null)
    val localEndpoint: StateFlow<LocalEndpointInfo?> = _localEndpoint.asStateFlow()

    private val _linkedDevices = MutableStateFlow<List<LinkedDeviceRecord>>(emptyList())
    val linkedDevices: StateFlow<List<LinkedDeviceRecord>> = _linkedDevices.asStateFlow()

    private val _diagnosticsReport = MutableStateFlow<ConnectionDiagnosticsReport?>(null)
    val diagnosticsReport: StateFlow<ConnectionDiagnosticsReport?> = _diagnosticsReport.asStateFlow()

    private val _wizardCompleted = MutableStateFlow(false)
    val wizardCompleted: StateFlow<Boolean> = _wizardCompleted.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        ConnectionSettingsRepository.init(appContext)
        val sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val secure = SecureCredentialStorage(appContext)
        prefs = sp
        secureStorage = secure

        val loadedSettings = ConnectionSettingsRepository.loadSettings()
        connectionManager.updateSettings(loadedSettings)
        _connectionSettings.value = loadedSettings
        _wizardCompleted.value = ConnectionSettingsRepository.isWizardCompleted()

        val loadedDevices = ConnectionSettingsRepository.loadLinkedDevices()
        pairingManager.restoreDevices(loadedDevices)
        _linkedDevices.value = pairingManager.getLinkedDevices()

        var storedSecret = secure.loadSharedSecret()
        val legacyPlaintextSecret = sp.getString(KEY_LEGACY_SHARED_SECRET, null)
        if (storedSecret.isBlank() && !legacyPlaintextSecret.isNullOrBlank()) {
            secure.saveSharedSecret(legacyPlaintextSecret)
            storedSecret = legacyPlaintextSecret
        }
        if (legacyPlaintextSecret != null) {
            sp.edit().remove(KEY_LEGACY_SHARED_SECRET).apply()
        }

        val creds = PairedDeviceCredentials(
            deviceId = sp.getString(KEY_DEVICE_ID, "tablet-lucas-01") ?: "tablet-lucas-01",
            parentId = sp.getString(KEY_PARENT_ID, "parent-hod-01") ?: "parent-hod-01",
            deviceName = sp.getString(KEY_DEVICE_NAME, "Tablet do Lucas") ?: "Tablet do Lucas",
            relayBaseUrl = sp.getString(KEY_RELAY_URL, loadedSettings.relayBaseUrl) ?: loadedSettings.relayBaseUrl,
            sharedSecret = storedSecret,
            pairedAtMillis = sp.getLong(KEY_PAIRED_AT, 0L),
            isPaired = sp.getBoolean(KEY_IS_PAIRED, false),
            localHost = sp.getString(KEY_LOCAL_HOST, "") ?: "",
            localPort = sp.getInt(KEY_LOCAL_PORT, 0)
        )
        engine.updateCredentials(creds)
        if (creds.hasLocalEndpoint()) {
            val ep = LocalEndpointInfo(
                deviceId = creds.deviceId,
                deviceName = creds.deviceName,
                host = creds.localHost,
                port = creds.localPort
            )
            connectionManager.updateLocalEndpoint(ep)
            _localEndpoint.value = ep
        }

        val restoredCommands = sp.getString(KEY_COMMANDS_JSON, null)
            ?.let { RemoteJsonCodec.commandsFromJsonString(it) }
            ?: emptyList()
        val restoredSeq = sp.getLong(KEY_SEQUENCE, 0L)
        val restoredNonces = sp.getStringSet(KEY_SEEN_NONCES, emptySet()) ?: emptySet()
        val restoredCmdIds = sp.getStringSet(KEY_SEEN_CMD_IDS, emptySet()) ?: emptySet()
        val restoredLastComm = sp.getLong(KEY_LAST_COMM_MS, 0L)

        engine.restoreState(
            restoredCommands = restoredCommands,
            restoredSequence = restoredSeq,
            restoredNonces = restoredNonces,
            restoredCommandIds = restoredCmdIds,
            restoredLastCommMillis = restoredLastComm
        )
        engine.sweepExpiredCommands()
        publishState()
    }

    fun setTransportForTesting(customTransport: RemoteTransport) {
        transport = customTransport
    }

    fun updateConnectionSettings(newSettings: ConnectionSettings) {
        val cleaned = newSettings.copy(relayBaseUrl = newSettings.relayBaseUrl.trim().trimEnd('/'))
        connectionManager.updateSettings(cleaned)
        ConnectionSettingsRepository.saveSettings(cleaned)
        _connectionSettings.value = cleaned

        val creds = engine.credentials
        if (cleaned.relayBaseUrl != creds.relayBaseUrl) {
            provisionedRelayKey = null
            engine.updateCredentials(creds.copy(relayBaseUrl = cleaned.relayBaseUrl))
            persistAll()
        }
        publishState()
    }

    fun updateLocalEndpoint(endpoint: LocalEndpointInfo?) {
        connectionManager.updateLocalEndpoint(endpoint)
        _localEndpoint.value = endpoint
        if (endpoint != null && endpoint.isValid() && engine.credentials.isPaired) {
            val updated = engine.credentials.copy(localHost = endpoint.host, localPort = endpoint.port)
            engine.updateCredentials(updated)
            persistAll()
        }
        publishState()
    }

    fun handleLocalServiceLost() {
        val current = _localEndpoint.value ?: connectionManager.discoveredLocalEndpoint
        if (current != null) {
            val unreachable = current.copy(isReachable = false)
            connectionManager.updateLocalEndpoint(unreachable)
            _localEndpoint.value = unreachable
        } else {
            connectionManager.clearLocalEndpoint()
            _localEndpoint.value = null
        }
        publishState()
    }

    fun markWizardCompleted(completed: Boolean = true) {
        ConnectionSettingsRepository.markWizardCompleted(completed)
        _wizardCompleted.value = completed
    }

    fun configurePairing(
        deviceId: String,
        parentId: String,
        deviceName: String,
        relayBaseUrl: String,
        sharedSecret: String,
        localHost: String = "",
        localPort: Int = 0
    ): PairedDeviceCredentials {
        provisionedRelayKey = null
        val normalizedSecret = sharedSecret.trim().ifBlank { RemoteSecurity.generateSharedSecret() }
        val cleanRelay = relayBaseUrl.trim().trimEnd('/').ifBlank { _connectionSettings.value.relayBaseUrl }
        val creds = PairedDeviceCredentials(
            deviceId = deviceId.trim().ifBlank { "tablet-lucas-01" },
            parentId = parentId.trim().ifBlank { "parent-hod-01" },
            deviceName = deviceName.trim().ifBlank { "Tablet do Lucas" },
            sharedSecret = normalizedSecret,
            relayBaseUrl = cleanRelay,
            pairedAtMillis = System.currentTimeMillis(),
            isPaired = true,
            localHost = localHost.trim(),
            localPort = localPort
        )
        engine.updateCredentials(creds)
        pairingManager.registerOrUpdateDevice(
            LinkedDeviceRecord(
                deviceId = creds.deviceId,
                deviceName = creds.deviceName,
                parentId = creds.parentId,
                pairedAtMillis = creds.pairedAtMillis,
                lastSeenMillis = creds.pairedAtMillis,
                lastTransport = if (creds.hasLocalEndpoint()) ActiveTransportType.LOCAL_WIFI else ActiveTransportType.REMOTE_INTERNET,
                revoked = false
            )
        )
        ConnectionSettingsRepository.saveLinkedDevices(pairingManager.getLinkedDevices(includeRevoked = true))
        _linkedDevices.value = pairingManager.getLinkedDevices()
        if (creds.hasLocalEndpoint()) {
            val ep = LocalEndpointInfo(
                deviceId = creds.deviceId,
                deviceName = creds.deviceName,
                host = creds.localHost,
                port = creds.localPort
            )
            connectionManager.updateLocalEndpoint(ep)
            _localEndpoint.value = ep
        }
        persistAll()
        publishState()
        return creds
    }

    fun unpairAndRevokeDevice(deviceId: String = engine.credentials.deviceId) {
        val credsToRevoke = engine.credentials
        pairingManager.revokeDevice(deviceId)
        ConnectionSettingsRepository.saveLinkedDevices(pairingManager.getLinkedDevices(includeRevoked = true))
        _linkedDevices.value = pairingManager.getLinkedDevices()
        if (deviceId == credsToRevoke.deviceId) {
            provisionedRelayKey = null
            if (credsToRevoke.isPaired) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { connectionManager.revokeDeviceOnRelay(credsToRevoke) }
                }
            }
            secureStorage?.saveSharedSecret("")
            connectionManager.clearLocalEndpoint()
            _localEndpoint.value = null
            engine.updateCredentials(
                PairedDeviceCredentials(
                    relayBaseUrl = _connectionSettings.value.relayBaseUrl,
                    isPaired = false
                )
            )
            persistAll()
            publishState()
        }
    }

    private suspend fun ensureRelayProvisionedIfNeeded() {
        val creds = engine.credentials
        val relayUrl = creds.relayBaseUrl
            .ifBlank { _connectionSettings.value.relayBaseUrl }
            .trim()
            .trimEnd('/')
        if (!creds.isPaired || creds.sharedSecret.isBlank() || relayUrl.isBlank()) return

        val effectiveCreds = creds.copy(relayBaseUrl = relayUrl)
        if (!effectiveCreds.hasValidRelay()) return

        val key = "${effectiveCreds.deviceId}|${effectiveCreds.parentId}|$relayUrl"
        if (provisionedRelayKey == key) return

        val result = runCatching {
            connectionManager.ensureRemoteProvisioned(effectiveCreds)
        }.getOrElse { Result.failure(it) }

        if (result.isSuccess) {
            provisionedRelayKey = key
        }
    }

    suspend fun runConnectionDiagnostics(role: String): ConnectionDiagnosticsReport {
        ensureRelayProvisionedIfNeeded()
        val report = connectionManager.runDiagnostics(
            credentials = engine.credentials,
            role = role,
            pendingCommandsCount = engine.getPendingCommandsCount(),
            pendingSyncCount = ControlRulesRepository.syncEnvelopes.value.count { it.ackedAtMillis == null }
        )
        _diagnosticsReport.value = report
        _activeTransport.value = connectionManager.activeTransport
        publishState()
        return report
    }

    suspend fun initiateKidsPairing(
        relayBaseUrl: String,
        deviceId: String,
        deviceName: String
    ): Result<PairingInitiation> {
        return transport.initiatePairing(
            relayBaseUrl = relayBaseUrl.trim().trimEnd('/'),
            deviceId = deviceId.trim().ifBlank { "tablet-lucas-01" },
            deviceName = deviceName.trim().ifBlank { "Tablet do Lucas" }
        )
    }

    suspend fun pollKidsPairingCompletion(
        relayBaseUrl: String,
        pairingCode: String,
        claimToken: String
    ): Result<PairingCompletionResult> {
        val cleanUrl = relayBaseUrl.trim().trimEnd('/')
        val res = transport.pollPairingCompletion(cleanUrl, pairingCode, claimToken)
        res.onSuccess { completion ->
            if (completion.status == PairingCompletionStatus.PAIRED && completion.sharedSecret.isNotBlank()) {
                configurePairing(
                    deviceId = completion.deviceId,
                    parentId = completion.parentId,
                    deviceName = completion.deviceName,
                    relayBaseUrl = cleanUrl,
                    sharedSecret = completion.sharedSecret
                )
            }
        }
        return res
    }

    suspend fun claimParentPairingCode(
        relayBaseUrl: String,
        pairingCode: String,
        parentId: String,
        deviceName: String? = null
    ): Result<PairedDeviceCredentials> {
        val cleanUrl = relayBaseUrl.trim().trimEnd('/')
        val res = transport.claimPairingCode(
            relayBaseUrl = cleanUrl,
            pairingCode = pairingCode.trim().uppercase(),
            parentId = parentId.trim().ifBlank { "parent-hod-01" },
            deviceName = deviceName?.trim()?.takeIf { it.isNotBlank() }
        )
        res.onSuccess { creds ->
            configurePairing(
                deviceId = creds.deviceId,
                parentId = creds.parentId,
                deviceName = creds.deviceName,
                relayBaseUrl = creds.relayBaseUrl.ifBlank { cleanUrl },
                sharedSecret = creds.sharedSecret
            )
        }
        return res
    }

    suspend fun claimLocalPairingCode(
        localPairingBaseUrl: String,
        pairingCode: String,
        deviceId: String,
        deviceName: String
    ): Result<PairedDeviceCredentials> {
        val res = localTransport.claimLocalPairing(
            localPairingBaseUrl = localPairingBaseUrl,
            pairingCode = pairingCode,
            deviceId = deviceId,
            deviceName = deviceName
        )
        res.onSuccess { creds ->
            configurePairing(
                deviceId = creds.deviceId,
                parentId = creds.parentId,
                deviceName = creds.deviceName,
                relayBaseUrl = creds.relayBaseUrl,
                sharedSecret = creds.sharedSecret
            )
        }
        return res
    }

    suspend fun sendParentCommand(
        type: CommandType,
        payload: Map<String, String> = emptyMap(),
        ttlMillis: Long = RemoteCommandEngine.DEFAULT_COMMAND_TTL_MS
    ): RemoteCommand {
        val cmd = engine.enqueueParentCommand(type = type, payload = payload, ttlMillis = ttlMillis)
        persistAll()
        publishState()

        engine.flushPendingCommands(transport)
        persistAll()
        publishState()

        return engine.getCommands().firstOrNull { it.commandId == cmd.commandId } ?: cmd
    }

    suspend fun syncParentSession(): Result<DeviceRemoteSnapshot> {
        _isSyncing.value = true
        publishState()
        return try {
            ensureRelayProvisionedIfNeeded()
            val flushRes = engine.flushPendingCommands(transport)
            val ackRes = engine.pollAndApplyAcks(transport)

            val creds = engine.credentials
            if (creds.hasAnyTransport() && creds.isPaired) {
                ControlRulesRepository.syncEnvelopes.value
                    .filter { it.ackedAtMillis == null }
                    .forEach { env ->
                        transport.pushRulesEnvelope(creds, env)
                    }

                val remoteTelemetry = transport.pullLatestTelemetry(creds).getOrNull()
                if (remoteTelemetry != null) {
                    val currentTs = UsageSyncRepository.telemetryState.value?.timestamp ?: 0L
                    if (remoteTelemetry.timestamp > currentTs) {
                        UsageSyncRepository.updateTelemetry(remoteTelemetry)
                    }
                }
            }

            _activeTransport.value = connectionManager.activeTransport
            persistAll()
            publishState()

            if (flushRes.isFailure && ackRes.isFailure) {
                Result.failure(flushRes.exceptionOrNull() ?: IllegalStateException("Sem comunicação ativa"))
            } else {
                Result.success(_snapshot.value)
            }
        } finally {
            _isSyncing.value = false
            publishState()
        }
    }

    suspend fun syncKidsSession(
        commandExecutor: (RemoteCommand) -> Pair<Boolean, String>
    ): Result<Int> {
        _isSyncing.value = true
        publishState()
        return try {
            ensureRelayProvisionedIfNeeded()
            val execResult = engine.pollAndExecuteKidsCommands(
                transport = transport,
                executor = commandExecutor
            )

            val creds = engine.credentials
            if (creds.hasAnyTransport() && creds.isPaired) {
                val remoteEnvelope = transport.pullLatestRulesEnvelope(creds).getOrNull()
                if (remoteEnvelope != null) {
                    ControlRulesRepository.applyIncomingSyncEnvelope(remoteEnvelope)
                }

                val localTelemetry = UsageSyncRepository.telemetryState.value
                if (localTelemetry != null && !localTelemetry.summary.isDemoData) {
                    transport.pushTelemetry(creds, localTelemetry)
                }
            }

            _activeTransport.value = connectionManager.activeTransport
            persistAll()
            publishState()
            execResult
        } finally {
            _isSyncing.value = false
            publishState()
        }
    }

    fun recordNotification(
        type: NotificationEventType,
        message: String,
        dedupKey: String
    ): ParentNotificationEvent? {
        val created = engine.recordNotification(type = type, message = message, dedupKey = dedupKey)
        if (created != null) {
            _notifications.value = engine.notificationDeduplicator.getEvents()
        }
        return created
    }

    fun markNotificationsRead() {
        engine.notificationDeduplicator.markAllRead()
        _notifications.value = engine.notificationDeduplicator.getEvents()
    }

    fun refreshSnapshotFromLocalState() {
        publishState()
    }

    private fun publishState() {
        val creds = engine.credentials
        val rulesState = ControlRulesRepository.rulesState.value
        val activeRoutines = rulesState.routines.filter { it.enabled }.map { it.name }
        val blockedApps = rulesState.appLimits.values.count { it.isBlocked }
        val unackedEnvelopes = ControlRulesRepository.syncEnvelopes.value.count { it.ackedAtMillis == null }
        val activeChannel = if (transport === connectionManager) {
            connectionManager.activeTransport
        } else if (engine.connectionState == RemoteConnectionState.ONLINE) {
            ActiveTransportType.REMOTE_INTERNET
        } else {
            ActiveTransportType.NONE
        }
        val mode = _connectionSettings.value.mode
        val syncing = _isSyncing.value
        val healthColor = ConnectionManager.computeHealthColor(
            state = engine.connectionState,
            isSyncing = syncing,
            isPaired = creds.isPaired
        )
        val summary = ConnectionManager.buildStatusSummary(
            state = engine.connectionState,
            activeTransport = activeChannel,
            isPaired = creds.isPaired,
            isSyncing = syncing
        )

        _activeTransport.value = activeChannel
        _credentials.value = creds
        _commands.value = engine.getCommands()
        _notifications.value = engine.notificationDeduplicator.getEvents()
        _snapshot.value = DeviceRemoteSnapshot(
            deviceId = creds.deviceId,
            deviceName = creds.deviceName,
            connectionState = engine.connectionState,
            lastCommunicationMillis = engine.lastCommunicationMillis,
            lastError = engine.lastError,
            activeRoutineNames = activeRoutines,
            blockedAppsCount = blockedApps,
            pendingCommandsCount = engine.getPendingCommandsCount(),
            pendingSyncEnvelopesCount = unackedEnvelopes,
            activeTransport = activeChannel,
            transportMode = mode,
            healthColor = healthColor,
            statusSummary = summary
        )

        ControlRulesRepository.setOnlineStatus(engine.connectionState == RemoteConnectionState.ONLINE)
    }

    private fun persistAll() {
        val sp = prefs ?: return
        val creds = engine.credentials
        secureStorage?.saveSharedSecret(creds.sharedSecret)
        sp.edit()
            .putString(KEY_DEVICE_ID, creds.deviceId)
            .putString(KEY_PARENT_ID, creds.parentId)
            .putString(KEY_DEVICE_NAME, creds.deviceName)
            .putString(KEY_RELAY_URL, creds.relayBaseUrl)
            .remove(KEY_LEGACY_SHARED_SECRET)
            .putLong(KEY_PAIRED_AT, creds.pairedAtMillis)
            .putBoolean(KEY_IS_PAIRED, creds.isPaired)
            .putString(KEY_LOCAL_HOST, creds.localHost)
            .putInt(KEY_LOCAL_PORT, creds.localPort)
            .putString(KEY_COMMANDS_JSON, RemoteJsonCodec.commandsToJsonString(engine.getCommands()))
            .putLong(KEY_SEQUENCE, engine.getSequenceCounter())
            .putStringSet(KEY_SEEN_NONCES, engine.getSeenNonces().take(200).toSet())
            .putStringSet(KEY_SEEN_CMD_IDS, engine.getSeenCommandIds().take(200).toSet())
            .putLong(KEY_LAST_COMM_MS, engine.lastCommunicationMillis)
            .apply()
    }
}
