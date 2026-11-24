package com.dicren.autospot.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dicren.autospot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifications = AppNotifications(context)

    @Test
    fun `creates the current channels and removes the legacy monitor one`() {
        manager.createNotificationChannel(
            NotificationChannel(
                "bluetooth_monitor_channel",
                "Legacy monitor",
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        notifications.createChannels()

        val shadowManager = shadowOf(manager)
        val ids = shadowManager.notificationChannels.map { it.id }

        assertTrue(ids.contains("bluetooth_monitor_channel_v2"))
        assertTrue(ids.contains("parking_location_channel"))
        assertTrue(shadowManager.isChannelDeleted("bluetooth_monitor_channel"))
    }

    @Test
    fun `the monitor notification opens the app`() {
        notifications.createChannels()

        val notification = notifications.monitorNotification()

        assertEquals("bluetooth_monitor_channel_v2", notification.channelId)
        assertEquals(
            context.getString(R.string.bluetooth_monitor_notification_title),
            notification.extras.getString(Notification.EXTRA_TITLE),
        )
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun `the saving notification is ongoing`() {
        notifications.createChannels()

        val notification = notifications.savingNotification()

        assertEquals("parking_location_channel", notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `a denied notification permission does not crash the failure notice`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.createChannels()

        notifications.notifySaveFailed()
    }
}
