package com.dicren.autospot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dicren.autospot.MainActivity
import com.dicren.autospot.R

/**
 * Builds the notifications used by the background services and owns their channels.
 *
 * Everything uses the monochrome [R.drawable.ic_notification]: a coloured launcher icon renders
 * as a white blob in the status bar.
 */
class AppNotifications(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        // A new channel id was required to lower the importance on existing installs: Android
        // ignores importance changes on channels that already exist.
        manager.deleteNotificationChannel(LEGACY_MONITOR_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                MONITOR_CHANNEL_ID,
                context.getString(R.string.bluetooth_monitor_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.bluetooth_monitor_channel_description)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SAVE_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            },
        )
    }

    fun monitorNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, MONITOR_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.bluetooth_monitor_notification_title))
            .setContentText(context.getString(R.string.bluetooth_monitor_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun savingNotification(): Notification =
        NotificationCompat
            .Builder(context, SAVE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    /** Tells the user the automatic save failed, instead of failing silently. */
    fun notifySaveFailed() {
        notifyFailure(
            id = SAVE_FAILED_NOTIFICATION_ID,
            title = context.getString(R.string.notification_save_failed_title),
            text = context.getString(R.string.notification_save_failed_text),
        )
    }

    /** Tells the user that auto-save is off because the monitor could not start. */
    fun notifyMonitorFailed() {
        notifyFailure(
            id = MONITOR_FAILED_NOTIFICATION_ID,
            title = context.getString(R.string.monitor_failed_title),
            text = context.getString(R.string.monitor_failed_text),
        )
    }

    private fun notifyFailure(
        id: Int,
        title: String,
        text: String,
    ) {
        val notification =
            NotificationCompat
                .Builder(context, SAVE_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was denied; the failure is still in logcat.
        }
    }

    companion object {
        const val MONITOR_NOTIFICATION_ID = 3
        const val SAVE_NOTIFICATION_ID = 1
        private const val SAVE_FAILED_NOTIFICATION_ID = 2
        private const val MONITOR_FAILED_NOTIFICATION_ID = 4

        private const val MONITOR_CHANNEL_ID = "bluetooth_monitor_channel_v2"
        private const val LEGACY_MONITOR_CHANNEL_ID = "bluetooth_monitor_channel"
        private const val SAVE_CHANNEL_ID = "parking_location_channel"
    }
}
