package com.opencode.sshterminal.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.opencode.sshterminal.R

class SshForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        val channelId = ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(channelId))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SshForegroundService:WakeLock").apply {
            acquire()
        }

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SshForegroundService:WifiLock").apply {
            acquire()
        }
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun ensureChannel(): String {
        val channelId = "ssh_session_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    private fun buildNotification(channelId: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_body))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
