package com.dohguard.shared.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dohguard.shared.theme.*

@Composable
fun DoHSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = DoHWhite,
            checkedTrackColor = DoHSagePrimary,
            uncheckedThumbColor = DoHWhite,
            uncheckedTrackColor = DoHSagePastel.copy(alpha = 0.5f),
            uncheckedBorderColor = DoHSagePastel
        )
    )
}
