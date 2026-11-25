package com.dohguard.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.SyncStatus
import com.dohguard.shared.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Card de destaque "EM USO AGORA" para o painel do responsável.
 * Exibe apenas informações reais de primeiro plano; quando indisponível ou desatualizado,
 * informa claramente "Informação aguardando atualização".
 */
@Composable
fun CurrentAppCard(
    deviceName: String,
    appName: String?,
    packageName: String?,
    startedAtMillis: Long?,
    lastUpdateFormatted: String?,
    syncStatus: SyncStatus,
    isDemoData: Boolean,
    category: AppCategory = AppCategory.OTHER,
    modifier: Modifier = Modifier
) {
    val hasFreshForeground = !isDemoData &&
        syncStatus == SyncStatus.ONLINE &&
        !appName.isNullOrBlank() &&
        !packageName.isNullOrBlank()

    val sinceText = if (hasFreshForeground && startedAtMillis != null && startedAtMillis > 0L) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        "Desde ${sdf.format(Date(startedAtMillis))}"
    } else null

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MonitorCardBg),
        border = BorderStroke(
            1.5.dp,
            if (hasFreshForeground) MonitorNeonGreen.copy(alpha = 0.7f) else MonitorBorder
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MonitorBgSecondary,
                            MonitorCardBg
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (hasFreshForeground) MonitorNeonGreen.copy(alpha = 0.16f)
                                else MonitorBgPrimary.copy(alpha = 0.7f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (hasFreshForeground) Icons.Default.PlayCircleFilled else Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = if (hasFreshForeground) MonitorNeonGreen else MonitorElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EM USO AGORA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = if (hasFreshForeground) MonitorNeonGreen else MonitorElectricCyan
                        )
                    }

                    StatusBadge(
                        text = syncStatus.label,
                        tone = when (syncStatus) {
                            SyncStatus.ONLINE -> BadgeTone.POSITIVE
                            SyncStatus.OUTDATED -> BadgeTone.ALERT
                            SyncStatus.OFFLINE -> BadgeTone.BLOCK
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (hasFreshForeground) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            packageName = packageName!!,
                            appName = appName!!,
                            category = category,
                            isBlocked = false,
                            size = 56.dp,
                            cornerRadius = 16.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appName,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonitorTextPrimary
                            )
                            if (sinceText != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = sinceText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonitorNeonGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$deviceName • Atualizado ${lastUpdateFormatted ?: "agora"}",
                                fontSize = 11.sp,
                                color = MonitorTextSecondary
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MonitorBgPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = MonitorElectricCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Informação aguardando atualização",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MonitorTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$deviceName • Última atualização: ${lastUpdateFormatted ?: "sem registro"}",
                                fontSize = 12.sp,
                                color = MonitorTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
