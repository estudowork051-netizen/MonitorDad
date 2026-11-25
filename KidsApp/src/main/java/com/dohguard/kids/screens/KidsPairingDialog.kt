package com.dohguard.kids.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dohguard.kids.remote.LocalKidsServer
import com.dohguard.shared.components.ButtonVariant
import com.dohguard.shared.components.ConnectionHubDialog
import com.dohguard.shared.components.DoHButton
import com.dohguard.shared.components.SetupWizardDialog
import com.dohguard.shared.components.healthColorToCompose
import com.dohguard.shared.remote.AndroidNsdDiscovery
import com.dohguard.shared.remote.LocalEndpointInfo
import com.dohguard.shared.remote.PairedDeviceCredentials
import com.dohguard.shared.remote.PairingCompletionStatus
import com.dohguard.shared.remote.PairingInitiation
import com.dohguard.shared.remote.PairingManager
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun KidsPairingDialog(
    currentCredentials: PairedDeviceCredentials,
    onDismiss: () -> Unit,
    onSavePairing: (deviceId: String, parentId: String, deviceName: String, relayUrl: String, sharedSecret: String) -> Unit,
    onPairingCompleted: (PairedDeviceCredentials) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val snapshot by RemoteControlRepository.snapshot.collectAsState()
    val connectionSettings by RemoteControlRepository.connectionSettings.collectAsState()
    val linkedDevices by RemoteControlRepository.linkedDevices.collectAsState()
    val diagnosticsReport by RemoteControlRepository.diagnosticsReport.collectAsState()
    val isSyncing by RemoteControlRepository.isSyncing.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Wi-Fi local (código/QR do Parent), 1 = Internet (Relay)
    var useManualMode by remember { mutableStateOf(false) }
    var showHubDialog by remember { mutableStateOf(false) }
    var showWizardDialog by remember { mutableStateOf(false) }
    var isRunningDiagnostics by remember { mutableStateOf(false) }

    val defaultTabletName = remember {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim().ifBlank { "Tablet do Lucas" }
    }
    var deviceId by remember { mutableStateOf(currentCredentials.deviceId) }
    var parentId by remember { mutableStateOf(currentCredentials.parentId) }
    var deviceName by remember { mutableStateOf(currentCredentials.deviceName.ifBlank { defaultTabletName }) }
    var relayUrl by remember {
        mutableStateOf(
            currentCredentials.relayBaseUrl.ifBlank { connectionSettings.relayBaseUrl }
        )
    }
    var sharedSecret by remember { mutableStateOf(currentCredentials.sharedSecret) }
    var codeOrQrInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    var discoveredParent by remember { mutableStateOf<LocalEndpointInfo?>(null) }
    var isClaimingLocal by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var initiation by remember { mutableStateOf<PairingInitiation?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(0) }

    // Descobre automaticamente o ParentApp na mesma rede Wi-Fi durante o pareamento
    DisposableEffect(selectedTab) {
        val nsd = if (selectedTab == 0) AndroidNsdDiscovery(context) else null
        nsd?.startDiscovery(
            serviceType = AndroidNsdDiscovery.SERVICE_TYPE_PAIRING,
            onServiceResolved = { endpoint, _ ->
                discoveredParent = endpoint
            }
        )
        onDispose {
            nsd?.stopDiscovery()
        }
    }

    LaunchedEffect(initiation) {
        val active = initiation ?: return@LaunchedEffect
        while (isActive) {
            val secs = ((active.expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt()
            if (secs <= 0) {
                remainingSeconds = 0
                validationError = "Código expirado. Gere um novo código."
                initiation = null
                break
            }
            remainingSeconds = secs

            val pollResult = RemoteControlRepository.pollKidsPairingCompletion(
                relayBaseUrl = relayUrl,
                pairingCode = active.pairingCode,
                claimToken = active.claimToken
            )
            val completion = pollResult.getOrNull()
            if (completion != null && completion.status == PairingCompletionStatus.PAIRED) {
                LocalKidsServer.startIfPaired(context)
                onPairingCompleted(RemoteControlRepository.credentials.value)
                onDismiss()
                break
            }
            delay(3_000L)
        }
    }

    if (showHubDialog) {
        ConnectionHubDialog(
            isParentApp = false,
            snapshot = snapshot,
            settings = connectionSettings,
            linkedDevices = linkedDevices,
            diagnosticsReport = diagnosticsReport,
            isSyncing = isSyncing,
            isRunningDiagnostics = isRunningDiagnostics,
            onDismiss = { showHubDialog = false },
            onAddOrPairDevice = { showHubDialog = false },
            onRunDiagnostics = {
                isRunningDiagnostics = true
                coroutineScope.launch {
                    RemoteControlRepository.runConnectionDiagnostics("kids")
                    isRunningDiagnostics = false
                }
            },
            onOpenWizard = {
                showHubDialog = false
                showWizardDialog = true
            },
            onUpdateSettings = { updated ->
                RemoteControlRepository.updateConnectionSettings(updated)
            },
            onRevokeDevice = { devId ->
                RemoteControlRepository.unpairAndRevokeDevice(devId)
                LocalKidsServer.stop()
            }
        )
    }

    if (showWizardDialog) {
        SetupWizardDialog(
            isParentApp = false,
            initialSettings = connectionSettings,
            onDismiss = { showWizardDialog = false },
            onFinish = { mode, url ->
                RemoteControlRepository.updateConnectionSettings(
                    connectionSettings.copy(mode = mode, relayBaseUrl = url)
                )
                if (url.isNotBlank()) relayUrl = url
                RemoteControlRepository.markWizardCompleted(true)
                showWizardDialog = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DoHDialogCornerRadius),
            color = DoHWhite,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Conexão e pareamento",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoHTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showHubDialog = true }) {
                        Icon(Icons.Default.Devices, contentDescription = "Conexão", tint = DoHSageDark)
                    }
                }

                // Cartão de estado atual do pareamento
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DoHBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DoHBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(healthColorToCompose(snapshot.healthColor))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = snapshot.statusSummary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHTextPrimary
                            )
                        }
                        if (currentCredentials.isPaired) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Vinculado ao responsável: ${currentCredentials.parentId} • Canal: ${snapshot.activeTransport.label}",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Este tablet ainda não está vinculado a nenhum responsável.",
                                fontSize = 11.sp,
                                color = DoHTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            validationError = null
                        },
                        label = { Text("Wi-Fi local (Código / QR)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            validationError = null
                        },
                        label = { Text("Internet (Relay)", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // Pareamento local digitando código ou colando payload do QR Code
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (discoveredParent != null) DoHSageSurface else DoHBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (discoveredParent != null) Icons.Default.CheckCircle else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (discoveredParent != null) DoHStatusOnline else DoHSageDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (discoveredParent != null) {
                                    "Celular dos pais encontrado na rede (${discoveredParent!!.host})."
                                } else {
                                    "Procurando celular dos pais na mesma rede Wi-Fi..."
                                },
                                fontSize = 11.sp,
                                color = DoHTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Nome deste tablet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = codeOrQrInput,
                        onValueChange = { codeOrQrInput = it },
                        label = { Text("Código do app dos pais ou QR") },
                        placeholder = { Text("Ex: AB12CD ou monitordad://pair?...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DoHButton(
                        text = if (isClaimingLocal) "Vinculando..." else "Confirmar vinculação",
                        onClick = {
                            val parsed = PairingManager.decodeQrPayload(codeOrQrInput)
                            if (parsed == null) {
                                validationError = "Digite o código de 6 caracteres exibido no celular dos pais."
                                return@DoHButton
                            }
                            val targetBaseUrl = when {
                                parsed.localHost.isNotBlank() && parsed.localPort in 1..65535 ->
                                    "http://${parsed.localHost}:${parsed.localPort}"
                                discoveredParent != null -> discoveredParent!!.baseUrl()
                                else -> ""
                            }
                            if (targetBaseUrl.isBlank()) {
                                validationError = "Celular dos pais não localizado na rede Wi-Fi. Abra 'Adicionar dispositivo' no celular dos pais na mesma rede Wi-Fi ou cole o texto completo do QR Code."
                                return@DoHButton
                            }
                            isClaimingLocal = true
                            validationError = null
                            coroutineScope.launch {
                                val res = RemoteControlRepository.claimLocalPairingCode(
                                    localPairingBaseUrl = targetBaseUrl,
                                    pairingCode = parsed.code,
                                    deviceId = deviceId.trim().ifBlank { "tablet-lucas-01" },
                                    deviceName = deviceName.trim().ifBlank { defaultTabletName }
                                )
                                isClaimingLocal = false
                                res.onSuccess { creds ->
                                    LocalKidsServer.startIfPaired(context)
                                    statusFeedback = "Tablet vinculado ao responsável ${creds.parentId}!"
                                    onPairingCompleted(creds)
                                    onDismiss()
                                }.onFailure { err ->
                                    validationError = err.message ?: "Não foi possível confirmar o pareamento."
                                }
                            }
                        },
                        enabled = !isClaimingLocal,
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Pareamento pela internet (Relay)
                    TextButton(
                        onClick = {
                            useManualMode = !useManualMode
                            validationError = null
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (useManualMode) "Voltar para código temporário" else "Configuração manual avançada",
                            fontSize = 12.sp,
                            color = DoHSageDark
                        )
                    }

                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = { relayUrl = it },
                        label = { Text("URL do servidor remoto (HTTPS)") },
                        placeholder = { Text("https://relay.seudominio.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Nome do tablet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = { deviceId = it },
                        label = { Text("ID do dispositivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!useManualMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (initiation != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DoHSageSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, DoHSageLight, RoundedCornerShape(14.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "CÓDIGO PARA DIGITAR NO APP DOS PAIS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DoHSageDark
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = initiation!!.pairingCode,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 4.sp,
                                        color = DoHTextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val mins = remainingSeconds / 60
                                    val secs = remainingSeconds % 60
                                    Text(
                                        text = "Expira em %02d:%02d • Aguardando confirmação...".format(mins, secs),
                                        fontSize = 11.sp,
                                        color = DoHTextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        DoHButton(
                            text = when {
                                isGenerating -> "Gerando código..."
                                initiation != null -> "Gerar novo código"
                                else -> "Gerar código remoto"
                            },
                            onClick = {
                                val trimmedUrl = relayUrl.trim().trimEnd('/')
                                val isAllowedUrl = trimmedUrl.startsWith("https://") ||
                                    trimmedUrl.startsWith("http://127.0.0.1") ||
                                    trimmedUrl.startsWith("http://localhost")
                                if (!isAllowedUrl) {
                                    validationError = "Informe uma URL HTTPS válida para o servidor remoto."
                                    return@DoHButton
                                }
                                isGenerating = true
                                validationError = null
                                coroutineScope.launch {
                                    val res = RemoteControlRepository.initiateKidsPairing(
                                        relayBaseUrl = trimmedUrl,
                                        deviceId = deviceId,
                                        deviceName = deviceName
                                    )
                                    isGenerating = false
                                    res.onSuccess { init ->
                                        initiation = init
                                    }.onFailure { err ->
                                        validationError = err.message ?: "Falha ao conectar no servidor remoto."
                                    }
                                }
                            },
                            enabled = !isGenerating,
                            variant = ButtonVariant.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = parentId,
                            onValueChange = { parentId = it },
                            label = { Text("ID do responsável") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = sharedSecret,
                            onValueChange = { sharedSecret = it },
                            label = { Text("Chave secreta HMAC") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DoHButton(
                            text = "Salvar configuração manual",
                            onClick = {
                                val trimmedUrl = relayUrl.trim().trimEnd('/')
                                val isAllowedUrl = trimmedUrl.startsWith("https://") ||
                                    trimmedUrl.startsWith("http://127.0.0.1") ||
                                    trimmedUrl.startsWith("http://localhost")
                                if (!isAllowedUrl) {
                                    validationError = "Informe uma URL HTTPS válida."
                                    return@DoHButton
                                }
                                if (sharedSecret.trim().length < 16) {
                                    validationError = "A chave secreta HMAC deve ter pelo menos 16 caracteres."
                                    return@DoHButton
                                }
                                onSavePairing(
                                    deviceId.trim().ifBlank { "tablet-lucas-01" },
                                    parentId.trim().ifBlank { "parent-hod-01" },
                                    deviceName.trim().ifBlank { defaultTabletName },
                                    trimmedUrl,
                                    sharedSecret.trim()
                                )
                                LocalKidsServer.startIfPaired(context)
                                onDismiss()
                            },
                            variant = ButtonVariant.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (!statusFeedback.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(statusFeedback ?: "", fontSize = 12.sp, color = DoHStatusOnline)
                }

                if (!validationError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError ?: "",
                        fontSize = 12.sp,
                        color = DoHAlert
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DoHButton(
                        text = "Testar / Ajustes",
                        onClick = { showHubDialog = true },
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    DoHButton(
                        text = "Fechar",
                        onClick = onDismiss,
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
