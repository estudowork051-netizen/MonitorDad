package com.dohguard.parent.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dohguard.shared.components.ButtonVariant
import com.dohguard.shared.components.DoHButton
import com.dohguard.shared.model.RoutineRule
import com.dohguard.shared.model.RoutineType
import com.dohguard.shared.model.TimeWindow
import com.dohguard.shared.model.Weekday
import com.dohguard.shared.theme.*
import java.util.UUID

@Composable
fun RoutineEditorDialog(
    initialRoutine: RoutineRule?,
    onDismiss: () -> Unit,
    onSave: (RoutineRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRoutine?.name ?: "Nova Rotina") }
    var type by remember { mutableStateOf(initialRoutine?.type ?: RoutineType.CUSTOM) }
    var startTime by remember { mutableStateOf(initialRoutine?.startTime ?: "19:00") }
    var endTime by remember { mutableStateOf(initialRoutine?.endTime ?: "20:30") }
    var selectedDays by remember { mutableStateOf(initialRoutine?.daysOfWeek ?: Weekday.ALL_DAYS) }
    var blockAllExceptAllowed by remember {
        mutableStateOf(initialRoutine?.blockAllExceptAllowed ?: true)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DoHDialogCornerRadius),
            color = DoHWhite,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (initialRoutine == null) "Nova Rotina" else "Editar Rotina",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoHTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da rotina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tipo de rotina",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoHTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoutineType.entries.chunked(2).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowTypes.forEach { rt ->
                                val selected = type == rt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) DoHSagePrimary else DoHCream)
                                        .clickable { type = rt }
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = rt.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) DoHWhite else DoHTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Início (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Término (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Dias da semana",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoHTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Weekday.entries.forEach { day ->
                        val active = day in selectedDays
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) DoHSagePrimary else DoHCream)
                                .clickable {
                                    selectedDays = if (active && selectedDays.size > 1) {
                                        selectedDays - day
                                    } else {
                                        selectedDays + day
                                    }
                                }
                                .padding(vertical = 8.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bloquear apps não liberados",
                        fontSize = 13.sp,
                        color = DoHTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = blockAllExceptAllowed,
                        onCheckedChange = { blockAllExceptAllowed = it }
                    )
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = validationError ?: "",
                        fontSize = 12.sp,
                        color = DoHBlock
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DoHButton(
                        text = "Cancelar",
                        onClick = onDismiss,
                        variant = ButtonVariant.OUTLINED,
                        modifier = Modifier.weight(1f)
                    )
                    DoHButton(
                        text = "Salvar",
                        onClick = {
                            if (name.isBlank()) {
                                validationError = "Informe um nome para a rotina."
                                return@DoHButton
                            }
                            if (TimeWindow.parseMinutes(startTime) == null ||
                                TimeWindow.parseMinutes(endTime) == null
                            ) {
                                validationError = "Use o formato HH:mm (ex: 21:00)."
                                return@DoHButton
                            }
                            val saved = RoutineRule(
                                id = initialRoutine?.id ?: "routine-${UUID.randomUUID().toString().take(8)}",
                                name = name.trim(),
                                type = type,
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                daysOfWeek = selectedDays,
                                allowedPackages = initialRoutine?.allowedPackages ?: emptySet(),
                                blockedPackages = initialRoutine?.blockedPackages ?: emptySet(),
                                blockAllExceptAllowed = if (type == RoutineType.FREE_TIME) false else blockAllExceptAllowed,
                                enabled = initialRoutine?.enabled ?: true
                            )
                            onSave(saved)
                            onDismiss()
                        },
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
