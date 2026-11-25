package com.dohguard.shared.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.model.ScreenTimeState
import com.dohguard.shared.theme.*
import kotlin.math.sin

/**
 * Medidor visual de tempo de tela com animação de onda suave e fluida.
 */
@Composable
fun WaveScreenTimeMeter(
    screenTimeState: ScreenTimeState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    val progress = screenTimeState.progressFraction.coerceIn(0.05f, 0.95f)

    Box(
        modifier = modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(DoHSageLight),
        contentAlignment = Alignment.Center
    ) {
        // Animated Canvas Wave
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val waterLevelY = height * (1f - progress)
            val waveHeight = 12.dp.toPx()
            val waveLength = width

            val path = Path().apply {
                moveTo(0f, height)
                lineTo(0f, waterLevelY)

                var x = 0f
                while (x <= width) {
                    val angle = (x / waveLength) * 2f * Math.PI.toFloat() + waveOffset
                    val y = waterLevelY + sin(angle) * waveHeight
                    lineTo(x, y)
                    x += 4f
                }

                lineTo(width, height)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MonitorElectricCyan.copy(alpha = 0.65f),
                        MonitorNeonGreen.copy(alpha = 0.85f)
                    ),
                    startY = waterLevelY - waveHeight,
                    endY = height
                )
            )
        }

        // Inner Content Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TEMPO USADO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MonitorTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = screenTimeState.formattedUsed,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MonitorTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MonitorBgPrimary.copy(alpha = 0.88f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Limite: ${screenTimeState.formattedLimit}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MonitorNeonGreen
                )
            }
        }
    }
}
