package com.dohguard.kids.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohguard.shared.components.*
import com.dohguard.shared.theme.*

@Composable
fun UsagePermissionCard(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    hasAccessibilityPermission: Boolean = true,
    onRequestAccessibilityPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allActive = hasPermission && hasAccessibilityPermission
    DoHCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (allActive) DoHSageLight else DoHAlert.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allActive) Icons.Default.CheckCircle else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (allActive) DoHStatusPositive else DoHAlert,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Monitoramento e Bloqueio",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoHTextPrimary
                        )
                        Text(
                            text = if (allActive) {
                                "Uso e bloqueio automático ativos"
                            } else {
                                "Permissões necessárias no tablet"
                            },
                            fontSize = 11.sp,
                            color = DoHTextSecondary
                        )
                    }
                }

                StatusBadge(
                    text = if (allActive) "ATIVO" else "PENDENTE",
                    tone = if (allActive) BadgeTone.POSITIVE else BadgeTone.ALERT
                )
            }

            if (!hasPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ative o Acesso ao Uso para que o responsável acompanhe o tempo diário de cada aplicativo.",
                    fontSize = 12.sp,
                    color = DoHTextSecondary,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                DoHButton(
                    text = "Ativar Acesso ao Uso",
                    onClick = onRequestPermission,
                    variant = ButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!hasAccessibilityPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ative o Serviço de Acessibilidade do MonitorDad para aplicar o bloqueio automático de aplicativos fora do horário ou com limite esgotado.",
                    fontSize = 12.sp,
                    color = DoHTextSecondary,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                DoHButton(
                    text = "Ativar Bloqueio (Acessibilidade)",
                    onClick = onRequestAccessibilityPermission,
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!allActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🔒 Privacidade garantida: mensagens, senhas e fotos nunca são coletadas.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DoHSageDark
                )
            }
        }
    }
}
