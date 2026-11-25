package com.dohguard.shared.control

import android.content.Context
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class UsageSnapshot(
    val dayKey: String,
    val totalMinutes: Int,
    val lastWallClockMillis: Long,
    val lastElapsedRealtimeMillis: Long,
    val tamperDetected: Boolean = false
)

/**
 * Lógica pura de proteção contra manipulação simples de relógio e dupla contagem.
 */
object ClockTamperDetector {

    private const val BACKWARD_JUMP_THRESHOLD_MS = 120_000L // 2 minutos
    private const val DRIFT_TOLERANCE_MS = 300_000L // 5 minutos

    fun reconcileUsage(
        previous: UsageSnapshot?,
        observedTotalMinutes: Int,
        currentDayKey: String,
        nowWallMillis: Long,
        nowElapsedRealtimeMillis: Long
    ): UsageSnapshot {
        if (previous == null) {
            return UsageSnapshot(
                dayKey = currentDayKey,
                totalMinutes = observedTotalMinutes.coerceAtLeast(0),
                lastWallClockMillis = nowWallMillis,
                lastElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                tamperDetected = false
            )
        }

        val wallDelta = nowWallMillis - previous.lastWallClockMillis
        val elapsedDelta = nowElapsedRealtimeMillis - previous.lastElapsedRealtimeMillis
        val rebooted = elapsedDelta < 0L

        val backwardClockJump = wallDelta < -BACKWARD_JUMP_THRESHOLD_MS
        val forwardSkewWithoutElapsed = !rebooted && elapsedDelta >= 0L &&
            abs(wallDelta - elapsedDelta) > DRIFT_TOLERANCE_MS &&
            wallDelta > 60_000L

        val isTamper = backwardClockJump || forwardSkewWithoutElapsed

        // Se houver tentativa de voltar o relógio, preserva o maior tempo acumulado (não reseta o limite!)
        if (backwardClockJump) {
            return previous.copy(
                totalMinutes = maxOf(previous.totalMinutes, observedTotalMinutes),
                lastElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                tamperDetected = true
            )
        }

        // Virada legítima de dia: apenas se o dia mudou para frente e não houve salto regressivo
        if (currentDayKey != previous.dayKey && !isTamper) {
            return UsageSnapshot(
                dayKey = currentDayKey,
                totalMinutes = observedTotalMinutes.coerceAtLeast(0),
                lastWallClockMillis = nowWallMillis,
                lastElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                tamperDetected = false
            )
        }

        // Mesmo dia (ou suspeita de avanço artificial de data): evita dupla contagem e nunca reduz uso acumulado
        val safeMinutes = maxOf(previous.totalMinutes, observedTotalMinutes)
        return UsageSnapshot(
            dayKey = previous.dayKey,
            totalMinutes = safeMinutes,
            lastWallClockMillis = maxOf(previous.lastWallClockMillis, nowWallMillis),
            lastElapsedRealtimeMillis = nowElapsedRealtimeMillis,
            tamperDetected = previous.tamperDetected || isTamper
        )
    }

    fun dayKeyForMillis(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(millis))
    }
}

/**
 * Adaptador Android para persistir o estado de integridade do tempo de uso em SharedPreferences.
 */
class ClockIntegrityGuard(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSnapshot(): UsageSnapshot? {
        val dayKey = prefs.getString(KEY_DAY_KEY, null) ?: return null
        return UsageSnapshot(
            dayKey = dayKey,
            totalMinutes = prefs.getInt(KEY_TOTAL_MINUTES, 0),
            lastWallClockMillis = prefs.getLong(KEY_LAST_WALL_MS, 0L),
            lastElapsedRealtimeMillis = prefs.getLong(KEY_LAST_ELAPSED_MS, 0L),
            tamperDetected = prefs.getBoolean(KEY_TAMPER_DETECTED, false)
        )
    }

    fun updateAndPersist(observedTotalMinutes: Int): UsageSnapshot {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val dayKey = ClockTamperDetector.dayKeyForMillis(nowWall)
        val reconciled = ClockTamperDetector.reconcileUsage(
            previous = loadSnapshot(),
            observedTotalMinutes = observedTotalMinutes,
            currentDayKey = dayKey,
            nowWallMillis = nowWall,
            nowElapsedRealtimeMillis = nowElapsed
        )
        prefs.edit()
            .putString(KEY_DAY_KEY, reconciled.dayKey)
            .putInt(KEY_TOTAL_MINUTES, reconciled.totalMinutes)
            .putLong(KEY_LAST_WALL_MS, reconciled.lastWallClockMillis)
            .putLong(KEY_LAST_ELAPSED_MS, reconciled.lastElapsedRealtimeMillis)
            .putBoolean(KEY_TAMPER_DETECTED, reconciled.tamperDetected)
            .apply()
        return reconciled
    }

    companion object {
        private const val PREFS_NAME = "monitordad_clock_integrity"
        private const val KEY_DAY_KEY = "day_key"
        private const val KEY_TOTAL_MINUTES = "total_minutes"
        private const val KEY_LAST_WALL_MS = "last_wall_ms"
        private const val KEY_LAST_ELAPSED_MS = "last_elapsed_ms"
        private const val KEY_TAMPER_DETECTED = "tamper_detected"
    }
}
