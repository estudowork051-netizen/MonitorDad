package com.dohguard.shared

import com.dohguard.shared.control.ClockTamperDetector
import com.dohguard.shared.control.RuleEvaluationEngine
import com.dohguard.shared.control.UsageSnapshot
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.BlockReason
import com.dohguard.shared.model.ControlRulesState
import com.dohguard.shared.remote.*
import com.dohguard.shared.security.PinHashUtils
import org.junit.Assert.*
import org.junit.Test

class FinalAuditAndSecurityTest {

    @Test
    fun pinHashUtilsVerifiesCorrectPinAndRejectsInvalidOnes() {
        val salt = PinHashUtils.generateSalt()
        val hash = PinHashUtils.hashPin("4829", salt)

        assertTrue(PinHashUtils.verifyPin("4829", hash, salt))
        assertFalse(PinHashUtils.verifyPin("1234", hash, salt))
        assertFalse(PinHashUtils.verifyPin("", hash, salt))
        assertFalse(PinHashUtils.verifyPin("4829", "", salt))
    }

    @Test
    fun clockTamperDetectorPreservesAccumulatedTimeOnBackwardJump() {
        val prev = UsageSnapshot(
            dayKey = "2026-11-23",
            totalMinutes = 120,
            lastWallClockMillis = 1_000_000_000L,
            lastElapsedRealtimeMillis = 500_000L,
            tamperDetected = false
        )

        val result = ClockTamperDetector.reconcileUsage(
            previous = prev,
            observedTotalMinutes = 15,
            currentDayKey = "2026-11-22",
            nowWallMillis = 1_000_000_000L - 3_600_000L,
            nowElapsedRealtimeMillis = 560_000L
        )

        assertTrue(result.tamperDetected)
        assertEquals(120, result.totalMinutes)
    }

    @Test
    fun remoteSecurityRedactsTokensSecretsPinsAndBearerHeaders() {
        val raw = "Erro de auth: token=abc123secret secret:xyz999 pin=1234 Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"
        val redacted = RemoteSecurity.redactSensitive(raw)

        assertFalse(redacted.contains("abc123secret"))
        assertFalse(redacted.contains("xyz999"))
        assertFalse(redacted.contains("1234"))
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(redacted.contains("[REDACTED]"))

        val extended = """
            password=minhaSenhaSuperSecreta senha:"segredo com espaco" pin='9876'
            Authorization: Basic dXNlcjpzZWNyZXQ= api_key=hod_live_key_998877
            {"sharedSecret": "chave-hmac-confidencial", "credential": "cred-dispositivo-42"}
            jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U
        """.trimIndent()
        val redactedExtended = RemoteSecurity.redactSensitive(extended)

        assertFalse(redactedExtended.contains("minhaSenhaSuperSecreta"))
        assertFalse(redactedExtended.contains("segredo com espaco"))
        assertFalse(redactedExtended.contains("9876"))
        assertFalse(redactedExtended.contains("dXNlcjpzZWNyZXQ="))
        assertFalse(redactedExtended.contains("hod_live_key_998877"))
        assertFalse(redactedExtended.contains("chave-hmac-confidencial"))
        assertFalse(redactedExtended.contains("cred-dispositivo-42"))
        assertFalse(redactedExtended.contains("dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"))
        assertTrue(redactedExtended.contains("[REDACTED]"))
    }

    @Test
    fun remoteSecurityRejectsUnauthorizedDeviceParentSignatureExpiredAndReplay() {
        val creds = PairedDeviceCredentials(
            deviceId = "tablet-lucas-01",
            parentId = "parent-hod-01",
            deviceName = "Tablet do Lucas",
            relayBaseUrl = "https://relay.monitordad.exemplo",
            sharedSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            pairedAtMillis = 1_000L,
            isPaired = true
        )

        val now = 100_000L
        val base = RemoteCommand(
            commandId = "cmd-1",
            targetDeviceId = creds.deviceId,
            issuedByParentId = creds.parentId,
            type = CommandType.PAUSE_DEVICE,
            createdAtMillis = now,
            expiresAtMillis = now + 60_000L,
            sequenceNumber = 1L,
            nonce = "nonce-1"
        )
        val valid = base.copy(signature = RemoteSecurity.signCommand(base, creds.sharedSecret))

        assertEquals(
            CommandSecurityResult.Authorized,
            RemoteSecurity.validateIncomingCommand(valid, creds, emptySet(), emptySet(), now)
        )

        val wrongDevice = valid.copy(targetDeviceId = "other-tablet")
        assertTrue(
            RemoteSecurity.validateIncomingCommand(wrongDevice, creds, emptySet(), emptySet(), now)
                is CommandSecurityResult.Rejected
        )

        val wrongParent = valid.copy(issuedByParentId = "intruder-parent")
        assertTrue(
            RemoteSecurity.validateIncomingCommand(wrongParent, creds, emptySet(), emptySet(), now)
                is CommandSecurityResult.Rejected
        )

        val tamperedSig = valid.copy(signature = "0000")
        assertTrue(
            RemoteSecurity.validateIncomingCommand(tamperedSig, creds, emptySet(), emptySet(), now)
                is CommandSecurityResult.Rejected
        )

        val expiredRes = RemoteSecurity.validateIncomingCommand(
            valid, creds, emptySet(), emptySet(), now + 120_000L
        )
        assertTrue(expiredRes is CommandSecurityResult.Rejected && expiredRes.isExpired)

        val replayRes = RemoteSecurity.validateIncomingCommand(
            valid, creds, setOf("nonce-1"), emptySet(), now
        )
        assertTrue(replayRes is CommandSecurityResult.Rejected && replayRes.isDuplicate)
    }

