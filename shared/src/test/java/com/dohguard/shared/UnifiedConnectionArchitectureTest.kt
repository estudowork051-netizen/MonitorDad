package com.dohguard.shared

import com.dohguard.shared.model.RuleSyncEnvelope
import com.dohguard.shared.model.TelemetryPayload
import com.dohguard.shared.remote.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class UnifiedConnectionArchitectureTest {

    private class StubTransport(
        var shouldSucceed: Boolean = true,
        var latencyMs: Long = 5L
    ) : RemoteTransport {
        val sentCommands = mutableListOf<RemoteCommand>()
        var heartbeatCalls = 0

        override suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String): Result<Long> {
            heartbeatCalls++
            return if (shouldSucceed) Result.success(System.currentTimeMillis())
            else Result.failure(IllegalStateException("Canal indisponível"))
        }

        override suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit> {
            return if (shouldSucceed) {
                sentCommands.add(command)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Falha no envio"))
            }
        }

        override suspend fun pollCommands(credentials: PairedDeviceCredentials): Result<List<RemoteCommand>> =
            if (shouldSucceed) Result.success(emptyList()) else Result.failure(IllegalStateException("Offline"))

        override suspend fun sendCommandAck(credentials: PairedDeviceCredentials, ack: CommandAck): Result<Unit> =
            if (shouldSucceed) Result.success(Unit) else Result.failure(IllegalStateException("Offline"))

        override suspend fun pollAcks(credentials: PairedDeviceCredentials): Result<List<CommandAck>> =
            if (shouldSucceed) Result.success(emptyList()) else Result.failure(IllegalStateException("Offline"))

        override suspend fun pushRulesEnvelope(credentials: PairedDeviceCredentials, envelope: RuleSyncEnvelope): Result<Unit> =
            if (shouldSucceed) Result.success(Unit) else Result.failure(IllegalStateException("Offline"))

        override suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials): Result<RuleSyncEnvelope?> =
            if (shouldSucceed) Result.success(null) else Result.failure(IllegalStateException("Offline"))

        override suspend fun pushTelemetry(credentials: PairedDeviceCredentials, payload: TelemetryPayload): Result<Unit> =
            if (shouldSucceed) Result.success(Unit) else Result.failure(IllegalStateException("Offline"))

        override suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials): Result<TelemetryPayload?> =
            if (shouldSucceed) Result.success(null) else Result.failure(IllegalStateException("Offline"))
    }

    @Test
    fun connectionManagerPrefersLocalWifiAndFailsOverToInternetWithoutDuplicatingCommands() = runBlocking {
        val local = StubTransport(shouldSucceed = true)
        val remote = StubTransport(shouldSucceed = true)
        val manager = ConnectionManager(
            initialSettings = ConnectionSettings(mode = TransportMode.AUTO),
            localTransport = local,
            remoteTransport = remote
        )
        manager.updateLocalEndpoint(
            LocalEndpointInfo(deviceId = "tablet-01", deviceName = "Tablet", host = "192.168.1.50", port = 45000)
        )
        val creds = PairedDeviceCredentials(
            deviceId = "tablet-01",
            parentId = "parent-01",
            sharedSecret = RemoteSecurity.generateSharedSecret(),
            relayBaseUrl = "https://relay.example.com",
            isPaired = true
        )

        val engine = RemoteCommandEngine(creds)
        engine.enqueueParentCommand(CommandType.PAUSE_DEVICE)

        // 1. Em AUTO com Wi-Fi local disponível, deve escolher LOCAL_WIFI
        val resLocal = engine.flushPendingCommands(manager)
        assertTrue(resLocal.isSuccess)
        assertEquals(ActiveTransportType.LOCAL_WIFI, manager.activeTransport)
        assertEquals(1, local.sentCommands.size)
        assertEquals(0, remote.sentCommands.size)

        // 2. Quando o Wi-Fi local cai, deve alternar automaticamente para REMOTE_INTERNET
        local.shouldSucceed = false
        engine.enqueueParentCommand(CommandType.RESUME_DEVICE)
        val resFailover = engine.flushPendingCommands(manager)
        assertTrue(resFailover.isSuccess)
        assertEquals(ActiveTransportType.REMOTE_INTERNET, manager.activeTransport)
        assertEquals(1, remote.sentCommands.size)
        // Primeiro comando já enviado NÃO foi duplicado
        assertEquals(CommandType.RESUME_DEVICE, remote.sentCommands.first().type)

        // 3. Quando ambos falham, retorna erro real e marca ACTIVE = NONE
        remote.shouldSucceed = false
        engine.enqueueParentCommand(CommandType.PLAY_SOUND)
        val resOffline = engine.flushPendingCommands(manager)
        assertTrue(resOffline.isFailure)
        assertEquals(ActiveTransportType.NONE, manager.activeTransport)
        assertEquals(RemoteConnectionState.OFFLINE, engine.connectionState)
    }

    @Test
    fun connectionManagerRespectsLocalOnlyAndRemoteOnlyModes() = runBlocking {
        val local = StubTransport(shouldSucceed = false)
        val remote = StubTransport(shouldSucceed = true)
        val manager = ConnectionManager(
            initialSettings = ConnectionSettings(mode = TransportMode.LOCAL_ONLY),
            localTransport = local,
            remoteTransport = remote
        )
        manager.updateLocalEndpoint(LocalEndpointInfo("t1", "Tablet", "192.168.1.10", 40000))
        val creds = PairedDeviceCredentials(
            deviceId = "t1",
            parentId = "p1",
            sharedSecret = RemoteSecurity.generateSharedSecret(),
            relayBaseUrl = "https://relay.example.com",
            isPaired = true
        )

        val localOnlyRes = manager.heartbeat(creds, "parent")
        assertTrue(localOnlyRes.isFailure)
        assertEquals(0, remote.heartbeatCalls)

        manager.updateSettings(ConnectionSettings(mode = TransportMode.REMOTE_ONLY))
        val remoteOnlyRes = manager.heartbeat(creds, "parent")
        assertTrue(remoteOnlyRes.isSuccess)
        assertEquals(ActiveTransportType.REMOTE_INTERNET, manager.activeTransport)
        assertEquals(1, remote.heartbeatCalls)
    }

    @Test
    fun pairingManagerEnforcesSingleUseExpiryBruteForceLockoutAndQrRoundtrip() {
        val pm = PairingManager(codeTtlMillis = 60_000L, maxFailedAttempts = 5)
        val now = 1_000_000L
        val session = pm.generateNewSession(parentId = "parent-hod", parentName = "Celular Pai", nowMillis = now)
        assertEquals(6, session.code.length)

        // QR Code roundtrip
        val qrText = PairingManager.encodeQrPayload(
            PairingQrPayload(
                code = session.code,
                parentId = session.parentId,
                parentName = session.parentName,
                localHost = "192.168.0.25",
                localPort = 48123,
                relayUrl = "https://relay.monitordad.app",
                expiresAtMillis = session.expiresAtMillis
            )
        )
        val decoded = PairingManager.decodeQrPayload(qrText)
        assertNotNull(decoded)
        assertEquals(session.code, decoded!!.code)
        assertEquals("192.168.0.25", decoded.localHost)
        assertEquals(48123, decoded.localPort)
        assertEquals("https://relay.monitordad.app", decoded.relayUrl)

        // 5 tentativas erradas bloqueiam o código
        repeat(4) {
            val r = pm.validateAndClaim("WRONG1", "tab-1", "Tablet", nowMillis = now + 1000)
            assertTrue(r is PairingManager.ClaimValidationResult.Rejected)
            assertFalse((r as PairingManager.ClaimValidationResult.Rejected).locked)
        }
        val fifth = pm.validateAndClaim("WRONG1", "tab-1", "Tablet", nowMillis = now + 2000)
        assertTrue(fifth is PairingManager.ClaimValidationResult.Rejected)
        assertTrue((fifth as PairingManager.ClaimValidationResult.Rejected).locked)

        // Mesmo código correto após bloqueio deve ser rejeitado
        val afterLock = pm.validateAndClaim(session.code, "tab-1", "Tablet", nowMillis = now + 3000)
        assertTrue(afterLock is PairingManager.ClaimValidationResult.Rejected)

        // Nova sessão: uso único e expiração
        val s2 = pm.generateNewSession("parent-hod", "Pai", nowMillis = now)
        val expired = pm.validateAndClaim(s2.code, "tab-1", "Tablet", nowMillis = now + 61_000L)
        assertTrue(expired is PairingManager.ClaimValidationResult.Rejected)

        val s3 = pm.generateNewSession("parent-hod", "Pai", nowMillis = now)
        val ok = pm.validateAndClaim(s3.code, "tab-1", "Tablet do Lucas", nowMillis = now + 1000L)
        assertTrue(ok is PairingManager.ClaimValidationResult.Accepted)
        val replay = pm.validateAndClaim(s3.code, "tab-2", "Outro Tablet", nowMillis = now + 2000L)
        assertTrue(replay is PairingManager.ClaimValidationResult.Rejected)

        // Revogação
        assertEquals(1, pm.getLinkedDevices().size)
        assertTrue(pm.revokeDevice("tab-1"))
        assertEquals(0, pm.getLinkedDevices().size)
    }

    @Test
    fun qrCodeGeneratorProducesValidMatrixWithFinderAndTimingPatterns() {
        val payload = "monitordad://pair?v=1&code=AB12CD&pid=parent-01&host=192.168.1.20&port=45123"
        val qr = QrCodeGenerator.generate(payload)
        assertEquals(17 + 4 * qr.version, qr.size)
        // Finder pattern top-left (7x7 dark border & 3x3 center)
        assertTrue(qr.isDark(0, 0))
        assertTrue(qr.isDark(6, 0))
        assertTrue(qr.isDark(0, 6))
        assertFalse(qr.isDark(1, 1))
        assertTrue(qr.isDark(3, 3))
        // Dark module
        assertTrue(qr.isDark(8, qr.size - 8))
    }

    @Test
    fun localHttpServerHandlesRealLoopbackHealthAndDiagnosticsReport() = runBlocking {
        val secret = RemoteSecurity.generateSharedSecret()
        val server = LocalHttpServer { req ->
            if (req.method == "GET" && req.path == "/health") {
                LocalHttpServer.HttpResponse(200, """{"status":"ok"}""")
            } else {
                LocalHttpServer.HttpResponse(404, """{"error":"not_found"}""")
            }
        }
        val port = server.start(bindPort = 0, loopbackOnly = true)
        try {
            assertTrue(port > 0)
            val local = StubTransport(shouldSucceed = true)
            val remote = StubTransport(shouldSucceed = false)
            val manager = ConnectionManager(
                initialSettings = ConnectionSettings(mode = TransportMode.AUTO),
                localTransport = local,
                remoteTransport = remote,
                localHealthPing = { baseUrl ->
                    val conn = (URL("$baseUrl/health").openConnection() as HttpURLConnection)
                    try {
                        if (conn.responseCode == 200) Result.success(2L)
                        else Result.failure(IllegalStateException("HTTP ${conn.responseCode}"))
                    } finally {
                        conn.disconnect()
                    }
                }
            )
            val creds = PairedDeviceCredentials(
                deviceId = "tablet-01",
                parentId = "parent-01",
                deviceName = "Tablet Lucas",
                sharedSecret = secret,
                isPaired = true,
                localHost = "127.0.0.1",
                localPort = port
            )
            val report = manager.runDiagnostics(
                credentials = creds,
                role = "parent",
                pendingCommandsCount = 0,
                pendingSyncCount = 0
            )
            assertTrue(report.overallSuccess)
            assertEquals(ActiveTransportType.LOCAL_WIFI, report.activeTransport)
            assertEquals(6, report.steps.size)
            assertTrue(report.steps.first { it.id == "local_ping" }.passed)
        } finally {
            server.stop()
        }
    }

    @Test
    fun remoteSecuritySignsVerifiesHttpRequestsAndEnforcesTimestampFreshness() {
        val secret = RemoteSecurity.generateSharedSecret()
        val nonce = RemoteSecurity.generateNonce()
        val now = 1_700_000_000_000L
        val body = """{"deviceId":"tablet-01","action":"PAUSE_DEVICE"}"""

        val sig = RemoteSecurity.signRequest(
            method = "POST",
            path = "/api/v1/devices/tablet-01/commands",
            timestampMillis = now,
            nonce = nonce,
            body = body,
            sharedSecret = secret
        )
        assertTrue(sig.isNotBlank())
        assertTrue(
            RemoteSecurity.verifyRequestSignature(
                method = "POST",
                path = "/api/v1/devices/tablet-01/commands",
                timestampMillis = now,
                nonce = nonce,
                body = body,
                sharedSecret = secret,
                providedSignature = sig
            )
        )
        // Adulteração de método, caminho, corpo, nonce ou segredo deve falhar
        assertFalse(
            RemoteSecurity.verifyRequestSignature(
                method = "GET",
                path = "/api/v1/devices/tablet-01/commands",
                timestampMillis = now,
                nonce = nonce,
                body = body,
                sharedSecret = secret,
                providedSignature = sig
            )
        )
        assertFalse(
            RemoteSecurity.verifyRequestSignature(
                method = "POST",
                path = "/api/v1/devices/tablet-01/rules",
                timestampMillis = now,
                nonce = nonce,
                body = body,
                sharedSecret = secret,
                providedSignature = sig
            )
        )
        assertFalse(
            RemoteSecurity.verifyRequestSignature(
                method = "POST",
                path = "/api/v1/devices/tablet-01/commands",
                timestampMillis = now,
                nonce = nonce,
                body = """{"deviceId":"tablet-01","action":"RESUME_DEVICE"}""",
                sharedSecret = secret,
                providedSignature = sig
            )
        )
        assertFalse(
            RemoteSecurity.verifyRequestSignature(
                method = "POST",
                path = "/api/v1/devices/tablet-01/commands",
                timestampMillis = now,
                nonce = "other-nonce",
                body = body,
                sharedSecret = secret,
                providedSignature = sig
            )
        )
        assertFalse(
            RemoteSecurity.verifyRequestSignature(
                method = "POST",
                path = "/api/v1/devices/tablet-01/commands",
                timestampMillis = now,
                nonce = nonce,
                body = body,
                sharedSecret = "wrong-secret",
                providedSignature = sig
            )
        )

        // Janela temporal (5 minutos por padrão)
        assertTrue(RemoteSecurity.isTimestampFresh(now, nowMillis = now))
        assertTrue(RemoteSecurity.isTimestampFresh(now - 299_000L, nowMillis = now))
        assertTrue(RemoteSecurity.isTimestampFresh(now + 299_000L, nowMillis = now))
        assertFalse(RemoteSecurity.isTimestampFresh(now - 301_000L, nowMillis = now))
        assertFalse(RemoteSecurity.isTimestampFresh(now + 301_000L, nowMillis = now))
        assertFalse(RemoteSecurity.isTimestampFresh(0L, nowMillis = now))
        assertFalse(RemoteSecurity.isTimestampFresh(-100L, nowMillis = now))
    }

    @Test
    fun connectionManagerReturnsAutomaticallyToLocalWifiAndProvisionsRelayWithoutRepairing() = runBlocking {
        var provisionedCount = 0
        val local = StubTransport(shouldSucceed = true)
        val remote = object : RemoteTransport {
            val sent = mutableListOf<RemoteCommand>()
            override suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String) = Result.success(1000L)
            override suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit> {
                sent.add(command)
                return Result.success(Unit)
            }
            override suspend fun pollCommands(credentials: PairedDeviceCredentials) = Result.success(emptyList<RemoteCommand>())
            override suspend fun sendCommandAck(credentials: PairedDeviceCredentials, ack: CommandAck) = Result.success(Unit)
            override suspend fun pollAcks(credentials: PairedDeviceCredentials) = Result.success(emptyList<CommandAck>())
            override suspend fun pushRulesEnvelope(credentials: PairedDeviceCredentials, envelope: com.dohguard.shared.model.RuleSyncEnvelope) = Result.success(Unit)
            override suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials) = Result.success(null)
            override suspend fun pushTelemetry(credentials: PairedDeviceCredentials, payload: com.dohguard.shared.model.TelemetryPayload) = Result.success(Unit)
            override suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials) = Result.success(null)
            override suspend fun provisionPairedDevice(credentials: PairedDeviceCredentials): Result<Unit> {
                provisionedCount++
                return Result.success(Unit)
            }
        }

        val manager = ConnectionManager(
            initialSettings = ConnectionSettings(mode = TransportMode.AUTO),
            localTransport = local,
            remoteTransport = remote
        )
        val creds = PairedDeviceCredentials(
            deviceId = "tablet-01",
            parentId = "parent-01",
            sharedSecret = RemoteSecurity.generateSharedSecret(),
            relayBaseUrl = "https://relay.example.com",
            isPaired = true,
            localHost = "192.168.1.50",
            localPort = 45000
        )

        // Provisiona credencial pareada localmente no relay remoto sem novo pareamento
        assertTrue(manager.ensureRemoteProvisioned(creds).isSuccess)
        assertEquals(1, provisionedCount)

        // 1. Inicia em Wi-Fi local
        manager.updateLocalEndpoint(LocalEndpointInfo("tablet-01", "Tablet", "192.168.1.50", 45000))
        assertTrue(manager.heartbeat(creds, "parent").isSuccess)
        assertEquals(ActiveTransportType.LOCAL_WIFI, manager.activeTransport)

        // 2. Sai da rede Wi-Fi local -> alterna automaticamente para internet
        manager.clearLocalEndpoint()
        val credsNoLocal = creds.copy(localHost = "", localPort = 0)
        assertTrue(manager.heartbeat(credsNoLocal, "parent").isSuccess)
        assertEquals(ActiveTransportType.REMOTE_INTERNET, manager.activeTransport)

        // 3. Retorna ao Wi-Fi local -> volta automaticamente para LOCAL_WIFI
        manager.updateLocalEndpoint(LocalEndpointInfo("tablet-01", "Tablet", "192.168.1.50", 45000))
        assertTrue(manager.heartbeat(creds, "parent").isSuccess)
        assertEquals(ActiveTransportType.LOCAL_WIFI, manager.activeTransport)
    }

    @Test
    fun offlineQueuedCommandTransitionsHonestlyFromPendingToSentToExecutedOnlyAfterSignedAck() = runBlocking {
        val secret = RemoteSecurity.generateSharedSecret()
        val creds = PairedDeviceCredentials(
            deviceId = "tablet-02",
            parentId = "parent-02",
            sharedSecret = secret,
            relayBaseUrl = "https://relay.example.com",
            isPaired = true
        )
        val parentEngine = RemoteCommandEngine(creds)
        val kidsEngine = RemoteCommandEngine(creds)

        var relayOnline = false
        val relayCommands = mutableListOf<RemoteCommand>()
        val relayAcks = mutableListOf<CommandAck>()

        val simulatedRelay = object : RemoteTransport {
            override suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String) =
                if (relayOnline) Result.success(System.currentTimeMillis()) else Result.failure(IllegalStateException("Offline"))
            override suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit> {
                if (!relayOnline) return Result.failure(IllegalStateException("Sem internet"))
                relayCommands.add(command)
                return Result.success(Unit)
            }
            override suspend fun pollCommands(credentials: PairedDeviceCredentials): Result<List<RemoteCommand>> =
                if (relayOnline) Result.success(relayCommands.toList()) else Result.failure(IllegalStateException("Sem internet"))
            override suspend fun sendCommandAck(credentials: PairedDeviceCredentials, ack: CommandAck): Result<Unit> {
                if (!relayOnline) return Result.failure(IllegalStateException("Sem internet"))
                relayAcks.add(ack)
                return Result.success(Unit)
            }
            override suspend fun pollAcks(credentials: PairedDeviceCredentials): Result<List<CommandAck>> =
                if (relayOnline) Result.success(relayAcks.toList()) else Result.failure(IllegalStateException("Sem internet"))
            override suspend fun pushRulesEnvelope(credentials: PairedDeviceCredentials, envelope: com.dohguard.shared.model.RuleSyncEnvelope) = Result.success(Unit)
            override suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials) = Result.success(null)
            override suspend fun pushTelemetry(credentials: PairedDeviceCredentials, payload: com.dohguard.shared.model.TelemetryPayload) = Result.success(Unit)
            override suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials) = Result.success(null)
        }

        // 1. Offline: comando é enfileirado como PENDING
        val queued = parentEngine.enqueueParentCommand(CommandType.PAUSE_DEVICE)
        assertEquals(CommandStatus.PENDING, queued.status)
        assertTrue(parentEngine.flushPendingCommands(simulatedRelay).isFailure)
        assertEquals(CommandStatus.PENDING, parentEngine.getCommands().first().status)

        // 2. Reconexão: envio ao relay marca SENT (nunca EXECUTED ainda)
        relayOnline = true
        assertTrue(parentEngine.flushPendingCommands(simulatedRelay).isSuccess)
        assertEquals(CommandStatus.SENT, parentEngine.getCommands().first().status)

        // 3. Kids recebe, executa e envia ACK assinado
        val kidsExec = kidsEngine.pollAndExecuteKidsCommands(simulatedRelay) { _ -> true to "Tablet pausado" }
        assertTrue(kidsExec.isSuccess)
        assertEquals(1, relayAcks.size)

        // 4. Parent recebe ACK assinado e só então marca EXECUTED
        val ackApplied = parentEngine.pollAndApplyAcks(simulatedRelay)
        assertTrue(ackApplied.isSuccess)
        assertEquals(CommandStatus.EXECUTED, parentEngine.getCommands().first().status)
    }
}
