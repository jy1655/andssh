package com.opencode.sshterminal.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.opencode.sshterminal.R
import com.opencode.sshterminal.app.MainActivity
import com.opencode.sshterminal.session.TabId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BellNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastBellTime = mutableMapOf<TabId, Long>()

    init {
        createChannel()
    }

    fun notifyBell(tabId: TabId, tabTitle: String) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lastBellTime) {
            val prev = lastBellTime[tabId] ?: 0L
            if (now - prev < DEBOUNCE_MS) return
            lastBellTime[tabId] = now
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tabId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(context.getString(R.string.bell_notification_title))
            .setContentText(context.getString(R.string.bell_notification_body, tabTitle))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        manager.notify(NOTIFICATION_TAG, tabId.hashCode(), notification)
    }

    fun clearTab(tabId: TabId) {
        synchronized(lastBellTime) { lastBellTime.remove(tabId) }
        manager.cancel(NOTIFICATION_TAG, tabId.hashCode())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.bell_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.bell_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ssh_bell_channel"
        private const val NOTIFICATION_TAG = "bell"
        private const val DEBOUNCE_MS = 5_000L
    }
}
