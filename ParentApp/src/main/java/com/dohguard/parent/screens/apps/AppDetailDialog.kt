package com.dohguard.parent.screens.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dohguard.shared.components.*
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.AppUsageInfo
import com.dohguard.shared.theme.*

@Composable
fun AppDetailDialog(
    app: AppUsageInfo,
    onDismiss: () -> Unit,
    onSaveLimitDemo: (Int) -> Unit = {}
) {
    var limitMinutes by remember { mutableStateOf(app.dailyLimitMinutes) }
    var showLimitSavedNotice by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DoHDialogCornerRadius),
            color = DoHWhite,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header: App icon, Title, Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIconImage(
                            packageName = app.packageName,
                            appName = app.appName,
                            category = app.category,
                            isBlocked = app.isBlocked,
                            size = 50.dp,
                            cornerRadius = 14.dp
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = app.appName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DoHTextPrimary
                            )
                            Text(
                                text = app.category.displayName,
                                fontSize = 12.sp,
                                color = DoHSageDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = DoHTextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = DoHCardBorder
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Metric Summary Cards Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DoHCream,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "Uso Hoje", fontSize = 11.sp, color = DoHTextSecondary)
                                    Text(
                                        text = app.formattedUsageTime,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DoHTextPrimary
                                    )
                                    Text(
                                        text = "Último: ${app.lastTimeUsedFormatted}",
                                        fontSize = 10.sp,
                                        color = DoHSageDark
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DoHCream,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "Uso Ontem", fontSize = 11.sp, color = DoHTextSecondary)
                                    Text(
                                        text = app.formattedUsageYesterday,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DoHTextPrimary
                                    )
                                    val diff = app.usedMinutesToday - app.usedMinutesYesterday
                                    Text(
                                        text = if (diff >= 0) "+$diff min vs ontem" else "$diff min vs ontem",
                                        fontSize = 10.sp,
                                        color = if (diff > 0) DoHAlert else DoHStatusPositive
                                    )
                                }
                            }
                        }
                    }

                    // Package details & monitoring metadata
                    item {
                        DoHCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Identificação Técnica",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DoHTextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Pacote", fontSize = 11.sp, color = DoHTextSecondary)
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DoHTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Tipo de App", fontSize = 11.sp, color = DoHTextSecondary)
                                    Text(
                                        text = if (app.isSystemApp) "Sistema / Fábrica" else "Instalado pelo Usuário",
                                        fontSize = 11.sp,
                                        color = DoHTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Estado de Monitoramento", fontSize = 11.sp, color = DoHTextSecondary)
                                    StatusBadge(text = "ATIVO", tone = BadgeTone.POSITIVE)
                                }
                            }
                        }
                    }

                    // Session History
                    item {
                        Text(
                            text = "Histórico de Sessões de Hoje",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary
                        )
                    }

                    if (app.sessionHistory.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DoHCream,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Nenhuma sessão contínua superior a 1 minuto registrada hoje.",
                                    fontSize = 12.sp,
                                    color = DoHTextSecondary,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        items(app.sessionHistory) { session ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DoHCream,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(DoHSageLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = DoHSageDark,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "${session.startTimeFormatted} às ${session.endTimeFormatted}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DoHTextPrimary
                                            )
                                            Text(
                                                text = "Sessão finalizada",
                                                fontSize = 10.sp,
                                                color = DoHTextSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${session.durationMinutes} min",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DoHSageDark
                                    )
                                }
                            }
                        }
                    }

                    // Preparation for Individual Limit Controls (Etapa 2 UI Prep)
                    item {
                        DoHCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Definição de Limite Diário",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DoHTextPrimary
                                    )
                                    StatusBadge(text = "PREPARAÇÃO", tone = BadgeTone.NEUTRAL)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Controle visual preparado para aplicação de limites individuais e bloqueio automático (Etapa 3).",
                                    fontSize = 11.sp,
                                    color = DoHTextSecondary
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Limite selecionado:", fontSize = 12.sp, color = DoHTextSecondary)
                                    Text(
                                        text = "$limitMinutes minutos",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DoHSageDark
                                    )
                                }

                                Slider(
                                    value = limitMinutes.toFloat(),
                                    onValueChange = { limitMinutes = (it / 15).toInt() * 15 },
                                    valueRange = 15f..180f,
                                    steps = 10,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DoHSagePrimary,
                                        activeTrackColor = DoHSagePrimary,
                                        inactiveTrackColor = DoHSageLight
                                    )
                                )

                                if (showLimitSavedNotice) {
                                    Text(
                                        text = "Configuração de limite salva demonstrativamente para $limitMinutes min.",
                                        fontSize = 11.sp,
                                        color = DoHStatusPositive,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                DoHButton(
                                    text = "Salvar Limite (Demonstração)",
                                    onClick = {
                                        onSaveLimitDemo(limitMinutes)
                                        showLimitSavedNotice = true
                                    },
                                    variant = ButtonVariant.SECONDARY,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                DoHButton(
                    text = "Fechar",
                    onClick = onDismiss,
                    variant = ButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
