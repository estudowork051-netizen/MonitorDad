package com.dohguard.kids

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.dohguard.kids.monitoring.KidsScreenTimeController
import com.dohguard.kids.monitoring.UsageMonitoringWorker
import com.dohguard.kids.monitoring.UsageStatsCollector
import com.dohguard.kids.permission.UsagePermissionHelper
import com.dohguard.kids.remote.KidsCommandExecutor
import com.dohguard.kids.remote.LocalKidsServer
import com.dohguard.kids.remote.RemoteSyncWorker
import com.dohguard.kids.screens.KidsHomeScreen
import com.dohguard.kids.screens.KidsPairingDialog
import com.dohguard.kids.screens.LockScreen
import com.dohguard.kids.screens.PinAuthDialog
import com.dohguard.kids.screens.RestScreen
import com.dohguard.shared.components.MonitorDadSplashScreen
import com.dohguard.shared.control.ClockIntegrityGuard
import com.dohguard.shared.control.RuleEvaluationEngine
import com.dohguard.shared.model.AppCategory
import com.dohguard.shared.model.AppUsageInfo
import com.dohguard.shared.model.ScreenTimeState
import com.dohguard.shared.remote.NotificationEventType
import com.dohguard.shared.remote.RemoteConnectionState
import com.dohguard.shared.remote.RemoteControlRepository
import com.dohguard.shared.security.PinSecurityManager
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository
import com.dohguard.shared.theme.DoHGuardTheme
import com.dohguard.shared.ui.ImmersiveModeHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class KidsActiveScreen {
    HOME,
    REST,
    LOCK
}

class MainActivity : ComponentActivity() {

    private var hasUsagePermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private lateinit var pinManager: PinSecurityManager
    private lateinit var clockGuard: ClockIntegrityGuard

