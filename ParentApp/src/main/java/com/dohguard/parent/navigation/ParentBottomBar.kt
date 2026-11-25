package com.dohguard.parent.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.theme.*

@Composable
fun ParentBottomBar(
    currentTab: ParentTab,
    onTabSelected: (ParentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ambientColor = DoHShadow,
                spotColor = DoHShadow
            )
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = MonitorBgSecondary,
        tonalElevation = 2.dp
    ) {
        NavigationBar(
            containerColor = MonitorBgSecondary,
            contentColor = MonitorNeonGreen,
            tonalElevation = 0.dp,
            modifier = Modifier.height(72.dp)
        ) {
            ParentTab.values().forEach { tab ->
                val selected = currentTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = if (selected) MonitorNeonGreen else MonitorTextSecondary
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MonitorNeonGreen,
                        unselectedIconColor = MonitorTextSecondary,
                        indicatorColor = MonitorCardBg
                    )
                )
            }
        }
    }
}
