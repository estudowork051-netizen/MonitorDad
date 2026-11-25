package com.dohguard.parent.remote

import android.content.Context
import com.dohguard.shared.remote.AndroidNsdDiscovery
import com.dohguard.shared.remote.LocalHttpServer
import com.dohguard.shared.remote.PairedDeviceCredentials
import com.dohguard.shared.remote.PairingManager
import com.dohguard.shared.remote.RemoteControlRepository
import org.json.JSONObject

/**
 * Servidor temporário de pareamento local em rede Wi-Fi no MonitorDad Parent.
 * Ativado quando o responsável abre a tela "Adicionar dispositivo" e encerrado automaticamente
 * após o pareamento ou ao fechar a tela.
 */
class LocalPairingServer(context: Context) {

    private val appContext = context.applicationContext
    private val nsdDiscovery = AndroidNsdDiscovery(appContext)
    private var server: LocalHttpServer? = null

    var listeningPort: Int = 0
        private set

    fun start(
        parentId: String,
        parentName: String,
        onDevicePaired: (PairedDeviceCredentials) -> Unit
    ): Int {
        stop()
        val httpServer = LocalHttpServer { req ->
            when {
                req.method == "GET" && req.path == "/health" -> {
                    LocalHttpServer.HttpResponse(
                        statusCode = 200,
                        body = JSONObject().apply {
                            put("status", "ok")
                            put("role", "parent-pairing")
                            put("serverTimeMillis", System.currentTimeMillis())
                        }.toString()
                    )
                }

                req.method == "POST" && req.path == "/api/v1/local-pairing/claim" -> {
                    handleClaimRequest(req.body, onDevicePaired)
                }

                else -> {
                    LocalHttpServer.HttpResponse(
                        statusCode = 404,
                        body = """{"error":"Rota não encontrada"}"""
                    )
                }
            }
        }
        val port = httpServer.start(bindPort = 0, loopbackOnly = false)
        server = httpServer
        listeningPort = port

        nsdDiscovery.registerService(
            serviceType = AndroidNsdDiscovery.SERVICE_TYPE_PAIRING,
            serviceName = "MonitorDad-Parent-${parentId.takeLast(4)}",
            port = port,
            attributes = mapOf(
                "parentId" to parentId,
                "parentName" to parentName
            )
        )
        return port
    }

    fun stop() {
        nsdDiscovery.unregisterService()
        server?.stop()
        server = null
        listeningPort = 0
    }

    private fun handleClaimRequest(
        rawBody: String,
        onDevicePaired: (PairedDeviceCredentials) -> Unit
    ): LocalHttpServer.HttpResponse {
        val body = runCatching { JSONObject(rawBody) }.getOrNull()
            ?: return LocalHttpServer.HttpResponse(400, """{"error":"JSON inválido"}""")

        val code = body.optString("pairingCode", "")
        val deviceId = body.optString("deviceId", "")
        val deviceName = body.optString("deviceName", "Tablet MonitorDad")
        val now = System.currentTimeMillis()

        return when (val result = RemoteControlRepository.pairingManager.validateAndClaim(
            submittedCode = code,
            deviceId = deviceId,
            deviceName = deviceName,
            nowMillis = now
        )) {
            is PairingManager.ClaimValidationResult.Rejected -> {
                val status = if (result.locked) 429 else 403
                LocalHttpServer.HttpResponse(
                    statusCode = status,
                    body = JSONObject().apply {
                        put("error", result.reason)
                        put("locked", result.locked)
                    }.toString()
                )
            }

            is PairingManager.ClaimValidationResult.Accepted -> {
                val relayUrl = RemoteControlRepository.connectionSettings.value.relayBaseUrl
                val creds = RemoteControlRepository.configurePairing(
                    deviceId = result.deviceId,
                    parentId = result.session.parentId,
                    deviceName = result.deviceName,
                    relayBaseUrl = relayUrl,
                    sharedSecret = result.sharedSecret
                )
                onDevicePaired(creds)
                LocalHttpServer.HttpResponse(
                    statusCode = 200,
                    body = JSONObject().apply {
                        put("deviceId", creds.deviceId)
                        put("parentId", creds.parentId)
                        put("deviceName", creds.deviceName)
                        put("sharedSecret", creds.sharedSecret)
                        put("relayBaseUrl", creds.relayBaseUrl)
                        put("pairedAtMillis", creds.pairedAtMillis)
                    }.toString()
                )
            }
        }
    }
}
