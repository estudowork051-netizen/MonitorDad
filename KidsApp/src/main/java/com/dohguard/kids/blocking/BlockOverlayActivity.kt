package com.dohguard.kids.blocking

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dohguard.kids.screens.LockScreen
import com.dohguard.kids.screens.PinAuthDialog
import com.dohguard.shared.security.PinSecurityManager
import com.dohguard.shared.sync.ControlRulesRepository
import com.dohguard.shared.theme.DoHGuardTheme
import com.dohguard.shared.ui.ImmersiveModeHelper

/**
 * Tela exibida quando um aplicativo ou o dispositivo é bloqueado automaticamente
 * por limite diário, limite de app/categoria, rotina ativa ou pausa remota.
 */
class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImmersiveModeHelper.applyImmersiveMode(this)
        ControlRulesRepository.init(this)

        val blockTitle = intent.getStringExtra(EXTRA_BLOCK_TITLE) ?: "Tempo Encerrado por Hoje!"
        val blockMessage = intent.getStringExtra(EXTRA_BLOCK_MESSAGE)
            ?: "Este aplicativo atingiu o limite ou está pausado pelas regras familiares."
        val unlockTime = intent.getStringExtra(EXTRA_UNLOCK_TIME)?.takeIf { it.isNotBlank() }
        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        val blockedAppName = intent.getStringExtra(EXTRA_BLOCKED_APP_NAME)

        val pinManager = PinSecurityManager(this)

        setContent {
            DoHGuardTheme {
                var showPinDialog by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    LockScreen(
                        title = blockTitle,
                        message = blockMessage,
                        unlockTimeFormatted = unlockTime,
                        onDismissDemo = { finish() },
                        onRequestMoreTime = {
                            ControlRulesRepository.requestExtraTimeFromKids(
                                appName = blockedAppName,
                                requestedMinutes = 15,
                                description = if (blockedAppName != null) {
                                    "Lucas pediu +15 min para $blockedAppName"
                                } else {
                                    "Lucas pediu +15 min de tempo adicional"
                                }
                            )
                            Toast.makeText(
                                this@BlockOverlayActivity,
                                "Pedido de +15 min enviado ao responsável!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onOpenPin = { showPinDialog = true }
                    )

                    if (showPinDialog) {
                        PinAuthDialog(
                            onVerifyPin = { input -> pinManager.verifyPin(input) },
                            onDismiss = { showPinDialog = false },
                            onPinSuccess = {
                                showPinDialog = false
                                ControlRulesRepository.grantExtraTime(
                                    minutes = 15,
                                    targetPackage = blockedPkg,
                                    targetAppName = blockedAppName,
                                    reason = "Liberação imediata via PIN parental"
                                )
                                Toast.makeText(
                                    this@BlockOverlayActivity,
                                    "Liberado por 15 minutos pelo responsável",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ImmersiveModeHelper.applyImmersiveMode(this)
    }

    companion object {
        const val EXTRA_BLOCK_TITLE = "extra_block_title"
        const val EXTRA_BLOCK_MESSAGE = "extra_block_message"
        const val EXTRA_UNLOCK_TIME = "extra_unlock_time"
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name"
    }
}
