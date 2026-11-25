package com.dohguard.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MonitorDadDarkColorScheme = darkColorScheme(
    primary = MonitorNeonGreen,
    onPrimary = MonitorBgPrimary,
    primaryContainer = MonitorBgSecondary,
    onPrimaryContainer = MonitorNeonGreen,
    secondary = MonitorElectricCyan,
    onSecondary = MonitorBgPrimary,
    secondaryContainer = MonitorCardBg,
    onSecondaryContainer = MonitorTextPrimary,
    tertiary = MonitorNeonGreen,
    background = MonitorBgPrimary,
    onBackground = MonitorTextPrimary,
    surface = MonitorCardBg,
    onSurface = MonitorTextPrimary,
    surfaceVariant = MonitorBgSecondary,
    onSurfaceVariant = MonitorTextSecondary,
    outline = MonitorBorder,
    error = MonitorAlertRed,
    onError = MonitorTextPrimary
)

@Composable
fun DoHGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonitorDadDarkColorScheme,
        typography = DoHTypography,
        shapes = DoHShapes,
        content = content
    )
}
