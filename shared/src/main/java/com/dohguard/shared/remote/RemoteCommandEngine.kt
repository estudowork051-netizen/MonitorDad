package com.dohguard.shared.remote

import java.util.UUID

/**
 * Motor puro em Kotlin para gerenciamento do ciclo de vida de comandos remotos,
 * fila offline, expiração, proteção contra replay, confirmações (ACK) e notificações.
 * Totalmente testável em JVM (JUnit) sem dependências de Android.
 */
class RemoteCommandEngine(
    initialCredentials: PairedDeviceCredentials = PairedDeviceCredentials()
) {
    var credentials: PairedDeviceCredentials = initialCredentials
        private set

    var connectionState: RemoteConnectionState =
        if (initialCredentials.hasAnyTransport() && initialCredentials.isPaired) {
            RemoteConnectionState.OFFLINE
        } else {
            RemoteConnectionState.UNCONFIGURED
        }
        private set

    var lastCommunicationMillis: Long = 0L
        private set

    var lastError: String? = null
        private set

    private var sequenceCounter: Long = 0L
    private val commands = mutableListOf<RemoteCommand>()
    private val seenNonces = mutableSetOf<String>()
    private val seenCommandIds = mutableSetOf<String>()
    private val pendingOutgoingAcks = mutableListOf<CommandAck>()
    val notificationDeduplicator = NotificationEventDeduplicator()

    fun updateCredentials(newCredentials: PairedDeviceCredentials) {
        credentials = newCredentials
        connectionState = if (newCredentials.hasAnyTransport() && newCredentials.isPaired) {
            if (lastCommunicationMillis > 0L) RemoteConnectionState.ONLINE else RemoteConnectionState.OFFLINE
        } else {
            RemoteConnectionState.UNCONFIGURED
        }
    }

    fun restoreState(
        restoredCommands: List<RemoteCommand>,
        restoredSequence: Long,
        restoredNonces: Set<String>,
        restoredCommandIds: Set<String>,
        restoredLastCommMillis: Long
    ) {
        commands.clear()
        commands.addAll(restoredCommands.take(100))
        sequenceCounter = maxOf(sequenceCounter, restoredSequence)
        seenNonces.addAll(restoredNonces)
        seenCommandIds.addAll(restoredCommandIds)
        lastCommunicationMillis = restoredLastCommMillis
    }

    fun getCommands(): List<RemoteCommand> = commands.toList()

    fun getPendingCommandsCount(): Int = commands.count { it.status == CommandStatus.PENDING }

    fun getSeenNonces(): Set<String> = seenNonces.toSet()

    fun getSeenCommandIds(): Set<String> = seenCommandIds.toSet()

    fun getSequenceCounter(): Long = sequenceCounter

    fun enqueueParentCommand(
        type: CommandType,
        payload: Map<String, String> = emptyMap(),
        ttlMillis: Long = DEFAULT_COMMAND_TTL_MS,
        nowMillis: Long = System.currentTimeMillis()
    ): RemoteCommand {
        sequenceCounter += 1L
        val nonce = RemoteSecurity.generateNonce()
        val unsigned = RemoteCommand(
            commandId = "cmd-${sequenceCounter}-${UUID.randomUUID().toString().take(8)}",
            targetDeviceId = credentials.deviceId,
            issuedByParentId = credentials.parentId,
            type = type,
            payload = payload,
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + ttlMillis.coerceAtLeast(1_000L),
            sequenceNumber = sequenceCounter,
            nonce = nonce,
            status = CommandStatus.PENDING
        )
        val signed = unsigned.copy(
            signature = RemoteSecurity.signCommand(unsigned, credentials.sharedSecret)
        )
        commands.add(0, signed)
        trimCommands()
        return signed
    }

    fun sweepExpiredCommands(nowMillis: Long = System.currentTimeMillis()): Int {
        var expiredCount = 0
        for (i in commands.indices) {
            val cmd = commands[i]
            if (!cmd.isTerminal() && cmd.isExpiredAt(nowMillis)) {
                commands[i] = cmd.copy(
                    status = CommandStatus.EXPIRED,
                    errorMessage = "Comando expirou antes da confirmação do tablet."
                )
                expiredCount++
            }
        }
        return expiredCount
    }

    suspend fun flushPendingCommands(
        transport: RemoteTransport,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Int> {
        sweepExpiredCommands(nowMillis)
        if (!credentials.hasAnyTransport() || !credentials.isPaired) {
            connectionState = RemoteConnectionState.UNCONFIGURED
            lastError = "Relay remoto não configurado ou dispositivo não pareado."
            return Result.failure(IllegalStateException(lastError))
        }

        val previousState = connectionState
        connectionState = RemoteConnectionState.RECONNECTING
        var sentCount = 0

        val pendingIndices = commands.indices.filter { commands[it].status == CommandStatus.PENDING }.reversed()
        for (idx in pendingIndices) {
            val cmd = commands[idx]
            val sendRes = transport.sendCommand(credentials, cmd)
            if (sendRes.isSuccess) {
                commands[idx] = cmd.copy(
                    status = CommandStatus.SENT,
                    sentAtMillis = nowMillis,
                    errorMessage = null
                )
                sentCount++
            } else {
                val err = RemoteSecurity.redactSensitive(
                    sendRes.exceptionOrNull()?.message ?: "Falha de comunicação com o relay"
                )
                markOffline(err, nowMillis, previousState)
                return Result.failure(sendRes.exceptionOrNull() ?: IllegalStateException(err))
            }
        }

        markOnline(nowMillis, previousState)
        return Result.success(sentCount)
    }

    suspend fun pollAndApplyAcks(
        transport: RemoteTransport,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Int> {
        sweepExpiredCommands(nowMillis)
        if (!credentials.hasAnyTransport() || !credentials.isPaired) {
            connectionState = RemoteConnectionState.UNCONFIGURED
            return Result.failure(IllegalStateException("Relay não configurado."))
        }

        val previousState = connectionState
        val acksResult = transport.pollAcks(credentials)
        if (acksResult.isFailure) {
            val err = RemoteSecurity.redactSensitive(
                acksResult.exceptionOrNull()?.message ?: "Tablet ou relay indisponível"
            )
            markOffline(err, nowMillis, previousState)
            return Result.failure(acksResult.exceptionOrNull() ?: IllegalStateException(err))
        }

        markOnline(nowMillis, previousState)
        var applied = 0
        for (ack in acksResult.getOrDefault(emptyList())) {
            if (!RemoteSecurity.verifyAckSignature(ack, credentials.sharedSecret)) {
                continue
            }
            val idx = commands.indexOfFirst { it.commandId == ack.commandId }
            if (idx >= 0) {
                val current = commands[idx]
                if (!current.isTerminal()) {
                    val safeAckError = ack.errorMessage?.let { RemoteSecurity.redactSensitive(it) }
                    val safeAckResult = ack.resultMessage?.let { RemoteSecurity.redactSensitive(it) }
                    commands[idx] = current.copy(
                        status = ack.status,
                        receivedAtMillis = current.receivedAtMillis ?: ack.timestampMillis,
                        executedAtMillis = if (ack.status == CommandStatus.EXECUTED) ack.timestampMillis else current.executedAtMillis,
                        resultMessage = safeAckResult,
                        errorMessage = safeAckError
                    )
                    applied++
                    if (ack.status == CommandStatus.FAILED) {
                        recordNotification(
                            type = NotificationEventType.COMMAND_FAILED,
                            message = "${current.type.displayName}: ${safeAckError ?: "falha na execução"}",
                            dedupKey = "cmd-fail-${current.commandId}",
                            nowMillis = nowMillis
                        )
                    }
                }
            }
        }
        return Result.success(applied)
    }

    /**
     * Lado Kids: consulta comandos pendentes no relay, valida segurança/assinatura/replay/expiração,
     * executa localmente e envia CommandAck assinado.
     */
    suspend fun pollAndExecuteKidsCommands(
        transport: RemoteTransport,
        nowMillis: Long = System.currentTimeMillis(),
        executor: (RemoteCommand) -> Pair<Boolean, String>
    ): Result<Int> {
        if (!credentials.hasAnyTransport() || !credentials.isPaired) {
            connectionState = RemoteConnectionState.UNCONFIGURED
            return Result.failure(IllegalStateException("Relay não configurado."))
        }

        flushPendingAcks(transport)

        val previousState = connectionState
        val polled = transport.pollCommands(credentials)
        if (polled.isFailure) {
            val err = RemoteSecurity.redactSensitive(
                polled.exceptionOrNull()?.message ?: "Sem conexão com o relay"
            )
            markOffline(err, nowMillis, previousState)
            return Result.failure(polled.exceptionOrNull() ?: IllegalStateException(err))
        }

        markOnline(nowMillis, previousState)
        var executedCount = 0

        for (cmd in polled.getOrDefault(emptyList())) {
            val validation = RemoteSecurity.validateIncomingCommand(
                command = cmd,
                credentials = credentials,
                seenNonces = seenNonces,
                seenCommandIds = seenCommandIds,
                nowMillis = nowMillis
            )

            when (validation) {
                is CommandSecurityResult.Rejected -> {
                    if (validation.isDuplicate) {
                        continue
                    }
                    seenCommandIds.add(cmd.commandId)
                    val rejectStatus = if (validation.isExpired) CommandStatus.EXPIRED else CommandStatus.FAILED
                    val ack = buildSignedAck(
                        commandId = cmd.commandId,
                        status = rejectStatus,
                        timestampMillis = nowMillis,
                        resultMessage = null,
                        errorMessage = validation.reason
                    )
                    val recorded = cmd.copy(
                        status = rejectStatus,
                        receivedAtMillis = nowMillis,
                        errorMessage = validation.reason
                    )
                    commands.add(0, recorded)
                    sendOrQueueAck(transport, ack)
                }

                is CommandSecurityResult.Authorized -> {
                    seenCommandIds.add(cmd.commandId)
                    seenNonces.add(cmd.nonce)

                    val (success, msg) = try {
                        val (ok, rawMsg) = executor(cmd)
                        ok to rawMsg?.let { RemoteSecurity.redactSensitive(it) }
                    } catch (e: Exception) {
                        false to RemoteSecurity.redactSensitive(e.message ?: "Erro interno ao executar comando")
                    }

                    val finalStatus = if (success) CommandStatus.EXECUTED else CommandStatus.FAILED
                    val executedCmd = cmd.copy(
                        status = finalStatus,
                        receivedAtMillis = nowMillis,
                        executedAtMillis = if (success) nowMillis else null,
                        resultMessage = if (success) msg else null,
                        errorMessage = if (!success) msg else null
                    )
                    commands.add(0, executedCmd)

                    val ack = buildSignedAck(
                        commandId = cmd.commandId,
                        status = finalStatus,
                        timestampMillis = nowMillis,
                        resultMessage = if (success) msg else null,
                        errorMessage = if (!success) msg else null
                    )
                    sendOrQueueAck(transport, ack)
                    if (success) executedCount++
                }
            }
        }

        trimCommands()
        return Result.success(executedCount)
    }

    suspend fun flushPendingAcks(transport: RemoteTransport): Int {
        if (pendingOutgoingAcks.isEmpty()) return 0
        var flushed = 0
        val iterator = pendingOutgoingAcks.iterator()
        while (iterator.hasNext()) {
            val ack = iterator.next()
            val res = transport.sendCommandAck(credentials, ack)
            if (res.isSuccess) {
                iterator.remove()
                flushed++
            } else {
                break
            }
        }
        return flushed
    }

    fun recordNotification(
        type: NotificationEventType,
        message: String,
        dedupKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ParentNotificationEvent? {
        val event = ParentNotificationEvent(
            eventId = "notif-${UUID.randomUUID().toString().take(8)}",
            type = type,
            title = type.title,
            message = message,
            deviceId = credentials.deviceId,
            timestampMillis = nowMillis,
            dedupKey = dedupKey
        )
        return if (notificationDeduplicator.recordIfNew(event, nowMillis)) event else null
    }

    private fun buildSignedAck(
        commandId: String,
        status: CommandStatus,
        timestampMillis: Long,
        resultMessage: String?,
        errorMessage: String?
    ): CommandAck {
        val unsigned = CommandAck(
            commandId = commandId,
            deviceId = credentials.deviceId,
            status = status,
            timestampMillis = timestampMillis,
            resultMessage = resultMessage,
            errorMessage = errorMessage
        )
        return unsigned.copy(
            signature = RemoteSecurity.signAck(unsigned, credentials.sharedSecret)
        )
    }

    private suspend fun sendOrQueueAck(transport: RemoteTransport, ack: CommandAck) {
        val res = transport.sendCommandAck(credentials, ack)
        if (res.isFailure) {
            pendingOutgoingAcks.add(ack)
        }
    }

    private fun markOnline(nowMillis: Long, previousState: RemoteConnectionState) {
        connectionState = RemoteConnectionState.ONLINE
        lastCommunicationMillis = nowMillis
        lastError = null
        if (previousState == RemoteConnectionState.OFFLINE) {
            recordNotification(
                type = NotificationEventType.DEVICE_BACK_ONLINE,
                message = "${credentials.deviceName} voltou a responder remotamente.",
                dedupKey = "conn-online-${credentials.deviceId}",
                nowMillis = nowMillis
            )
        }
    }

    private fun markOffline(error: String, nowMillis: Long, previousState: RemoteConnectionState) {
        connectionState = RemoteConnectionState.OFFLINE
        lastError = error
        if (previousState == RemoteConnectionState.ONLINE) {
            recordNotification(
                type = NotificationEventType.DEVICE_OFFLINE,
                message = "Sem comunicação com ${credentials.deviceName}: $error",
                dedupKey = "conn-offline-${credentials.deviceId}",
                nowMillis = nowMillis
            )
        }
    }

    private fun trimCommands() {
        while (commands.size > 100) {
            commands.removeAt(commands.lastIndex)
        }
    }

    companion object {
        const val DEFAULT_COMMAND_TTL_MS = 15 * 60_000L
    }
}
