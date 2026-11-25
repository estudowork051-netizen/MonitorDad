package com.dohguard.shared.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * DoH Guard Design System Shapes
 * Cards with rounded corners between 20dp and 28dp
 */
val DoHShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val DoHCardCornerRadius = 24.dp
val DoHButtonCornerRadius = 20.dp
val DoHDialogCornerRadius = 28.dp
val DoHBadgeCornerRadius = 16.dp
