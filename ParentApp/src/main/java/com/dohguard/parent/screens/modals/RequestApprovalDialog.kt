package com.dohguard.parent.screens.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dohguard.shared.components.ButtonVariant
import com.dohguard.shared.components.DoHButton
import com.dohguard.shared.model.PendingRequest
import com.dohguard.shared.theme.*

@Composable
fun RequestApprovalDialog(
    request: PendingRequest,
    onDismiss: () -> Unit,
    onApprove: (PendingRequest) -> Unit,
    onReject: (PendingRequest) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DoHDialogCornerRadius),
            color = DoHWhite,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DoHSageLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = DoHSageDark,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = request.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoHTextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = request.description,
                    fontSize = 14.sp,
                    color = DoHTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Solicitado ${request.requestedAt}",
                    fontSize = 12.sp,
                    color = DoHSageDark,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DoHButton(
                        text = "Recusar",
                        onClick = {
                            onReject(request)
                            onDismiss()
                        },
                        variant = ButtonVariant.OUTLINED,
                        modifier = Modifier.weight(1f)
                    )
                    DoHButton(
                        text = "Aprovar +${request.requestedMinutes}m",
                        onClick = {
                            onApprove(request)
                            onDismiss()
                        },
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}
