package com.dohguard.shared.remote

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class CommandSecurityResult {
    data object Authorized : CommandSecurityResult()
    data class Rejected(val reason: String, val isExpired: Boolean = false, val isDuplicate: Boolean = false) :
        CommandSecurityResult()
}

/**
 * Camada de segurança para comunicação remota entre MonitorDad Parent e MonitorDad Kids.
 * Garante autenticação por dispositivo/responsável, assinatura HMAC-SHA256,
 * proteção contra replay (nonce/commandId) e expiração de comandos.
 * Nunca registra segredos, tokens ou PINs em logs.
 */
object RemoteSecurity {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private val random = SecureRandom()

    fun generateNonce(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateSharedSecret(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun canonicalPayload(payload: Map<String, String>): String {
        return payload.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
    }

    fun signCommand(command: RemoteCommand, sharedSecret: String): String {
        if (sharedSecret.isBlank()) return ""
        val canonical = listOf(
            command.commandId,
            command.targetDeviceId,
            command.issuedByParentId,
            command.type.name,
            command.createdAtMillis.toString(),
            command.expiresAtMillis.toString(),
            command.sequenceNumber.toString(),
            command.nonce,
            canonicalPayload(command.payload)
        ).joinToString("|")
        return hmacSha256Hex(canonical, sharedSecret)
    }

    fun verifyCommandSignature(command: RemoteCommand, sharedSecret: String): Boolean {
        if (sharedSecret.isBlank() || command.signature.isBlank()) return false
        val expected = signCommand(command, sharedSecret)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            command.signature.toByteArray(Charsets.UTF_8)
        )
    }

    fun signAck(ack: CommandAck, sharedSecret: String): String {
        if (sharedSecret.isBlank()) return ""
        val canonical = listOf(
            ack.commandId,
            ack.deviceId,
            ack.status.name,
            ack.timestampMillis.toString(),
            ack.resultMessage.orEmpty(),
            ack.errorMessage.orEmpty()
        ).joinToString("|")
        return hmacSha256Hex(canonical, sharedSecret)
    }

    fun verifyAckSignature(ack: CommandAck, sharedSecret: String): Boolean {
        if (sharedSecret.isBlank() || ack.signature.isBlank()) return false
        val expected = signAck(ack, sharedSecret)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            ack.signature.toByteArray(Charsets.UTF_8)
        )
    }

    fun validateIncomingCommand(
        command: RemoteCommand,
        credentials: PairedDeviceCredentials,
        seenNonces: Set<String>,
        seenCommandIds: Set<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): CommandSecurityResult {
        if (!credentials.isPaired || credentials.sharedSecret.isBlank()) {
            return CommandSecurityResult.Rejected("Dispositivo não pareado com responsável autorizado.")
        }
        if (command.targetDeviceId != credentials.deviceId) {
            return CommandSecurityResult.Rejected("Comando destinado a outro dispositivo (${command.targetDeviceId}).")
        }
        if (command.issuedByParentId != credentials.parentId) {
            return CommandSecurityResult.Rejected("Responsável não autorizado (${command.issuedByParentId}).")
        }
        if (seenCommandIds.contains(command.commandId) || seenNonces.contains(command.nonce)) {
            return CommandSecurityResult.Rejected(
                reason = "Comando ou nonce duplicado (proteção contra replay).",
                isDuplicate = true
            )
        }
        if (command.isExpiredAt(nowMillis)) {
            return CommandSecurityResult.Rejected(
                reason = "Comando expirado antes da execução.",
                isExpired = true
            )
        }
        if (!verifyCommandSignature(command, credentials.sharedSecret)) {
            return CommandSecurityResult.Rejected("Assinatura HMAC inválida para o comando.")
        }
        return CommandSecurityResult.Authorized
    }

    private const val DEFAULT_REQUEST_MAX_SKEW_MILLIS = 5 * 60_000L

    fun isTimestampFresh(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        maxSkewMillis: Long = DEFAULT_REQUEST_MAX_SKEW_MILLIS
    ): Boolean {
        if (timestampMillis <= 0L || maxSkewMillis < 0L) return false
        return kotlin.math.abs(nowMillis - timestampMillis) <= maxSkewMillis
    }

    fun signRequest(
        method: String,
        path: String,
        timestampMillis: Long,
        nonce: String,
        body: String,
        sharedSecret: String
    ): String {
        if (sharedSecret.isBlank() || nonce.isBlank()) return ""
        val canonical = listOf(
            method.trim().uppercase(),
            path.trim(),
            timestampMillis.toString(),
            nonce.trim(),
            sha256Hex(body)
        ).joinToString("|")
        return hmacSha256Hex(canonical, sharedSecret)
    }

    fun verifyRequestSignature(
        method: String,
        path: String,
        timestampMillis: Long,
        nonce: String,
        body: String,
        sharedSecret: String,
        providedSignature: String
    ): Boolean {
        if (sharedSecret.isBlank() || providedSignature.isBlank() || nonce.isBlank()) return false
        val expected = signRequest(method, path, timestampMillis, nonce, body, sharedSecret)
        if (expected.isBlank()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            providedSignature.trim().lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hmacSha256Hex(data: String, secret: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(keySpec)
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val authSchemeHeaderRegex = Regex(
        "(?i)(authorization)\\s*[:=]\\s*\"?(bearer|basic|token)\\s+([^\\s\",;]+)\"?"
    )
    private val schemeTokenRegex = Regex("(?i)\\b(bearer|basic)\\s+([^\\s\",;]+)")
    private const val SENSITIVE_KEYS =
        "token|access_token|refresh_token|id_token|secret|shared[_-]?secret|signature|hmac|" +
            "pin|password|pass|senha|api[_-]?key|apikey|credential|credentials|device[_-]?secret|" +
            "session[_-]?id|private[_-]?key|authorization"
    private val doubleQuotedKeyValueRegex = Regex("(?i)(\"?(?:$SENSITIVE_KEYS)\"?)(\\s*[:=]\\s*)\"([^\"]*)\"")
    private val singleQuotedKeyValueRegex = Regex("(?i)(\"?(?:$SENSITIVE_KEYS)\"?)(\\s*[:=]\\s*)'([^']*)'")
    private val unquotedKeyValueRegex = Regex(
        "(?i)\\b($SENSITIVE_KEYS)(\\s*[:=]\\s*)(?!(?:bearer|basic)\\s+\\[REDACTED\\])([^\\s,;]+)"
    )
    private val standaloneJwtRegex = Regex("\\beyJ[A-Za-z0-9_-]{8,}(?:\\.[A-Za-z0-9_-]+){1,2}")

    /**
     * Mascara tokens, segredos, senhas, PINs e cabeçalhos Authorization antes de qualquer saída de log ou diagnóstico.
     */
    fun redactSensitive(message: String): String {
        return message
            .replace(authSchemeHeaderRegex, "$1: $2 [REDACTED]")
            .replace(schemeTokenRegex, "$1 [REDACTED]")
            .replace(doubleQuotedKeyValueRegex, "$1$2\"[REDACTED]\"")
            .replace(singleQuotedKeyValueRegex, "$1$2'[REDACTED]'")
            .replace(unquotedKeyValueRegex, "$1$2[REDACTED]")
            .replace(standaloneJwtRegex, "[REDACTED]")
    }
}
