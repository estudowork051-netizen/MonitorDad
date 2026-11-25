package com.dohguard.shared.control

import com.dohguard.shared.model.*
import java.util.Calendar
import java.util.TimeZone

/**
 * Motor puro de avaliação de regras parentais (limites diários, por app, por categoria,
 * janelas de horário, rotinas que cruzam meia-noite e desbloqueios temporários).
 * Sem dependências do Android Framework para permitir testes unitários determinísticos na JVM.
 */
object RuleEvaluationEngine {

    val DEFAULT_EXEMPT_PACKAGES = setOf(
        "com.dohguard.kids",
        "com.dohguard.parent",
        "com.android.systemui",
        "com.android.dialer",
        "com.google.android.dialer"
    )

    fun evaluateAppAccess(
        state: ControlRulesState,
        packageName: String,
        appCategory: AppCategory,
        totalDeviceUsedMinutes: Int,
        appUsedMinutes: Int,
        categoryUsedMinutes: Int,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        exemptPackages: Set<String> = DEFAULT_EXEMPT_PACKAGES
    ): BlockDecision {
        if (packageName in exemptPackages) {
            return BlockDecision.ALLOWED
        }

        // 1. Pausa imediata do dispositivo tem prioridade máxima
        if (state.isDevicePaused) {
            return BlockDecision(
                isBlocked = true,
                reason = BlockReason.DEVICE_PAUSED,
                title = "Dispositivo Pausado",
                message = "O responsável pausou o uso do tablet temporariamente.",
                unlockTimeFormatted = "Quando o responsável liberar"
            )
        }

        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val today = Weekday.fromCalendarDay(cal.get(Calendar.DAY_OF_WEEK))
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // Desbloqueio temporário específico para este aplicativo
        val activeAppUnlock = state.extraGrants.firstOrNull { grant ->
            grant.targetPackage == packageName && grant.isActiveAt(nowMillis)
        }

        // Tempo extra concedido para o dispositivo hoje
        val activeDeviceExtraMinutes = state.extraGrants
            .filter { it.targetPackage == null && it.isActiveAt(nowMillis) }
            .sumOf { it.minutes }

        // 2. Verificar rotinas ativas (suportando virada de meia-noite)
        val activeRoutines = state.routines.filter { routine ->
            routine.enabled && isRoutineActiveAt(routine, today, minuteOfDay)
        }

        // Se existir uma rotina FREE_TIME ativa, ela libera o acesso (a menos que haja bloqueio explícito do app)
        val hasFreeTimeRoutine = activeRoutines.any { it.type == RoutineType.FREE_TIME }

        // Priorizar rotinas restritivas (BEDTIME > STUDY > REST > CUSTOM)
        val blockingRoutine = activeRoutines
            .filter { it.type != RoutineType.FREE_TIME }
            .sortedBy { routinePriority(it.type) }
            .firstOrNull { routine ->
                if (packageName in routine.allowedPackages) {
                    false
                } else if (packageName in routine.blockedPackages) {
                    true
                } else {
                    routine.blockAllExceptAllowed
                }
            }

        if (blockingRoutine != null && activeAppUnlock == null && !hasFreeTimeRoutine) {
            return BlockDecision(
                isBlocked = true,
                reason = BlockReason.ROUTINE_ACTIVE,
                title = blockingRoutine.name,
                message = "Este aplicativo está pausado durante a rotina \"${blockingRoutine.name}\".",
                unlockTimeFormatted = blockingRoutine.endTime,
                activeRoutineName = blockingRoutine.name
            )
        }

        val appRule = state.appLimits[packageName]

        // 3. Bloqueio manual permanente do app (respeita desbloqueio temporário)
        if (appRule != null && appRule.isBlocked && activeAppUnlock == null) {
            return BlockDecision(
                isBlocked = true,
                reason = BlockReason.APP_BLOCKED,
                title = "Aplicativo Bloqueado",
                message = "${appRule.appName} foi bloqueado pelo responsável.",
                unlockTimeFormatted = "Até liberação do responsável"
            )
        }

        // 4. Limite diário total do dispositivo
        val effectiveDailyLimit = state.dailyDeviceLimitMinutes + activeDeviceExtraMinutes
        if (today in state.dailyLimitDays && state.dailyDeviceLimitMinutes > 0 &&
            totalDeviceUsedMinutes >= effectiveDailyLimit && activeAppUnlock == null
        ) {
            return BlockDecision(
                isBlocked = true,
                reason = BlockReason.DAILY_LIMIT_REACHED,
                title = "Tempo Diário Encerrado",
                message = "Você atingiu o limite diário de ${formatMinutes(effectiveDailyLimit)} de uso do tablet.",
                unlockTimeFormatted = "00:00 (Amanhã)"
            )
        }

        // 5. Limite por categoria
        val categoryRule = state.categoryLimits[appCategory]
        if (categoryRule != null && categoryRule.enabled && today in categoryRule.daysOfWeek &&
            categoryRule.dailyLimitMinutes > 0 && categoryUsedMinutes >= categoryRule.dailyLimitMinutes &&
            activeAppUnlock == null
        ) {
            return BlockDecision(
                isBlocked = true,
                reason = BlockReason.CATEGORY_LIMIT_REACHED,
                title = "Limite da Categoria Atingido",
                message = "O tempo diário para ${appCategory.displayName} (${formatMinutes(categoryRule.dailyLimitMinutes)}) terminou.",
                unlockTimeFormatted = "00:00 (Amanhã)"
            )
        }

        // 6. Limite individual por aplicativo e janela permitida do app
        if (appRule != null && appRule.enabled && today in appRule.daysOfWeek && activeAppUnlock == null) {
            if (appRule.dailyLimitMinutes > 0 && appUsedMinutes >= appRule.dailyLimitMinutes) {
                return BlockDecision(
                    isBlocked = true,
                    reason = BlockReason.APP_LIMIT_REACHED,
                    title = "Limite de ${appRule.appName} Esgotado",
                    message = "Você já utilizou ${formatMinutes(appUsedMinutes)} de ${appRule.appName} hoje.",
                    unlockTimeFormatted = "00:00 (Amanhã)"
                )
            }

            val window = appRule.allowedWindow
            if (window != null && !isTimeInWindow(minuteOfDay, window.startTime, window.endTime)) {
                return BlockDecision(
                    isBlocked = true,
                    reason = BlockReason.APP_OUTSIDE_SCHEDULE,
                    title = "Fora do Horário Permitido",
                    message = "${appRule.appName} só pode ser usado entre ${window.startTime} e ${window.endTime}.",
                    unlockTimeFormatted = window.startTime
                )
            }
        }

        return BlockDecision.ALLOWED
    }

