package com.dohguard.shared.remote

import com.dohguard.shared.model.RuleSyncEnvelope
import com.dohguard.shared.model.TelemetryPayload

/**
 * Gerenciador central unificado de transporte e diagnóstico (Local Wi-Fi <-> Internet).
 * Implementa RemoteTransport delegando dinamicamente para LocalTransport ou RemoteTransport
 * conforme o modo escolhido, disponibilidade na rede e failover automático.
 * 100% puro em Kotlin para testes unitários JVM sem dependências de Android.
 */
class ConnectionManager(
    initialSettings: ConnectionSettings = ConnectionSettings(),
    private val localTransport: RemoteTransport,
    private val remoteTransport: RemoteTransport,
    private val localHealthPing: suspend (String) -> Result<Long> = { Result.failure(IllegalStateException("Sem ping local")) },
    private val remoteHealthPing: suspend (String) -> Result<Long> = { Result.failure(IllegalStateException("Sem ping remoto")) }
) : RemoteTransport {

    var settings: ConnectionSettings = initialSettings
        private set

    var discoveredLocalEndpoint: LocalEndpointInfo? = null
        private set

    var activeTransport: ActiveTransportType = ActiveTransportType.NONE
        private set

    var lastTransportError: String? = null
        private set

    fun updateSettings(newSettings: ConnectionSettings) {
        settings = newSettings
    }

    fun updateLocalEndpoint(endpoint: LocalEndpointInfo?) {
        discoveredLocalEndpoint = endpoint
    }

    fun clearLocalEndpoint() {
        discoveredLocalEndpoint = null
        if (activeTransport == ActiveTransportType.LOCAL_WIFI) {
            activeTransport = ActiveTransportType.NONE
        }
    }

    fun effectiveLocalEndpoint(credentials: PairedDeviceCredentials): LocalEndpointInfo? {
        val discovered = discoveredLocalEndpoint
        if (discovered != null) {
            return if (discovered.isValid() && discovered.isReachable) discovered else null
        }
        if (credentials.hasLocalEndpoint()) {
            return LocalEndpointInfo(
                deviceId = credentials.deviceId,
                deviceName = credentials.deviceName,
                host = credentials.localHost,
                port = credentials.localPort
            )
        }
        return null
    }

    suspend fun ensureRemoteProvisioned(credentials: PairedDeviceCredentials): Result<Unit> {
        val relay = credentials.relayBaseUrl.ifBlank { settings.relayBaseUrl }.trim().trimEnd('/')
        if (!credentials.isPaired || credentials.sharedSecret.isBlank() || relay.isBlank()) {
            return Result.failure(IllegalStateException("Credenciais ou relay ausentes para provisionamento."))
        }
        return remoteTransport.provisionPairedDevice(credentials.copy(relayBaseUrl = relay))
    }

    suspend fun revokeDeviceOnRelay(credentials: PairedDeviceCredentials): Result<Unit> {
        val relay = credentials.relayBaseUrl.ifBlank { settings.relayBaseUrl }.trim().trimEnd('/')
        if (!credentials.isPaired || credentials.sharedSecret.isBlank() || relay.isBlank()) {
            return Result.success(Unit)
        }
        return remoteTransport.revokeDevice(credentials.copy(relayBaseUrl = relay))
    }

    fun resolveTransportOrder(credentials: PairedDeviceCredentials): List<ActiveTransportType> {
        val localAvailable = settings.localDiscoveryEnabled && effectiveLocalEndpoint(credentials) != null
        val remoteAvailable = credentials.hasValidRelay() || settings.relayBaseUrl.trim().startsWith("https://")
        return when (settings.mode) {
            TransportMode.AUTO -> buildList {
                if (localAvailable) add(ActiveTransportType.LOCAL_WIFI)
                if (remoteAvailable) add(ActiveTransportType.REMOTE_INTERNET)
            }
            TransportMode.LOCAL_ONLY -> if (localAvailable) listOf(ActiveTransportType.LOCAL_WIFI) else emptyList()
            TransportMode.REMOTE_ONLY -> if (remoteAvailable) listOf(ActiveTransportType.REMOTE_INTERNET) else emptyList()
        }
    }

    private fun enrichCredentialsFor(
        type: ActiveTransportType,
        credentials: PairedDeviceCredentials
    ): PairedDeviceCredentials {
        val local = effectiveLocalEndpoint(credentials)
        val relay = credentials.relayBaseUrl.ifBlank { settings.relayBaseUrl.trim() }
        return when (type) {
            ActiveTransportType.LOCAL_WIFI -> credentials.copy(
                localHost = local?.host ?: credentials.localHost,
                localPort = local?.port ?: credentials.localPort,
                relayBaseUrl = relay
            )
            ActiveTransportType.REMOTE_INTERNET -> credentials.copy(
                relayBaseUrl = relay
            )
            ActiveTransportType.NONE -> credentials
        }
    }

    private suspend fun <T> executeWithFailover(
        credentials: PairedDeviceCredentials,
        block: suspend (RemoteTransport, PairedDeviceCredentials) -> Result<T>
    ): Result<T> {
        val order = resolveTransportOrder(credentials)
        if (order.isEmpty()) {
            activeTransport = ActiveTransportType.NONE
            lastTransportError = when (settings.mode) {
                TransportMode.LOCAL_ONLY -> "Nenhum dispositivo encontrado na mesma rede Wi-Fi."
                TransportMode.REMOTE_ONLY -> "Servidor remoto não configurado."
                TransportMode.AUTO -> "Nenhum canal disponível (nem Wi-Fi local nem servidor remoto)."
            }
            return Result.failure(IllegalStateException(lastTransportError))
        }

        var lastFailure: Throwable? = null
        for (channel in order) {
            val transport = when (channel) {
                ActiveTransportType.LOCAL_WIFI -> localTransport
                ActiveTransportType.REMOTE_INTERNET -> remoteTransport
                ActiveTransportType.NONE -> continue
            }
            val enriched = enrichCredentialsFor(channel, credentials)
            val result = block(transport, enriched)
            if (result.isSuccess) {
                activeTransport = channel
                lastTransportError = null
                return result
            }
            lastFailure = result.exceptionOrNull()
            if (channel == ActiveTransportType.LOCAL_WIFI) {
                discoveredLocalEndpoint = discoveredLocalEndpoint?.copy(isReachable = false)
            }
        }

        activeTransport = ActiveTransportType.NONE
        lastTransportError = lastFailure?.message ?: "Falha na comunicação nos canais disponíveis."
        return Result.failure(lastFailure ?: IllegalStateException(lastTransportError))
    }

    override suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String): Result<Long> =
        executeWithFailover(credentials) { transport, creds -> transport.heartbeat(creds, role) }

    override suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit> =
        executeWithFailover(credentials) { transport, creds -> transport.sendCommand(creds, command) }

    override suspend fun pollCommands(credentials: PairedDeviceCredentials): Result<List<RemoteCommand>> =
        executeWithFailover(credentials) { transport, creds -> transport.pollCommands(creds) }

    override suspend fun sendCommandAck(credentials: PairedDeviceCredentials, ack: CommandAck): Result<Unit> =
        executeWithFailover(credentials) { transport, creds -> transport.sendCommandAck(creds, ack) }

    override suspend fun pollAcks(credentials: PairedDeviceCredentials): Result<List<CommandAck>> =
        executeWithFailover(credentials) { transport, creds -> transport.pollAcks(creds) }

    override suspend fun pushRulesEnvelope(credentials: PairedDeviceCredentials, envelope: RuleSyncEnvelope): Result<Unit> =
        executeWithFailover(credentials) { transport, creds -> transport.pushRulesEnvelope(creds, envelope) }

    override suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials): Result<RuleSyncEnvelope?> =
        executeWithFailover(credentials) { transport, creds -> transport.pullLatestRulesEnvelope(creds) }

    override suspend fun pushTelemetry(credentials: PairedDeviceCredentials, payload: TelemetryPayload): Result<Unit> =
        executeWithFailover(credentials) { transport, creds -> transport.pushTelemetry(creds, payload) }

    override suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials): Result<TelemetryPayload?> =
        executeWithFailover(credentials) { transport, creds -> transport.pullLatestTelemetry(creds) }

    suspend fun runDiagnostics(
        credentials: PairedDeviceCredentials,
        role: String,
        pendingCommandsCount: Int,
        pendingSyncCount: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): ConnectionDiagnosticsReport {
        val steps = mutableListOf<DiagnosticStepResult>()

        // 1. Pareamento & credenciais
        val pairingOk = credentials.isPaired && credentials.sharedSecret.isNotBlank()
        steps.add(
            DiagnosticStepResult(
                id = "pairing",
                title = "Pareamento e chave de segurança",
                passed = pairingOk,
                detail = if (pairingOk) {
                    "Dispositivo vinculado (${credentials.deviceName}) com chave HMAC ativa."
                } else {
                    "Dispositivo ainda não pareado. Use 'Adicionar dispositivo' para vincular."
                }
            )
        )

        // 2. Descoberta local Wi-Fi
        val localEndpoint = effectiveLocalEndpoint(credentials)
        val localDiscovered = localEndpoint != null && localEndpoint.isValid()
        steps.add(
            DiagnosticStepResult(
                id = "local_discovery",
                title = "Descoberta na rede Wi-Fi local",
                passed = localDiscovered,
                detail = if (localDiscovered) {
                    "Endpoint local detectado (${localEndpoint!!.host}:${localEndpoint.port})."
                } else {
                    "Nenhum tablet localizado na mesma rede Wi-Fi no momento."
                }
            )
        )

        // 3. Ping local
        var localPingOk = false
        if (localDiscovered && settings.mode != TransportMode.REMOTE_ONLY) {
            val start = System.currentTimeMillis()
            val ping = localHealthPing(localEndpoint!!.baseUrl())
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            localPingOk = ping.isSuccess
            steps.add(
                DiagnosticStepResult(
                    id = "local_ping",
                    title = "Comunicação direta pelo Wi-Fi",
                    passed = localPingOk,
                    detail = if (localPingOk) {
                        "Resposta local confirmada em ${elapsed} ms."
                    } else {
                        "Sem resposta local: ${ping.exceptionOrNull()?.message ?: "timeout"}."
                    },
                    latencyMillis = if (localPingOk) elapsed else null
                )
            )
        } else {
            steps.add(
                DiagnosticStepResult(
                    id = "local_ping",
                    title = "Comunicação direta pelo Wi-Fi",
                    passed = false,
                    detail = if (settings.mode == TransportMode.REMOTE_ONLY) {
                        "Ignorado porque o modo está definido como 'Somente internet'."
                    } else {
                        "Aguardando descoberta do dispositivo na rede Wi-Fi."
                    }
                )
            )
        }

        // 4. Ping remoto internet
        val relayUrl = credentials.relayBaseUrl.ifBlank { settings.relayBaseUrl }.trim().trimEnd('/')
        val relayConfigured = relayUrl.startsWith("https://") ||
            relayUrl.startsWith("http://127.0.0.1") ||
            relayUrl.startsWith("http://localhost")
        var remotePingOk = false
        if (relayConfigured && settings.mode != TransportMode.LOCAL_ONLY) {
            val start = System.currentTimeMillis()
            val ping = remoteHealthPing(relayUrl)
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            remotePingOk = ping.isSuccess
            steps.add(
                DiagnosticStepResult(
                    id = "remote_ping",
                    title = "Conexão remota pela internet",
                    passed = remotePingOk,
                    detail = if (remotePingOk) {
                        "Servidor remoto acessível em ${elapsed} ms."
                    } else {
                        "Servidor remoto inacessível: ${ping.exceptionOrNull()?.message ?: "erro de rede"}."
                    },
                    latencyMillis = if (remotePingOk) elapsed else null
                )
            )
        } else {
            steps.add(
                DiagnosticStepResult(
                    id = "remote_ping",
                    title = "Conexão remota pela internet",
                    passed = false,
                    detail = if (!relayConfigured) {
                        "Servidor remoto não configurado (opcional se usar apenas Wi-Fi local)."
                    } else {
                        "Ignorado porque o modo está definido como 'Somente Wi-Fi local'."
                    }
                )
            )
        }

        // 5. Heartbeat autenticado fim a fim
        var authOk = false
        var authChannel = ActiveTransportType.NONE
        if (pairingOk && (localPingOk || remotePingOk)) {
            val start = System.currentTimeMillis()
            val hb = heartbeat(credentials, role)
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            authOk = hb.isSuccess
            authChannel = if (authOk) activeTransport else ActiveTransportType.NONE
            steps.add(
                DiagnosticStepResult(
                    id = "auth_heartbeat",
                    title = "Autenticação e troca de mensagens",
                    passed = authOk,
                    detail = if (authOk) {
                        "Canal ${authChannel.label} validado com assinatura HMAC em ${elapsed} ms."
                    } else {
                        "Falha na validação assinada: ${hb.exceptionOrNull()?.message ?: "erro desconhecido"}."
                    },
                    latencyMillis = if (authOk) elapsed else null
                )
            )
        } else {
            steps.add(
                DiagnosticStepResult(
                    id = "auth_heartbeat",
                    title = "Autenticação e troca de mensagens",
                    passed = false,
                    detail = "Não foi possível testar mensagens porque nenhum canal está respondendo."
                )
            )
        }

        // 6. Fila offline
        val queueClean = pendingCommandsCount == 0 && pendingSyncCount == 0
        steps.add(
            DiagnosticStepResult(
                id = "sync_queue",
                title = "Sincronização de regras e comandos",
                passed = authOk || queueClean,
                detail = if (queueClean) {
                    "Nenhuma pendência aguardando envio."
                } else {
                    "$pendingCommandsCount comando(s) e $pendingSyncCount pacote(s) de regra na fila."
                }
            )
        )

        val overallSuccess = pairingOk && authOk
        val summary = when {
            overallSuccess && authChannel == ActiveTransportType.LOCAL_WIFI ->
                "Conectado pelo Wi-Fi local. Tudo pronto para enviar regras e comandos."
            overallSuccess && authChannel == ActiveTransportType.REMOTE_INTERNET ->
                "Conectado pela internet. Comunicação ativa mesmo fora da mesma rede."
            !pairingOk ->
                "Dispositivo não pareado. Inicie o pareamento para conectar."
            else ->
                "Sem resposta do outro dispositivo. Verifique se ambos estão ligados e conectados à rede."
        }

        return ConnectionDiagnosticsReport(
            executedAtMillis = nowMillis,
            activeTransport = authChannel,
            overallSuccess = overallSuccess,
            summary = summary,
            steps = steps
        )
    }

    companion object {
        fun computeHealthColor(
            state: RemoteConnectionState,
            isSyncing: Boolean,
            isPaired: Boolean
        ): ConnectionHealthColor {
            if (!isPaired || state == RemoteConnectionState.UNCONFIGURED) {
                return ConnectionHealthColor.GRAY
            }
            if (isSyncing || state == RemoteConnectionState.RECONNECTING) {
                return ConnectionHealthColor.YELLOW
            }
            return when (state) {
                RemoteConnectionState.ONLINE -> ConnectionHealthColor.GREEN
                RemoteConnectionState.OFFLINE -> ConnectionHealthColor.RED
                RemoteConnectionState.RECONNECTING -> ConnectionHealthColor.YELLOW
                RemoteConnectionState.UNCONFIGURED -> ConnectionHealthColor.GRAY
            }
        }

        fun buildStatusSummary(
            state: RemoteConnectionState,
            activeTransport: ActiveTransportType,
            isPaired: Boolean,
            isSyncing: Boolean
        ): String {
            if (!isPaired) return "Aguardando pareamento"
            if (isSyncing) return "Sincronizando dados..."
            return when (state) {
                RemoteConnectionState.ONLINE -> when (activeTransport) {
                    ActiveTransportType.LOCAL_WIFI -> "Conectado via Wi-Fi local"
                    ActiveTransportType.REMOTE_INTERNET -> "Conectado via internet"
                    ActiveTransportType.NONE -> "Conectado"
                }
                RemoteConnectionState.RECONNECTING -> "Reconectando..."
                RemoteConnectionState.OFFLINE -> "Sem resposta no momento (modo offline seguro)"
                RemoteConnectionState.UNCONFIGURED -> "Aguardando configuração de rede"
            }
        }
    }
}
