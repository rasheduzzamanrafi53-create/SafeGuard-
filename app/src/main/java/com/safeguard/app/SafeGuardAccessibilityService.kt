package com.safeguard.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SafeGuardAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastActionTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Only watch Settings and the package installer, nothing else
        if (!WATCHED_PACKAGES.contains(pkg)) return

        // Emergency pause: do nothing while paused
        if (System.currentTimeMillis() < pausedUntil) return

        val root = rootInActiveWindow ?: return
        val texts = StringBuilder()
        collectText(root, texts, 0)
        val screen = texts.toString().lowercase()

        if (isDangerous(pkg, screen)) {
            val now = System.currentTimeMillis()
            if (now - lastActionTime > 800) {
                lastActionTime = now
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 25 || sb.length > 4000) return
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb, depth + 1)
        }
    }

    private fun isDangerous(pkg: String, screen: String): Boolean {
        val mentionsApp = screen.contains("safeguard")

        // Uninstall dialog from the package installer
        if (pkg.contains("packageinstaller") && mentionsApp) return true

        // App info page of SafeGuard (uninstall / force stop)
        if (mentionsApp && (screen.contains("uninstall") || screen.contains("force stop"))) {
            return true
        }

        // Device admin list or SafeGuard's admin page
        if (screen.contains("device admin") && (mentionsApp || screen.contains("admin apps"))) {
            return true
        }

        // Accessibility page that lists SafeGuard
        if (mentionsApp && screen.contains("accessibility")) return true

        // VPN settings page that lists SafeGuard
        if (mentionsApp && screen.contains("vpn")) return true

        return false
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var pausedUntil: Long = 0L

        private val WATCHED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )
    }
}
