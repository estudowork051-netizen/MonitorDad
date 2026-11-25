package com.dohguard.shared

import com.dohguard.shared.model.ControlRulesState
import com.dohguard.shared.model.RuleSyncEnvelope
import com.dohguard.shared.model.TelemetryPayload
import com.dohguard.shared.remote.*
import com.dohguard.shared.sync.RuleSyncValidator
import com.dohguard.shared.sync.SyncValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemoteCommunicationTest {

    private class FakeRelayTransport : RemoteTransport {
        var isReachable: Boolean = true
        val queuedCommands = mutableListOf<RemoteCommand>()
        val queuedAcks = mutableListOf<CommandAck>()
        var latestEnvelope: RuleSyncEnvelope? = null
        var latestTelemetry: TelemetryPayload? = null
        private val pendingPairings = mutableMapOf<String, MutableMap<String, Any?>>()

        override suspend fun initiatePairing(
            relayBaseUrl: String,
            deviceId: String,
            deviceName: String
        ): Result<PairingInitiation> {
            if (!isReachable) return Result.failure(IllegalStateException("Relay inacessível"))
            val code = "MD49K2"
            val token = "claim-token-${System.nanoTime()}"
            val expires = System.currentTimeMillis() + 600_000L
            pendingPairings[code] = mutableMapOf(
                "deviceId" to deviceId,
                "deviceName" to deviceName,
                "claimToken" to token,
                "expiresAtMillis" to expires,
                "claimed" to false
            )
            return Result.success(PairingInitiation(code, token, expires, deviceId, deviceName))
        }

        override suspend fun claimPairingCode(
            relayBaseUrl: String,
            pairingCode: String,
            parentId: String,
            deviceName: String?
        ): Result<PairedDeviceCredentials> {
            val entry = pendingPairings[pairingCode]
                ?: return Result.failure(IllegalStateException("Código inválido ou expirado"))
            if (entry["claimed"] == true) {
                return Result.failure(IllegalStateException("Código já utilizado"))
            }
            val secret = RemoteSecurity.generateSharedSecret()
            entry["claimed"] = true
            entry["parentId"] = parentId
            entry["sharedSecret"] = secret
            return Result.success(
                PairedDeviceCredentials(
                    deviceId = entry["deviceId"] as String,
                    parentId = parentId,
                    deviceName = deviceName ?: (entry["deviceName"] as String),
                    sharedSecret = secret,
                    relayBaseUrl = relayBaseUrl,
                    pairedAtMillis = System.currentTimeMillis(),
                    isPaired = true
                )
            )
        }

        override suspend fun pollPairingCompletion(
            relayBaseUrl: String,
            pairingCode: String,
            claimToken: String
        ): Result<PairingCompletionResult> {
            val entry = pendingPairings[pairingCode]
                ?: return Result.failure(IllegalStateException("Sessão expirada"))
            if (entry["claimToken"] != claimToken) {
                return Result.failure(IllegalStateException("claimToken inválido"))
            }
            if (entry["claimed"] != true) {
                return Result.success(
                    PairingCompletionResult(
                        status = PairingCompletionStatus.PENDING,
                        expiresAtMillis = entry["expiresAtMillis"] as Long
                    )
                )
            }
            pendingPairings.remove(pairingCode)
            return Result.success(
                PairingCompletionResult(
                    status = PairingCompletionStatus.PAIRED,
                    deviceId = entry["deviceId"] as String,
                    parentId = entry["parentId"] as String,
                    deviceName = entry["deviceName"] as String,
                    sharedSecret = entry["sharedSecret"] as String,
                    pairedAtMillis = System.currentTimeMillis()
                )
            )
        }

        override suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String): Result<Long> {
            return if (isReachable) Result.success(System.currentTimeMillis())
            else Result.failure(IllegalStateException("Relay inacessível"))
        }

        override suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            queuedCommands.add(command)
            return Result.success(Unit)
        }

        override suspend fun pollCommands(credentials: PairedDeviceCredentials): Result<List<RemoteCommand>> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            val copy = queuedCommands.toList()
            queuedCommands.clear()
            return Result.success(copy)
        }

        override suspend fun sendCommandAck(credentials: PairedDeviceCredentials, ack: CommandAck): Result<Unit> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            queuedAcks.add(ack)
            return Result.success(Unit)
        }

        override suspend fun pollAcks(credentials: PairedDeviceCredentials): Result<List<CommandAck>> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            val copy = queuedAcks.toList()
            queuedAcks.clear()
            return Result.success(copy)
        }

        override suspend fun pushRulesEnvelope(credentials: PairedDeviceCredentials, envelope: RuleSyncEnvelope): Result<Unit> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            latestEnvelope = envelope
            return Result.success(Unit)
        }

        override suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials): Result<RuleSyncEnvelope?> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            return Result.success(latestEnvelope)
        }

        override suspend fun pushTelemetry(credentials: PairedDeviceCredentials, payload: TelemetryPayload): Result<Unit> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            latestTelemetry = payload
            return Result.success(Unit)
        }

        override suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials): Result<TelemetryPayload?> {
            if (!isReachable) return Result.failure(IllegalStateException("Sem internet"))
            return Result.success(latestTelemetry)
        }
    }

    private fun testCredentials(relayUrl: String = "https://relay.monitordad.test"): PairedDeviceCredentials {
        return PairedDeviceCredentials(
            deviceId = "tablet-lucas-01",
            parentId = "parent-hod-01",
            deviceName = "Tablet do Lucas",
            sharedSecret = "super-secret-hmac-key-0123456789",
            relayBaseUrl = relayUrl,
            pairedAtMillis = 1_000_000L,
            isPaired = true
        )
    }

    @Test
    fun `1_fullCommandLifecycle_pendingToSentToExecuted`() = runBlocking {
        val creds = testCredentials()
        val relay = FakeRelayTransport()
        val parentEngine = RemoteCommandEngine(creds)
        val kidsEngine = RemoteCommandEngine(creds)
        val now = 2_000_000L

        val cmd = parentEngine.enqueueParentCommand(
            type = CommandType.PAUSE_DEVICE,
            nowMillis = now
        )
        assertEquals(CommandStatus.PENDING, cmd.status)

        // Parent sends to relay -> must become SENT, NOT EXECUTED
        val flushRes = parentEngine.flushPendingCommands(relay, now + 100L)
        assertTrue(flushRes.isSuccess)
        assertEquals(CommandStatus.SENT, parentEngine.getCommands().first().status)

        // Kids polls, validates, executes, and sends signed ACK
        var executedLocally = false
        val kidsRes = kidsEngine.pollAndExecuteKidsCommands(relay, now + 200L) { incoming ->
            executedLocally = incoming.type == CommandType.PAUSE_DEVICE
            true to "Tablet pausado"
        }
        assertTrue(kidsRes.isSuccess)
        assertEquals(1, kidsRes.getOrNull())
        assertTrue(executedLocally)

        // Parent polls ACK -> transitions to EXECUTED
        val ackRes = parentEngine.pollAndApplyAcks(relay, now + 300L)
        assertTrue(ackRes.isSuccess)
        assertEquals(1, ackRes.getOrNull())
        val finalCmd = parentEngine.getCommands().first()
        assertEquals(CommandStatus.EXECUTED, finalCmd.status)
        assertEquals("Tablet pausado", finalCmd.resultMessage)
        assertEquals(RemoteConnectionState.ONLINE, parentEngine.connectionState)
    }

    @Test
    fun `2_connectionLossQueuesPendingAndRecoversOnReconnect`() = runBlocking {
        val creds = testCredentials()
        val relay = FakeRelayTransport()
        val parentEngine = RemoteCommandEngine(creds)
        val kidsEngine = RemoteCommandEngine(creds)
        val now = 3_000_000L

        // Initially online
        parentEngine.flushPendingCommands(relay, now)
        assertEquals(RemoteConnectionState.ONLINE, parentEngine.connectionState)

        // Network drops
        relay.isReachable = false
        parentEngine.enqueueParentCommand(CommandType.PLAY_SOUND, nowMillis = now + 1000L)
        val offlineFlush = parentEngine.flushPendingCommands(relay, now + 2000L)
        assertTrue(offlineFlush.isFailure)
        assertEquals(RemoteConnectionState.OFFLINE, parentEngine.connectionState)
        assertEquals(CommandStatus.PENDING, parentEngine.getCommands().first().status)
        assertTrue(
            parentEngine.notificationDeduplicator.getEvents()
                .any { it.type == NotificationEventType.DEVICE_OFFLINE }
        )

        // Network recovers
        relay.isReachable = true
        parentEngine.flushPendingCommands(relay, now + 5000L)
        kidsEngine.pollAndExecuteKidsCommands(relay, now + 6000L) { true to "Som emitido" }
        parentEngine.pollAndApplyAcks(relay, now + 7000L)

        assertEquals(RemoteConnectionState.ONLINE, parentEngine.connectionState)
        assertEquals(CommandStatus.EXECUTED, parentEngine.getCommands().first().status)
        assertTrue(
            parentEngine.notificationDeduplicator.getEvents()
                .any { it.type == NotificationEventType.DEVICE_BACK_ONLINE }
        )
    }

    @Test
    fun `3_unconfiguredRelayDoesNotFakeConnectionOrExecution`() = runBlocking {
        val creds = testCredentials(relayUrl = "")
        val relay = FakeRelayTransport()
        val parentEngine = RemoteCommandEngine(creds)

        val cmd = parentEngine.enqueueParentCommand(CommandType.PAUSE_DEVICE, nowMillis = 4_000_000L)
        val res = parentEngine.flushPendingCommands(relay, 4_000_100L)

        assertTrue(res.isFailure)
        assertEquals(RemoteConnectionState.UNCONFIGURED, parentEngine.connectionState)
        assertEquals(CommandStatus.PENDING, parentEngine.getCommands().first { it.commandId == cmd.commandId }.status)
    }

    @Test
    fun `4_expiredCommandsAreMarkedExpiredAndNeverExecuted`() = runBlocking {
        val creds = testCredentials()
        val relay = FakeRelayTransport()
        val parentEngine = RemoteCommandEngine(creds)
        val kidsEngine = RemoteCommandEngine(creds)
        val now = 5_000_000L

        val cmd = parentEngine.enqueueParentCommand(
            type = CommandType.ADD_EXTRA_TIME,
            payload = mapOf("minutes" to "15"),
            ttlMillis = 5_000L,
            nowMillis = now
        )
        parentEngine.flushPendingCommands(relay, now + 1_000L)

        // Kids polls after expiration
        var wasExecuted = false
        kidsEngine.pollAndExecuteKidsCommands(relay, now + 10_000L) {
            wasExecuted = true
            true to "OK"
        }
        assertFalse(wasExecuted)

        parentEngine.pollAndApplyAcks(relay, now + 11_000L)
        assertEquals(CommandStatus.EXPIRED, parentEngine.getCommands().first { it.commandId == cmd.commandId }.status)
    }

    @Test
    fun `5_duplicateNonceAndUnauthorizedCommandAreRejected`() = runBlocking {
        val creds = testCredentials()
        val relay = FakeRelayTransport()
        val parentEngine = RemoteCommandEngine(creds)
        val kidsEngine = RemoteCommandEngine(creds)
        val now = 6_000_000L

        val cmd = parentEngine.enqueueParentCommand(CommandType.PAUSE_DEVICE, nowMillis = now)
        parentEngine.flushPendingCommands(relay, now + 100L)

        // Replay the same command twice in relay
        relay.queuedCommands.add(cmd)

        var execCount = 0
        kidsEngine.pollAndExecuteKidsCommands(relay, now + 200L) {
            execCount++
            true to "OK"
        }
        assertEquals(1, execCount)

        // Unauthorized parent / tampered signature
        val forged = cmd.copy(
            commandId = "cmd-forged-1",
            nonce = "fresh-nonce-999",
            issuedByParentId = "attacker-parent",
            signature = "bad-sig"
        )
        relay.queuedCommands.add(forged)
        kidsEngine.pollAndExecuteKidsCommands(relay, now + 300L) {
            execCount++
            true to "Should not run"
        }
        assertEquals(1, execCount)
    }

    @Test
    fun `6_failedKidsExecutionTriggersCommandFailedNotification`() = runBlocking {
        val creds = testCredentials()
        val relay = FakeRelayTransport()
        val parentEngine = RemoteCommandEngine(creds)
        val kidsEngine = RemoteCommandEngine(creds)
        val now = 7_000_000L

        parentEngine.enqueueParentCommand(CommandType.REFRESH_USAGE, nowMillis = now)
        parentEngine.flushPendingCommands(relay, now + 100L)

        kidsEngine.pollAndExecuteKidsCommands(relay, now + 200L) {
            false to "Permissão PACKAGE_USAGE_STATS ausente"
        }

        parentEngine.pollAndApplyAcks(relay, now + 300L)
        val cmd = parentEngine.getCommands().first()
        assertEquals(CommandStatus.FAILED, cmd.status)
        assertTrue(
            parentEngine.notificationDeduplicator.getEvents()
                .any { it.type == NotificationEventType.COMMAND_FAILED }
        )
    }

    @Test
    fun `7_notificationDeduplicationSuppressesSpamWithinWindow`() {
        val dedup = NotificationEventDeduplicator(minIntervalMillis = 60_000L)
        val e1 = ParentNotificationEvent(
            eventId = "ev-1",
            type = NotificationEventType.DAILY_LIMIT_REACHED,
            title = "Limite atingido",
            message = "Uso diário encerrado",
            deviceId = "tablet-lucas-01",
            timestampMillis = 100_000L,
            dedupKey = "daily-limit-tablet-lucas-01"
        )
        val e2 = e1.copy(eventId = "ev-2", timestampMillis = 120_000L)
        val e3 = e1.copy(eventId = "ev-3", timestampMillis = 170_000L)

        assertTrue(dedup.recordIfNew(e1))
        assertFalse(dedup.recordIfNew(e2))
        assertTrue(dedup.recordIfNew(e3))
        assertEquals(2, dedup.getEvents().size)
    }

    @Test
    fun `8_sensitiveSecretsAreRedactedFromLogs`() {
        val raw = "Auth failed with token=abc123secret pin=1234 Bearer xyz987"
        val redacted = RemoteSecurity.redactSensitive(raw)
        assertFalse(redacted.contains("abc123secret"))
        assertFalse(redacted.contains("1234"))
        assertFalse(redacted.contains("xyz987"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `9_persistenceRestoreAndRuleSyncConflictResolution`() {
        val creds = testCredentials()
        val engine = RemoteCommandEngine(creds)
        val restoredCmd = engine.enqueueParentCommand(CommandType.PAUSE_DEVICE, nowMillis = 8_000_000L)

        val newEngine = RemoteCommandEngine(creds)
        newEngine.restoreState(
            restoredCommands = listOf(restoredCmd),
            restoredSequence = 5L,
            restoredNonces = setOf(restoredCmd.nonce),
            restoredCommandIds = setOf(restoredCmd.commandId),
            restoredLastCommMillis = 8_000_000L
        )
        assertEquals(1, newEngine.getCommands().size)
        assertEquals(5L, newEngine.getSequenceCounter())
        assertTrue(newEngine.getSeenNonces().contains(restoredCmd.nonce))

        val stateV3 = ControlRulesState.defaultDemo(8_000_000L).copy(version = 3L)
        val envV3 = RuleSyncValidator.createEnvelope("env-3", 3L, 8_000_000L, stateV3)
        val stateV2 = ControlRulesState.defaultDemo(8_000_000L).copy(version = 2L)
        val envV2 = RuleSyncValidator.createEnvelope("env-2", 2L, 8_000_000L, stateV2)

        assertTrue(
            RuleSyncValidator.validateIncomingEnvelope(envV3, 2L, emptySet()) is SyncValidationResult.Accepted
        )
        assertTrue(
            RuleSyncValidator.validateIncomingEnvelope(envV2, 3L, setOf("env-3")) is SyncValidationResult.Rejected
        )
    }
}
