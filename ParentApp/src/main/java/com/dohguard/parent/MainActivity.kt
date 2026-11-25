package com.dohguard.parent

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.dohguard.parent.navigation.ParentMainScaffold
import com.dohguard.parent.navigation.ParentTab
import com.dohguard.parent.notifications.ParentNotificationHelper
import com.dohguard.parent.remote.ParentBackgroundSyncWorker
import com.dohguard.shared.components.MonitorDadSplashScreen
import com.dohguard.shared.model.*
import com.dohguard.shared.remote.*
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.sync.UsageSyncRepository
import com.dohguard.shared.theme.DoHGuardTheme
import com.dohguard.shared.ui.ImmersiveModeHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var nsdDiscovery: AndroidNsdDiscovery? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onStart() {
        super.onStart()
        val nsd = nsdDiscovery ?: AndroidNsdDiscovery(this).also { nsdDiscovery = it }
        nsd.startDiscovery(
            serviceType = AndroidNsdDiscovery.SERVICE_TYPE_KIDS,
            onServiceResolved = { endpoint, _ ->
                RemoteControlRepository.updateLocalEndpoint(endpoint)
            },
            onServiceLost = { _ ->
                RemoteControlRepository.handleLocalServiceLost()
            }
        )
    }

    override fun onStop() {
        super.onStop()
        nsdDiscovery?.stopDiscovery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImmersiveModeHelper.applyImmersiveMode(this)
        UsageSyncRepository.init(this)
        ControlRulesRepository.init(this)
        RemoteControlRepository.init(this)
        ParentNotificationHelper.ensureChannel(this)
        ParentBackgroundSyncWorker.enqueuePeriodicSync(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ParentNotificationHelper.hasNotificationPermission(this)
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            DoHGuardTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }

                var currentTab by remember { mutableStateOf(ParentTab.OVERVIEW) }
                var baseDeviceInfo by remember { mutableStateOf(DeviceInfo.DEMO_TABLET) }
                var locationInfo by remember { mutableStateOf(LocationInfo.DEMO) }
                val ruleSettings by remember { mutableStateOf(RuleSettings()) }

                // Integração com os repositórios de telemetria, regras de controle e comunicação remota
                val telemetryPayload by UsageSyncRepository.telemetryState.collectAsState()
                val isTelemetrySyncing by UsageSyncRepository.isSyncing.collectAsState()
                val isRemoteSyncing by RemoteControlRepository.isSyncing.collectAsState()
                val isSyncing = isTelemetrySyncing || isRemoteSyncing
                val controlState by ControlRulesRepository.rulesState.collectAsState()
                val pendingRequests by ControlRulesRepository.pendingRequests.collectAsState()
                val syncEnvelopes by ControlRulesRepository.syncEnvelopes.collectAsState()
                val isOnline by ControlRulesRepository.isOnline.collectAsState()
                val remoteSnapshot by RemoteControlRepository.snapshot.collectAsState()
                val remoteCommands by RemoteControlRepository.commands.collectAsState()
                val remoteNotifications by RemoteControlRepository.notifications.collectAsState()
                val pairedCredentials by RemoteControlRepository.credentials.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    while (isActive) {
                        RemoteControlRepository.syncParentSession()
                        delay(15_000L)
                    }
                }

                val notifiedIds = remember { mutableStateListOf<String>() }
                LaunchedEffect(remoteNotifications) {
                    remoteNotifications
                        .filter { !it.isRead && it.eventId !in notifiedIds }
                        .forEach { ev ->
                            notifiedIds.add(ev.eventId)
                            ParentNotificationHelper.showNotification(this@MainActivity, ev)
                        }
                }

                val rawApps = telemetryPayload?.apps ?: AppUsageInfo.DEMO_APPS
                val appsList = remember(rawApps, controlState) {
                    rawApps.map { app ->
                        val rule = controlState.appLimits[app.packageName]
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

                val usageSummary = telemetryPayload?.summary ?: UsageSummary.fromAppsList(
                    apps = appsList,
                    isDemoData = true
                )

                val nowMillis = System.currentTimeMillis()
                val extraGrantedMinutes = controlState.extraGrants
                    .filter { it.targetPackage == null && it.isActiveAt(nowMillis) }
                    .sumOf { it.minutes }

                val realUsedMinutes = if (!usageSummary.isDemoData || pairedCredentials.isPaired) {
                    usageSummary.totalUsedMinutesToday
                } else {
                    usageSummary.totalUsedMinutesToday.takeIf { it > 0 } ?: 135
                }

                val screenTimeState = ScreenTimeState(
                    usedMinutes = realUsedMinutes,
                    totalLimitMinutes = controlState.dailyDeviceLimitMinutes,
                    extraMinutesGranted = extraGrantedMinutes
                )

                val deviceInfo = baseDeviceInfo.copy(
                    deviceId = if (pairedCredentials.isPaired) pairedCredentials.deviceId else baseDeviceInfo.deviceId,
                    deviceName = if (pairedCredentials.isPaired) pairedCredentials.deviceName else baseDeviceInfo.deviceName,
                    isPaused = controlState.isDevicePaused,
                    isOnline = isOnline
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    ParentMainScaffold(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it },
                        deviceInfo = deviceInfo,
                        screenTimeState = screenTimeState,
                        pendingRequests = pendingRequests,
                        appsList = appsList,
                        usageSummary = usageSummary,
                        isSyncing = isSyncing,
                        locationInfo = locationInfo,
                        ruleSettings = ruleSettings,
                        controlState = controlState,
                        syncEnvelopesCount = syncEnvelopes.size,
                        isOnline = isOnline,
                        remoteSnapshot = remoteSnapshot,
                        remoteCommands = remoteCommands,
                        remoteNotifications = remoteNotifications,
                        pairedCredentials = pairedCredentials,
                        telemetryPayload = telemetryPayload,
                        onAddFifteenMinutes = {
                            ControlRulesRepository.grantExtraTime(
                                minutes = 15,
                                reason = "Bônus rápido +15 min pelo responsável"
                            )
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.ADD_EXTRA_TIME,
                                    payload = mapOf(
                                        "minutes" to "15",
                                        "reason" to "Bônus rápido +15 min pelo responsável"
                                    )
                                )
                            }
                        },
                        onTogglePauseDevice = {
                            val willPause = !controlState.isDevicePaused
                            ControlRulesRepository.setDevicePaused(willPause)
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = if (willPause) CommandType.PAUSE_DEVICE else CommandType.RESUME_DEVICE
                                )
                            }
                        },
                        onPlaySound = {
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.PLAY_SOUND
                                )
                            }
                        },
                        onConfigurePairing = { devId, parId, devName, url, secret ->
                            RemoteControlRepository.configurePairing(
                                deviceId = devId,
                                parentId = parId,
                                deviceName = devName,
                                relayBaseUrl = url,
                                sharedSecret = secret
                            )
                            coroutineScope.launch {
                                RemoteControlRepository.syncParentSession()
                            }
                        },
                        onMarkNotificationsRead = {
                            RemoteControlRepository.markNotificationsRead()
                        },
                        onToggleBlockApp = { pkg ->
                            val appName = appsList.firstOrNull { it.packageName == pkg }?.appName ?: pkg
                            val currentlyBlocked = controlState.appLimits[pkg]?.isBlocked ?: false
                            val nextBlocked = !currentlyBlocked
                            ControlRulesRepository.setAppBlocked(pkg, appName, nextBlocked)
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = if (nextBlocked) CommandType.BLOCK_APP else CommandType.UNBLOCK_APP,
                                    payload = mapOf("packageName" to pkg, "appName" to appName)
                                )
                            }
                        },
                        onUpdateAppLimit = { pkg, appName, newLimit ->
                            ControlRulesRepository.updateAppLimit(
                                packageName = pkg,
                                appName = appName,
                                dailyLimitMinutes = newLimit
                            )
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.UPDATE_LIMITS,
                                    payload = mapOf(
                                        "packageName" to pkg,
                                        "appName" to appName,
                                        "appLimitMinutes" to newLimit.toString()
                                    )
                                )
                            }
                        },
                        onUpdateDailyLimit = { limit, days ->
                            ControlRulesRepository.updateDailyLimit(limit, days)
                            RemoteControlRepository.recordNotification(
                                type = NotificationEventType.RULES_CHANGED,
                                message = "Limite diário atualizado para ${limit} min.",
                                dedupKey = "rules-daily-$limit-${System.currentTimeMillis() / 10_000L}"
                            )
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.UPDATE_LIMITS,
                                    payload = mapOf("dailyDeviceLimitMinutes" to limit.toString())
                                )
                            }
                        },
                        onUpsertCategoryLimit = { rule ->
                            ControlRulesRepository.upsertCategoryLimit(rule)
                            RemoteControlRepository.recordNotification(
                                type = NotificationEventType.RULES_CHANGED,
                                message = "Limite da categoria ${rule.category.displayName} ajustado para ${rule.dailyLimitMinutes} min.",
                                dedupKey = "rules-cat-${rule.category.name}-${rule.dailyLimitMinutes}-${System.currentTimeMillis() / 10_000L}"
                            )
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.UPDATE_LIMITS,
                                    payload = mapOf(
                                        "category" to rule.category.name,
                                        "categoryLimitMinutes" to rule.dailyLimitMinutes.toString()
                                    )
                                )
                            }
                        },
                        onUpsertRoutine = { routine ->
                            ControlRulesRepository.upsertRoutine(routine)
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.UPSERT_ROUTINE,
                                    payload = mapOf(
                                        "routineId" to routine.id,
                                        "name" to routine.name,
                                        "routineType" to routine.type.name,
                                        "startTime" to routine.startTime,
                                        "endTime" to routine.endTime,
                                        "enabled" to routine.enabled.toString(),
                                        "days" to routine.daysOfWeek.joinToString(",") { it.name }
                                    )
                                )
                            }
                        },
                        onToggleRoutine = { id, enabled ->
                            ControlRulesRepository.toggleRoutineEnabled(id, enabled)
                            val routine = ControlRulesRepository.rulesState.value.routines.firstOrNull { it.id == id }
                            if (routine != null) {
                                coroutineScope.launch {
                                    RemoteControlRepository.sendParentCommand(
                                        type = CommandType.UPSERT_ROUTINE,
                                        payload = mapOf(
                                            "routineId" to routine.id,
                                            "name" to routine.name,
                                            "routineType" to routine.type.name,
                                            "startTime" to routine.startTime,
                                            "endTime" to routine.endTime,
                                            "enabled" to enabled.toString(),
                                            "days" to routine.daysOfWeek.joinToString(",") { it.name }
                                        )
                                    )
                                }
                            }
                        },
                        onDeleteRoutine = { id ->
                            ControlRulesRepository.deleteRoutine(id)
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.DELETE_ROUTINE,
                                    payload = mapOf("routineId" to id)
                                )
                            }
                        },
                        onGrantExtraTime = { mins, pkg ->
                            ControlRulesRepository.grantExtraTime(minutes = mins, targetPackage = pkg)
                            coroutineScope.launch {
                                val payload = mutableMapOf("minutes" to mins.toString())
                                if (!pkg.isNullOrBlank()) payload["packageName"] = pkg
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.ADD_EXTRA_TIME,
                                    payload = payload
                                )
                            }
                        },
                        onToggleOnline = { online ->
                            ControlRulesRepository.setOnlineStatus(online)
                        },
                        onApproveRequest = { req ->
                            ControlRulesRepository.approvePendingRequest(req)
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(
                                    type = CommandType.ADD_EXTRA_TIME,
                                    payload = mapOf(
                                        "minutes" to req.requestedMinutes.coerceAtLeast(5).toString(),
                                        "reason" to "Solicitação aprovada: ${req.title}"
                                    )
                                )
                            }
                        },
                        onRejectRequest = { req ->
                            ControlRulesRepository.rejectPendingRequest(req)
                        },
                        onRefreshLocation = {
                            locationInfo = locationInfo.copy(
                                lastUpdatedFormatted = "Agora mesmo (Atualizado)"
                            )
                        },
                        onManualSync = {
                            coroutineScope.launch {
                                RemoteControlRepository.sendParentCommand(CommandType.REFRESH_USAGE)
                                RemoteControlRepository.syncParentSession()
                                UsageSyncRepository.triggerManualSync()
                                ControlRulesRepository.flushPendingSyncQueue()
                            }
                        }
                    )

                    if (showSplash) {
                        MonitorDadSplashScreen(
                            splashDrawableRes = R.drawable.monitor_dad_splash,
                            appSubtitle = "MonitorDad Parent • Controle Familiar",
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ImmersiveModeHelper.applyImmersiveMode(this)
        }
    }
}
