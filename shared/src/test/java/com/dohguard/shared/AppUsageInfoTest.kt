package com.dohguard.shared

import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.AppUsageInfo
import org.junit.Assert.*
import org.junit.Test

class AppUsageInfoTest {

    @Test
    fun testFormattedTimesAndProgress() {
        val app = AppUsageInfo(
            packageName = "com.test.app",
            appName = "Test App",
            category = AppCategory.EDUCATION,
            usedMinutesToday = 45,
            usedMinutesYesterday = 30,
            dailyLimitMinutes = 60
        )

        assertEquals("45 min", app.formattedUsageTime)
        assertEquals("30 min", app.formattedUsageYesterday)
        assertEquals("1h 0m", app.formattedLimitTime)
        assertEquals(0.75f, app.usagePercentage, 0.01f)
    }

    @Test
    fun testZeroLimitHandledGracefully() {
        val app = AppUsageInfo(
            packageName = "com.test.app2",
            appName = "Test App 2",
            category = AppCategory.COMMUNICATION,
            usedMinutesToday = 20,
            dailyLimitMinutes = 0
        )

        assertEquals(0f, app.usagePercentage, 0.01f)
    }
}
