package com.safeguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class SafeGuardVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (tunnel != null) return
        tunnel = Builder()
            .setSession("SafeGuard")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.1")
            .addRoute("10.0.0.1", 32)
            .establish()
        running = tunnel != null
    }

    private fun stopVpn() {
        tunnel?.close()
        tunnel = null
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        tunnel?.close()
        tunnel = null
        running = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "SafeGuard protection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeGuard")
            .setContentText("Protection is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.safeguard.app.STOP"
        private const val CHANNEL_ID = "safeguard_protection"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var running: Boolean = false
    }
}
