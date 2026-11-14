package com.safeguard.app

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SafeGuardAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (!WATCHED_PACKAGES.contains(pkg)) return
        if (System.currentTimeMillis() < pausedUntil) return

        // Page not readable yet: do nothing, the next event retries
        val root = rootInActiveWindow ?: return

        val texts = StringBuilder()
        collectText(root, texts, 0)
        val screen = texts.toString().lowercase()

        if (isDangerous(pkg, screen)) {
            kickOut()
        }
    }

    private fun kickOut() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        for (i in 1..4) {
            handler.postDelayed({
                if (System.currentTimeMillis() >= pausedUntil) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, i * 300L)
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

    // The main "Network & internet" page has these words. The VPN page does not.
    private fun isNetworkMainPage(screen: String): Boolean {
        return screen.contains("hotspot") ||
            screen.contains("airplane mode") ||
            screen.contains("data saver") ||
            screen.contains("private dns") ||
            screen.contains("wi-fi") ||
            screen.contains("mobile network")
    }

    private fun isDangerous(pkg: String, screen: String): Boolean {
        val mentionsApp = screen.contains("safeguard")

        // Uninstall dialog
        if (pkg.contains("packageinstaller") && mentionsApp) return true

        // SafeGuard app info (uninstall / force stop)
        if (mentionsApp && (screen.contains("uninstall") || screen.contains("force stop"))) {
            return true
        }

        // Device admin pages
        if (screen.contains("device admin") && (mentionsApp || screen.contains("admin apps"))) {
            return true
        }

        // Accessibility page that lists SafeGuard
        if (mentionsApp && screen.contains("accessibility")) return true

        // VPN settings of SafeGuard (always-on, forget)
        if (mentionsApp && (
                screen.contains("always-on vpn") ||
                screen.contains("block connections without vpn") ||
                screen.contains("forget"))
        ) return true

        // VPN list page: mentions SafeGuard and VPN, but is NOT the Network main page
        if (mentionsApp && screen.contains("vpn") && !isNetworkMainPage(screen)) return true

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
