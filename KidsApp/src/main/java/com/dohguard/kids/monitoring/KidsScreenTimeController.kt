package com.dohguard.kids.monitoring

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Controlador monotônico de tempo ativo para a tela inicial do MonitorDad Kids.
 *
 * Usa SystemClock.elapsedRealtime() (imune a alterações manuais do relógio do tablet)
 * e persiste segundos acumulados por dia em SharedPreferences para que o cronômetro:
 * - Atualize em tempo real na tela inicial (ex: 01h 15min -> 01h 14min).
 * - Não reinicie durante recomposições ou navegação entre telas.
 * - Mantenha a contagem correta após fechar e reabrir o aplicativo.
 */
object KidsScreenTimeController {

    private const val PREFS_NAME = "monitordad_kids_ticker_prefs"
    private const val KEY_DAY = "ticker_day"
    private const val KEY_ACCUMULATED_SECONDS = "ticker_accumulated_seconds"

    private var prefs: SharedPreferences? = null
    private var lastTickElapsedMs: Long = 0L

    private val _activeExtraMinutes = MutableStateFlow(0)
    val activeExtraMinutes: StateFlow<Int> = _activeExtraMinutes.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        resetIfNewDay()
        _activeExtraMinutes.value = (getAccumulatedSeconds() / 60L).toInt()
        lastTickElapsedMs = SystemClock.elapsedRealtime()
    }

    /**
     * Avança o contador somente quando o dispositivo não está pausado ou bloqueado.
     * Também reconcilia com o total observado pelo UsageStatsManager para evitar contagem dupla.
     */
    fun tick(isActiveSession: Boolean, observedExternalMinutes: Int) {
        val sp = prefs ?: return
        resetIfNewDay()

        val nowElapsed = SystemClock.elapsedRealtime()
        if (lastTickElapsedMs == 0L || nowElapsed < lastTickElapsedMs) {
            lastTickElapsedMs = nowElapsed
            return
        }

        val deltaSeconds = ((nowElapsed - lastTickElapsedMs) / 1000L).coerceIn(0L, 60L)
        lastTickElapsedMs = nowElapsed

        if (!isActiveSession || deltaSeconds <= 0L) {
            return
        }

        val currentSeconds = getAccumulatedSeconds() + deltaSeconds
        sp.edit().putLong(KEY_ACCUMULATED_SECONDS, currentSeconds).apply()

        val tickerMinutes = (currentSeconds / 60L).toInt()
        _activeExtraMinutes.value = maxOf(tickerMinutes, observedExternalMinutes)
    }

    fun syncObservedMinutes(observedExternalMinutes: Int) {
        val sp = prefs ?: return
        resetIfNewDay()
        val currentSeconds = getAccumulatedSeconds()
        val observedSeconds = observedExternalMinutes.coerceAtLeast(0) * 60L
        if (observedSeconds > currentSeconds) {
            sp.edit().putLong(KEY_ACCUMULATED_SECONDS, observedSeconds).apply()
            _activeExtraMinutes.value = observedExternalMinutes
        } else {
            _activeExtraMinutes.value = (currentSeconds / 60L).toInt()
        }
    }

    private fun getAccumulatedSeconds(): Long {
        return prefs?.getLong(KEY_ACCUMULATED_SECONDS, 0L) ?: 0L
    }

    private fun resetIfNewDay() {
        val sp = prefs ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val storedDay = sp.getString(KEY_DAY, null)
        if (storedDay != today) {
            sp.edit()
                .putString(KEY_DAY, today)
                .putLong(KEY_ACCUMULATED_SECONDS, 0L)
                .apply()
            _activeExtraMinutes.value = 0
        }
    }
}
