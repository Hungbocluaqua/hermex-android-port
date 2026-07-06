package com.uzairansar.hermex.ui.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uzairansar.hermex.MainActivity
import com.uzairansar.hermex.R

class StreamStatusNotifier(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun show(
        sessionId: String,
        streamId: String?,
        recoveryLabel: String?,
        toolActivity: String?,
        preview: String?,
    ) {
        ensureChannel()
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val normalizedRecoveryLabel = recoveryLabel?.takeIf { it.isNotBlank() }
        val title = if (normalizedRecoveryLabel != null) {
            if (normalizedRecoveryLabel.startsWith("Checking", ignoreCase = true)) {
                "Hermex is checking stream"
            } else {
                "Hermex is reconnecting"
            }
        } else {
            "Hermex is responding"
        }
        val content = if (normalizedRecoveryLabel != null) {
            normalizedRecoveryLabel
        } else {
            toolActivity
                ?: preview?.takeIf { it.isNotBlank() }?.take(120)
                ?: "Response streaming"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setSubText(streamId?.take(8))
            .build()

        runCatching { notificationManager.notify(notificationId(sessionId), notification) }
    }

    fun clear(sessionId: String) {
        notificationManager.cancel(notificationId(sessionId))
    }

    fun showResponseComplete(sessionId: String) {
        ensureCompletionChannel()
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            "complete:$sessionId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Hermes response complete")
            .setContentText("The assistant finished responding.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching { notificationManager.notify(completionNotificationId(sessionId), notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Chat status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Status while Hermex responses are streaming"
                setShowBadge(false)
            },
        )
    }

    private fun ensureCompletionChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(COMPLETION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Response complete",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts when a Hermex response finishes"
                setShowBadge(true)
            },
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(sessionId: String): Int = "stream:$sessionId".hashCode()
    private fun completionNotificationId(sessionId: String): Int = "complete:$sessionId:${System.currentTimeMillis()}".hashCode()

    private companion object {
        const val CHANNEL_ID = "chat_stream_status"
        const val COMPLETION_CHANNEL_ID = "response_completion"
    }
}
