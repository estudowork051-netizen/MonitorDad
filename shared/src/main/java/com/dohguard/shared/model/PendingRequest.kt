package com.dohguard.shared.model

enum class RequestType {
    EXTRA_TIME,
    APP_UNLOCK
}

data class PendingRequest(
    val id: String,
    val type: RequestType,
    val title: String,
    val description: String,
    val requestedMinutes: Int = 0,
    val requestedAt: String,
    val appName: String? = null
) {
    companion object {
        val DEMO_REQUESTS = listOf(
            PendingRequest(
                id = "req-1",
                type = RequestType.EXTRA_TIME,
                title = "Tempo Adicional",
                description = "Lucas pediu +30 min para assistir vídeos",
                requestedMinutes = 30,
                requestedAt = "há 8 minutos",
                appName = "YouTube Kids"
            )
        )
    }
}
