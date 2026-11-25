package com.dohguard.shared.remote

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Gerenciador puro de sessões de pareamento, códigos temporários de uso único,
 * proteção contra força bruta, payload de QR Code e revogação de dispositivos vinculados.
 * Sem dependências de Android para execução direta em testes unitários JVM.
 */
class PairingManager(
    private val random: SecureRandom = SecureRandom(),
    private val codeTtlMillis: Long = DEFAULT_CODE_TTL_MS,
    private val maxFailedAttempts: Int = DEFAULT_MAX_ATTEMPTS
) {

    data class PairingSession(
        val code: String,
        val parentId: String,
        val parentName: String,
        val createdAtMillis: Long,
        val expiresAtMillis: Long,
        val failedAttempts: Int = 0,
        val isUsed: Boolean = false,
        val isLocked: Boolean = false,
        val isCancelled: Boolean = false
    ) {
        fun isActiveAt(nowMillis: Long): Boolean =
            !isUsed && !isLocked && !isCancelled && nowMillis <= expiresAtMillis
    }

    sealed class ClaimValidationResult {
        data class Accepted(
            val session: PairingSession,
            val sharedSecret: String,
            val deviceId: String,
            val deviceName: String
        ) : ClaimValidationResult()

        data class Rejected(val reason: String, val locked: Boolean = false) : ClaimValidationResult()
    }

    private var activeSession: PairingSession? = null
    private val linkedDevices = mutableListOf<LinkedDeviceRecord>()

    fun generateNewSession(
        parentId: String,
        parentName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PairingSession {
        val code = generatePairingCode()
        val session = PairingSession(
            code = code,
            parentId = parentId.trim().ifBlank { "parent-hod-01" },
            parentName = parentName.trim().ifBlank { "Responsável" },
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + codeTtlMillis
        )
        activeSession = session
        return session
    }

    fun getActiveSession(nowMillis: Long = System.currentTimeMillis()): PairingSession? {
        val current = activeSession ?: return null
        return if (current.isActiveAt(nowMillis)) current else null
    }

    fun cancelActiveSession() {
        activeSession = activeSession?.copy(isCancelled = true)
    }

    fun validateAndClaim(
        submittedCode: String,
        deviceId: String,
        deviceName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ClaimValidationResult {
        val session = activeSession
            ?: return ClaimValidationResult.Rejected("Nenhum código de pareamento ativo. Gere um novo código no app dos pais.")

        if (session.isCancelled) {
            return ClaimValidationResult.Rejected("Este código foi substituído ou cancelado.")
        }
        if (session.isUsed) {
            return ClaimValidationResult.Rejected("Este código já foi utilizado. Gere um novo código para outro dispositivo.")
        }
        if (session.isLocked) {
            return ClaimValidationResult.Rejected("Código bloqueado por excesso de tentativas incorretas. Gere um novo código.", locked = true)
        }
        if (nowMillis > session.expiresAtMillis) {
            return ClaimValidationResult.Rejected("Código expirado. Toque em 'Gerar novo código'.")
        }

        val normalizedInput = normalizeCode(submittedCode)
        if (normalizedInput != session.code) {
            val nextAttempts = session.failedAttempts + 1
            val lockedNow = nextAttempts >= maxFailedAttempts
            activeSession = session.copy(
                failedAttempts = nextAttempts,
                isLocked = lockedNow
            )
            return if (lockedNow) {
                ClaimValidationResult.Rejected(
                    "Muitas tentativas incorretas. Código bloqueado por segurança; gere um novo código.",
                    locked = true
                )
            } else {
                val remaining = maxFailedAttempts - nextAttempts
                ClaimValidationResult.Rejected(
                    "Código incorreto. Restam $remaining tentativa(s)."
                )
            }
        }

        val cleanDeviceId = deviceId.trim().ifBlank { "tablet-kids-${(1000..9999).random()}" }
        val cleanDeviceName = deviceName.trim().ifBlank { "Tablet MonitorDad" }
        val secret = RemoteSecurity.generateSharedSecret()

        val completed = session.copy(isUsed = true)
        activeSession = completed

        registerOrUpdateDevice(
            LinkedDeviceRecord(
                deviceId = cleanDeviceId,
                deviceName = cleanDeviceName,
                parentId = session.parentId,
                pairedAtMillis = nowMillis,
                lastSeenMillis = nowMillis,
                lastTransport = ActiveTransportType.LOCAL_WIFI,
                revoked = false
            )
        )

        return ClaimValidationResult.Accepted(
            session = completed,
            sharedSecret = secret,
            deviceId = cleanDeviceId,
            deviceName = cleanDeviceName
        )
    }

    fun registerOrUpdateDevice(record: LinkedDeviceRecord) {
        val index = linkedDevices.indexOfFirst { it.deviceId == record.deviceId }
        if (index >= 0) {
            linkedDevices[index] = record
        } else {
            linkedDevices.add(0, record)
        }
    }

    fun restoreDevices(records: List<LinkedDeviceRecord>) {
        linkedDevices.clear()
        linkedDevices.addAll(records)
    }

    fun getLinkedDevices(includeRevoked: Boolean = false): List<LinkedDeviceRecord> =
        if (includeRevoked) linkedDevices.toList() else linkedDevices.filter { !it.revoked }

    fun revokeDevice(deviceId: String): Boolean {
        val index = linkedDevices.indexOfFirst { it.deviceId == deviceId }
        if (index < 0) return false
        linkedDevices[index] = linkedDevices[index].copy(revoked = true)
        return true
    }

    fun removeDevice(deviceId: String): Boolean {
        return linkedDevices.removeAll { it.deviceId == deviceId }
    }

    private fun generatePairingCode(): String {
        val chars = CODE_ALPHABET
        return buildString(6) {
            repeat(6) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    companion object {
        const val DEFAULT_CODE_TTL_MS = 10 * 60 * 1000L // 10 minutos
        const val DEFAULT_MAX_ATTEMPTS = 5
        private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val QR_SCHEME = "monitordad://pair"

        fun normalizeCode(raw: String): String =
            raw.trim().uppercase().replace("-", "").replace(" ", "")

        fun encodeQrPayload(payload: PairingQrPayload): String {
            fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
            val params = buildList {
                add("v=${payload.version}")
                add("code=${enc(normalizeCode(payload.code))}")
                add("pid=${enc(payload.parentId)}")
                add("pname=${enc(payload.parentName)}")
                if (payload.localHost.isNotBlank() && payload.localPort in 1..65535) {
                    add("host=${enc(payload.localHost)}")
                    add("port=${payload.localPort}")
                }
                if (payload.relayUrl.isNotBlank()) {
                    add("relay=${enc(payload.relayUrl.trim().trimEnd('/'))}")
                }
                if (payload.expiresAtMillis > 0L) {
                    add("exp=${payload.expiresAtMillis}")
                }
            }
            return "$QR_SCHEME?${params.joinToString("&")}"
        }

        fun decodeQrPayload(raw: String): PairingQrPayload? {
            val trimmed = raw.trim()
            if (!trimmed.startsWith(QR_SCHEME)) {
                val normalized = normalizeCode(trimmed)
                return if (normalized.length in 5..10 && normalized.all { it.isLetterOrDigit() }) {
                    PairingQrPayload(
                        code = normalized,
                        parentId = "",
                        parentName = ""
                    )
                } else {
                    null
                }
            }
            val queryIdx = trimmed.indexOf('?')
            if (queryIdx < 0 || queryIdx == trimmed.lastIndex) return null
            val query = trimmed.substring(queryIdx + 1)
            val map = mutableMapOf<String, String>()
            query.split('&').forEach { pair ->
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                    map[key] = value
                }
            }
            val code = normalizeCode(map["code"] ?: return null)
            if (code.length < 4) return null
            return PairingQrPayload(
                version = map["v"]?.toIntOrNull() ?: 1,
                code = code,
                parentId = map["pid"].orEmpty(),
                parentName = map["pname"].orEmpty(),
                localHost = map["host"].orEmpty(),
                localPort = map["port"]?.toIntOrNull() ?: 0,
                relayUrl = map["relay"].orEmpty(),
                expiresAtMillis = map["exp"]?.toLongOrNull() ?: 0L
            )
        }
    }
}
