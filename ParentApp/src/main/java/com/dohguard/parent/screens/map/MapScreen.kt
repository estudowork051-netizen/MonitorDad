package com.dohguard.parent.screens.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.components.*
import com.dohguard.shared.model.LocationInfo
import com.dohguard.shared.theme.*

@Composable
fun MapScreen(
    locationInfo: LocationInfo,
    onRefreshLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshNotice by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DoHBackground),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mapa GPS",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoHTextPrimary
                    )
                    Text(
                        text = "Localização e zonas de segurança do dispositivo",
                        fontSize = 13.sp,
                        color = DoHTextSecondary
                    )
                }

                // Refresh GPS button
                DoHButton(
                    text = if (isRefreshing) "Buscando..." else "Atualizar",
                    onClick = {
                        isRefreshing = true
                        onRefreshLocation()
                        refreshNotice = "Sinal GPS consultado (demonstração)"
                        isRefreshing = false
                    },
                    icon = Icons.Default.Refresh,
                    variant = ButtonVariant.SECONDARY
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (refreshNotice != null) {
            item {
                Surface(
                    color = DoHSageLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = refreshNotice ?: "",
                        fontSize = 12.sp,
                        color = DoHSageDark,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 1. Reserved Area for Map (Clearly marked as Demo)
        item {
            DoHCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(DoHSageLight.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(DoHWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = DoHSagePrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = locationInfo.currentZoneName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Última atualização: ${locationInfo.lastUpdatedFormatted} • ${locationInfo.accuracyDescription}",
                            fontSize = 11.sp,
                            color = DoHTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DoHCream
                        ) {
                            Text(
                                text = "Área reservada para mapa interativo • Demonstração visual",
                                fontSize = 11.sp,
                                color = DoHSageDark,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // 2. Safe Zones Section
        item {
            Text(
                text = "Zonas Seguras Cadastradas",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(locationInfo.safeZones) { zone ->
            DoHCard(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (zone.isInside) DoHSageLight else DoHCream),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (zone.name) {
                                "Casa" -> Icons.Default.Home
                                "Escola Municipal" -> Icons.Default.School
                                else -> Icons.Default.Place
                            },
                            contentDescription = null,
                            tint = if (zone.isInside) DoHStatusPositive else DoHSageDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zone.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DoHTextPrimary
                        )
                        Text(
                            text = "${zone.address} (Raio: ${zone.radiusMeters}m)",
                            fontSize = 12.sp,
                            color = DoHTextSecondary
                        )
                    }

                    StatusBadge(
                        text = if (zone.isInside) "DENTRO" else "FORA",
                        tone = if (zone.isInside) BadgeTone.POSITIVE else BadgeTone.NEUTRAL
                    )
                }
            }
        }

        // 3. Timeline of Location Events
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Linha do Tempo de Hoje",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(locationInfo.timeline) { event ->
            DoHCard(modifier = Modifier.padding(vertical = 3.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DoHSageLight
                    ) {
                        Text(
                            text = event.timeFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHSageDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DoHTextPrimary
                        )
                        Text(
                            text = event.subtitle,
                            fontSize = 11.sp,
                            color = DoHTextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DoHStatusPositive,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
