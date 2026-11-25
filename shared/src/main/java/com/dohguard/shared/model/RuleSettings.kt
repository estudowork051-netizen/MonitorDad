package com.dohguard.shared.model

data class BedtimeRule(
    val isEnabled: Boolean = true,
    val bedtimeFormatted: String = "21:00",
    val wakeTimeFormatted: String = "07:00",
    val activeDaysSummary: String = "Segunda a Domingo"
)

data class SchoolModeRule(
    val isEnabled: Boolean = true,
    val startTimeFormatted: String = "07:30",
    val endTimeFormatted: String = "12:00",
    val blockEntertainment: Boolean = true,
    val activeDaysSummary: String = "Seg a Sex"
)

data class AuthorizedContact(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String,
    val isEmergency: Boolean = false
)

data class RuleSettings(
    val bedtime: BedtimeRule = BedtimeRule(),
    val schoolMode: SchoolModeRule = SchoolModeRule(),
    val dohSecureDnsEnabled: Boolean = true,
    val searchSafeFilterEnabled: Boolean = true,
    val blockInAppPurchases: Boolean = true,
    val contacts: List<AuthorizedContact> = listOf(
        AuthorizedContact("c1", "Mãe (Clara)", "Mãe", "(11) 98765-4321", isEmergency = true),
        AuthorizedContact("c2", "Pai (Rodrigo)", "Pai", "(11) 98765-1234", isEmergency = true),
        AuthorizedContact("c3", "Avó Helena", "Família", "(11) 91234-5678", isEmergency = false)
    )
)
