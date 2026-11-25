package com.dohguard.kids.remote

import android.content.Context
import com.dohguard.shared.remote.AndroidNsdDiscovery
import com.dohguard.shared.remote.CommandAck
import com.dohguard.shared.remote.CommandStatus
import com.dohguard.shared.remote.LocalHttpServer
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.remote.RemoteJsonCodec
import com.dohguard.shared.remote.RemoteSecurity
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/**
 * Servidor local em rede Wi-Fi no MonitorDad Kids.
 * Recebe comandos, regras e pedidos de telemetria diretamente do MonitorDad Parent na mesma rede,
 * com validação obrigatória de assinatura HMAC-SHA256, janela temporal e proteção contra replay.
 */
object LocalKidsServer {

    private var nsdDiscovery: AndroidNsdDiscovery? = null
    private var server: LocalHttpServer? = null
    private val seenNonces = Collections.synchronizedSet(LinkedHashSet<String>())
    private val localAcks = Collections.synchronizedList(mutableListOf<CommandAck>())

    var listeningPort: Int = 0
        private set

    fun startIfPaired(context: Context) {
        val creds = RemoteControlRepository.credentials.value
        if (!creds.isPaired || creds.sharedSecret.isBlank()) {
            stop()
            return
        }
        if (server?.isRunning == true) return

        val appContext = context.applicationContext
        val httpServer = LocalHttpServer { req -> handleRequest(appContext, req) }
        val port = httpServer.start(bindPort = 0, loopbackOnly = false)
        server = httpServer
        listeningPort = port

        val nsd = nsdDiscovery ?: AndroidNsdDiscovery(appContext).also { nsdDiscovery = it }
        nsd.registerService(
            serviceType = AndroidNsdDiscovery.SERVICE_TYPE_KIDS,
            serviceName = "MonitorDad-Kids-${creds.deviceId.takeLast(6)}",
            port = port,
            attributes = mapOf(
                "deviceId" to creds.deviceId,
                "deviceName" to creds.deviceName
            )
        )
    }

    fun stop() {
        nsdDiscovery?.unregisterService()
        server?.stop()
        server = null
        listeningPort = 0
    }

