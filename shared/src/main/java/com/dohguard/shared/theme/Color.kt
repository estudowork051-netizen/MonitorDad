package com.dohguard.shared.theme

import androidx.compose.ui.graphics.Color

/**
 * MonitorDad • by HoD — Official Design System Color Palette
 * Style: Deep Space Purple & Neon Green
 */
val MonitorBgPrimary = Color(0xFF1A0E2E)      // Fundo principal
val MonitorBgSecondary = Color(0xFF2B164B)    // Fundo secundário
val MonitorCardBg = Color(0xFF351C5B)         // Cards e containers
val MonitorBorder = Color(0xFF6B42B0)         // Bordas
val MonitorNeonGreen = Color(0xFF9EFF00)      // Destaque verde neon
val MonitorElectricCyan = Color(0xFF00E5FF)   // Ciano elétrico
val MonitorAlertRed = Color(0xFFFF3B5C)       // Alerta/bloqueado
val MonitorTextPrimary = Color(0xFFFFFFFF)    // Texto principal
val MonitorTextSecondary = Color(0xFFB9A8D6)  // Texto secundário

// Legacy semantic token aliases preserved for full compatibility across modules
val DoHBackground = MonitorBgPrimary
val DoHSagePrimary = MonitorNeonGreen
val DoHSageDark = MonitorElectricCyan
val DoHSageLight = MonitorBgSecondary
val DoHSagePastel = MonitorBorder
val DoHCream = MonitorCardBg
val DoHWhite = MonitorTextPrimary
val DoHTextPrimary = MonitorTextPrimary
val DoHTextSecondary = MonitorTextSecondary
val DoHStatusPositive = MonitorNeonGreen
val DoHAlert = MonitorElectricCyan
val DoHBlock = MonitorAlertRed

val DoHSageSurface = MonitorBgSecondary
val DoHTextMuted = MonitorTextSecondary
val DoHStatusOnline = MonitorNeonGreen
val DoHStatusWarning = Color(0xFFFFB020)
val DoHStatusOffline = MonitorAlertRed

// Glassmorphism and surface overlays
val DoHCardBorder = MonitorBorder.copy(alpha = 0.55f)
val DoHBorderLight = MonitorBorder.copy(alpha = 0.45f)
val DoHShadow = Color(0x66000000)
val DoHGlassBackground = Color(0xE62B164B)
