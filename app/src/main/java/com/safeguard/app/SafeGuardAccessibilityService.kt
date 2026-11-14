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

        // Ignore every app except Settings, installer and launchers
        if (!WATCHED_PACKAGES.contains(pkg)) return
        if (System.currentTimeMillis() < pausedUntil) return

        val root = rootInActiveWindow ?: return

        val texts = StringBuilder()
        collectText(root, texts, 0)
        val screen = texts.toString().lowercase()

        val danger = if (LAUNCHER_PACKAGES.contains(pkg)) {
            isLauncherDanger(screen)
        } else {
            isDangerous(pkg, screen)
        }

        if (danger) kickOut()
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

    // Home screen: long-press menu or drag-to-uninstall target
    private fun isLauncherDanger(screen: String): Boolean {
        if (screen.contains("uninstall")) return true
        if (screen.contains("safeguard") && (
                screen.contains("app info") ||
                screen.contains("remove") ||
                screen.contains("pause app"))
        ) return true
        return false
    }

    private fun isNetworkMainPage(screen: String): Boolean {
        return screen.contains("hotspot") ||
            screen.contains("airplane mode") ||
            screen.contains("data saver") ||
            screen.contains("private dns") ||
            screen.contains("wi-fi") ||
            screen.contains("mobile network")
    }

    private fun isPrivacyMainPage(screen: String): Boolean {
        return screen.contains("permission manager") ||
            screen.contains("privacy dashboard") ||
            screen.contains("show passwords") ||
            screen.contains("lock screen") ||
            screen.contains("autofill") ||
            screen.contains("ads") ||
            screen.contains("clipboard")
    }

    private fun isDangerous(pkg: String, screen: String): Boolean {
        val mentionsApp = screen.contains("safeguard")

        if (pkg.contains("packageinstaller") && mentionsApp) return true

        if (mentionsApp && (screen.contains("uninstall") || screen.contains("force stop"))) {
            return true
        }

        if (screen.contains("device admin") && (mentionsApp || screen.contains("admin apps"))) {
            return true
        }

        if (mentionsApp && screen.contains("accessibility usage") && !isPrivacyMainPage(screen)) {
            return true
        }

        if (mentionsApp && screen.contains("accessibility") && !isPrivacyMainPage(screen)) {
            return true
        }

        if (mentionsApp && (
                screen.contains("use safeguard") ||
                screen.contains("stop safeguard") ||
                screen.contains("full control") ||
                screen.contains("view and control screen"))
        ) return true

        if (mentionsApp && (
                screen.contains("always-on vpn") ||
                screen.contains("block connections without vpn") ||
                screen.contains("forget"))
        ) return true

        if (mentionsApp && screen.contains("vpn") && !isNetworkMainPage(screen)) return true

        return false
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var pausedUntil: Long = 0L

        private val LAUNCHER_PACKAGES = setOf(
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.transsion.hilauncher",
            "com.mediatek.launcher"
        )

        private val WATCHED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        ) + LAUNCHER_PACKAGES
    }
}