    @Test
    fun notificationDeduplicatorHandlesAllSecurityAndRuleEvents() {
        val dedup = NotificationEventDeduplicator()
        val now = 500_000L

        val e1 = ParentNotificationEvent(
            eventId = "ev-rules-1",
            type = NotificationEventType.RULES_CHANGED,
            title = NotificationEventType.RULES_CHANGED.title,
            message = "Limite diário atualizado",
            deviceId = "tablet-lucas-01",
            timestampMillis = now,
            dedupKey = "rules-1"
        )
        val e1Dup = e1.copy(eventId = "ev-rules-1-dup", timestampMillis = now + 1_000L)
        val e2 = ParentNotificationEvent(
            eventId = "ev-daily-1",
            type = NotificationEventType.DAILY_LIMIT_REACHED,
            title = NotificationEventType.DAILY_LIMIT_REACHED.title,
            message = "Limite diário atingido",
            deviceId = "tablet-lucas-01",
            timestampMillis = now + 2_000L,
            dedupKey = "daily-1"
        )
        val e3 = ParentNotificationEvent(
            eventId = "ev-block-yt",
            type = NotificationEventType.APP_BLOCKED,
            title = NotificationEventType.APP_BLOCKED.title,
            message = "YouTube bloqueado",
            deviceId = "tablet-lucas-01",
            timestampMillis = now + 3_000L,
            dedupKey = "block-yt"
        )
        val e4 = ParentNotificationEvent(
            eventId = "ev-extra-1",
            type = NotificationEventType.EXTRA_TIME_REQUESTED,
            title = NotificationEventType.EXTRA_TIME_REQUESTED.title,
            message = "Lucas pediu +15 min",
            deviceId = "tablet-lucas-01",
            timestampMillis = now + 4_000L,
            dedupKey = "extra-1"
        )

        assertTrue(dedup.recordIfNew(e1))
        assertFalse(dedup.recordIfNew(e1Dup))
        assertTrue(dedup.recordIfNew(e2))
        assertTrue(dedup.recordIfNew(e3))
        assertTrue(dedup.recordIfNew(e4))
        assertEquals(4, dedup.getEvents().size)
    }

    @Test
    fun ruleEvaluationEngineProtectsExemptPackagesAndBlocksPausedOrOverLimitApps() {
        val pausedState = ControlRulesState(
            dailyDeviceLimitMinutes = 120,
            isDevicePaused = true
        )

        val kidsDecision = RuleEvaluationEngine.evaluateAppAccess(
            state = pausedState,
            packageName = "com.dohguard.kids",
            appCategory = AppCategory.EDUCATION,
            totalDeviceUsedMinutes = 200,
            appUsedMinutes = 50,
            categoryUsedMinutes = 50,
            nowMillis = 1_000_000L
        )
        assertFalse(kidsDecision.isBlocked)

        val youtubePaused = RuleEvaluationEngine.evaluateAppAccess(
            state = pausedState,
            packageName = "com.google.android.youtube",
            appCategory = AppCategory.VIDEOS,
            totalDeviceUsedMinutes = 10,
            appUsedMinutes = 5,
            categoryUsedMinutes = 5,
            nowMillis = 1_000_000L
        )
        assertTrue(youtubePaused.isBlocked)
        assertEquals(BlockReason.DEVICE_PAUSED, youtubePaused.reason)

        val limitState = pausedState.copy(isDevicePaused = false)
        val youtubeOverDaily = RuleEvaluationEngine.evaluateAppAccess(
            state = limitState,
            packageName = "com.google.android.youtube",
            appCategory = AppCategory.VIDEOS,
            totalDeviceUsedMinutes = 120,
            appUsedMinutes = 10,
            categoryUsedMinutes = 10,
            nowMillis = 1_000_000L
        )
        assertTrue(youtubeOverDaily.isBlocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, youtubeOverDaily.reason)
    }
}
