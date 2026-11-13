package com.safeguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (VpnService.prepare(context) == null) {
            context.startForegroundService(Intent(context, SafeGuardVpnService::class.java))
        }
    }
}
