package com.dohguard.shared.sync

import com.dohguard.shared.model.ControlRulesState
import com.dohguard.shared.model.RuleSyncEnvelope
import com.dohguard.shared.model.TimeWindow
import java.security.MessageDigest

sealed class SyncValidationResult {
    data class Accepted(val envelope: RuleSyncEnvelope) : SyncValidationResult()
    data class Rejected(val reason: String) : SyncValidationResult()
}

/**
 * Validador puro de integridade, ordenação e deduplicação de regras sincronizadas.
 */
object RuleSyncValidator {

    fun computeChecksum(state: ControlRulesState): String {
        val canonical = buildString {
            append(state.deviceId).append('|')
            append(state.version).append('|')
            append(state.dailyDeviceLimitMinutes).append('|')
            append(state.isDevicePaused).append('|')
            append(state.dailyLimitDays.sortedBy { it.ordinal }.joinToString(",") { it.name }).append('|')
            state.appLimits.entries.sortedBy { it.key }.forEach { (pkg, r) ->
                append(pkg).append(':').append(r.dailyLimitMinutes).append(':').append(r.isBlocked)
                    .append(':').append(r.enabled).append(';')
            }
            append('|')
            state.categoryLimits.entries.sortedBy { it.key.name }.forEach { (cat, r) ->
                append(cat.name).append(':').append(r.dailyLimitMinutes).append(':').append(r.enabled).append(';')
            }
            append('|')
            state.routines.sortedBy { it.id }.forEach { r ->
                append(r.id).append(':').append(r.type.name).append(':').append(r.startTime)
                    .append('-').append(r.endTime).append(':').append(r.enabled).append(';')
            }
            append('|')
            state.extraGrants.sortedBy { it.id }.forEach { g ->
                append(g.id).append(':').append(g.minutes).append(':').append(g.expiresAtMillis).append(';')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun createEnvelope(
        envelopeId: String,
        sequenceNumber: Long,
        timestampMillis: Long,
        state: ControlRulesState
    ): RuleSyncEnvelope {
        return RuleSyncEnvelope(
            envelopeId = envelopeId,
            deviceId = state.deviceId,
            sequenceNumber = sequenceNumber,
            timestampMillis = timestampMillis,
            checksum = computeChecksum(state),
            state = state,
            ackedAtMillis = null
        )
    }

    fun validateState(state: ControlRulesState): String? {
        if (state.deviceId.isBlank()) return "deviceId inválido"
        if (state.dailyDeviceLimitMinutes < 0 || state.dailyDeviceLimitMinutes > 1440) {
            return "Limite diário fora do intervalo permitido (0..1440)"
        }
        state.appLimits.values.forEach { rule ->
            if (rule.dailyLimitMinutes < 0 || rule.dailyLimitMinutes > 1440) {
                return "Limite inválido para ${rule.appName}"
            }
            val window = rule.allowedWindow
            if (window != null && !window.isValid()) {
                return "Janela de horário inválida para ${rule.appName}"
            }
        }
        state.categoryLimits.values.forEach { rule ->
            if (rule.dailyLimitMinutes < 0 || rule.dailyLimitMinutes > 1440) {
                return "Limite inválido para categoria ${rule.category.displayName}"
            }
        }
        state.routines.forEach { routine ->
            if (TimeWindow.parseMinutes(routine.startTime) == null ||
                TimeWindow.parseMinutes(routine.endTime) == null
            ) {
                return "Horário inválido na rotina ${routine.name}"
            }
        }
        state.extraGrants.forEach { grant ->
            if (grant.minutes <= 0 || grant.expiresAtMillis < grant.grantedAtMillis) {
                return "Concessão de tempo extra inválida"
            }
        }
        return null
    }

    fun validateIncomingEnvelope(
        envelope: RuleSyncEnvelope,
        lastAcceptedSequence: Long,
        processedEnvelopeIds: Set<String>
    ): SyncValidationResult {
        if (envelope.envelopeId in processedEnvelopeIds) {
            return SyncValidationResult.Rejected("Pacote duplicado (${envelope.envelopeId})")
        }
        if (envelope.sequenceNumber <= lastAcceptedSequence) {
            return SyncValidationResult.Rejected(
                "Pacote fora de ordem (seq=${envelope.sequenceNumber} <= $lastAcceptedSequence)"
            )
        }
        val expectedChecksum = computeChecksum(envelope.state)
        if (envelope.checksum != expectedChecksum) {
            return SyncValidationResult.Rejected("Checksum inválido")
        }
        val stateError = validateState(envelope.state)
        if (stateError != null) {
            return SyncValidationResult.Rejected(stateError)
        }
        return SyncValidationResult.Accepted(envelope)
    }
}
