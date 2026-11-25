package com.dohguard.shared.remote

import com.dohguard.shared.model.RuleSyncEnvelope
import com.dohguard.shared.model.TelemetryPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transporte local pela mesma rede Wi-Fi (HTTP sobre LAN com autenticação HMAC-SHA256).
 * Reutiliza exatamente o mesmo protocolo de rotas e payloads do HttpsRemoteTransport,
 * permitindo alternância transparente entre Wi-Fi local e internet.
 */
class LocalHttpTransport(
    private val connectTimeoutMs: Int = 2_500,
    private val readTimeoutMs: Int = 4_000
) : RemoteTransport {

    suspend fun pingHealth(baseUrl: String): Result<Long> = withContext(Dispatchers.IO) {
        val clean = baseUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://")) {
            return@withContext Result.failure(IllegalArgumentException("Endpoint local inválido."))
        }
        val start = System.currentTimeMillis()
        runCatching {
            val conn = (URL("$clean/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Servidor local retornou HTTP $code")
                }
                (System.currentTimeMillis() - start).coerceAtLeast(1L)
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun claimLocalPairing(
        localPairingBaseUrl: String,
        pairingCode: String,
        deviceId: String,
        deviceName: String
    ): Result<PairedDeviceCredentials> {
        val body = JSONObject().apply {
            put("pairingCode", PairingManager.normalizeCode(pairingCode))
            put("deviceId", deviceId.trim())
            put("deviceName", deviceName.trim().ifBlank { "Tablet MonitorDad" })
        }.toString()
        return executePublicPost(localPairingBaseUrl, "/api/v1/local-pairing/claim", body).mapCatching { raw ->
            val obj = JSONObject(raw)
            PairedDeviceCredentials(
                deviceId = obj.getString("deviceId"),
                parentId = obj.getString("parentId"),
                deviceName = obj.optString("deviceName", deviceName),
                sharedSecret = obj.getString("sharedSecret"),
                relayBaseUrl = obj.optString("relayBaseUrl", ""),
                pairedAtMillis = obj.optLong("pairedAtMillis", System.currentTimeMillis()),
                isPaired = true
            )
        }
    }

    override suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String): Result<Long> {
        val body = JSONObject().apply {
            put("deviceId", credentials.deviceId)
            put("parentId", credentials.parentId)
            put("role", role)
            put("timestampMillis", System.currentTimeMillis())
        }.toString()
        return executeSignedRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/heartbeat",
            body = body
        ).map { raw ->
            runCatching { JSONObject(raw).optLong("serverTimeMillis", System.currentTimeMillis()) }
                .getOrDefault(System.currentTimeMillis())
        }
    }

    override suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit> {
        val body = RemoteJsonCodec.commandToJson(command).toString()
        return executeSignedRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/commands",
            body = body
        ).map { }
    }

    override suspend fun pollCommands(credentials: PairedDeviceCredentials): Result<List<RemoteCommand>> {
        return executeSignedRequest(
            credentials = credentials,
            method = "GET",
            path = "/api/v1/devices/${credentials.deviceId}/commands/pending",
            body = null
        ).mapCatching { raw ->
            val root = JSONObject(raw)
            val arr = root.optJSONArray("commands") ?: JSONArray()
            val list = mutableListOf<RemoteCommand>()
            for (i in 0 until arr.length()) {
                RemoteJsonCodec.commandFromJson(arr.getJSONObject(i))?.let { list.add(it) }
            }
            list
        }
    }

    override suspend fun sendCommandAck(credentials: PairedDeviceCredentials, ack: CommandAck): Result<Unit> {
        val body = RemoteJsonCodec.ackToJson(ack).toString()
        return executeSignedRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/commands/${ack.commandId}/ack",
            body = body
        ).map { }
    }

    override suspend fun pollAcks(credentials: PairedDeviceCredentials): Result<List<CommandAck>> {
        return executeSignedRequest(
            credentials = credentials,
            method = "GET",
            path = "/api/v1/devices/${credentials.deviceId}/acks",
            body = null
        ).mapCatching { raw ->
            val root = JSONObject(raw)
            val arr = root.optJSONArray("acks") ?: JSONArray()
            val list = mutableListOf<CommandAck>()
            for (i in 0 until arr.length()) {
                RemoteJsonCodec.ackFromJson(arr.getJSONObject(i))?.let { list.add(it) }
            }
            list
        }
    }

    override suspend fun pushRulesEnvelope(
        credentials: PairedDeviceCredentials,
        envelope: RuleSyncEnvelope
    ): Result<Unit> {
        val body = RemoteJsonCodec.envelopeToJson(envelope).toString()
        return executeSignedRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/rules",
            body = body
        ).map { }
    }

    override suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials): Result<RuleSyncEnvelope?> {
        return executeSignedRequest(
            credentials = credentials,
            method = "GET",
            path = "/api/v1/devices/${credentials.deviceId}/rules/latest",
            body = null
        ).mapCatching { raw ->
            val root = JSONObject(raw)
            val envObj = root.optJSONObject("envelope") ?: return@mapCatching null
            RemoteJsonCodec.envelopeFromJson(envObj)
        }
    }

    override suspend fun pushTelemetry(
        credentials: PairedDeviceCredentials,
        payload: TelemetryPayload
    ): Result<Unit> {
        val body = RemoteJsonCodec.telemetryToJson(payload).toString()
        return executeSignedRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/telemetry",
            body = body
        ).map { }
    }

    override suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials): Result<TelemetryPayload?> {
        return executeSignedRequest(
            credentials = credentials,
            method = "GET",
            path = "/api/v1/devices/${credentials.deviceId}/telemetry/latest",
            body = null
        ).mapCatching { raw ->
            val root = JSONObject(raw)
            val telObj = root.optJSONObject("telemetry") ?: return@mapCatching null
            RemoteJsonCodec.telemetryFromJson(telObj)
        }
    }

    private suspend fun executePublicPost(
        baseUrl: String,
        path: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("http://")) {
            return@withContext Result.failure(IllegalArgumentException("Endpoint de pareamento local inválido."))
        }
        runCatching {
            val conn = (URL("$cleanBase$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }
            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.let { s ->
                    BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
                }.orEmpty()
                if (code !in 200..299) {
                    val serverMsg = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                    throw IllegalStateException(serverMsg?.takeIf { it.isNotBlank() } ?: "Erro local HTTP $code")
                }
                responseText
            } finally {
                conn.disconnect()
            }
        }
    }

    private suspend fun executeSignedRequest(
        credentials: PairedDeviceCredentials,
        method: String,
        path: String,
        body: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!credentials.hasLocalEndpoint()) {
            return@withContext Result.failure(
                IllegalStateException("Dispositivo não localizado na rede Wi-Fi local.")
            )
        }
        if (!credentials.isPaired || credentials.sharedSecret.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Dispositivo ainda não pareado.")
            )
        }

        val timestamp = System.currentTimeMillis()
        val nonce = RemoteSecurity.generateNonce()
        val bodyStr = body.orEmpty()
        val signature = RemoteSecurity.signRequest(
            method = method,
            path = path,
            timestampMillis = timestamp,
            nonce = nonce,
            body = bodyStr,
            sharedSecret = credentials.sharedSecret
        )
        val fullUrl = credentials.localBaseUrl() + path

        runCatching {
            val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Device-Id", credentials.deviceId)
                setRequestProperty("X-Parent-Id", credentials.parentId)
                setRequestProperty("X-Timestamp", timestamp.toString())
                setRequestProperty("X-Nonce", nonce)
                setRequestProperty("X-Signature", signature)
                if (body != null && (method == "POST" || method == "PUT")) {
                    doOutput = true
                }
            }
            try {
                if (body != null && (method == "POST" || method == "PUT")) {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(body)
                        writer.flush()
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.let { s ->
                    BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
                }.orEmpty()
                if (code !in 200..299) {
                    throw IllegalStateException("Erro local HTTP $code: ${RemoteSecurity.redactSensitive(responseText)}")
                }
                responseText
            } finally {
                conn.disconnect()
            }
        }
    }
}
