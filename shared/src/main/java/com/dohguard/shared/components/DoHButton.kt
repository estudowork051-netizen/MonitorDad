package com.dohguard.shared.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.theme.*

enum class ButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINED,
    DANGER
}

@Composable
fun DoHButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true
) {
    val containerColor by animateColorAsState(
        targetValue = when (variant) {
            ButtonVariant.PRIMARY -> DoHSagePrimary
            ButtonVariant.SECONDARY -> DoHSageLight
            ButtonVariant.OUTLINED -> Color.Transparent
            ButtonVariant.DANGER -> DoHBlock
        },
        label = "btnColor"
    )

    val contentColor = when (variant) {
        ButtonVariant.PRIMARY -> MonitorBgPrimary
        ButtonVariant.SECONDARY -> MonitorTextPrimary
        ButtonVariant.OUTLINED -> MonitorElectricCyan
        ButtonVariant.DANGER -> MonitorTextPrimary
    }

    val border = if (variant == ButtonVariant.OUTLINED) {
        BorderStroke(1.dp, MonitorBorder)
    } else null

    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(DoHButtonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = DoHSageLight.copy(alpha = 0.5f),
            disabledContentColor = DoHTextSecondary.copy(alpha = 0.5f)
        ),
        border = border,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (variant == ButtonVariant.PRIMARY) 2.dp else 0.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
