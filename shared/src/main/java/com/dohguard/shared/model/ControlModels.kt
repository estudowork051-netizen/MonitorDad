package com.dohguard.shared.model

import java.util.Calendar

enum class Weekday(val calendarDay: Int, val shortLabel: String) {
    MON(Calendar.MONDAY, "Seg"),
    TUE(Calendar.TUESDAY, "Ter"),
    WED(Calendar.WEDNESDAY, "Qua"),
    THU(Calendar.THURSDAY, "Qui"),
    FRI(Calendar.FRIDAY, "Sex"),
    SAT(Calendar.SATURDAY, "Sáb"),
    SUN(Calendar.SUNDAY, "Dom");

    fun previousDay(): Weekday {
        val values = entries
        val idx = (ordinal - 1 + values.size) % values.size
        return values[idx]
    }

    companion object {
        val ALL_DAYS: Set<Weekday> = entries.toSet()
        val WEEKDAYS: Set<Weekday> = setOf(MON, TUE, WED, THU, FRI)
        val WEEKENDS: Set<Weekday> = setOf(SAT, SUN)

        fun fromCalendarDay(day: Int): Weekday {
            return entries.firstOrNull { it.calendarDay == day } ?: MON
        }
    }
}

enum class RoutineType(val displayName: String) {
    BEDTIME("Horário de Dormir"),
    STUDY("Horário de Estudos"),
    REST("Horário de Descanso"),
    FREE_TIME("Horário Livre"),
    CUSTOM("Rotina Personalizada")
}

data class TimeWindow(
    val startTime: String,
    val endTime: String
) {
    fun isValid(): Boolean = parseMinutes(startTime) != null && parseMinutes(endTime) != null

    companion object {
        fun parseMinutes(hhmm: String): Int? {
            val parts = hhmm.trim().split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
    }
}

data class AppLimitRule(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val isBlocked: Boolean = false,
    val allowedWindow: TimeWindow? = null,
    val daysOfWeek: Set<Weekday> = Weekday.ALL_DAYS,
    val enabled: Boolean = true
)

data class CategoryLimitRule(
    val category: AppCategory,
    val dailyLimitMinutes: Int,
    val daysOfWeek: Set<Weekday> = Weekday.ALL_DAYS,
    val enabled: Boolean = true
)

data class RoutineRule(
    val id: String,
    val name: String,
    val type: RoutineType,
    val startTime: String,
    val endTime: String,
    val daysOfWeek: Set<Weekday> = Weekday.ALL_DAYS,
    val allowedPackages: Set<String> = emptySet(),
    val blockedPackages: Set<String> = emptySet(),
    val blockAllExceptAllowed: Boolean = true,
    val enabled: Boolean = true
)

data class ExtraTimeGrant(
    val id: String,
    val targetPackage: String? = null,
    val targetAppName: String? = null,
    val minutes: Int,
    val grantedAtMillis: Long,
    val expiresAtMillis: Long,
    val grantedBy: String = "Responsável",
    val reason: String = "Tempo adicional"
) {
    fun isActiveAt(nowMillis: Long): Boolean = nowMillis in grantedAtMillis until expiresAtMillis
}

enum class BlockReason {
    NONE,
    DEVICE_PAUSED,
    DAILY_LIMIT_REACHED,
    APP_BLOCKED,
    APP_LIMIT_REACHED,
    APP_OUTSIDE_SCHEDULE,
    CATEGORY_LIMIT_REACHED,
    ROUTINE_ACTIVE
}

data class BlockDecision(
    val isBlocked: Boolean,
    val reason: BlockReason = BlockReason.NONE,
    val title: String = "",
    val message: String = "",
    val unlockTimeFormatted: String? = null,
    val activeRoutineName: String? = null,
    val allowPinBypass: Boolean = true
) {
    companion object {
        val ALLOWED = BlockDecision(isBlocked = false, reason = BlockReason.NONE)
    }
}

data class ControlRulesState(
    val deviceId: String = "tablet-lucas-01",
    val version: Long = 1L,
    val updatedAtMillis: Long = 0L,
    val dailyDeviceLimitMinutes: Int = 210,
    val dailyLimitDays: Set<Weekday> = Weekday.ALL_DAYS,
    val isDevicePaused: Boolean = false,
    val appLimits: Map<String, AppLimitRule> = emptyMap(),
    val categoryLimits: Map<AppCategory, CategoryLimitRule> = emptyMap(),
    val routines: List<RoutineRule> = emptyList(),
    val extraGrants: List<ExtraTimeGrant> = emptyList(),
    val grantHistory: List<ExtraTimeGrant> = emptyList()
) {
    companion object {
        fun defaultDemo(nowMillis: Long = System.currentTimeMillis()): ControlRulesState {
            val defaultApps = AppUsageInfo.DEMO_APPS.associate { app ->
                app.packageName to AppLimitRule(
                    packageName = app.packageName,
                    appName = app.appName,
                    dailyLimitMinutes = app.dailyLimitMinutes,
                    isBlocked = app.isBlocked,
                    daysOfWeek = Weekday.ALL_DAYS,
                    enabled = true
                )
            }
            val defaultCategories = mapOf(
                AppCategory.GAMES to CategoryLimitRule(
                    category = AppCategory.GAMES,
                    dailyLimitMinutes = 90,
                    daysOfWeek = Weekday.ALL_DAYS,
                    enabled = true
                ),
                AppCategory.VIDEOS to CategoryLimitRule(
                    category = AppCategory.VIDEOS,
                    dailyLimitMinutes = 90,
                    daysOfWeek = Weekday.ALL_DAYS,
                    enabled = true
                )
            )
            val defaultRoutines = listOf(
                RoutineRule(
                    id = "routine-bedtime",
                    name = "Horário de Dormir",
                    type = RoutineType.BEDTIME,
                    startTime = "21:00",
                    endTime = "07:00",
                    daysOfWeek = Weekday.ALL_DAYS,
                    allowedPackages = emptySet(),
                    blockAllExceptAllowed = true,
                    enabled = true
                ),
                RoutineRule(
                    id = "routine-study",
                    name = "Horário de Estudos",
                    type = RoutineType.STUDY,
                    startTime = "07:30",
                    endTime = "12:00",
                    daysOfWeek = Weekday.WEEKDAYS,
                    allowedPackages = setOf("com.duolingo", "org.khanacademy.android"),
                    blockAllExceptAllowed = true,
                    enabled = true
                ),
                RoutineRule(
                    id = "routine-rest",
                    name = "Horário de Descanso",
                    type = RoutineType.REST,
                    startTime = "13:00",
                    endTime = "14:00",
                    daysOfWeek = Weekday.ALL_DAYS,
                    allowedPackages = emptySet(),
                    blockAllExceptAllowed = true,
                    enabled = false
                )
            )
            return ControlRulesState(
                deviceId = "tablet-lucas-01",
                version = 1L,
                updatedAtMillis = nowMillis,
                dailyDeviceLimitMinutes = 210,
                dailyLimitDays = Weekday.ALL_DAYS,
                isDevicePaused = false,
                appLimits = defaultApps,
                categoryLimits = defaultCategories,
                routines = defaultRoutines,
                extraGrants = emptyList(),
                grantHistory = emptyList()
            )
        }
    }
}

data class RuleSyncEnvelope(
    val envelopeId: String,
    val deviceId: String,
    val sequenceNumber: Long,
    val timestampMillis: Long,
    val checksum: String,
    val state: ControlRulesState,
    val ackedAtMillis: Long? = null
)
