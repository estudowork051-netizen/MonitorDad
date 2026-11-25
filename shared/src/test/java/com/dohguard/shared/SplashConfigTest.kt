package com.dohguard.shared

import com.dohguard.shared.config.SplashConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashConfigTest {

    @Test
    fun testDefaultSplashDurationWithinRecommendedRange() {
        assertTrue(SplashConfig.SPLASH_DURATION_MS >= SplashConfig.MIN_SPLASH_DURATION_MS)
        assertTrue(SplashConfig.SPLASH_DURATION_MS <= SplashConfig.MAX_SPLASH_DURATION_MS)
        assertEquals(4_000L, SplashConfig.clampedDurationMs())
    }

    @Test
    fun testClampedSplashDurationBounds() {
        assertEquals(3_000L, SplashConfig.clampedDurationMs(1_000L))
        assertEquals(5_000L, SplashConfig.clampedDurationMs(9_000L))
        assertEquals(3_500L, SplashConfig.clampedDurationMs(3_500L))
    }
}
