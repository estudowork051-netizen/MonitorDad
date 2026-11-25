package com.dohguard.parent.screens.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.components.*
import com.dohguard.shared.model.SyncStatus
import com.dohguard.shared.model.UsageSummary
import com.dohguard.shared.theme.*

@Composable
fun UsageSummaryCard(
    summary: UsageSummary,
    isSyncing: Boolean,
    onManualSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoHCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Row: Title, Sync Status Badge, Sync Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Resumo de Utilização",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoHTextPrimary
                    )
                    Text(
                        text = "${summary.deviceName} (${summary.deviceId})",
                        fontSize = 11.sp,
                        color = DoHTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Sincronização: ${summary.syncStatus.label} • ${summary.lastSyncFormatted}",
                        fontSize = 11.sp,
                        color = when (summary.syncStatus) {
                            SyncStatus.ONLINE -> DoHStatusPositive
                            SyncStatus.OUTDATED -> DoHAlert
                            SyncStatus.OFFLINE -> DoHBlock
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summary.isDemoData) {
                        Text(
                            text = "Dados demonstrativos (aguardando conexão com tablet)",
                            fontSize = 10.sp,
                            color = DoHAlert,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(
                        text = summary.syncStatus.label.uppercase(),
                        tone = when (summary.syncStatus) {
                            SyncStatus.ONLINE -> BadgeTone.POSITIVE
                            SyncStatus.OUTDATED -> BadgeTone.ALERT
                            SyncStatus.OFFLINE -> BadgeTone.BLOCK
                        }
                    )

                    IconButton(
                        onClick = onManualSync,
                        enabled = !isSyncing,
                        modifier = Modifier.size(34.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DoHSagePrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar",
                                tint = DoHSageDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Grid Row: Total Today vs Most Used
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric 1: Total Today & comparison
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DoHWhite,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Tempo Total Hoje",
                            fontSize = 11.sp,
                            color = DoHTextSecondary
                        )
                        Text(
                            text = summary.formattedTotalToday,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary
                        )
                        Text(
                            text = summary.differenceVsYesterdayFormatted,
                            fontSize = 10.sp,
                            color = DoHSageDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Metric 2: Apps Count & Top App
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DoHWhite,
                    modifier = Modifier.weight(1.1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Mais Utilizado (${summary.appsCountUsedToday} apps)",
                            fontSize = 11.sp,
                            color = DoHTextSecondary
                        )
                        Text(
                            text = summary.mostUsedFormatted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "Ontem total: ${summary.formattedTotalYesterday}",
                            fontSize = 10.sp,
                            color = DoHTextSecondary
                        )
                    }
                }
            }
        }
    }
}
