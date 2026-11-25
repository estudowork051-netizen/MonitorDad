package com.dohguard.parent.screens.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.dohguard.shared.components.*
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.AppUsageInfo
import com.dohguard.shared.model.UsageSummary
import com.dohguard.shared.theme.*

@Composable
fun AppsScreen(
    appsList: List<AppUsageInfo>,
    usageSummary: UsageSummary = UsageSummary.DEMO,
    isSyncing: Boolean = false,
    onManualSync: () -> Unit = {},
    onToggleBlockApp: (String) -> Unit,
    onUpdateAppLimit: (String, String, Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }
    var dohFilterEnabled by remember { mutableStateOf(true) }
    var safeSearchEnabled by remember { mutableStateOf(true) }
    var blockPurchasesEnabled by remember { mutableStateOf(true) }

    // App detail dialog selection
    var selectedAppForDetail by remember { mutableStateOf<AppUsageInfo?>(null) }

    val filteredApps = remember(selectedCategory, appsList) {
        if (selectedCategory == AppCategory.ALL) {
            appsList
        } else {
            appsList.filter { it.category == selectedCategory }
        }
    }

    // Detail dialog
    selectedAppForDetail?.let { app ->
        AppDetailDialog(
            app = app,
            onDismiss = { selectedAppForDetail = null },
            onSaveLimitDemo = { newLimit ->
                onUpdateAppLimit(app.packageName, app.appName, newLimit)
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
                text = "Apps & Conteúdo",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary
            )
            Text(
                text = "Monitoramento em tempo real e limites de utilização",
                fontSize = 13.sp,
                color = DoHTextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 1. Resumo de Utilização e Conectividade
        item {
            UsageSummaryCard(
                summary = usageSummary,
                isSyncing = isSyncing,
                onManualSync = onManualSync
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Filtros de Categoria (Todos, Jogos, Vídeos, Educação, Comunicação, Outros)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val categories = listOf(
                    AppCategory.ALL to "Todos",
                    AppCategory.GAMES to "Jogos",
                    AppCategory.VIDEOS to "Vídeos",
                    AppCategory.EDUCATION to "Educação",
                    AppCategory.COMMUNICATION to "Comunicação",
                    AppCategory.OTHER to "Outros"
                )

                items(categories) { (cat, label) ->
                    val selected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) DoHSagePrimary else DoHCream,
                        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, DoHCardBorder),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) DoHWhite else DoHTextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Card de Proteção de Conteúdo (Navegação Segura DoH, SafeSearch, Compras)
        item {
            DoHCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = DoHSagePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Proteção de Conteúdo",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DoHTextPrimary
                            )
                        }
                        StatusBadge(text = "ATIVO", tone = BadgeTone.POSITIVE)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // DoH DNS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Navegação Segura DoH",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHTextPrimary
                            )
                            Text(
                                text = "Filtro no nível de DNS criptografado contra sites maliciosos",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                        }
                        DoHSwitch(
                            checked = dohFilterEnabled,
                            onCheckedChange = { dohFilterEnabled = it }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = DoHCardBorder
                    )

                    // SafeSearch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Filtro de Busca Segura",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHTextPrimary
                            )
                            Text(
                                text = "Força SafeSearch em navegadores e vídeos",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                        }
                        DoHSwitch(
                            checked = safeSearchEnabled,
                            onCheckedChange = { safeSearchEnabled = it }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = DoHCardBorder
                    )

                    // In-app Purchases
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bloqueio de Compras em Apps",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHTextPrimary
                            )
                            Text(
                                text = "Exige aprovação para microtransações e downloads pagos",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                        }
                        DoHSwitch(
                            checked = blockPurchasesEnabled,
                            onCheckedChange = { blockPurchasesEnabled = it }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // 4. Seção da Lista de Aplicativos
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aplicativos Monitorados (${filteredApps.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoHTextPrimary
                )
                Text(
                    text = "Toque para detalhes",
                    fontSize = 11.sp,
                    color = DoHSageDark
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // List of Apps with clickable card
        items(filteredApps) { app ->
            DoHCard(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { selectedAppForDetail = app }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconImage(
                        packageName = app.packageName,
                        appName = app.appName,
                        category = app.category,
                        isBlocked = app.isBlocked,
                        size = 46.dp,
                        cornerRadius = 14.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.appName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (app.isBlocked) DoHTextSecondary else DoHTextPrimary
                            )
                            Text(
                                text = if (app.isBlocked) "Bloqueado" else app.formattedUsageTime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (app.isBlocked) DoHBlock else DoHSageDark
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { if (app.isBlocked) 1f else app.usagePercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (app.isBlocked) DoHBlock else DoHSagePrimary,
                            trackColor = DoHSageLight
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Último uso: ${app.lastTimeUsedFormatted}",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                            Text(
                                text = "Limite: ${app.formattedLimitTime}",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Switch visual de bloqueio
                    DoHSwitch(
                        checked = !app.isBlocked,
                        onCheckedChange = { onToggleBlockApp(app.packageName) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
