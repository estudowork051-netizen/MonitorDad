package com.dohguard.shared.remote

/**
 * Tipos de comandos remotos que o MonitorDad Parent pode enviar ao MonitorDad Kids.
 */
enum class CommandType(val displayName: String) {
    PAUSE_DEVICE("Pausar tablet"),
    RESUME_DEVICE("Liberar tablet"),
    ADD_EXTRA_TIME("Adicionar tempo extra"),
    BLOCK_APP("Bloquear aplicativo"),
    UNBLOCK_APP("Desbloquear aplicativo"),
    UPDATE_LIMITS("Atualizar limites"),
    UPSERT_ROUTINE("Salvar rotina"),
    DELETE_ROUTINE("Remover rotina"),
    REFRESH_USAGE("Atualizar uso agora"),
    PLAY_SOUND("Tocar som no dispositivo")
}

/**
 * Ciclo de vida completo de um comando remoto.
 * Nunca deve avançar para EXECUTED sem confirmação real do Kids.
 */
enum class CommandStatus(val label: String) {
    PENDING("Pendente"),
    SENT("Enviado"),
    RECEIVED("Recebido"),
    EXECUTED("Executado"),
    FAILED("Falhou"),
    EXPIRED("Expirado")
}

enum class RemoteConnectionState(val label: String) {
    ONLINE("Online"),
    RECONNECTING("Reconectando"),
    OFFLINE("Offline"),
    UNCONFIGURED("Relay não configurado")
}

data class RemoteCommand(
    val commandId: String,
    val targetDeviceId: String,
    val issuedByParentId: String,
    val type: CommandType,
    val payload: Map<String, String> = emptyMap(),
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val sequenceNumber: Long,
    val nonce: String,
    val signature: String = "",
    val status: CommandStatus = CommandStatus.PENDING,
    val sentAtMillis: Long? = null,
    val receivedAtMillis: Long? = null,
    val executedAtMillis: Long? = null,
    val resultMessage: String? = null,
    val errorMessage: String? = null
) {
    fun isExpiredAt(nowMillis: Long): Boolean = nowMillis > expiresAtMillis

    fun isTerminal(): Boolean =
        status == CommandStatus.EXECUTED ||
            status == CommandStatus.FAILED ||
            status == CommandStatus.EXPIRED
}

data class CommandAck(
    val commandId: String,
    val deviceId: String,
    val status: CommandStatus,
    val timestampMillis: Long,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val signature: String = ""
)

enum class TransportMode(val label: String, val description: String) {
    AUTO(
        "Automático (Recomendado)",
        "Prefere Wi-Fi local quando estiver na mesma rede e muda para internet automaticamente."
    ),
    LOCAL_ONLY(
        "Somente Wi-Fi local",
        "Comunicação direta na mesma rede Wi-Fi, sem depender de servidor externo."
    ),
    REMOTE_ONLY(
        "Somente internet",
        "Comunicação pela internet para funcionar em redes diferentes."
    )
}

enum class ActiveTransportType(val label: String) {
    NONE("Sem conexão"),
    LOCAL_WIFI("Wi-Fi local"),
    REMOTE_INTERNET("Internet")
}

enum class ConnectionHealthColor {
    GREEN,
    YELLOW,
    RED,
    GRAY
}

data class LocalEndpointInfo(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val discoveredAtMillis: Long = System.currentTimeMillis(),
    val isReachable: Boolean = true
) {
    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535
    fun baseUrl(): String = if (isValid()) "http://$host:$port" else ""
}

data class LinkedDeviceRecord(
    val deviceId: String,
    val deviceName: String,
    val parentId: String,
    val pairedAtMillis: Long,
    val lastSeenMillis: Long = 0L,
    val lastTransport: ActiveTransportType = ActiveTransportType.NONE,
    val revoked: Boolean = false
)

data class ConnectionSettings(
    val mode: TransportMode = TransportMode.AUTO,
    val localDiscoveryEnabled: Boolean = true,
    val relayBaseUrl: String = "",
    val environmentLabel: String = "Produção"
)

data class DiagnosticStepResult(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String,
    val latencyMillis: Long? = null
)

data class ConnectionDiagnosticsReport(
    val executedAtMillis: Long,
    val activeTransport: ActiveTransportType,
    val overallSuccess: Boolean,
    val summary: String,
    val steps: List<DiagnosticStepResult>
)

data class PairingQrPayload(
    val version: Int = 1,
    val code: String,
    val parentId: String,
    val parentName: String,
    val localHost: String = "",
    val localPort: Int = 0,
    val relayUrl: String = "",
    val expiresAtMillis: Long = 0L
)

data class PairedDeviceCredentials(
    val deviceId: String = "tablet-lucas-01",
    val parentId: String = "parent-hod-01",
    val deviceName: String = "Tablet do Lucas",
    val sharedSecret: String = "",
    val relayBaseUrl: String = "",
    val pairedAtMillis: Long = 0L,
    val isPaired: Boolean = false,
    val localHost: String = "",
    val localPort: Int = 0
) {
    fun hasValidRelay(): Boolean {
        val trimmed = relayBaseUrl.trim()
        return trimmed.startsWith("https://") ||
            trimmed.startsWith("http://127.0.0.1") ||
            trimmed.startsWith("http://localhost")
    }

    fun hasLocalEndpoint(): Boolean = localHost.isNotBlank() && localPort in 1..65535

    fun localBaseUrl(): String = if (hasLocalEndpoint()) "http://$localHost:$localPort" else ""

    fun hasAnyTransport(): Boolean = hasLocalEndpoint() || hasValidRelay()
}

data class DeviceRemoteSnapshot(
    val deviceId: String,
    val deviceName: String,
    val connectionState: RemoteConnectionState = RemoteConnectionState.UNCONFIGURED,
    val lastCommunicationMillis: Long = 0L,
    val lastError: String? = null,
    val activeRoutineNames: List<String> = emptyList(),
    val blockedAppsCount: Int = 0,
    val pendingCommandsCount: Int = 0,
    val pendingSyncEnvelopesCount: Int = 0,
    val activeTransport: ActiveTransportType = ActiveTransportType.NONE,
    val transportMode: TransportMode = TransportMode.AUTO,
    val healthColor: ConnectionHealthColor = ConnectionHealthColor.GRAY,
    val statusSummary: String = "Aguardando configuração"
)

enum class NotificationEventType(val title: String) {
    DEVICE_OFFLINE("Tablet offline"),
    DEVICE_BACK_ONLINE("Tablet voltou a ficar online"),
    DAILY_LIMIT_REACHED("Limite diário atingido"),
    APP_BLOCKED("Aplicativo bloqueado"),
    EXTRA_TIME_REQUESTED("Pedido de tempo extra"),
    UNLOCK_REQUESTED("Pedido de desbloqueio"),
    COMMAND_FAILED("Falha em comando remoto"),
    RULES_CHANGED("Alteração importante nas regras")
}

data class ParentNotificationEvent(
    val eventId: String,
    val type: NotificationEventType,
    val title: String,
    val message: String,
    val deviceId: String,
    val timestampMillis: Long,
    val dedupKey: String,
    val isRead: Boolean = false
)

data class PairingInitiation(
    val pairingCode: String,
    val claimToken: String,
    val expiresAtMillis: Long,
    val deviceId: String,
    val deviceName: String
)

enum class PairingCompletionStatus {
    PENDING,
    PAIRED
}

data class PairingCompletionResult(
    val status: PairingCompletionStatus,
    val deviceId: String = "",
    val parentId: String = "",
    val deviceName: String = "",
    val sharedSecret: String = "",
    val pairedAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L
)

