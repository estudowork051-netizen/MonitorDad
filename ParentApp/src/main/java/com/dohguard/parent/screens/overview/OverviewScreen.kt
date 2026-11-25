package com.dohguard.parent.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.parent.navigation.ParentTab
import com.dohguard.parent.screens.modals.PlaySoundDialog
import com.dohguard.parent.screens.modals.RemotePairingDialog
import com.dohguard.parent.screens.modals.RequestApprovalDialog
import com.dohguard.shared.components.*
import com.dohguard.shared.model.*
import com.dohguard.shared.remote.*
import com.dohguard.shared.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewScreen(
    deviceInfo: DeviceInfo,
    screenTimeState: ScreenTimeState,
    pendingRequests: List<PendingRequest>,
    topApps: List<AppUsageInfo>,
    onAddFifteenMinutes: () -> Unit,
    onTogglePauseDevice: () -> Unit,
    onApproveRequest: (PendingRequest) -> Unit,
    onRejectRequest: (PendingRequest) -> Unit,
    modifier: Modifier = Modifier,
    remoteSnapshot: DeviceRemoteSnapshot = DeviceRemoteSnapshot(
        deviceId = deviceInfo.deviceId,
        deviceName = deviceInfo.deviceName
    ),
    remoteCommands: List<RemoteCommand> = emptyList(),
    remoteNotifications: List<ParentNotificationEvent> = emptyList(),
    pairedCredentials: PairedDeviceCredentials = PairedDeviceCredentials(),
    usageSummary: UsageSummary = UsageSummary.DEMO,
    telemetryPayload: TelemetryPayload? = null,
    isSyncing: Boolean = false,
    onPlaySound: () -> Unit = {},
    onManualSync: () -> Unit = {},
    onConfigurePairing: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onMarkNotificationsRead: () -> Unit = {},
    onNavigateToTab: (ParentTab) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val connectionSettings by RemoteControlRepository.connectionSettings.collectAsState()
    val linkedDevices by RemoteControlRepository.linkedDevices.collectAsState()
    val diagnosticsReport by RemoteControlRepository.diagnosticsReport.collectAsState()

    var showSoundDialog by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showConnectionHub by remember { mutableStateOf(false) }
    var showSetupWizard by remember { mutableStateOf(false) }
    var isRunningDiagnostics by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<PendingRequest?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    if (showSoundDialog) {
        PlaySoundDialog(
            deviceName = deviceInfo.deviceName,
            onDismiss = { showSoundDialog = false },
            onTriggerSound = {
                onPlaySound()
                feedbackMessage = "Comando 'Tocar som' enfileirado. Aguardando confirmação de ${deviceInfo.deviceName}."
            }
        )
    }

    if (showConnectionHub) {
        ConnectionHubDialog(
            isParentApp = true,
            snapshot = remoteSnapshot,
            settings = connectionSettings,
            linkedDevices = linkedDevices,
            diagnosticsReport = diagnosticsReport,
            isSyncing = isSyncing,
            isRunningDiagnostics = isRunningDiagnostics,
            onDismiss = { showConnectionHub = false },
            onAddOrPairDevice = {
                showConnectionHub = false
                showPairingDialog = true
            },
            onRunDiagnostics = {
                isRunningDiagnostics = true
                coroutineScope.launch {
                    RemoteControlRepository.runConnectionDiagnostics("parent")
                    isRunningDiagnostics = false
                }
            },
            onOpenWizard = {
                showConnectionHub = false
                showSetupWizard = true
            },
            onUpdateSettings = { updated ->
                RemoteControlRepository.updateConnectionSettings(updated)
                feedbackMessage = "Preferências de conexão atualizadas (${updated.mode.label})."
            },
            onRevokeDevice = { devId ->
                RemoteControlRepository.unpairAndRevokeDevice(devId)
                feedbackMessage = "Dispositivo $devId desconectado e revogado."
            }
        )
    }

    if (showSetupWizard) {
        SetupWizardDialog(
            isParentApp = true,
            initialSettings = connectionSettings,
            onDismiss = { showSetupWizard = false },
            onFinish = { mode, relayUrl ->
                RemoteControlRepository.updateConnectionSettings(
                    connectionSettings.copy(mode = mode, relayBaseUrl = relayUrl)
                )
                RemoteControlRepository.markWizardCompleted(true)
                showSetupWizard = false
                showPairingDialog = true
            }
        )
    }

    if (showPairingDialog) {
        RemotePairingDialog(
            currentCredentials = pairedCredentials,
            onDismiss = { showPairingDialog = false },
            onSavePairing = { devId, parId, devName, url, secret ->
                onConfigurePairing(devId, parId, devName, url, secret)
                feedbackMessage = if (url.isBlank()) {
                    "Pareamento salvo (modo Wi-Fi local: nenhuma URL de relay configurada)."
                } else {
                    "Relay HTTPS e chaves HMAC salvos. Iniciando sincronização..."
                }
            },
            onCodeClaimed = { creds ->
                feedbackMessage = "Tablet ${creds.deviceName} vinculado com sucesso! Sincronizando..."
                onManualSync()
            }
        )
    }

    selectedRequest?.let { request ->
        RequestApprovalDialog(
            request = request,
            onDismiss = { selectedRequest = null },
            onApprove = { req ->
                onApproveRequest(req)
                feedbackMessage = "Solicitação aprovada (+${req.requestedMinutes} min) e enviada para sincronização."
            },
            onReject = { req ->
                onRejectRequest(req)
                feedbackMessage = "Solicitação recusada."
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DoHBackground),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // 1. Header with Brand, PAIS badge & Device profile quick access
        item {
            DoHHeader(
                deviceInfo = deviceInfo,
                onDeviceProfileClick = { showConnectionHub = true }
            )
        }

        // 1.5 Card "EM USO AGORA" (real foreground app or waiting state)
        item {
            val fgPkg = telemetryPayload?.currentForegroundPackage
            val fgCategory = telemetryPayload?.apps
                ?.firstOrNull { it.packageName == fgPkg }
                ?.category ?: AppCategory.OTHER
            CurrentAppCard(
                deviceName = deviceInfo.deviceName,
                appName = telemetryPayload?.currentForegroundAppName,
                packageName = fgPkg,
                startedAtMillis = telemetryPayload?.currentForegroundStartedAtMillis,
                lastUpdateFormatted = if (telemetryPayload != null && !usageSummary.isDemoData) {
                    usageSummary.lastSyncFormatted
                } else null,
                syncStatus = if (remoteSnapshot.connectionState == RemoteConnectionState.ONLINE) {
                    usageSummary.syncStatus
                } else {
                    SyncStatus.OFFLINE
                },
                isDemoData = telemetryPayload == null || usageSummary.isDemoData,
                category = fgCategory,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        // Demo feedback banner if present
        if (feedbackMessage != null) {
            item {
                Surface(
                    color = DoHSageLight,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = DoHSageDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feedbackMessage ?: "",
                            fontSize = 12.sp,
                            color = DoHTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { feedbackMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = DoHTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Main Device Status Card
        item {
            DoHCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Status Atual",
                                fontSize = 12.sp,
                                color = DoHTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (deviceInfo.isPaused) "Dispositivo Pausado" else "Protegido & Liberado",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (deviceInfo.isPaused) DoHAlert else DoHStatusPositive
                            )
                        }

                        StatusBadge(
                            text = if (deviceInfo.isPaused) "PAUSADO" else "SEGURO",
                            tone = if (deviceInfo.isPaused) BadgeTone.ALERT else BadgeTone.POSITIVE
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Wave Screen Time Meter
                    WaveScreenTimeMeter(screenTimeState = screenTimeState)

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Usado hoje", fontSize = 11.sp, color = MonitorTextSecondary)
                            Text(
                                screenTimeState.formattedUsed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonitorTextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Limite diário", fontSize = 11.sp, color = MonitorTextSecondary)
                            Text(
                                screenTimeState.formattedLimit,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonitorElectricCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Restante", fontSize = 11.sp, color = MonitorTextSecondary)
                            Text(
                                screenTimeState.formattedRemainingPadded,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (screenTimeState.remainingMinutes <= 15) MonitorAlertRed else MonitorNeonGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons Row: +15m, Pause/Resume, Play Sound
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Add 15 Min Button
                        DoHButton(
                            text = "+15 min",
                            onClick = {
                                onAddFifteenMinutes()
                                feedbackMessage = "Comando +15 min enfileirado para confirmação do tablet."
                            },
                            icon = Icons.Default.MoreTime,
                            variant = ButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )

                        // Pause / Release Button
                        DoHButton(
                            text = if (deviceInfo.isPaused) "Liberar" else "Pausar",
                            onClick = {
                                onTogglePauseDevice()
                                feedbackMessage = if (deviceInfo.isPaused) {
                                    "Comando 'Liberar tablet' enfileirado."
                                } else {
                                    "Comando 'Pausar tablet' enfileirado."
                                }
                            },
                            icon = if (deviceInfo.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            variant = if (deviceInfo.isPaused) ButtonVariant.PRIMARY else ButtonVariant.OUTLINED,
                            modifier = Modifier.weight(1f)
                        )

                        // Play Sound Button
                        IconButton(
                            onClick = { showSoundDialog = true },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(DoHButtonCornerRadius))
                                .background(DoHSageLight)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Tocar som",
                                tint = DoHSageDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Ações rápidas de navegação
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DoHButton(
                            text = "Apps",
                            onClick = { onNavigateToTab(ParentTab.APPS) },
                            icon = Icons.Default.Apps,
                            variant = ButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        DoHButton(
                            text = "Rotina & Limites",
                            onClick = { onNavigateToTab(ParentTab.RULES) },
                            icon = Icons.Default.Schedule,
                            variant = ButtonVariant.OUTLINED,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2b. Conexão e Dispositivos (Local Wi-Fi <-> Internet)
        item {
            val healthDotColor = healthColorToCompose(remoteSnapshot.healthColor)
            val connTone = when (remoteSnapshot.healthColor) {
                ConnectionHealthColor.GREEN -> BadgeTone.POSITIVE
                ConnectionHealthColor.YELLOW -> BadgeTone.ALERT
                ConnectionHealthColor.RED -> BadgeTone.BLOCK
                ConnectionHealthColor.GRAY -> BadgeTone.NEUTRAL
            }
            val connLabel = when {
                isSyncing -> "SINCRONIZANDO"
                remoteSnapshot.connectionState == RemoteConnectionState.ONLINE ->
                    remoteSnapshot.activeTransport.label.uppercase()
                else -> remoteSnapshot.connectionState.label.uppercase()
            }
            val lastCommText = formatCommTimestamp(remoteSnapshot.lastCommunicationMillis)

            DoHCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
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
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(healthDotColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Conexão e dispositivos",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DoHTextPrimary
                                )
                                Text(
                                    text = "${remoteSnapshot.statusSummary} • $lastCommText",
                                    fontSize = 11.sp,
                                    color = DoHTextSecondary
                                )
                            }
                        }
                        StatusBadge(
                            text = connLabel,
                            tone = connTone
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Rotinas ativas: ${remoteSnapshot.activeRoutineNames.size} • Apps bloqueados: ${remoteSnapshot.blockedAppsCount} • Fila pendente: ${remoteSnapshot.pendingCommandsCount}",
                        fontSize = 12.sp,
                        color = DoHTextSecondary
                    )

                    if (!remoteSnapshot.lastError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = remoteSnapshot.lastError ?: "",
                            fontSize = 11.sp,
                            color = DoHAlert
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DoHButton(
                            text = "Sincronizar",
                            onClick = onManualSync,
                            icon = Icons.Default.Sync,
                            variant = ButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        DoHButton(
                            text = "Testar / Ajustes",
                            onClick = { showConnectionHub = true },
                            icon = Icons.Default.Devices,
                            variant = ButtonVariant.OUTLINED,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    DoHButton(
                        text = if (pairedCredentials.isPaired) "Adicionar / Gerenciar dispositivo" else "Adicionar dispositivo (QR / Código)",
                        onClick = { showPairingDialog = true },
                        icon = Icons.Default.QrCode2,
                        variant = if (pairedCredentials.isPaired) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2c. Unread Parent Notifications
        val unreadNotifications = remoteNotifications.filter { !it.isRead }
        if (unreadNotifications.isNotEmpty()) {
            item {
                DoHCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Alertas Recentes (${unreadNotifications.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DoHTextPrimary
                            )
                            TextButton(onClick = onMarkNotificationsRead) {
                                Text("Marcar lidas", fontSize = 12.sp, color = DoHSageDark)
                            }
                        }
                        unreadNotifications.take(3).forEach { ev ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• ${ev.title}: ${ev.message}",
                                fontSize = 12.sp,
                                color = DoHTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 2d. Remote Command History
        if (remoteCommands.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Histórico de Comandos Remotos",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoHTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    remoteCommands.take(5).forEach { cmd ->
                        val cmdTone = when (cmd.status) {
                            CommandStatus.EXECUTED -> BadgeTone.POSITIVE
                            CommandStatus.SENT, CommandStatus.RECEIVED -> BadgeTone.ALERT
                            CommandStatus.FAILED, CommandStatus.EXPIRED -> BadgeTone.BLOCK
                            CommandStatus.PENDING -> BadgeTone.NEUTRAL
                        }
                        DoHCard(modifier = Modifier.padding(vertical = 3.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cmd.type.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DoHTextPrimary
                                    )
                                    val detail = cmd.resultMessage ?: cmd.errorMessage ?: "Aguardando confirmação do tablet"
                                    Text(
                                        text = detail,
                                        fontSize = 11.sp,
                                        color = DoHTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusBadge(
                                    text = cmd.status.label.uppercase(),
                                    tone = cmdTone
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Pending Requests Area
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Solicitações Pendentes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoHTextPrimary
                    )
                    if (pendingRequests.isNotEmpty()) {
                        StatusBadge(
                            text = "${pendingRequests.size} nova",
                            tone = BadgeTone.ALERT
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (pendingRequests.isEmpty()) {
                    DoHCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhuma solicitação pendente no momento.",
                                fontSize = 13.sp,
                                color = DoHTextSecondary
                            )
                        }
                    }
                } else {
                    pendingRequests.forEach { request ->
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
                                        .background(DoHSageLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = DoHSageDark,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = request.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DoHTextPrimary
                                    )
                                    Text(
                                        text = request.description,
                                        fontSize = 12.sp,
                                        color = DoHTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                DoHButton(
                                    text = "Revisar",
                                    onClick = { selectedRequest = request },
                                    variant = ButtonVariant.PRIMARY,
                                    modifier = Modifier.height(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Most Used Apps List
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Mais Utilizados Hoje",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoHTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        items(topApps) { app ->
            DoHCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
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
                        size = 44.dp,
                        cornerRadius = 12.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = app.appName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHTextPrimary
                            )
                            Text(
                                text = app.formattedUsageTime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DoHSageDark
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Usage Progress Bar
                        LinearProgressIndicator(
                            progress = { app.usagePercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
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
                                text = "Limite: ${app.formattedLimitTime}",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                            if (app.isBlocked) {
                                Text(
                                    text = "Bloqueado",
                                    fontSize = 11.sp,
                                    color = DoHBlock,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
