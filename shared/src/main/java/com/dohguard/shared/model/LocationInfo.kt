package com.dohguard.shared.model

data class SafeZone(
    val id: String,
    val name: String,
    val address: String,
    val radiusMeters: Int,
    val isInside: Boolean
)

data class LocationTimelineEvent(
    val id: String,
    val timeFormatted: String,
    val title: String,
    val subtitle: String,
    val isSafeZone: Boolean
)

data class LocationInfo(
    val currentZoneName: String,
    val lastUpdatedFormatted: String,
    val accuracyDescription: String,
    val safeZones: List<SafeZone>,
    val timeline: List<LocationTimelineEvent>
) {
    companion object {
        val DEMO = LocationInfo(
            currentZoneName = "Escola Municipal Monteiro Lobato",
            lastUpdatedFormatted = "Hoje às 14:32",
            accuracyDescription = "Precisão alta (~15m)",
            safeZones = listOf(
                SafeZone("sz1", "Casa", "Rua das Acácias, 120", 150, false),
                SafeZone("sz2", "Escola Municipal", "Av. Brasil, 450", 250, true),
                SafeZone("sz3", "Casa dos Avós", "Rua das Palmeiras, 88", 100, false)
            ),
            timeline = listOf(
                LocationTimelineEvent("tl1", "14:15", "Biblioteca Escolar", "Dentro da Zona Escola", true),
                LocationTimelineEvent("tl2", "12:30", "Pátio / Refeitório", "Dentro da Zona Escola", true),
                LocationTimelineEvent("tl3", "07:45", "Chegada na Escola", "Entrada detectada", true),
                LocationTimelineEvent("tl4", "07:20", "Saída de Casa", "Partida detectada", true)
            )
        )
    }
}
