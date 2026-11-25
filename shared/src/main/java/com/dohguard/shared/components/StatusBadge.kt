package com.dohguard.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.theme.*

enum class BadgeTone {
    POSITIVE,
    ALERT,
    BLOCK,
    NEUTRAL
}

@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone = BadgeTone.POSITIVE,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (tone) {
        BadgeTone.POSITIVE -> MonitorNeonGreen.copy(alpha = 0.16f) to MonitorNeonGreen
        BadgeTone.ALERT -> MonitorElectricCyan.copy(alpha = 0.16f) to MonitorElectricCyan
        BadgeTone.BLOCK -> MonitorAlertRed.copy(alpha = 0.18f) to MonitorAlertRed
        BadgeTone.NEUTRAL -> MonitorBgSecondary to MonitorTextSecondary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
