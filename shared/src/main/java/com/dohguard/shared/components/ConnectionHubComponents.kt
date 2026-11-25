package com.dohguard.shared.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dohguard.shared.remote.ActiveTransportType
import com.dohguard.shared.remote.ConnectionDiagnosticsReport
import com.dohguard.shared.remote.ConnectionHealthColor
import com.dohguard.shared.remote.ConnectionSettings
import com.dohguard.shared.remote.DeviceRemoteSnapshot
import com.dohguard.shared.remote.LinkedDeviceRecord
import com.dohguard.shared.remote.QrCodeGenerator
import com.dohguard.shared.remote.RemoteConnectionState
import com.dohguard.shared.remote.TransportMode
import com.dohguard.shared.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QrCodeView(
    payload: String,
    modifier: Modifier = Modifier,
    darkColor: Color = DoHTextPrimary,
    lightColor: Color = DoHWhite
) {
    val matrix = remember(payload) {
        runCatching { QrCodeGenerator.generate(payload) }.getOrNull()
    }
    if (matrix == null) {
        Box(
            modifier = modifier
                .background(DoHBackground, RoundedCornerShape(12.dp))
                .border(1.dp, DoHBorderLight, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("QR indisponível", fontSize = 12.sp, color = DoHTextSecondary)
        }
        return
    }

    Box(
        modifier = modifier
            .background(lightColor, RoundedCornerShape(12.dp))
            .border(1.dp, DoHBorderLight, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val quietZone = 2
            val totalModules = matrix.size + quietZone * 2
            val moduleSize = minOf(size.width, size.height) / totalModules.toFloat()
            val offsetX = (size.width - moduleSize * totalModules) / 2f
            val offsetY = (size.height - moduleSize * totalModules) / 2f

            drawRect(color = lightColor)
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix.isDark(x, y)) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(
                                x = offsetX + (x + quietZone) * moduleSize,
                                y = offsetY + (y + quietZone) * moduleSize
                            ),
                            size = Size(moduleSize + 0.5f, moduleSize + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

fun healthColorToCompose(color: ConnectionHealthColor): Color = when (color) {
    ConnectionHealthColor.GREEN -> DoHStatusOnline
    ConnectionHealthColor.YELLOW -> DoHStatusWarning
    ConnectionHealthColor.RED -> DoHStatusOffline
    ConnectionHealthColor.GRAY -> DoHTextMuted
}

fun formatCommTimestamp(millis: Long): String {
    if (millis <= 0L) return "Ainda sem comunicação"
    val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR"))
    return fmt.format(Date(millis))
}

@Composable
fun SetupWizardDialog(
    isParentApp: Boolean,
    initialSettings: ConnectionSettings,
    onDismiss: () -> Unit,
    onFinish: (TransportMode, String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf(initialSettings.mode) }
    var relayUrl by remember { mutableStateOf(initialSettings.relayBaseUrl) }

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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Assistente de configuração (${step + 1}/4)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoHSageDark
                )
                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { (step + 1) / 4f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = DoHSagePrimary,
                    trackColor = DoHSageSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                when (step) {
                    0 -> {
                        Text(
                            text = if (isParentApp) "Conecte o celular ao tablet" else "Conecte este tablet ao app dos pais",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "O MonitorDad funciona de forma automática:\n" +
                                "• Em casa, usa a mesma rede Wi-Fi sem precisar de servidor.\n" +
                                "• Fora de casa, alterna para a internet se o servidor remoto estiver configurado.",
                            fontSize = 13.sp,
                            color = DoHTextSecondary,
                            lineHeight = 19.sp
                        )
                    }

                    1 -> {
                        Text(
                            text = "Escolha como deseja conectar",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TransportMode.entries.forEach { mode ->
                            val selected = selectedMode == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) DoHSageSurface else DoHBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selected) DoHSagePrimary else DoHBorderLight
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedMode = mode }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { selectedMode = mode }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(mode.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoHTextPrimary)
                                        Text(mode.description, fontSize = 11.sp, color = DoHTextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Text(
                            text = "Acesso pela internet (Opcional)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Se quiser controlar o tablet mesmo fora do Wi-Fi de casa, informe o endereço HTTPS do seu servidor MonitorDad. Se for usar apenas no Wi-Fi de casa, deixe em branco.",
                            fontSize = 12.sp,
                            color = DoHTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = { relayUrl = it },
                            label = { Text("Endereço do servidor (opcional)") },
                            placeholder = { Text("https://relay.seudominio.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> {
                        Text(
                            text = "Tudo pronto para vincular!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isParentApp) {
                                "No próximo passo, toque em 'Adicionar dispositivo' para gerar um código temporário e um QR Code. Depois digite ou leia esse código no tablet MonitorDad Kids."
                            } else {
                                "No próximo passo, digite o código exibido no celular dos pais (MonitorDad Parent) ou cole o conteúdo do QR Code para concluir o vínculo."
                            },
                            fontSize = 13.sp,
                            color = DoHTextSecondary,
                            lineHeight = 19.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DoHButton(
                        text = if (step == 0) "Agora não" else "Voltar",
                        onClick = {
                            if (step == 0) onDismiss() else step -= 1
                        },
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    DoHButton(
                        text = if (step < 3) "Continuar" else "Concluir",
                        onClick = {
                            if (step < 3) {
                                step += 1
                            } else {
                                onFinish(selectedMode, relayUrl.trim())
                            }
                        },
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionHubDialog(
    isParentApp: Boolean,
    snapshot: DeviceRemoteSnapshot,
    settings: ConnectionSettings,
    linkedDevices: List<LinkedDeviceRecord>,
    diagnosticsReport: ConnectionDiagnosticsReport?,
    isSyncing: Boolean,
    isRunningDiagnostics: Boolean,
    onDismiss: () -> Unit,
    onAddOrPairDevice: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onOpenWizard: () -> Unit,
    onUpdateSettings: (ConnectionSettings) -> Unit,
    onRevokeDevice: (String) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var relayDraft by remember(settings.relayBaseUrl) { mutableStateOf(settings.relayBaseUrl) }
    var envDraft by remember(settings.environmentLabel) { mutableStateOf(settings.environmentLabel) }

    val badgeColor = healthColorToCompose(snapshot.healthColor)

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
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = DoHSageDark,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Conexão e dispositivos",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoHTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cartão de status principal
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DoHBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DoHBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = snapshot.statusSummary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DoHTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (snapshot.activeTransport == ActiveTransportType.LOCAL_WIFI) {
                                    Icons.Default.Wifi
                                } else {
                                    Icons.Default.Public
                                },
                                contentDescription = null,
                                tint = DoHSageDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Canal: ${snapshot.activeTransport.label} • Modo: ${settings.mode.label}",
                                fontSize = 12.sp,
                                color = DoHTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dispositivo: ${snapshot.deviceName} • Última comunicação: ${formatCommTimestamp(snapshot.lastCommunicationMillis)}",
                            fontSize = 11.sp,
                            color = DoHTextMuted
                        )
                        if (snapshot.pendingCommandsCount > 0 || snapshot.pendingSyncEnvelopesCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pendências na fila: ${snapshot.pendingCommandsCount} comando(s), ${snapshot.pendingSyncEnvelopesCount} regra(s)",
                                fontSize = 11.sp,
                                color = DoHStatusWarning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ações principais
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DoHButton(
                        text = if (isParentApp) "Adicionar dispositivo" else "Vincular aparelho",
                        onClick = onAddOrPairDevice,
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                    DoHButton(
                        text = if (isRunningDiagnostics) "Testando..." else "Testar conexão",
                        onClick = onRunDiagnostics,
                        enabled = !isRunningDiagnostics && !isSyncing,
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onOpenWizard,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp), tint = DoHSageDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Abrir assistente guiado (4 passos)", fontSize = 12.sp, color = DoHSageDark)
                }

                // Resultados reais do diagnóstico
                if (diagnosticsReport != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (diagnosticsReport.overallSuccess) DoHSageSurface else Color(0xFFFFF5F5),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (diagnosticsReport.overallSuccess) DoHSageLight else DoHStatusOffline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = diagnosticsReport.summary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DoHTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            diagnosticsReport.steps.forEach { step ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (step.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (step.passed) DoHStatusOnline else DoHStatusOffline,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = step.title + (step.latencyMillis?.let { " (${it} ms)" } ?: ""),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DoHTextPrimary
                                        )
                                        Text(step.detail, fontSize = 11.sp, color = DoHTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Modo de conexão",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoHTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TransportMode.entries.forEach { mode ->
                        val selected = settings.mode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateSettings(settings.copy(mode = mode)) },
                            label = {
                                Text(
                                    text = when (mode) {
                                        TransportMode.AUTO -> "Automático"
                                        TransportMode.LOCAL_ONLY -> "Wi-Fi local"
                                        TransportMode.REMOTE_ONLY -> "Internet"
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }

                // Dispositivos vinculados
                if (linkedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
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
                                    Text(
                                        text = "ID: ${dev.deviceId} • ${dev.lastTransport.label}",
                                        fontSize = 11.sp,
                                        color = DoHTextSecondary
                                    )
                                }
                                TextButton(onClick = { onRevokeDevice(dev.deviceId) }) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = DoHStatusOffline)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remover", fontSize = 11.sp, color = DoHStatusOffline)
                                }
                            }
                        }
                    }
                }

                // Configurações avançadas (ocultas por padrão)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "Configurações avançadas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DoHTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = DoHTextSecondary
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = relayDraft,
                        onValueChange = { relayDraft = it },
                        label = { Text("Servidor remoto HTTPS (opcional)") },
                        placeholder = { Text("https://relay.seudominio.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = envDraft,
                        onValueChange = { envDraft = it },
                        label = { Text("Ambiente") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Descoberta automática na rede Wi-Fi", fontSize = 12.sp, color = DoHTextPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.localDiscoveryEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(settings.copy(localDiscoveryEnabled = enabled))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    DoHButton(
                        text = "Salvar configurações avançadas",
                        onClick = {
                            onUpdateSettings(
                                settings.copy(
                                    relayBaseUrl = relayDraft.trim(),
                                    environmentLabel = envDraft.trim().ifBlank { "Produção" }
                                )
                            )
                        },
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                DoHButton(
                    text = "Fechar",
                    onClick = onDismiss,
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