    override fun onStart() {
        super.onStart()
        LocalKidsServer.startIfPaired(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            LocalKidsServer.stop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImmersiveModeHelper.applyImmersiveMode(this)
        UsageSyncRepository.init(this)
        ControlRulesRepository.init(this)
        RemoteControlRepository.init(this)
        KidsScreenTimeController.init(this)
        LocalKidsServer.startIfPaired(this)
        pinManager = PinSecurityManager(this)
        clockGuard = ClockIntegrityGuard(this)

        // Verifica as permissões e agenda o monitoramento e sincronização remota em segundo plano
        hasUsagePermission = UsagePermissionHelper.hasUsageStatsPermission(this)
        hasAccessibilityPermission = UsagePermissionHelper.isAccessibilityServiceEnabled(this)
        if (hasUsagePermission) {
            UsageMonitoringWorker.enqueuePeriodicMonitoring(this)
        }
        RemoteSyncWorker.enqueuePeriodicSync(this)
        RemoteSyncWorker.enqueueImmediateSync(this)

        setContent {
            DoHGuardTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var activeScreen by rememberSaveable { mutableStateOf(KidsActiveScreen.HOME) }
                var showPinDialog by remember { mutableStateOf(false) }
                var showPairingDialog by remember { mutableStateOf(false) }
                val pairedCredentials by RemoteControlRepository.credentials.collectAsState()
                val remoteSnapshot by RemoteControlRepository.snapshot.collectAsState()
                val isRemoteSyncing by RemoteControlRepository.isSyncing.collectAsState()
                val rulesState by ControlRulesRepository.rulesState.collectAsState()
                val telemetryPayload by UsageSyncRepository.telemetryState.collectAsState()
                val tickerMinutes by KidsScreenTimeController.activeExtraMinutes.collectAsState()

                LaunchedEffect(pairedCredentials.isPaired) {
                    if (pairedCredentials.isPaired) {
                        LocalKidsServer.startIfPaired(this@MainActivity)
                    } else {
                        LocalKidsServer.stop()
                    }
                }

                // Coleta real de telemetria + sincronização a cada 20s
                LaunchedEffect(Unit) {
                    while (isActive) {
                        runCatching {
                            UsageStatsCollector.refreshTelemetryIfPermitted(this@MainActivity)
                        }
                        RemoteControlRepository.syncKidsSession { cmd ->
                            KidsCommandExecutor.execute(this@MainActivity, cmd)
                        }
                        delay(20_000L)
                    }
                }

                val observedMinutes = when {
                    telemetryPayload != null && telemetryPayload?.summary?.isDemoData == false ->
                        telemetryPayload?.summary?.totalUsedMinutesToday ?: 0
                    hasUsagePermission || pairedCredentials.isPaired ->
                        telemetryPayload?.summary?.totalUsedMinutesToday ?: 0
                    else ->
                        telemetryPayload?.summary?.totalUsedMinutesToday ?: 135
                }

                LaunchedEffect(observedMinutes) {
                    KidsScreenTimeController.syncObservedMinutes(observedMinutes)
                }

                // Tick monotônico a cada 5s para manter o contador atualizado em tempo real
                LaunchedEffect(activeScreen, rulesState.isDevicePaused, observedMinutes) {
                    while (isActive) {
                        val isActiveSession = activeScreen != KidsActiveScreen.LOCK && !rulesState.isDevicePaused
                        KidsScreenTimeController.tick(
                            isActiveSession = isActiveSession,
                            observedExternalMinutes = observedMinutes
                        )
                        delay(5_000L)
                    }
                }

                val rawApps = telemetryPayload?.apps ?: AppUsageInfo.DEMO_APPS

                val appsWithRules = remember(rawApps, rulesState) {
                    rawApps.map { app ->
                        val rule = rulesState.appLimits[app.packageName]
                        if (rule != null) {
                            app.copy(
                                isBlocked = rule.isBlocked,
                                dailyLimitMinutes = rule.dailyLimitMinutes
                            )
                        } else {
                            app
                        }
                    }
                }

                val combinedUsedMinutes = maxOf(observedMinutes, tickerMinutes)
                val safeUsedMinutes = remember(combinedUsedMinutes) {
                    clockGuard.updateAndPersist(combinedUsedMinutes).totalMinutes
                }
                val nowMillis = System.currentTimeMillis()
                val extraDeviceMinutes = rulesState.extraGrants
                    .filter { it.targetPackage == null && it.isActiveAt(nowMillis) }
                    .sumOf { it.minutes }

                val screenTimeState = ScreenTimeState(
                    usedMinutes = safeUsedMinutes,
                    totalLimitMinutes = rulesState.dailyDeviceLimitMinutes,
                    extraMinutesGranted = extraDeviceMinutes
                )

                val deviceBlockDecision = remember(rulesState, safeUsedMinutes) {
                    RuleEvaluationEngine.evaluateAppAccess(
                        state = rulesState,
                        packageName = "com.google.android.youtube",
                        appCategory = AppCategory.VIDEOS,
                        totalDeviceUsedMinutes = safeUsedMinutes,
                        appUsedMinutes = 0,
                        categoryUsedMinutes = 0,
                        nowMillis = System.currentTimeMillis()
                    )
                }

                LaunchedEffect(screenTimeState.remainingMinutes, deviceBlockDecision.isBlocked) {
                    if ((screenTimeState.remainingMinutes <= 0 || deviceBlockDecision.isBlocked) &&
                        activeScreen == KidsActiveScreen.HOME
                    ) {
                        activeScreen = KidsActiveScreen.LOCK
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    if (showPinDialog) {
                        PinAuthDialog(
                            onVerifyPin = { input -> pinManager.verifyPin(input) },
                            onDismiss = { showPinDialog = false },
                            onPinSuccess = {
                                showPinDialog = false
                                if (activeScreen == KidsActiveScreen.HOME) {
                                    showPairingDialog = true
                                } else {
                                    activeScreen = KidsActiveScreen.HOME
                                }
                                Toast.makeText(
                                    this@MainActivity,
                                    "Autenticação do responsável realizada",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    if (showPairingDialog) {
                        KidsPairingDialog(
                            currentCredentials = pairedCredentials,
                            onDismiss = { showPairingDialog = false },
                            onSavePairing = { devId, parId, devName, url, secret ->
                                RemoteControlRepository.configurePairing(
                                    deviceId = devId,
                                    parentId = parId,
                                    deviceName = devName,
                                    relayBaseUrl = url,
                                    sharedSecret = secret
                                )
                                RemoteSyncWorker.enqueueImmediateSync(this@MainActivity)
                                Toast.makeText(
                                    this@MainActivity,
                                    "Pareamento salvo com segurança",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onPairingCompleted = {
                                RemoteSyncWorker.enqueueImmediateSync(this@MainActivity)
                                Toast.makeText(
                                    this@MainActivity,
                                    "Tablet vinculado ao MonitorDad Parent!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    when (activeScreen) {
                        KidsActiveScreen.HOME -> KidsHomeScreen(
                            childName = "Lucas",
                            deviceName = pairedCredentials.deviceName.ifBlank { "Tablet do Lucas" },
                            screenTimeState = screenTimeState,
                            authorizedApps = appsWithRules,
                            activeRoutines = rulesState.routines.filter { it.enabled },
                            isOnline = remoteSnapshot.connectionState == RemoteConnectionState.ONLINE,
                            isSyncing = isRemoteSyncing,
                            hasUsagePermission = hasUsagePermission,
                            hasAccessibilityPermission = hasAccessibilityPermission,
                            onRequestUsagePermission = {
                                try {
                                    startActivity(UsagePermissionHelper.createUsageAccessSettingsIntent())
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Abra Configurações > Acesso ao Uso manualmente",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onRequestAccessibilityPermission = {
                                try {
                                    startActivity(UsagePermissionHelper.createAccessibilitySettingsIntent())
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Abra Configurações > Acessibilidade manualmente",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onOpenRestScreen = { activeScreen = KidsActiveScreen.REST },
                            onOpenLockScreen = { activeScreen = KidsActiveScreen.LOCK },
                            onOpenPinAuth = { showPinDialog = true }
                        )
                        KidsActiveScreen.REST -> RestScreen(
                            onContinue = { activeScreen = KidsActiveScreen.HOME },
                            onOpenPin = { showPinDialog = true }
                        )
                        KidsActiveScreen.LOCK -> LockScreen(
                            title = if (deviceBlockDecision.isBlocked) deviceBlockDecision.title else "Tempo Encerrado por Hoje!",
                            message = if (deviceBlockDecision.isBlocked) deviceBlockDecision.message else "Você aproveitou bastante o seu tablet hoje. Agora é hora de descansar e recarregar as energias!",
                            unlockTimeFormatted = deviceBlockDecision.unlockTimeFormatted,
                            onDismissDemo = { activeScreen = KidsActiveScreen.HOME },
                            onRequestMoreTime = {
                                ControlRulesRepository.requestExtraTimeFromKids(
                                    requestedMinutes = 15,
                                    description = "Lucas pediu +15 min de tempo adicional"
                                )
                                RemoteControlRepository.recordNotification(
                                    type = NotificationEventType.EXTRA_TIME_REQUESTED,
                                    message = "Lucas pediu +15 min de tempo adicional.",
                                    dedupKey = "extra-req-${System.currentTimeMillis() / 30_000L}"
                                )
                                Toast.makeText(
                                    this@MainActivity,
                                    "Solicitação de +15 min enviada aos pais",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onOpenPin = { showPinDialog = true }
                        )
                    }

                    if (showSplash) {
                        MonitorDadSplashScreen(
                            splashDrawableRes = R.drawable.monitor_dad_splash,
                            appSubtitle = "MonitorDad Kids • Ambiente Seguro",
                            onSplashFinished = { showSplash = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ImmersiveModeHelper.applyImmersiveMode(this)
        hasAccessibilityPermission = UsagePermissionHelper.isAccessibilityServiceEnabled(this)
        val permissionNow = UsagePermissionHelper.hasUsageStatsPermission(this)
        if (permissionNow != hasUsagePermission) {
            hasUsagePermission = permissionNow
            if (permissionNow) {
                UsageMonitoringWorker.enqueuePeriodicMonitoring(this)
                runCatching {
                    UsageStatsCollector.refreshTelemetryIfPermitted(this)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ImmersiveModeHelper.applyImmersiveMode(this)
        }
    }
}
