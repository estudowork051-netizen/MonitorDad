package com.dohguard.kids.remote

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.dohguard.kids.monitoring.UsageStatsCollector
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.CategoryLimitRule
import com.dohguard.shared.model.RoutineRule
import com.dohguard.shared.model.RoutineType
import com.dohguard.shared.model.Weekday
import com.dohguard.shared.remote.CommandType
import com.dohguard.shared.remote.RemoteCommand
import com.dohguard.shared.sync.ControlRulesRepository

/**
 * Executa comandos remotos já validados pela camada de segurança no tablet da criança.
 * Retorna Pair(sucesso, mensagem) para envio do CommandAck real ao MonitorDad Parent.
 */
object KidsCommandExecutor {

    fun execute(context: Context, command: RemoteCommand): Pair<Boolean, String> {
        return when (command.type) {
            CommandType.PAUSE_DEVICE -> {
                ControlRulesRepository.setDevicePaused(true)
                true to "Tablet pausado remotamente"
            }

            CommandType.RESUME_DEVICE -> {
                ControlRulesRepository.setDevicePaused(false)
                true to "Tablet liberado remotamente"
            }

            CommandType.ADD_EXTRA_TIME -> {
                val minutes = command.payload["minutes"]?.toIntOrNull()?.coerceIn(1, 240) ?: 15
                val targetPkg = command.payload["packageName"]?.takeIf { it.isNotBlank() }
                val targetAppName = command.payload["appName"]?.takeIf { it.isNotBlank() }
                val reason = command.payload["reason"] ?: "Tempo extra remoto"
                val grant = ControlRulesRepository.grantExtraTime(
                    minutes = minutes,
                    targetPackage = targetPkg,
                    targetAppName = targetAppName,
                    reason = reason
                )
                if (grant != null) {
                    true to "+$minutes min concedidos"
                } else {
                    false to "Quantidade de minutos inválida"
                }
            }

            CommandType.BLOCK_APP -> {
                val pkg = command.payload["packageName"]?.takeIf { it.isNotBlank() }
                    ?: return false to "Pacote do aplicativo não informado"
                val appName = command.payload["appName"]?.takeIf { it.isNotBlank() } ?: pkg
                ControlRulesRepository.setAppBlocked(pkg, appName, blocked = true)
                true to "$appName bloqueado"
            }

            CommandType.UNBLOCK_APP -> {
                val pkg = command.payload["packageName"]?.takeIf { it.isNotBlank() }
                    ?: return false to "Pacote do aplicativo não informado"
                val appName = command.payload["appName"]?.takeIf { it.isNotBlank() } ?: pkg
                ControlRulesRepository.setAppBlocked(pkg, appName, blocked = false)
                true to "$appName desbloqueado"
            }

            CommandType.UPDATE_LIMITS -> {
                val dailyLimit = command.payload["dailyDeviceLimitMinutes"]?.toIntOrNull()
                val pkg = command.payload["packageName"]?.takeIf { it.isNotBlank() }
                val appLimit = command.payload["appLimitMinutes"]?.toIntOrNull()
                val appName = command.payload["appName"]?.takeIf { it.isNotBlank() } ?: pkg
                val category = command.payload["category"]?.let { raw ->
                    runCatching { AppCategory.valueOf(raw.trim()) }.getOrNull()
                }
                val categoryLimit = command.payload["categoryLimitMinutes"]?.toIntOrNull()

                var updated = false
                if (dailyLimit != null && dailyLimit > 0) {
                    val currentDays = ControlRulesRepository.rulesState.value.dailyLimitDays
                    ControlRulesRepository.updateDailyLimit(dailyLimit, currentDays)
                    updated = true
                }
                if (pkg != null && appLimit != null && appName != null && appLimit >= 0) {
                    ControlRulesRepository.updateAppLimit(
                        packageName = pkg,
                        appName = appName,
                        dailyLimitMinutes = appLimit
                    )
                    updated = true
                }
                if (category != null && categoryLimit != null && categoryLimit > 0) {
                    val existing = ControlRulesRepository.rulesState.value.categoryLimits[category]
                    ControlRulesRepository.upsertCategoryLimit(
                        CategoryLimitRule(
                            category = category,
                            dailyLimitMinutes = categoryLimit,
                            daysOfWeek = existing?.daysOfWeek ?: Weekday.ALL_DAYS,
                            enabled = true
                        )
                    )
                    updated = true
                }
                if (!updated) {
                    false to "Nenhum limite válido informado"
                } else {
                    true to "Limites atualizados"
                }
            }

            CommandType.UPSERT_ROUTINE -> {
                val id = command.payload["routineId"]?.takeIf { it.isNotBlank() }
                    ?: return false to "ID da rotina ausente"
                val name = command.payload["name"]?.takeIf { it.isNotBlank() } ?: "Rotina"
                val type = runCatching {
                    RoutineType.valueOf(command.payload["routineType"] ?: "CUSTOM")
                }.getOrDefault(RoutineType.CUSTOM)
                val start = command.payload["startTime"] ?: "21:00"
                val end = command.payload["endTime"] ?: "07:00"
                val enabled = command.payload["enabled"]?.toBooleanStrictOrNull() ?: true
                val days = command.payload["days"]
                    ?.split(",")
                    ?.mapNotNull { runCatching { Weekday.valueOf(it.trim()) }.getOrNull() }
                    ?.toSet()
                    ?.ifEmpty { Weekday.ALL_DAYS }
                    ?: Weekday.ALL_DAYS

                ControlRulesRepository.upsertRoutine(
                    RoutineRule(
                        id = id,
                        name = name,
                        type = type,
                        startTime = start,
                        endTime = end,
                        daysOfWeek = days,
                        enabled = enabled
                    )
                )
                true to "Rotina '$name' atualizada"
            }

            CommandType.DELETE_ROUTINE -> {
                val id = command.payload["routineId"]?.takeIf { it.isNotBlank() }
                    ?: return false to "ID da rotina ausente"
                ControlRulesRepository.deleteRoutine(id)
                true to "Rotina removida"
            }

            CommandType.REFRESH_USAGE -> {
                val telemetry = UsageStatsCollector.refreshTelemetryIfPermitted(context)
                if (telemetry == null) {
                    false to "Permissão PACKAGE_USAGE_STATS não concedida no tablet"
                } else {
                    true to "Telemetria de uso atualizada (${telemetry.apps.size} apps)"
                }
            }

            CommandType.PLAY_SOUND -> {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 90)
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching { toneGen.release() }
                    }, 1600L)
                    true to "Sinal sonoro emitido no tablet"
                } catch (e: Exception) {
                    false to "Não foi possível emitir som: ${e.message ?: "áudio indisponível"}"
                }
            }
        }
    }
}
