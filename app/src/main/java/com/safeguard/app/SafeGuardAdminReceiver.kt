package com.safeguard.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class SafeGuardAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Turning this off removes uninstall protection. " +
            "Wait 10 minutes and think before you continue."
    }
}
