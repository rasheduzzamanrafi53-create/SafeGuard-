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

        BlockStore.init(this)

        if (System.currentTimeMillis() < pausedUntil) return

        // 0) Apps you blocked: opening them sends you Home
        if (BlockStore.apps.contains(pkg) &&
            pkg != packageName &&
            pkg != "com.android.settings" &&
            !LAUNCHER_PACKAGES.contains(pkg)
        ) {
            kickOut()
            return
        }

        // 1) Search box words in YouTube and browsers
        if (SEARCH_PACKAGES.contains(pkg)) {
            val root = rootInActiveWindow ?: return
            if (searchHasBlockedWord(root, 0)) {
                kickOut()
                return
            }
        }

        // 2) Settings / installer / launcher protection
        if (!WATCHED_PACKAGES.contains(pkg)) return

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

    private fun searchHasBlockedWord(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 25) return false
        if (node.isEditable && !node.isPassword) {
            val typed = node.text?.toString()?.lowercase() ?: ""
            if (typed.isNotEmpty() && containsBlockedWord(typed)) return true
        }
        for (i in 0 until node.childCount) {
            if (searchHasBlockedWord(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun containsBlockedWord(text: String): Boolean {
        val cleaned = text
            .replace('0', 'o').replace('1', 'i').replace('3', 'e')
            .replace('4', 'a').replace('5', 's').replace('@', 'a').replace('$', 's')
        val squashed = cleaned.replace(Regex("[^a-z]"), "")
        for (w in SEARCH_WORDS) {
            if (cleaned.contains(w) || squashed.contains(w)) return true
        }
        for (w in BlockStore.keywords) {
            val ww = w.replace(" ", "")
            if (ww.length >= 2 && (cleaned.contains(w) || squashed.contains(ww))) return true
        }
        return false
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

        if (pkg == "com.android.settings" && (
                screen.contains("reset app preferences") ||
                screen.contains("reset app settings") ||
                screen.contains("reset apps"))
        ) return true

        if (pkg == "com.android.settings" && mentionsApp && (
                screen.contains("display over other apps") ||
                screen.contains("appear on top") ||
                screen.contains("draw over other apps") ||
                screen.contains("allow display over"))
        ) return true

        if (pkg == "com.android.settings" && mentionsApp && (
                screen.contains("battery") ||
                screen.contains("restricted") ||
                screen.contains("optimi"))
        ) return true

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

        private val SEARCH_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.sec.android.app.sbrowser",
            "com.google.android.googlequicksearchbox"
        )

        private val SEARCH_WORDS = listOf(
            "porn", "xxx", "hentai", "nude", "nudes", "naked", "sexvideo",
            "sexvideos", "pornhub", "xvideos", "xnxx", "xhamster", "onlyfans",
            "blowjob", "handjob", "milf", "camgirl", "erotic", "fuckvideo",
            "pussy", "boobs", "bdsm", "hotvideo", "nangavideo", "chudai",
            "bhabhi", "sexscene", "stripper", "orgasm", "masturbat"
        )

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
