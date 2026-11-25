package com.dohguard.shared

import com.dohguard.shared.control.ClockTamperDetector
import com.dohguard.shared.control.RuleEvaluationEngine
import com.dohguard.shared.control.UsageSnapshot
import com.dohguard.shared.model.*
import com.dohguard.shared.security.PinHashUtils
import com.dohguard.shared.sync.RuleSyncValidator
import com.dohguard.shared.sync.SyncValidationResult
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ControlEngineAndSecurityTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(utc).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `1_dailyLimitBlocksWhenTotalUsageReachesLimit`() {
        val now = millisAt(2026, Calendar.NOVEMBER, 23, 15, 0) // Monday 15:00
        val state = ControlRulesState.defaultDemo(now).copy(
            dailyDeviceLimitMinutes = 120,
            routines = emptyList()
        )
        val decision = RuleEvaluationEngine.evaluateAppAccess(
            state = state,
            packageName = "com.google.android.youtube",
            appCategory = AppCategory.VIDEOS,
            totalDeviceUsedMinutes = 120,
            appUsedMinutes = 30,
            categoryUsedMinutes = 30,
            nowMillis = now,
            timeZone = utc
        )
        assertTrue(decision.isBlocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, decision.reason)
    }

    @Test
    fun `2_perAppLimitAndAllowedScheduleWindowEnforced`() {
        val mondayAfternoon = millisAt(2026, Calendar.NOVEMBER, 23, 16, 0)
        val appRule = AppLimitRule(
            packageName = "com.roblox.client",
            appName = "Roblox",
            dailyLimitMinutes = 45,
            allowedWindow = TimeWindow("14:00", "18:00"),
            daysOfWeek = Weekday.ALL_DAYS
        )
        val state = ControlRulesState(
            dailyDeviceLimitMinutes = 300,
            appLimits = mapOf("com.roblox.client" to appRule)
        )

        val blockedByLimit = RuleEvaluationEngine.evaluateAppAccess(
            state = state,
            packageName = "com.roblox.client",
            appCategory = AppCategory.GAMES,
            totalDeviceUsedMinutes = 60,
            appUsedMinutes = 45,
            categoryUsedMinutes = 45,
            nowMillis = mondayAfternoon,
            timeZone = utc
        )
        assertTrue(blockedByLimit.isBlocked)
        assertEquals(BlockReason.APP_LIMIT_REACHED, blockedByLimit.reason)

        val mondayNight = millisAt(2026, Calendar.NOVEMBER, 23, 20, 0)
        val blockedByWindow = RuleEvaluationEngine.evaluateAppAccess(
            state = state,
            packageName = "com.roblox.client",
            appCategory = AppCategory.GAMES,
            totalDeviceUsedMinutes = 20,
            appUsedMinutes = 10,
            categoryUsedMinutes = 10,
            nowMillis = mondayNight,
            timeZone = utc
        )
        assertTrue(blockedByWindow.isBlocked)
        assertEquals(BlockReason.APP_OUTSIDE_SCHEDULE, blockedByWindow.reason)
        assertEquals("14:00", blockedByWindow.unlockTimeFormatted)
    }

    @Test
    fun `3_categoryLimitBlocksAppsInSameCategory`() {
        val now = millisAt(2026, Calendar.NOVEMBER, 23, 15, 0)
        val state = ControlRulesState(
            dailyDeviceLimitMinutes = 300,
            categoryLimits = mapOf(
                AppCategory.GAMES to CategoryLimitRule(
                    category = AppCategory.GAMES,
                    dailyLimitMinutes = 60,
                    daysOfWeek = Weekday.ALL_DAYS
                )
            )
        )
        val decision = RuleEvaluationEngine.evaluateAppAccess(
            state = state,
            packageName = "com.mojang.minecraftpe",
            appCategory = AppCategory.GAMES,
            totalDeviceUsedMinutes = 80,
            appUsedMinutes = 20,
            categoryUsedMinutes = 60,
            nowMillis = now,
            timeZone = utc
        )
        assertTrue(decision.isBlocked)
        assertEquals(BlockReason.CATEGORY_LIMIT_REACHED, decision.reason)
    }

    @Test
    fun `4_dayOfWeekRoutineAppliesOnlyOnConfiguredDays`() {
        val studyRoutine = RoutineRule(
            id = "study",
            name = "Estudos",
            type = RoutineType.STUDY,
            startTime = "08:00",
            endTime = "12:00",
            daysOfWeek = Weekday.WEEKDAYS,
            allowedPackages = setOf("com.duolingo"),
            blockAllExceptAllowed = true
        )
        val state = ControlRulesState(
            dailyDeviceLimitMinutes = 300,
            routines = listOf(studyRoutine)
        )
        val mondayMorning = millisAt(2026, Calendar.NOVEMBER, 23, 9, 30) // Monday
        val saturdayMorning = millisAt(2026, Calendar.NOVEMBER, 28, 9, 30) // Saturday

        val mondayRoblox = RuleEvaluationEngine.evaluateAppAccess(
            state, "com.roblox.client", AppCategory.GAMES, 10, 0, 0, mondayMorning, utc
        )
        val mondayDuolingo = RuleEvaluationEngine.evaluateAppAccess(
            state, "com.duolingo", AppCategory.EDUCATION, 10, 0, 0, mondayMorning, utc
        )
        val saturdayRoblox = RuleEvaluationEngine.evaluateAppAccess(
            state, "com.roblox.client", AppCategory.GAMES, 10, 0, 0, saturdayMorning, utc
        )

        assertTrue(mondayRoblox.isBlocked)
        assertEquals(BlockReason.ROUTINE_ACTIVE, mondayRoblox.reason)
        assertFalse(mondayDuolingo.isBlocked)
        assertFalse(saturdayRoblox.isBlocked)
    }

    @Test
    fun `5_midnightCrossingRoutineEvaluatesAcrossDayRollover`() {
        // Friday-only bedtime 21:00 -> 07:00 (must block Saturday 02:00, not Friday 02:00)
        val fridayBedtime = RoutineRule(
            id = "bed-fri",
            name = "Dormir Sexta",
            type = RoutineType.BEDTIME,
            startTime = "21:00",
            endTime = "07:00",
            daysOfWeek = setOf(Weekday.FRI),
            blockAllExceptAllowed = true
        )
        val friday0200 = millisAt(2026, Calendar.NOVEMBER, 27, 2, 0) // Friday 02:00 (Thursday night)
        val friday2230 = millisAt(2026, Calendar.NOVEMBER, 27, 22, 30) // Friday 22:30
        val saturday0200 = millisAt(2026, Calendar.NOVEMBER, 28, 2, 0) // Saturday 02:00 (Friday night)
        val saturday0730 = millisAt(2026, Calendar.NOVEMBER, 28, 7, 30) // Saturday 07:30

        assertFalse(RuleEvaluationEngine.isRoutineActiveAt(fridayBedtime, friday0200, utc))
        assertTrue(RuleEvaluationEngine.isRoutineActiveAt(fridayBedtime, friday2230, utc))
        assertTrue(RuleEvaluationEngine.isRoutineActiveAt(fridayBedtime, saturday0200, utc))
        assertFalse(RuleEvaluationEngine.isRoutineActiveAt(fridayBedtime, saturday0730, utc))
    }

    @Test
    fun `6_extraTimeGrantExtendsDailyLimitAndExpires`() {
        val now = millisAt(2026, Calendar.NOVEMBER, 23, 15, 0)
        val grant = ExtraTimeGrant(
            id = "g1",
            targetPackage = null,
            minutes = 30,
            grantedAtMillis = now,
            expiresAtMillis = now + 30 * 60_000L
        )
        val state = ControlRulesState(
            dailyDeviceLimitMinutes = 120,
            extraGrants = listOf(grant)
        )

        // 140 min used < 120 + 30 = 150 while active
        val duringGrant = RuleEvaluationEngine.evaluateAppAccess(
            state, "com.google.android.youtube", AppCategory.VIDEOS,
            140, 20, 20, now + 10 * 60_000L, utc
        )
        assertFalse(duringGrant.isBlocked)

        // After expiration (now + 31 min), limit returns to 120 -> blocked
        val afterGrant = RuleEvaluationEngine.evaluateAppAccess(
            state, "com.google.android.youtube", AppCategory.VIDEOS,
            140, 20, 20, now + 31 * 60_000L, utc
        )
        assertTrue(afterGrant.isBlocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, afterGrant.reason)
    }

    @Test
    fun `7_temporaryAppUnlockBypassesBlockUntilExpiration`() {
        val now = millisAt(2026, Calendar.NOVEMBER, 23, 15, 0)
        val tempUnlock = ExtraTimeGrant(
            id = "g-app",
            targetPackage = "com.roblox.client",
            minutes = 15,
            grantedAtMillis = now,
            expiresAtMillis = now + 15 * 60_000L
        )
        val state = ControlRulesState(
            dailyDeviceLimitMinutes = 300,
            appLimits = mapOf(
                "com.roblox.client" to AppLimitRule(
                    packageName = "com.roblox.client",
                    appName = "Roblox",
                    dailyLimitMinutes = 30,
                    isBlocked = true
                )
            ),
            extraGrants = listOf(tempUnlock)
        )

        val unlocked = RuleEvaluationEngine.evaluateAppAccess(
            state, "com.roblox.client", AppCategory.GAMES, 50, 30, 30, now + 5 * 60_000L, utc
        )
        val expired = RuleEvaluationEngine.evaluateAppAccess(
            state, "com.roblox.client", AppCategory.GAMES, 50, 30, 30, now + 16 * 60_000L, utc
        )

        assertFalse(unlocked.isBlocked)
        assertTrue(expired.isBlocked)
        assertEquals(BlockReason.APP_BLOCKED, expired.reason)
    }

    @Test
    fun `8_clockTamperAndRebootPreserveAccumulatedUsage`() {
        val day = "2026-11-23"
        val t1 = millisAt(2026, Calendar.NOVEMBER, 23, 14, 0)
        val initial = UsageSnapshot(
            dayKey = day,
            totalMinutes = 110,
            lastWallClockMillis = t1,
            lastElapsedRealtimeMillis = 500_000L
        )

        // Reboot scenario: elapsedRealtime resets to 10_000L, observed drops to 0 -> must keep 110!
        val afterReboot = ClockTamperDetector.reconcileUsage(
            previous = initial,
            observedTotalMinutes = 0,
            currentDayKey = day,
            nowWallMillis = t1 + 60_000L,
            nowElapsedRealtimeMillis = 10_000L
        )
        assertEquals(110, afterReboot.totalMinutes)
        assertFalse(afterReboot.tamperDetected)

        // Clock rollback tamper: user rolls clock back 1 day -> must flag tamper and keep 110!
        val afterRollback = ClockTamperDetector.reconcileUsage(
            previous = afterReboot,
            observedTotalMinutes = 5,
            currentDayKey = "2026-11-22",
            nowWallMillis = t1 - 86_400_000L,
            nowElapsedRealtimeMillis = 40_000L
        )
        assertTrue(afterRollback.tamperDetected)
        assertEquals(110, afterRollback.totalMinutes)
        assertEquals(day, afterRollback.dayKey)
    }

    @Test
    fun `9_syncValidatorRejectsDuplicatesOutOfOrderAndBadChecksums`() {
        val state = ControlRulesState.defaultDemo(1_000_000L).copy(version = 5L)
        val env = RuleSyncValidator.createEnvelope("env-5", 5L, 1_000_000L, state)

        // Valid envelope
        val accepted = RuleSyncValidator.validateIncomingEnvelope(env, 4L, emptySet())
        assertTrue(accepted is SyncValidationResult.Accepted)

        // Duplicate envelope
        val duplicate = RuleSyncValidator.validateIncomingEnvelope(env, 4L, setOf("env-5"))
        assertTrue(duplicate is SyncValidationResult.Rejected)

        // Out-of-order envelope
        val outOfOrder = RuleSyncValidator.validateIncomingEnvelope(env, 5L, emptySet())
        assertTrue(outOfOrder is SyncValidationResult.Rejected)

        // Tampered checksum
        val tampered = env.copy(checksum = "deadbeef")
        val badChecksum = RuleSyncValidator.validateIncomingEnvelope(tampered, 4L, emptySet())
        assertTrue(badChecksum is SyncValidationResult.Rejected)
    }

    @Test
    fun `10_conflictingRulesRespectPriorityOrder`() {
        val now = millisAt(2026, Calendar.NOVEMBER, 23, 22, 0)
        val freeTime = RoutineRule(
            id = "free",
            name = "Tempo Livre",
            type = RoutineType.FREE_TIME,
            startTime = "21:00",
            endTime = "23:00",
            blockAllExceptAllowed = false
        )
        val bedtime = RoutineRule(
            id = "bed",
            name = "Dormir",
            type = RoutineType.BEDTIME,
            startTime = "21:00",
            endTime = "07:00",
            blockAllExceptAllowed = true
        )

        // When device is paused, pause wins over everything
        val pausedState = ControlRulesState(isDevicePaused = true, routines = listOf(freeTime))
        val pausedDecision = RuleEvaluationEngine.evaluateAppAccess(
            pausedState, "com.duolingo", AppCategory.EDUCATION, 10, 0, 0, now, utc
        )
        assertEquals(BlockReason.DEVICE_PAUSED, pausedDecision.reason)

        // When FREE_TIME and BEDTIME overlap without pause, FREE_TIME allows non-blocked apps
        val freeState = ControlRulesState(isDevicePaused = false, routines = listOf(bedtime, freeTime))
        val freeDecision = RuleEvaluationEngine.evaluateAppAccess(
            freeState, "com.duolingo", AppCategory.EDUCATION, 10, 0, 0, now, utc
        )
        assertFalse(freeDecision.isBlocked)
    }

    @Test
    fun `11_pinHashingIsSaltedAndNeverPlaintext`() {
        val salt1 = PinHashUtils.generateSalt()
        val salt2 = PinHashUtils.generateSalt()
        val hash1 = PinHashUtils.hashPin("1234", salt1)
        val hash2 = PinHashUtils.hashPin("1234", salt2)

        assertNotEquals("1234", hash1)
        assertNotEquals(hash1, hash2)
        assertTrue(PinHashUtils.verifyPin("1234", hash1, salt1))
        assertFalse(PinHashUtils.verifyPin("9999", hash1, salt1))
    }
}
