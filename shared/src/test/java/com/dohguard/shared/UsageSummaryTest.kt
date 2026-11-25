package com.dohguard.shared

import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.AppUsageInfo
import com.dohguard.shared.model.SyncStatus
import com.dohguard.shared.model.UsageSummary
import org.junit.Assert.*
import org.junit.Test

class UsageSummaryTest {

    @Test
    fun testUsageSummaryCalculation() {
        val apps = listOf(
            AppUsageInfo(
                packageName = "app1",
                appName = "App 1",
                category = AppCategory.VIDEOS,
                usedMinutesToday = 60,
                usedMinutesYesterday = 40,
                dailyLimitMinutes = 90
            ),
            AppUsageInfo(
                packageName = "app2",
                appName = "App 2",
                category = AppCategory.GAMES,
                usedMinutesToday = 30,
                usedMinutesYesterday = 50,
                dailyLimitMinutes = 45
            )
        )

        val summary = UsageSummary.fromAppsList(apps, System.currentTimeMillis())

        assertEquals(90, summary.totalUsedMinutesToday)
        assertEquals(90, summary.totalUsedMinutesYesterday)
        assertEquals(2, summary.appsCountUsedToday)
        assertEquals("App 1", summary.mostUsedAppName)
        assertEquals(60, summary.mostUsedAppMinutes)
        assertEquals(SyncStatus.ONLINE, summary.syncStatus)
        assertEquals("Mesmo tempo de ontem", summary.differenceVsYesterdayFormatted)
    }

    @Test
    fun testSyncStatusOutdatedAndOffline() {
        val apps = AppUsageInfo.DEMO_APPS
        val now = System.currentTimeMillis()

        // 30 min ago -> OUTDATED
        val outdatedSummary = UsageSummary.fromAppsList(apps, now - 1000 * 60 * 30)
        assertEquals(SyncStatus.OUTDATED, outdatedSummary.syncStatus)

        // 3 hours ago -> OFFLINE
        val offlineSummary = UsageSummary.fromAppsList(apps, now - 1000 * 60 * 180)
        assertEquals(SyncStatus.OFFLINE, offlineSummary.syncStatus)
        assertTrue(offlineSummary.lastSyncFormatted.isNotBlank())
        assertEquals("tablet-lucas-01", offlineSummary.deviceId)
        assertEquals("Tablet do Lucas", offlineSummary.deviceName)
        assertFalse(offlineSummary.isDemoData)
        assertTrue(UsageSummary.DEMO.isDemoData)
    }
}
