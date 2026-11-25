package com.dohguard.kids.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NaturePeople
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.components.ButtonVariant
import com.dohguard.shared.components.DoHButton
import com.dohguard.shared.theme.*

/**
 * Tela de descanso visual para a criança relaxar a visão e o corpo.
 */
@Composable
fun RestScreen(
    onContinue: () -> Unit,
    onOpenPin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DoHBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(DoHSageLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NaturePeople,
                    contentDescription = "Descanso",
                    tint = DoHSagePrimary,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Hora de Descansar!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = DoHTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Seus olhos precisam de uma pausa. Olhe para longe pela janela, estique as pernas e beba uma água fresca!",
                fontSize = 15.sp,
                color = DoHTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Relaxing suggestions chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DoHCream
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = DoHSageDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Descansar os olhos",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DoHTextPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DoHCream
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = DoHSageDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Beber água",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DoHTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            DoHButton(
                text = "Já descansei, voltar (Demo)",
                onClick = onContinue,
                variant = ButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onOpenPin) {
                Text(
                    text = "Acesso dos Pais (PIN)",
                    color = DoHSageDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
