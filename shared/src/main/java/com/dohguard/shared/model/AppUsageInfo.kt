package com.dohguard.shared.model

/**
 * Categorias de aplicativos conforme especificação do DoH Guard.
 */
enum class AppCategory(val displayName: String) {
    ALL("Todos"),
    GAMES("Jogos"),
    VIDEOS("Vídeos"),
    EDUCATION("Educação"),
    COMMUNICATION("Comunicação"),
    NAVIGATION("Navegação"),
    OTHER("Outros")
}

/**
 * Estado de autorização do aplicativo no tablet da criança.
 */
enum class AppAuthorizationState {
    ALLOWED,
    RESTRICTED,
    PENDING
}

/**
 * Sessão individual de utilização de aplicativo (início, término e duração).
 */
data class AppSession(
    val id: String,
    val startTimeFormatted: String,
    val endTimeFormatted: String,
    val durationMinutes: Int,
    val timestamp: Long
)

/**
 * Informações detalhadas de utilização e identificação de aplicativo.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val usedMinutesToday: Int,
    val usedMinutesYesterday: Int = 0,
    val dailyLimitMinutes: Int = 60,
    val isBlocked: Boolean = false,
    val iconIdentifier: String = "default",
    val lastTimeUsedTimestamp: Long = 0L,
    val lastTimeUsedFormatted: String = "Hoje",
    val sessionHistory: List<AppSession> = emptyList(),
    val isSystemApp: Boolean = false,
    val isMonitored: Boolean = true,
    val authorizationState: AppAuthorizationState = AppAuthorizationState.ALLOWED
) {
    val usagePercentage: Float
        get() = if (dailyLimitMinutes > 0) {
            (usedMinutesToday.toFloat() / dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedUsageTime: String
        get() = formatMinutes(usedMinutesToday)

    val formattedUsageYesterday: String
        get() = formatMinutes(usedMinutesYesterday)

    val formattedLimitTime: String
        get() = formatMinutes(dailyLimitMinutes)

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m} min"
    }

    companion object {
        val DEMO_APPS = listOf(
            AppUsageInfo(
                packageName = "com.google.android.apps.youtube.kids",
                appName = "YouTube Kids",
                category = AppCategory.VIDEOS,
                usedMinutesToday = 70,
                usedMinutesYesterday = 55,
                dailyLimitMinutes = 90,
                isBlocked = false,
                iconIdentifier = "youtube",
                lastTimeUsedFormatted = "Hoje às 14:10",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 25,
                sessionHistory = listOf(
                    AppSession("s1", "13:35", "14:10", 35, System.currentTimeMillis() - 1000 * 60 * 25),
                    AppSession("s2", "10:15", "10:50", 35, System.currentTimeMillis() - 1000 * 60 * 220)
                )
            ),
            AppUsageInfo(
                packageName = "com.duolingo",
                appName = "Duolingo ABC",
                category = AppCategory.EDUCATION,
                usedMinutesToday = 35,
                usedMinutesYesterday = 30,
                dailyLimitMinutes = 60,
                isBlocked = false,
                iconIdentifier = "duolingo",
                lastTimeUsedFormatted = "Hoje às 11:30",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 185,
                sessionHistory = listOf(
                    AppSession("s3", "10:55", "11:30", 35, System.currentTimeMillis() - 1000 * 60 * 185)
                )
            ),
            AppUsageInfo(
                packageName = "com.mojang.minecraftpe",
                appName = "Minecraft",
                category = AppCategory.GAMES,
                usedMinutesToday = 30,
                usedMinutesYesterday = 45,
                dailyLimitMinutes = 45,
                isBlocked = false,
                iconIdentifier = "minecraft",
                lastTimeUsedFormatted = "Hoje às 12:45",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 110,
                sessionHistory = listOf(
                    AppSession("s4", "12:15", "12:45", 30, System.currentTimeMillis() - 1000 * 60 * 110)
                )
            ),
            AppUsageInfo(
                packageName = "com.whatsapp",
                appName = "WhatsApp Família",
                category = AppCategory.COMMUNICATION,
                usedMinutesToday = 12,
                usedMinutesYesterday = 8,
                dailyLimitMinutes = 30,
                isBlocked = false,
                iconIdentifier = "whatsapp",
                lastTimeUsedFormatted = "Hoje às 14:25",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 10,
                sessionHistory = listOf(
                    AppSession("s5", "14:15", "14:25", 10, System.currentTimeMillis() - 1000 * 60 * 10)
                )
            ),
            AppUsageInfo(
                packageName = "com.khanacademy.kids",
                appName = "Khan Academy Kids",
                category = AppCategory.EDUCATION,
                usedMinutesToday = 20,
                usedMinutesYesterday = 25,
                dailyLimitMinutes = 60,
                isBlocked = false,
                iconIdentifier = "khan",
                lastTimeUsedFormatted = "Hoje às 09:40",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 300,
                sessionHistory = listOf(
                    AppSession("s6", "09:20", "09:40", 20, System.currentTimeMillis() - 1000 * 60 * 300)
                )
            ),
            AppUsageInfo(
                packageName = "com.roblox.client",
                appName = "Roblox",
                category = AppCategory.GAMES,
                usedMinutesToday = 0,
                usedMinutesYesterday = 20,
                dailyLimitMinutes = 40,
                isBlocked = true,
                iconIdentifier = "roblox",
                lastTimeUsedFormatted = "Ontem às 16:30",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24
            ),
            AppUsageInfo(
                packageName = "com.disney.disneyplus",
                appName = "Disney+",
                category = AppCategory.VIDEOS,
                usedMinutesToday = 15,
                usedMinutesYesterday = 35,
                dailyLimitMinutes = 60,
                isBlocked = false,
                iconIdentifier = "disney",
                lastTimeUsedFormatted = "Hoje às 08:30",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 360,
                sessionHistory = listOf(
                    AppSession("s7", "08:15", "08:30", 15, System.currentTimeMillis() - 1000 * 60 * 360)
                )
            ),
            AppUsageInfo(
                packageName = "com.google.android.calculator",
                appName = "Calculadora",
                category = AppCategory.OTHER,
                usedMinutesToday = 5,
                usedMinutesYesterday = 2,
                dailyLimitMinutes = 60,
                isBlocked = false,
                iconIdentifier = "calc",
                lastTimeUsedFormatted = "Hoje às 10:05",
                lastTimeUsedTimestamp = System.currentTimeMillis() - 1000 * 60 * 270
            )
        )
    }
}
