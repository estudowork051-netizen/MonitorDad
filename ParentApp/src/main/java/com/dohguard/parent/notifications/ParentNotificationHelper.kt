package com.dohguard.parent.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dohguard.shared.remote.ParentNotificationEvent

/**
 * Gerenciador de notificações locais do MonitorDad Parent.
 * Respeita permissão POST_NOTIFICATIONS (Android 13+) e utiliza canal dedicado.
 */
object ParentNotificationHelper {

    private const val CHANNEL_ID = "monitordad_parent_alerts"
    private const val CHANNEL_NAME = "Alertas do Tablet"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notificações de conexão, limites, pedidos e comandos remotos do MonitorDad"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showNotification(context: Context, event: ParentNotificationEvent) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(event.title)
                .setContentText(event.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify(event.dedupKey.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permissão revogada em tempo de execução
        }
    }
}
