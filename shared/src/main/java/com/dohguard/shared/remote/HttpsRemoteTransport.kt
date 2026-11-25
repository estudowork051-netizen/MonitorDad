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
 * Transporte remoto via HTTPS/TLS usando HttpURLConnection padrão do Android/Java.
 * Exige URL HTTPS (exceto loopback localhost/127.0.0.1 para testes) e retorna Result.failure
 * quando o servidor não está configurado ou está inacessível (sem simulação).
 */
class HttpsRemoteTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 35_000
) : RemoteTransport {

    suspend fun pingHealth(relayBaseUrl: String): Result<Long> = withContext(Dispatchers.IO) {
        val clean = relayBaseUrl.trim().trimEnd('/')
        val isAllowed = clean.startsWith("https://") ||
            clean.startsWith("http://127.0.0.1") ||
            clean.startsWith("http://localhost")
        if (!isAllowed) {
            return@withContext Result.failure(IllegalArgumentException("URL remota deve usar HTTPS."))
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
                    throw IllegalStateException("Servidor remoto retornou HTTP $code")
                }
                (System.currentTimeMillis() - start).coerceAtLeast(1L)
            } finally {
                conn.disconnect()
            }
        }
    }

    override suspend fun initiatePairing(
        relayBaseUrl: String,
        deviceId: String,
        deviceName: String
    ): Result<PairingInitiation> {
        val body = JSONObject().apply {
            put("deviceId", deviceId.trim())
            put("deviceName", deviceName.trim().ifBlank { "Tablet MonitorDad" })
        }.toString()
        return executePublicPost(relayBaseUrl, "/api/v1/pairing/initiate", body).mapCatching { raw ->
            val obj = JSONObject(raw)
            PairingInitiation(
                pairingCode = obj.getString("pairingCode"),
                claimToken = obj.getString("claimToken"),
                expiresAtMillis = obj.getLong("expiresAtMillis"),
                deviceId = obj.optString("deviceId", deviceId),
                deviceName = obj.optString("deviceName", deviceName)
            )
        }
    }

    override suspend fun claimPairingCode(
        relayBaseUrl: String,
        pairingCode: String,
        parentId: String,
        deviceName: String?
    ): Result<PairedDeviceCredentials> {
        val body = JSONObject().apply {
            put("pairingCode", pairingCode.trim().uppercase())
            put("parentId", parentId.trim())
            if (!deviceName.isNullOrBlank()) {
                put("deviceName", deviceName.trim())
            }
        }.toString()
        return executePublicPost(relayBaseUrl, "/api/v1/pairing/claim", body).mapCatching { raw ->
            val obj = JSONObject(raw)
            PairedDeviceCredentials(
                deviceId = obj.getString("deviceId"),
                parentId = obj.getString("parentId"),
                deviceName = obj.optString("deviceName", "Tablet MonitorDad"),
                sharedSecret = obj.getString("sharedSecret"),
                relayBaseUrl = relayBaseUrl.trim().trimEnd('/'),
                pairedAtMillis = obj.optLong("pairedAtMillis", System.currentTimeMillis()),
                isPaired = true
            )
        }
    }

    override suspend fun pollPairingCompletion(
        relayBaseUrl: String,
        pairingCode: String,
        claimToken: String
    ): Result<PairingCompletionResult> {
        val body = JSONObject().apply {
            put("pairingCode", pairingCode.trim().uppercase())
            put("claimToken", claimToken)
        }.toString()
        return executePublicPost(relayBaseUrl, "/api/v1/pairing/complete", body).mapCatching { raw ->
            val obj = JSONObject(raw)
            val statusStr = obj.optString("status", "PENDING")
            if (statusStr == "PAIRED") {
                PairingCompletionResult(
                    status = PairingCompletionStatus.PAIRED,
                    deviceId = obj.getString("deviceId"),
                    parentId = obj.getString("parentId"),
                    deviceName = obj.optString("deviceName", "Tablet MonitorDad"),
                    sharedSecret = obj.getString("sharedSecret"),
                    pairedAtMillis = obj.optLong("pairedAtMillis", System.currentTimeMillis())
                )
            } else {
                PairingCompletionResult(
                    status = PairingCompletionStatus.PENDING,
                    expiresAtMillis = obj.optLong("expiresAtMillis", 0L)
                )
            }
        }
    }

    override suspend fun heartbeat(credentials: PairedDeviceCredentials, role: String): Result<Long> {
        val body = JSONObject().apply {
            put("deviceId", credentials.deviceId)
            put("parentId", credentials.parentId)
            put("role", role)
            put("timestampMillis", System.currentTimeMillis())
        }.toString()
        return executeJsonRequest(credentials, "POST", "/api/v1/devices/${credentials.deviceId}/heartbeat", body)
            .map { raw ->
                runCatching { JSONObject(raw).optLong("serverTimeMillis", System.currentTimeMillis()) }
                    .getOrDefault(System.currentTimeMillis())
            }
    }

    override suspend fun sendCommand(credentials: PairedDeviceCredentials, command: RemoteCommand): Result<Unit> {
        val body = RemoteJsonCodec.commandToJson(command).toString()
        return executeJsonRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/commands",
            body = body
        ).map { }
    }

    override suspend fun pollCommands(credentials: PairedDeviceCredentials): Result<List<RemoteCommand>> {
        return executeJsonRequest(
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
        return executeJsonRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/commands/${ack.commandId}/ack",
            body = body
        ).map { }
    }

    override suspend fun pollAcks(credentials: PairedDeviceCredentials): Result<List<CommandAck>> {
        return executeJsonRequest(
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
        return executeJsonRequest(
            credentials = credentials,
            method = "PUT",
            path = "/api/v1/devices/${credentials.deviceId}/rules",
            body = body
        ).map { }
    }

    override suspend fun pullLatestRulesEnvelope(credentials: PairedDeviceCredentials): Result<RuleSyncEnvelope?> {
        return executeJsonRequest(
            credentials = credentials,
            method = "GET",
            path = "/api/v1/devices/${credentials.deviceId}/rules",
            body = null
        ).mapCatching { raw ->
            if (raw.isBlank() || raw == "{}") null
            else RemoteJsonCodec.envelopeFromJson(JSONObject(raw))
        }
    }

    override suspend fun pushTelemetry(
        credentials: PairedDeviceCredentials,
        payload: TelemetryPayload
    ): Result<Unit> {
        val body = RemoteJsonCodec.telemetryToJson(payload).toString()
        return executeJsonRequest(
            credentials = credentials,
            method = "PUT",
            path = "/api/v1/devices/${credentials.deviceId}/telemetry",
            body = body
        ).map { }
    }

    override suspend fun pullLatestTelemetry(credentials: PairedDeviceCredentials): Result<TelemetryPayload?> {
        return executeJsonRequest(
            credentials = credentials,
            method = "GET",
            path = "/api/v1/devices/${credentials.deviceId}/telemetry",
            body = null
        ).mapCatching { raw ->
            if (raw.isBlank() || raw == "{}") null
            else RemoteJsonCodec.telemetryFromJson(JSONObject(raw))
        }
    }

    override suspend fun provisionPairedDevice(credentials: PairedDeviceCredentials): Result<Unit> {
        val body = JSONObject().apply {
            put("parentId", credentials.parentId)
            put("deviceName", credentials.deviceName)
            put("sharedSecret", credentials.sharedSecret)
        }.toString()
        return executeJsonRequest(
            credentials = credentials,
            method = "POST",
            path = "/api/v1/devices/${credentials.deviceId}/provision",
            body = body
        ).map { }
    }

    override suspend fun revokeDevice(credentials: PairedDeviceCredentials): Result<Unit> {
        return executeJsonRequest(
            credentials = credentials,
            method = "DELETE",
            path = "/api/v1/devices/${credentials.deviceId}",
            body = null
        ).map { }
    }

    private suspend fun executeJsonRequest(
        credentials: PairedDeviceCredentials,
        method: String,
        path: String,
        body: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!credentials.hasValidRelay()) {
            return@withContext Result.failure(
                IllegalStateException("URL do relay remoto inválida ou não configurada (use https://).")
            )
        }
        val baseUrl = credentials.relayBaseUrl.trim().trimEnd('/')
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl$path")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-MonitorDad-Device", credentials.deviceId)
                setRequestProperty("X-MonitorDad-Parent", credentials.parentId)
                if (credentials.sharedSecret.isNotBlank()) {
                    val authDigest = RemoteSecurity.hmacSha256Hex(
                        "${credentials.deviceId}:${credentials.parentId}",
                        credentials.sharedSecret
                    )
                    setRequestProperty("Authorization", "Bearer $authDigest")
                }
                if (body != null) {
                    doOutput = true
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(body)
                        writer.flush()
                    }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else ""

            if (code in 200..299) {
                Result.success(responseText)
            } else {
                val serverError = runCatching {
                    JSONObject(responseText).optString("error", "")
                }.getOrDefault("").trim()
                val safeDetail = if (serverError.isNotEmpty()) {
                    ": ${RemoteSecurity.redactSensitive(serverError)}"
                } else ""
                Result.failure(IllegalStateException("HTTP $code ao comunicar com o relay remoto$safeDetail"))
            }
        } catch (e: Exception) {
            val safeMessage = RemoteSecurity.redactSensitive(e.message ?: "Falha de comunicação HTTPS")
            Result.failure(IllegalStateException(safeMessage))
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun executePublicPost(
        relayBaseUrl: String,
        path: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedBase = relayBaseUrl.trim().trimEnd('/')
        val isValidUrl = trimmedBase.startsWith("https://") ||
            trimmedBase.startsWith("http://127.0.0.1") ||
            trimmedBase.startsWith("http://localhost")
        if (!isValidUrl) {
            return@withContext Result.failure(
                IllegalStateException("URL do relay inválida. Use um servidor https://.")
            )
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL("$trimmedBase$path")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else ""

            if (code in 200..299) {
                Result.success(responseText)
            } else {
                val errMsg = runCatching { JSONObject(responseText).optString("error", "") }
                    .getOrDefault("")
                    .ifBlank { "HTTP $code ao parear com o servidor relay" }
                Result.failure(IllegalStateException(RemoteSecurity.redactSensitive(errMsg)))
            }
        } catch (e: Exception) {
            val safeMessage = RemoteSecurity.redactSensitive(e.message ?: "Falha de conexão com o relay")
            Result.failure(IllegalStateException(safeMessage))
        } finally {
            conn?.disconnect()
        }
    }
}
