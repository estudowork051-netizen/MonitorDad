package com.dohguard.parent.screens.modals

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dohguard.parent.remote.LocalPairingServer
import com.dohguard.shared.components.ButtonVariant
import com.dohguard.shared.components.DoHButton
import com.dohguard.shared.components.QrCodeView
import com.dohguard.shared.remote.AndroidNsdDiscovery
import com.dohguard.shared.remote.PairedDeviceCredentials
import com.dohguard.shared.remote.PairingManager
import com.dohguard.shared.remote.PairingQrPayload
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.remote.RemoteSecurity
import com.dohguard.shared.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun RemotePairingDialog(
    currentCredentials: PairedDeviceCredentials,
    onDismiss: () -> Unit,
    onSavePairing: (deviceId: String, parentId: String, deviceName: String, relayUrl: String, sharedSecret: String) -> Unit,
    onCodeClaimed: (PairedDeviceCredentials) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Wi-Fi Local (QR + Código), 1 = Internet (Relay)
    var useManualMode by remember { mutableStateOf(false) }

    var parentId by remember { mutableStateOf(currentCredentials.parentId.ifBlank { "parent-hod-01" }) }
    val parentDeviceName = remember {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim().ifBlank { "Celular do Responsável" }
    }
    var deviceId by remember { mutableStateOf(currentCredentials.deviceId) }
    var deviceName by remember { mutableStateOf(currentCredentials.deviceName) }
    var relayUrl by remember {
        mutableStateOf(
            currentCredentials.relayBaseUrl.ifBlank {
                RemoteControlRepository.connectionSettings.value.relayBaseUrl
            }
        )
    }
    var pairingCodeInput by remember { mutableStateOf("") }
    var sharedSecret by remember {
        mutableStateOf(currentCredentials.sharedSecret.ifBlank { RemoteSecurity.generateSharedSecret() })
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isClaiming by remember { mutableStateOf(false) }

    // Sessão local de pareamento
    val linkedDevices by RemoteControlRepository.linkedDevices.collectAsState()
    var localPort by remember { mutableIntStateOf(0) }
    val localHost = remember { AndroidNsdDiscovery.getLocalWifiIpv4Address().orEmpty() }
    var activeSession by remember {
        mutableStateOf(
            RemoteControlRepository.pairingManager.generateNewSession(
                parentId = parentId,
                parentName = parentDeviceName
            )
        )
    }
    var remainingSeconds by remember { mutableIntStateOf(600) }

    // Inicia servidor local temporário enquanto a tela está aberta
    DisposableEffect(Unit) {
        val server = LocalPairingServer(context)
        val port = runCatching {
            server.start(
                parentId = parentId,
                parentName = parentDeviceName,
                onDevicePaired = { creds ->
                    coroutineScope.launch {
                        onCodeClaimed(creds)
                        onDismiss()
                    }
                }
            )
        }.getOrDefault(0)
        localPort = port
        onDispose {
            server.stop()
        }
    }

    LaunchedEffect(activeSession) {
        while (isActive) {
            val secs = ((activeSession.expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt()
            if (secs <= 0) {
                remainingSeconds = 0
                break
            }
            remainingSeconds = secs
            delay(1_000L)
        }
    }

    val qrPayloadString = remember(activeSession, localHost, localPort, relayUrl) {
        PairingManager.encodeQrPayload(
            PairingQrPayload(
                code = activeSession.code,
                parentId = activeSession.parentId,
                parentName = activeSession.parentName,
                localHost = localHost,
                localPort = localPort,
                relayUrl = relayUrl.trim(),
                expiresAtMillis = activeSession.expiresAtMillis
            )
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
                Text(
                    text = "Adicionar dispositivo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoHTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aparelho atual: $parentDeviceName",
                    fontSize = 12.sp,
                    color = DoHTextSecondary
                )

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
                        label = { Text("Wi-Fi local (QR / Código)", fontSize = 11.sp) }
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

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // Pareamento direto pelo Wi-Fi com Código + QR Code
                    Text(
                        text = "No tablet MonitorDad Kids, abra Configurações > Vincular aparelho e digite o código abaixo ou leia o QR Code:",
                        fontSize = 12.sp,
                        color = DoHTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeView(
                            payload = qrPayloadString,
                            modifier = Modifier.size(180.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                                text = "CÓDIGO TEMPORÁRIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoHSageDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeSession.code.chunked(3).joinToString(" "),
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
                                text = if (remainingSeconds > 0) {
                                    "Expira em %02d:%02d • Uso único".format(mins, secs)
                                } else {
                                    "Código expirado — gere um novo código"
                                },
                                fontSize = 11.sp,
                                color = if (remainingSeconds > 0) DoHTextSecondary else DoHStatusOffline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    DoHButton(
                        text = "Gerar novo código",
                        onClick = {
                            activeSession = RemoteControlRepository.pairingManager.generateNewSession(
                                parentId = parentId,
                                parentName = parentDeviceName
                            )
                            validationError = null
                        },
                        icon = Icons.Default.Refresh,
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (localHost.isNotBlank() && localPort > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Descoberta Wi-Fi ativa na rede local ($localHost:$localPort)",
                            fontSize = 11.sp,
                            color = DoHSageDark,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Modo Internet / Relay
                    TextButton(
                        onClick = {
                            useManualMode = !useManualMode
                            validationError = null
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (useManualMode) "Voltar para pareamento por código" else "Configuração manual avançada",
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

                    if (!useManualMode) {
                        OutlinedTextField(
                            value = pairingCodeInput,
                            onValueChange = { pairingCodeInput = it.uppercase() },
                            label = { Text("Código exibido no tablet") },
                            placeholder = { Text("Ex: MD49K2") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = parentId,
                            onValueChange = { parentId = it },
                            label = { Text("ID do responsável") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
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
                            label = { Text("ID do tablet") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                    }
                }

                if (linkedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Dispositivos vinculados",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DoHTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    linkedDevices.forEach { dev ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DoHBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.deviceName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DoHTextPrimary)
                                    Text("ID: ${dev.deviceId}", fontSize = 11.sp, color = DoHTextSecondary)
                                }
                                TextButton(onClick = { RemoteControlRepository.unpairAndRevokeDevice(dev.deviceId) }) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = DoHStatusOffline)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remover", fontSize = 11.sp, color = DoHStatusOffline)
                                }
                            }
                        }
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError ?: "",
                        fontSize = 12.sp,
                        color = DoHAlert
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DoHButton(
                        text = "Fechar",
                        onClick = onDismiss,
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedTab == 1) {
                        DoHButton(
                            text = if (isClaiming) "Vinculando..." else if (useManualMode) "Salvar" else "Vincular",
                            onClick = {
                                val trimmedUrl = relayUrl.trim().trimEnd('/')
                                val isAllowedUrl = trimmedUrl.startsWith("https://") ||
                                    trimmedUrl.startsWith("http://127.0.0.1") ||
                                    trimmedUrl.startsWith("http://localhost")
                                if (!isAllowedUrl) {
                                    validationError = "Informe uma URL HTTPS válida para o servidor remoto."
                                    return@DoHButton
                                }

                                if (useManualMode) {
                                    if (sharedSecret.trim().length < 16) {
                                        validationError = "A chave secreta HMAC deve ter pelo menos 16 caracteres."
                                        return@DoHButton
                                    }
                                    onSavePairing(
                                        deviceId.trim().ifBlank { "tablet-lucas-01" },
                                        parentId.trim().ifBlank { "parent-hod-01" },
                                        deviceName.trim().ifBlank { "Tablet do Lucas" },
                                        trimmedUrl,
                                        sharedSecret.trim()
                                    )
                                    onDismiss()
                                } else {
                                    val cleanCode = pairingCodeInput.trim().uppercase()
                                    if (cleanCode.length < 4) {
                                        validationError = "Digite o código temporário exibido no tablet."
                                        return@DoHButton
                                    }
                                    isClaiming = true
                                    validationError = null
                                    coroutineScope.launch {
                                        val res = RemoteControlRepository.claimParentPairingCode(
                                            relayBaseUrl = trimmedUrl,
                                            pairingCode = cleanCode,
                                            parentId = parentId.trim().ifBlank { "parent-hod-01" },
                                            deviceName = deviceName.trim().takeIf { it.isNotBlank() }
                                        )
                                        isClaiming = false
                                        res.onSuccess { creds ->
                                            onCodeClaimed(creds)
                                            onDismiss()
                                        }.onFailure { err ->
                                            validationError = err.message ?: "Não foi possível validar o código."
                                        }
                                    }
                                }
                            },
                            enabled = !isClaiming,
                            variant = ButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
