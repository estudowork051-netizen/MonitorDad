package com.dohguard.shared.config

/**
 * Configuração centralizada do tempo de exibição da Splash Screen do MonitorDad.
 * Valor padrão recomendado: 4000 ms (entre 3000 ms e 5000 ms).
 */
object SplashConfig {
    const val MIN_SPLASH_DURATION_MS: Long = 3_000L
    const val MAX_SPLASH_DURATION_MS: Long = 5_000L
    const val SPLASH_DURATION_MS: Long = 4_000L

    /**
     * Retorna a duração efetiva da Splash Screen limitada ao intervalo seguro [3000ms, 5000ms].
     */
    fun clampedDurationMs(durationMs: Long = SPLASH_DURATION_MS): Long {
        return durationMs.coerceIn(MIN_SPLASH_DURATION_MS, MAX_SPLASH_DURATION_MS)
    }
}
