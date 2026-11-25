package com.dohguard.parent.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.dohguard.shared.control.RuleEvaluationEngine
import com.dohguard.shared.model.*
import com.dohguard.shared.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RulesScreen(
    deviceInfo: DeviceInfo,
    ruleSettings: RuleSettings,
    controlState: ControlRulesState = ControlRulesState.defaultDemo(),
    screenTimeState: ScreenTimeState = ScreenTimeState.DEMO,
    syncEnvelopesCount: Int = 0,
    isOnline: Boolean = true,
    onUpdateDailyLimit: (Int, Set<Weekday>) -> Unit = { _, _ -> },
    onUpsertCategoryLimit: (CategoryLimitRule) -> Unit = {},
    onUpsertRoutine: (RoutineRule) -> Unit = {},
    onToggleRoutine: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteRoutine: (String) -> Unit = {},
    onGrantExtraTime: (Int, String?) -> Unit = { _, _ -> },
    onToggleOnline: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editingRoutine by remember { mutableStateOf<RoutineRule?>(null) }
    var showNewRoutineDialog by remember { mutableStateOf(false) }

    if (showNewRoutineDialog || editingRoutine != null) {
        RoutineEditorDialog(
            initialRoutine = editingRoutine,
            onDismiss = {
                showNewRoutineDialog = false
                editingRoutine = null
            },
            onSave = { saved ->
                onUpsertRoutine(saved)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DoHBackground),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Regras, Limites & Rotinas",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Text(
                text = "Controle diário, categorias, rotinas e concessão de tempo extra",
                fontSize = 13.sp,
                color = DoHTextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 1. Limite Diário do Tablet
        item {
            DoHCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Limite Diário do Tablet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DoHTextPrimary
                            )
                            Text(
                                text = "Usado: ${screenTimeState.formattedUsed} • Restante: ${screenTimeState.formattedRemaining}",
                                fontSize = 12.sp,
                                color = DoHSageDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        StatusBadge(
                            text = RuleEvaluationEngine.formatMinutes(controlState.dailyDeviceLimitMinutes),
                            tone = BadgeTone.NEUTRAL
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DoHButton(
                            text = "-30 min",
                            onClick = {
                                val next = (controlState.dailyDeviceLimitMinutes - 30).coerceAtLeast(30)
                                onUpdateDailyLimit(next, controlState.dailyLimitDays)
                            },
                            variant = ButtonVariant.OUTLINED,
                            modifier = Modifier.weight(1f)
                        )
                        DoHButton(
                            text = "+30 min",
                            onClick = {
                                val next = (controlState.dailyDeviceLimitMinutes + 30).coerceAtMost(720)
                                onUpdateDailyLimit(next, controlState.dailyLimitDays)
                            },
                            variant = ButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Weekday.entries.forEach { day ->
                            val active = day in controlState.dailyLimitDays
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) DoHSagePrimary else DoHCream)
                                    .clickable {
                                        val nextDays = if (active && controlState.dailyLimitDays.size > 1) {
                                            controlState.dailyLimitDays - day
                                        } else {
                                            controlState.dailyLimitDays + day
                                        }
                                        onUpdateDailyLimit(controlState.dailyDeviceLimitMinutes, nextDays)
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.shortLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) DoHWhite else DoHTextPrimary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Limites por Categoria
        item {
            Text(
                text = "Limites por Categoria",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val managedCategories = listOf(
                AppCategory.GAMES,
                AppCategory.VIDEOS,
                AppCategory.EDUCATION,
                AppCategory.COMMUNICATION
            )

            DoHCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    managedCategories.forEachIndexed { index, cat ->
                        val existing = controlState.categoryLimits[cat] ?: CategoryLimitRule(
                            category = cat,
                            dailyLimitMinutes = 60,
                            enabled = false
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cat.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DoHTextPrimary
                                )
                                Text(
                                    text = if (existing.enabled) {
                                        "Limite: ${RuleEvaluationEngine.formatMinutes(existing.dailyLimitMinutes)}"
                                    } else {
                                        "Sem limite por categoria"
                                    },
                                    fontSize = 11.sp,
                                    color = DoHTextSecondary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (existing.enabled) {
                                    TextButton(
                                        onClick = {
                                            onUpsertCategoryLimit(
                                                existing.copy(
                                                    dailyLimitMinutes = (existing.dailyLimitMinutes - 15).coerceAtLeast(15)
                                                )
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("-15m", fontSize = 12.sp, color = DoHSageDark)
                                    }
                                    TextButton(
                                        onClick = {
                                            onUpsertCategoryLimit(
                                                existing.copy(
                                                    dailyLimitMinutes = (existing.dailyLimitMinutes + 15).coerceAtMost(480)
                                                )
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("+15m", fontSize = 12.sp, color = DoHSageDark)
                                    }
                                }
                                DoHSwitch(
                                    checked = existing.enabled,
                                    onCheckedChange = { enabled ->
                                        onUpsertCategoryLimit(existing.copy(enabled = enabled))
                                    }
                                )
                            }
                        }

                        if (index < managedCategories.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DoHCardBorder)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Rotinas & Horários
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rotinas & Horários (${controlState.routines.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoHTextPrimary
                )
                TextButton(onClick = { showNewRoutineDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = DoHSageDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Nova Rotina",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoHSageDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(controlState.routines, key = { it.id }) { routine ->
            DoHCard(modifier = Modifier.padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DoHSageLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (routine.type) {
                                        RoutineType.BEDTIME -> Icons.Default.Bedtime
                                        RoutineType.STUDY -> Icons.Default.School
                                        RoutineType.REST -> Icons.Default.SelfImprovement
                                        RoutineType.FREE_TIME -> Icons.Default.WbSunny
                                        RoutineType.CUSTOM -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = DoHSageDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = routine.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DoHTextPrimary
                                )
                                Text(
                                    text = "${routine.startTime} às ${routine.endTime} • ${
                                        routine.daysOfWeek.sortedBy { it.ordinal }.joinToString(", ") { it.shortLabel }
                                    }",
                                    fontSize = 11.sp,
                                    color = DoHTextSecondary
                                )
                            }
                        }

                        DoHSwitch(
                            checked = routine.enabled,
                            onCheckedChange = { onToggleRoutine(routine.id, it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = routine.type.displayName,
                            fontSize = 11.sp,
                            color = DoHSageDark,
                            fontWeight = FontWeight.Medium
                        )
                        Row {
                            TextButton(
                                onClick = { editingRoutine = routine },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Editar", fontSize = 12.sp, color = DoHSageDark)
                            }
                            TextButton(
                                onClick = { onDeleteRoutine(routine.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Remover", fontSize = 12.sp, color = DoHBlock)
                            }
                        }
                    }
                }
            }
        }

        // 4. Tempo Extra & Histórico de Concessões
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tempo Extra & Histórico",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            DoHCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Conceder tempo adicional imediato no tablet",
                        fontSize = 12.sp,
                        color = DoHTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { mins ->
                            DoHButton(
                                text = "+${mins}m",
                                onClick = { onGrantExtraTime(mins, null) },
                                variant = ButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (controlState.grantHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DoHCardBorder)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Últimas concessões",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DoHTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        controlState.grantHistory.take(4).forEach { grant ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "+${grant.minutes} min • ${grant.targetAppName ?: "Tablet completo"}",
                                    fontSize = 12.sp,
                                    color = DoHTextPrimary
                                )
                                Text(
                                    text = sdf.format(Date(grant.grantedAtMillis)),
                                    fontSize = 11.sp,
                                    color = DoHTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Authorized Contacts
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Contatos Autorizados",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(ruleSettings.contacts) { contact ->
            DoHCard(modifier = Modifier.padding(vertical = 3.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DoHSageLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = DoHSageDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = contact.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHTextPrimary
                            )
                            if (contact.isEmergency) {
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusBadge(text = "SOS", tone = BadgeTone.ALERT)
                            }
                        }
                        Text(
                            text = "${contact.relationship} • ${contact.phone}",
                            fontSize = 12.sp,
                            color = DoHTextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Ligar",
                        tint = DoHSagePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 6. Device Information & Rule Sync Status
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sincronização de Regras & Dispositivo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            DoHCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Modo de Rede (Sync)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHTextPrimary
                            )
                            Text(
                                text = if (isOnline) "Online • Versão v${controlState.version}" else "Offline • Fila local ($syncEnvelopesCount)",
                                fontSize = 11.sp,
                                color = if (isOnline) DoHStatusPositive else DoHAlert
                            )
                        }
                        DoHSwitch(
                            checked = isOnline,
                            onCheckedChange = onToggleOnline
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DoHCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Modelo", fontSize = 13.sp, color = DoHTextSecondary)
                        Text(text = deviceInfo.model, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DoHTextPrimary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DoHCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Sistema Operacional", fontSize = 13.sp, color = DoHTextSecondary)
                        Text(text = deviceInfo.osVersion, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DoHTextPrimary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DoHCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Versão do MonitorDad Parent", fontSize = 13.sp, color = DoHTextSecondary)
                        Text(text = "v1.0.0-alpha03", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoHSageDark)
                    }
                }
            }
        }

        // 7. Official Signature & HoD Branding
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MonitorDad • Desenvolvido por HoD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoHSageDark,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Protegendo sua família com respeito, segurança e harmonia.",
                    fontSize = 11.sp,
                    color = DoHTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
