package com.dohguard.kids.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.components.AppIconImage
import com.dohguard.shared.components.ButtonVariant
import com.dohguard.shared.components.DoHButton
import com.dohguard.shared.components.DoHCard
import com.dohguard.shared.model.AppUsageInfo
import com.dohguard.shared.model.RoutineRule
import com.dohguard.shared.model.ScreenTimeState
import com.dohguard.shared.theme.*

@Composable
fun KidsHomeScreen(
    childName: String,
    deviceName: String = "Tablet do $childName",
    screenTimeState: ScreenTimeState,
    authorizedApps: List<AppUsageInfo>,
    activeRoutines: List<RoutineRule> = emptyList(),
    isOnline: Boolean = true,
    isSyncing: Boolean = false,
    hasUsagePermission: Boolean = true,
    hasAccessibilityPermission: Boolean = true,
    onRequestUsagePermission: () -> Unit = {},
    onRequestAccessibilityPermission: () -> Unit = {},
    onOpenRestScreen: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onOpenPinAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val remaining = screenTimeState.remainingMinutes
    val isLowTime = remaining in 1..15
    val isTimeUp = remaining <= 0

    val timerAccent by animateColorAsState(
        targetValue = when {
            isTimeUp -> MonitorAlertRed
            isLowTime -> MonitorElectricCyan
            else -> MonitorNeonGreen
        },
        label = "kidsTimerAccent"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MonitorBgSecondary, MonitorBgPrimary)
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Kids Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MonitorNeonGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MonitorBgPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MonitorDad",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonitorTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MonitorNeonGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "KIDS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MonitorNeonGreen
                            )
                        }
                    }
                    Text(
                        text = "Olá, $childName! 👋",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MonitorTextPrimary
                    )
                    Text(
                        text = deviceName,
                        fontSize = 12.sp,
                        color = MonitorTextSecondary
                    )
                }
            }

            // Connection & PIN Access
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            when {
                                isSyncing -> MonitorElectricCyan.copy(alpha = 0.16f)
                                isOnline -> MonitorNeonGreen.copy(alpha = 0.16f)
                                else -> MonitorAlertRed.copy(alpha = 0.16f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isSyncing -> Icons.Default.Sync
                            isOnline -> Icons.Default.Wifi
                            else -> Icons.Default.WifiOff
                        },
                        contentDescription = null,
                        tint = when {
                            isSyncing -> MonitorElectricCyan
                            isOnline -> MonitorNeonGreen
                            else -> MonitorAlertRed
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onOpenPinAuth,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MonitorCardBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Acesso dos Pais",
                        tint = MonitorElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Permission status card
            item(span = { GridItemSpan(2) }) {
                UsagePermissionCard(
                    hasPermission = hasUsagePermission,
                    onRequestPermission = onRequestUsagePermission,
                    hasAccessibilityPermission = hasAccessibilityPermission,
                    onRequestAccessibilityPermission = onRequestAccessibilityPermission
                )
            }

            // 2. RemainingTimeCard
            item(span = { GridItemSpan(2) }) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MonitorCardBg,
                    border = BorderStroke(1.5.dp, timerAccent.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(timerAccent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTimeUp) Icons.Default.Bedtime else Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = timerAccent,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TEMPO RESTANTE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = timerAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = screenTimeState.formattedRemainingPadded,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MonitorTextPrimary
                                )
                                Text(
                                    text = when {
                                        isTimeUp -> "Seu tempo de hoje terminou. Hora de descansar!"
                                        isLowTime -> "Quase na hora de encerrar! Finalize o que está fazendo."
                                        else -> "Aproveite para aprender e brincar com calma!"
                                    },
                                    fontSize = 12.sp,
                                    color = MonitorTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val remainingFraction = if (screenTimeState.effectiveLimitMinutes > 0) {
                            (remaining.toFloat() / screenTimeState.effectiveLimitMinutes.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { remainingFraction },
                            color = timerAccent,
                            trackColor = MonitorBgPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Usado hoje: ${screenTimeState.formattedUsed}",
                                fontSize = 11.sp,
                                color = MonitorTextSecondary
                            )
                            Text(
                                text = "Limite: ${screenTimeState.formattedLimit}",
                                fontSize = 11.sp,
                                color = MonitorTextSecondary
                            )
                        }
                    }
                }
            }

            // 3. DailyRoutineCard (exclusively real configured routines)
            if (activeRoutines.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    DoHCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MonitorElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sua Rotina Configurada",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MonitorTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            activeRoutines.take(3).forEach { routine ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = routine.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MonitorTextPrimary
                                    )
                                    Text(
                                        text = "${routine.startTime} - ${routine.endTime}",
                                        fontSize = 12.sp,
                                        color = MonitorElectricCyan,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Quick screen preview actions
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DoHButton(
                        text = "Ver Descanso",
                        onClick = onOpenRestScreen,
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    DoHButton(
                        text = "Ver Bloqueio",
                        onClick = onOpenLockScreen,
                        variant = ButtonVariant.OUTLINED,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (toastMessage != null) {
                item(span = { GridItemSpan(2) }) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MonitorBgSecondary,
                        border = BorderStroke(1.dp, MonitorBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = toastMessage ?: "",
                            fontSize = 12.sp,
                            color = MonitorElectricCyan,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 5. Section Title: Seus Aplicativos
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Seus Aplicativos",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MonitorTextPrimary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // 6. Friendly 2-column grid showing LIBERADO / BLOQUEADO
            items(authorizedApps) { app ->
                val isBlocked = app.isBlocked
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MonitorCardBg,
                    border = BorderStroke(
                        1.dp,
                        if (isBlocked) MonitorAlertRed.copy(alpha = 0.55f) else MonitorBorder
                    ),
                    modifier = Modifier.clickable {
                        toastMessage = if (isBlocked) {
                            "${app.appName} está bloqueado no momento."
                        } else {
                            "${app.appName} está liberado!"
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconImage(
                            packageName = app.packageName,
                            appName = app.appName,
                            category = app.category,
                            isBlocked = app.isBlocked,
                            size = 58.dp,
                            cornerRadius = 18.dp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = app.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonitorTextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isBlocked) MonitorAlertRed.copy(alpha = 0.18f)
                                    else MonitorNeonGreen.copy(alpha = 0.16f)
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isBlocked) "BLOQUEADO" else "LIBERADO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isBlocked) MonitorAlertRed else MonitorNeonGreen
                            )
                        }

                        if (app.dailyLimitMinutes > 0 && !isBlocked) {
                            val appRem = (app.dailyLimitMinutes - app.usedMinutesToday).coerceAtLeast(0)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Restam ${appRem}min",
                                fontSize = 11.sp,
                                color = MonitorTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
