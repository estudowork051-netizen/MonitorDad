package com.dohguard.shared.model

/**
 * Estado do tempo de tela do dia.
 */
data class ScreenTimeState(
    val usedMinutes: Int,
    val totalLimitMinutes: Int,
    val extraMinutesGranted: Int = 0
) {
    val effectiveLimitMinutes: Int
        get() = totalLimitMinutes + extraMinutesGranted

    val remainingMinutes: Int
        get() = (effectiveLimitMinutes - usedMinutes).coerceAtLeast(0)

    val progressFraction: Float
        get() = if (effectiveLimitMinutes > 0) {
            (usedMinutes.toFloat() / effectiveLimitMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedUsed: String
        get() {
            val h = usedMinutes / 60
            val m = usedMinutes % 60
            return "${h}h ${m}m"
        }

    val formattedLimit: String
        get() {
            val h = effectiveLimitMinutes / 60
            val m = effectiveLimitMinutes % 60
            return "${h}h ${m}m"
        }

    val formattedRemaining: String
        get() {
            val h = remainingMinutes / 60
            val m = remainingMinutes % 60
            return if (h > 0) "${h}h ${m}m" else "${m} min"
        }

    val formattedRemainingPadded: String
        get() {
            val h = (remainingMinutes / 60).toString().padStart(2, '0')
            val m = (remainingMinutes % 60).toString().padStart(2, '0')
            return "${h}h ${m}min"
        }

    companion object {
        val DEMO = ScreenTimeState(
            usedMinutes = 135, // 2h 15m
            totalLimitMinutes = 210, // 3h 30m
            extraMinutesGranted = 0
        )
    }
}