    private fun handleRequest(
        context: Context,
        req: LocalHttpServer.HttpRequest
    ): LocalHttpServer.HttpResponse {
        if (req.method == "GET" && req.path == "/health") {
            return LocalHttpServer.HttpResponse(
                statusCode = 200,
                body = JSONObject().apply {
                    put("status", "ok")
                    put("role", "kids-local")
                    put("serverTimeMillis", System.currentTimeMillis())
                }.toString()
            )
        }

        val creds = RemoteControlRepository.credentials.value
        if (!creds.isPaired || creds.sharedSecret.isBlank()) {
            return LocalHttpServer.HttpResponse(401, """{"error":"Dispositivo não pareado"}""")
        }

        val expectedPrefix = "/api/v1/devices/${creds.deviceId}"
        if (!req.path.startsWith(expectedPrefix)) {
            return LocalHttpServer.HttpResponse(404, """{"error":"Dispositivo ou rota desconhecida"}""")
        }

        val deviceHeader = req.header("X-Device-Id")
        val parentHeader = req.header("X-Parent-Id")
        val tsHeader = req.header("X-Timestamp").toLongOrNull() ?: 0L
        val nonce = req.header("X-Nonce")
        val sig = req.header("X-Signature")

        if (deviceHeader != creds.deviceId || parentHeader != creds.parentId) {
            return LocalHttpServer.HttpResponse(403, """{"error":"Identidade de pareamento inválida"}""")
        }
        if (!RemoteSecurity.isTimestampFresh(tsHeader)) {
            return LocalHttpServer.HttpResponse(401, """{"error":"Timestamp expirado"}""")
        }
        if (nonce.isBlank() || !seenNonces.add(nonce)) {
            return LocalHttpServer.HttpResponse(401, """{"error":"Nonce inválido ou repetido"}""")
        }
        if (seenNonces.size > 500) {
            synchronized(seenNonces) {
                val it = seenNonces.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }

        val validSig = RemoteSecurity.verifyRequestSignature(
            method = req.method,
            path = req.path,
            timestampMillis = tsHeader,
            nonce = nonce,
            body = req.body,
            sharedSecret = creds.sharedSecret,
            providedSignature = sig
        )
        if (!validSig) {
            return LocalHttpServer.HttpResponse(401, """{"error":"Assinatura HMAC inválida"}""")
        }

        val subPath = req.path.removePrefix(expectedPrefix)
        return when {
            req.method == "POST" && subPath == "/heartbeat" -> {
                LocalHttpServer.HttpResponse(
                    200,
                    JSONObject().apply {
                        put("status", "ok")
                        put("serverTimeMillis", System.currentTimeMillis())
                    }.toString()
                )
            }

            req.method == "POST" && subPath == "/commands" -> {
                val cmdObj = runCatching { JSONObject(req.body) }.getOrNull()
                    ?: return LocalHttpServer.HttpResponse(400, """{"error":"JSON de comando inválido"}""")
                val cmd = RemoteJsonCodec.commandFromJson(cmdObj)
                    ?: return LocalHttpServer.HttpResponse(400, """{"error":"Comando inválido"}""")

                val (ok, msg) = if (RemoteSecurity.verifyCommandSignature(cmd, creds.sharedSecret)) {
                    KidsCommandExecutor.execute(context, cmd)
                } else {
                    false to "Assinatura de comando inválida."
                }
                val ackStatus = if (ok) CommandStatus.EXECUTED else CommandStatus.FAILED
                val unsignedAck = CommandAck(
                    commandId = cmd.commandId,
                    deviceId = creds.deviceId,
                    status = ackStatus,
                    timestampMillis = System.currentTimeMillis(),
                    resultMessage = if (ok) msg else null,
                    errorMessage = if (!ok) msg else null
                )
                val signedAck = unsignedAck.copy(
                    signature = RemoteSecurity.signAck(unsignedAck, creds.sharedSecret)
                )
                localAcks.add(signedAck)
                LocalHttpServer.HttpResponse(200, """{"accepted":true,"executed":$ok}""")
            }

            req.method == "GET" && subPath == "/acks" -> {
                val snapshotAcks = synchronized(localAcks) {
                    val copy = localAcks.toList()
                    localAcks.clear()
                    copy
                }
                val arr = JSONArray()
                snapshotAcks.forEach { arr.put(RemoteJsonCodec.ackToJson(it)) }
                LocalHttpServer.HttpResponse(
                    200,
                    JSONObject().apply { put("acks", arr) }.toString()
                )
            }

            req.method == "POST" && subPath == "/rules" -> {
                val envObj = runCatching { JSONObject(req.body) }.getOrNull()
                    ?: return LocalHttpServer.HttpResponse(400, """{"error":"JSON de regras inválido"}""")
                val envelope = RemoteJsonCodec.envelopeFromJson(envObj)
                    ?: return LocalHttpServer.HttpResponse(400, """{"error":"Envelope inválido"}""")
                ControlRulesRepository.applyIncomingSyncEnvelope(envelope)
                LocalHttpServer.HttpResponse(200, """{"applied":true}""")
            }

            req.method == "GET" && subPath == "/rules/latest" -> {
                val latest = ControlRulesRepository.syncEnvelopes.value.lastOrNull()
                val root = JSONObject()
                if (latest != null) {
                    root.put("envelope", RemoteJsonCodec.envelopeToJson(latest))
                }
                LocalHttpServer.HttpResponse(200, root.toString())
            }

            req.method == "GET" && subPath == "/telemetry/latest" -> {
                val telemetry = UsageSyncRepository.telemetryState.value
                val root = JSONObject()
                if (telemetry != null) {
                    root.put("telemetry", RemoteJsonCodec.telemetryToJson(telemetry))
                }
                LocalHttpServer.HttpResponse(200, root.toString())
            }

            req.method == "GET" && subPath == "/commands/pending" -> {
                LocalHttpServer.HttpResponse(200, """{"commands":[]}""")
            }

            else -> LocalHttpServer.HttpResponse(404, """{"error":"Rota não suportada"}""")
        }
    }
}
