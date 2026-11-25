package com.dohguard.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.model.DeviceInfo
import com.dohguard.shared.theme.*

@Composable
fun DoHHeader(
    deviceInfo: DeviceInfo,
    modifier: Modifier = Modifier,
    onDeviceProfileClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MonitorBgSecondary, MonitorBgPrimary)
                )
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // App Identity Brand Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MonitorNeonGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MD",
                        color = MonitorBgPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MonitorDad",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MonitorTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MonitorElectricCyan.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PAIS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = MonitorElectricCyan
                            )
                        }
                    }
                    Text(
                        text = "by HoD",
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = MonitorTextSecondary
                    )
                }
            }

            // Connection & Battery Status Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (deviceInfo.isOnline) MonitorNeonGreen.copy(alpha = 0.16f)
                            else MonitorAlertRed.copy(alpha = 0.18f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (deviceInfo.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = if (deviceInfo.isOnline) "Online" else "Offline",
                        tint = if (deviceInfo.isOnline) MonitorNeonGreen else MonitorAlertRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (deviceInfo.isOnline) "Online" else "Offline",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (deviceInfo.isOnline) MonitorNeonGreen else MonitorAlertRed
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MonitorCardBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Bateria",
                        tint = MonitorElectricCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${deviceInfo.batteryPercentage}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MonitorTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Device identifier row with quick access to device profile
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MonitorCardBg.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MonitorBorder.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onDeviceProfileClick != null) {
                        Modifier.clickable { onDeviceProfileClick() }
                    } else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MonitorBgSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TabletAndroid,
                            contentDescription = null,
                            tint = MonitorNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Dispositivo de ${deviceInfo.childName}",
                            fontSize = 12.sp,
                            color = MonitorTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = deviceInfo.deviceName,
                            fontSize = 17.sp,
                            color = MonitorTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (onDeviceProfileClick != null) {
                    Text(
                        text = "Perfil ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MonitorElectricCyan
                    )
                }
            }
        }
    }
}
