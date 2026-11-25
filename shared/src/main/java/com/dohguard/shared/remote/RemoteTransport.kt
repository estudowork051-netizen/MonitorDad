package com.dohguard.shared.remote

import com.dohguard.shared.model.RuleSyncEnvelope
import com.dohguard.shared.model.TelemetryPayload

/**
 * Contrato de transporte remoto pela internet entre MonitorDad Parent e MonitorDad Kids.
 * Implementações reais devem usar HTTPS/TLS e retornar Result.failure quando o servidor
 * estiver indisponível ou não configurado (nunca simular sucesso).
 */
interface RemoteTransport {
    suspend fun initiatePairing(
        relayBaseUrl: String,
        deviceId: String,
        deviceName: String
    ): Result<PairingInitiation> = Result.failure(UnsupportedOperationException("Pareamento não suportado"))

    suspend fun claimPairingCode(
        relayBaseUrl: String,
        pairingCode: String,
        parentId: String,
        deviceName: String? = null
    ): Result<PairedDeviceCredentials> = Result.failure(UnsupportedOperationException("Pareamento não suportado"))

    suspend fun pollPairingCompletion(
        relayBaseUrl: String,
        pairingCode: String,
        claimToken: String
    ): Result<PairingCompletionResult> = Result.failure(UnsupportedOperationException("Pareamento não suportado"))

    suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String): Result<Long>
    suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit>
    suspend fun pollCommands(credentials: PairedDeviceCredentials): Result<List<RemoteCommand>>
    suspend fun sendCommandAck(credentials: PairedDeviceCredentials, ack: CommandAck): Result<Unit>
    suspend fun pollAcks(credentials: PairedDeviceCredentials): Result<List<CommandAck>>
    suspend fun pushRulesEnvelope(credentials: PairedDeviceCredentials, envelope: RuleSyncEnvelope): Result<Unit>
    suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials): Result<RuleSyncEnvelope?>
    suspend fun pushTelemetry(credentials: PairedDeviceCredentials, payload: TelemetryPayload): Result<Unit>
    suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials): Result<TelemetryPayload?>

    suspend fun provisionPairedDevice(credentials: PairedDeviceCredentials): Result<Unit> = Result.success(Unit)
    suspend fun revokeDevice(credentials: PairedDeviceCredentials): Result<Unit> = Result.success(Unit)
}
