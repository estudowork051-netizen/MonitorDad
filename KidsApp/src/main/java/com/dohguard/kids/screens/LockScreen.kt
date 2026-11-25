package com.dohguard.kids.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.kids.R
import com.dohguard.shared.components.ButtonVariant
import com.dohguard.shared.components.DoHButton
import com.dohguard.shared.theme.*

/**
 * Tela de bloqueio e encerramento de tempo de tela do dia.
 * Preserva integralmente os botões, textos, ordem e ações originais.
 */
@Composable
fun LockScreen(
    onDismissDemo: () -> Unit,
    onRequestMoreTime: () -> Unit,
    onOpenPin: () -> Unit,
    title: String = "Tempo Encerrado por Hoje!",
    message: String = "Você aproveitou bastante o seu tablet hoje. Agora é hora de descansar, brincar offline e recarregar as energias para amanhã!",
    unlockTimeFormatted: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MonitorBgSecondary, MonitorBgPrimary)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MonitorCardBg.copy(alpha = 0.92f),
            border = BorderStroke(1.5.dp, MonitorBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                val timeExpiredPainter = painterResource(id = R.drawable.monitor_dad_time_expired)
                val hasCustomArtwork = timeExpiredPainter.intrinsicSize.width > 4f &&
                    timeExpiredPainter.intrinsicSize.height > 4f

                if (hasCustomArtwork) {
                    Image(
                        painter = timeExpiredPainter,
                        contentDescription = "Tempo Encerrado",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(136.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(MonitorAlertRed.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Bloqueio Ativo",
                            tint = MonitorAlertRed,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MonitorTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    fontSize = 15.sp,
                    color = MonitorTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (!unlockTimeFormatted.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = MonitorBgSecondary,
                        border = BorderStroke(1.dp, MonitorBorder)
                    ) {
                        Text(
                            text = "Liberação prevista: $unlockTimeFormatted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MonitorElectricCyan,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                DoHButton(
                    text = "Pedir Mais Tempo aos Pais",
                    onClick = onRequestMoreTime,
                    variant = ButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                DoHButton(
                    text = "Voltar à Tela Kids (Demo)",
                    onClick = onDismissDemo,
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onOpenPin) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MonitorElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Acesso dos Pais (PIN)",
                        color = MonitorElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
