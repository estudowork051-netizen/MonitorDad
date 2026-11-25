package com.dohguard.shared.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.config.SplashConfig
import com.dohguard.shared.theme.DoHBackground
import com.dohguard.shared.theme.DoHSageDark
import com.dohguard.shared.theme.DoHSagePrimary
import kotlinx.coroutines.delay

/**
 * Tela de abertura personalizada (Splash Screen) do MonitorDad • by HoD.
 * Exibida uma única vez na inicialização a frio, preservando proporção da arte
 * e realizando transição suave para o conteúdo principal.
 */
@Composable
fun MonitorDadSplashScreen(
    @DrawableRes splashDrawableRes: Int,
    appSubtitle: String,
    durationMs: Long = SplashConfig.clampedDurationMs(),
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startFadeIn by remember { mutableStateOf(false) }
    var startFadeOut by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = when {
            startFadeOut -> 0f
            startFadeIn -> 1f
            else -> 0.2f
        },
        animationSpec = tween(durationMillis = 450),
        label = "splashAlpha"
    )

    LaunchedEffect(durationMs) {
        startFadeIn = true
        val fadeOutLeadMs = 350L
        val waitMs = (durationMs - fadeOutLeadMs).coerceAtLeast(500L)
        delay(waitMs)
        startFadeOut = true
        delay(fadeOutLeadMs)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DoHBackground),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = splashDrawableRes),
            contentDescription = "MonitorDad Splash",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MonitorDad • by HoD",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DoHSageDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = appSubtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DoHSagePrimary
            )
        }
    }
}
