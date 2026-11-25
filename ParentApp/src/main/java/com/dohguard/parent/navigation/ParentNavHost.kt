package com.dohguard.parent.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dohguard.parent.screens.apps.AppsScreen
import com.dohguard.parent.screens.map.MapScreen
import com.dohguard.parent.screens.overview.OverviewScreen
import com.dohguard.parent.screens.rules.RulesScreen
import com.dohguard.shared.model.*
import com.dohguard.shared.remote.*
import com.dohguard.shared.theme.DoHBackground

@Composable
fun ParentMainScaffold(
    currentTab: ParentTab,
    onTabSelected: (ParentTab) -> Unit,
    deviceInfo: DeviceInfo,
    screenTimeState: ScreenTimeState,
    pendingRequests: List<PendingRequest>,
    appsList: List<AppUsageInfo>,
    usageSummary: UsageSummary,
    isSyncing: Boolean,
    locationInfo: LocationInfo,
    ruleSettings: RuleSettings,
    controlState: ControlRulesState = ControlRulesState.defaultDemo(),
    syncEnvelopesCount: Int = 0,
    isOnline: Boolean = true,
    remoteSnapshot: DeviceRemoteSnapshot = DeviceRemoteSnapshot(
        deviceId = deviceInfo.deviceId,
        deviceName = deviceInfo.deviceName
    ),
    remoteCommands: List<RemoteCommand> = emptyList(),
    remoteNotifications: List<ParentNotificationEvent> = emptyList(),
    pairedCredentials: PairedDeviceCredentials = PairedDeviceCredentials(),
    telemetryPayload: TelemetryPayload? = null,
    onAddFifteenMinutes: () -> Unit,
    onTogglePauseDevice: () -> Unit,
    onPlaySound: () -> Unit = {},
    onConfigurePairing: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onMarkNotificationsRead: () -> Unit = {},
    onToggleBlockApp: (String) -> Unit,
    onUpdateAppLimit: (String, String, Int) -> Unit = { _, _, _ -> },
    onUpdateDailyLimit: (Int, Set<Weekday>) -> Unit = { _, _ -> },
    onUpsertCategoryLimit: (CategoryLimitRule) -> Unit = {},
    onUpsertRoutine: (RoutineRule) -> Unit = {},
    onToggleRoutine: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteRoutine: (String) -> Unit = {},
    onGrantExtraTime: (Int, String?) -> Unit = { _, _ -> },
    onToggleOnline: (Boolean) -> Unit = {},
    onApproveRequest: (PendingRequest) -> Unit,
    onRejectRequest: (PendingRequest) -> Unit,
    onRefreshLocation: () -> Unit,
    onManualSync: () -> Unit
) {
    Scaffold(
        containerColor = DoHBackground,
        bottomBar = {
            ParentBottomBar(
                currentTab = currentTab,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ParentTab.OVERVIEW -> OverviewScreen(
                    deviceInfo = deviceInfo,
                    screenTimeState = screenTimeState,
                    pendingRequests = pendingRequests,
                    topApps = appsList.take(4),
                    onAddFifteenMinutes = onAddFifteenMinutes,
                    onTogglePauseDevice = onTogglePauseDevice,
                    onApproveRequest = onApproveRequest,
                    onRejectRequest = onRejectRequest,
                    remoteSnapshot = remoteSnapshot,
                    remoteCommands = remoteCommands,
                    remoteNotifications = remoteNotifications,
                    pairedCredentials = pairedCredentials,
                    usageSummary = usageSummary,
                    telemetryPayload = telemetryPayload,
                    isSyncing = isSyncing,
                    onPlaySound = onPlaySound,
                    onManualSync = onManualSync,
                    onConfigurePairing = onConfigurePairing,
                    onMarkNotificationsRead = onMarkNotificationsRead,
                    onNavigateToTab = onTabSelected
                )
                ParentTab.APPS -> AppsScreen(
                    appsList = appsList,
                    usageSummary = usageSummary,
                    isSyncing = isSyncing,
                    onManualSync = onManualSync,
                    onToggleBlockApp = onToggleBlockApp,
                    onUpdateAppLimit = onUpdateAppLimit
                )
                ParentTab.MAP -> MapScreen(
                    locationInfo = locationInfo,
                    onRefreshLocation = onRefreshLocation
                )
                ParentTab.RULES -> RulesScreen(
                    deviceInfo = deviceInfo,
                    ruleSettings = ruleSettings,
                    controlState = controlState,
                    screenTimeState = screenTimeState,
                    syncEnvelopesCount = syncEnvelopesCount,
                    isOnline = isOnline,
                    onUpdateDailyLimit = onUpdateDailyLimit,
                    onUpsertCategoryLimit = onUpsertCategoryLimit,
                    onUpsertRoutine = onUpsertRoutine,
                    onToggleRoutine = onToggleRoutine,
                    onDeleteRoutine = onDeleteRoutine,
                    onGrantExtraTime = onGrantExtraTime,
                    onToggleOnline = onToggleOnline
                )
            }
        }
    }
}