    fun isRoutineActiveAt(
        routine: RoutineRule,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (!routine.enabled) return false
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val today = Weekday.fromCalendarDay(cal.get(Calendar.DAY_OF_WEEK))
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return isRoutineActiveAt(routine, today, minuteOfDay)
    }

    fun isRoutineActiveAt(
        routine: RoutineRule,
        today: Weekday,
        minuteOfDay: Int
    ): Boolean {
        if (!routine.enabled || routine.daysOfWeek.isEmpty()) return false
        val startMin = TimeWindow.parseMinutes(routine.startTime) ?: return false
        val endMin = TimeWindow.parseMinutes(routine.endTime) ?: return false

        return if (startMin == endMin) {
            // 24h window on configured days
            today in routine.daysOfWeek
        } else if (startMin < endMin) {
            // Same-day schedule (e.g. 07:30 -> 12:00)
            today in routine.daysOfWeek && minuteOfDay in startMin until endMin
        } else {
            // Midnight-crossing schedule (e.g. 21:00 -> 07:00)
            val afterStartToday = today in routine.daysOfWeek && minuteOfDay >= startMin
            val beforeEndFromYesterday = today.previousDay() in routine.daysOfWeek && minuteOfDay < endMin
            afterStartToday || beforeEndFromYesterday
        }
    }

    fun isTimeInWindow(minuteOfDay: Int, startTime: String, endTime: String): Boolean {
        val startMin = TimeWindow.parseMinutes(startTime) ?: return true
        val endMin = TimeWindow.parseMinutes(endTime) ?: return true
        return if (startMin == endMin) {
            true
        } else if (startMin < endMin) {
            minuteOfDay in startMin until endMin
        } else {
            minuteOfDay >= startMin || minuteOfDay < endMin
        }
    }

    private fun routinePriority(type: RoutineType): Int = when (type) {
        RoutineType.BEDTIME -> 0
        RoutineType.STUDY -> 1
        RoutineType.REST -> 2
        RoutineType.CUSTOM -> 3
        RoutineType.FREE_TIME -> 4
    }

    fun formatMinutes(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m} min"
        }
    }
}
