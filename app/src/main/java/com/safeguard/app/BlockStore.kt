package com.safeguard.app

import android.content.Context
import android.content.SharedPreferences

object BlockStore {
    private var prefs: SharedPreferences? = null

    @Volatile var keywords: Set<String> = emptySet()
    @Volatile var websites: Set<String> = emptySet()
    @Volatile var apps: Set<String> = emptySet()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences("safeguard_blocklist", Context.MODE_PRIVATE)
        reload()
    }

    private fun load(type: String): Set<String> {
        val p = prefs ?: return emptySet()
        return HashSet<String>(p.getStringSet(type, null) ?: emptySet())
    }

    private fun reload() {
        keywords = load("keywords")
        websites = load("websites")
        apps = load("apps")
    }

    private fun clean(type: String, value: String): String {
        var v = value.trim().lowercase()
        if (type == "websites") {
            v = v.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            v = v.substringBefore("/")
        }
        return v
    }

    fun add(type: String, value: String) {
        val v = clean(type, value)
        if (v.isEmpty()) return
        val p = prefs ?: return
        val set = HashSet<String>(load(type))
        set.add(v)
        p.edit().putStringSet(type, set).apply()
        reload()
    }

    fun remove(type: String, value: String) {
        val p = prefs ?: return
        val set = HashSet<String>(load(type))
        set.remove(value)
        p.edit().putStringSet(type, set).apply()
        reload()
    }

    fun domainBlocked(domain: String): Boolean {
        var d = domain
        while (true) {
            if (websites.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot < 0) break
            d = d.substring(dot + 1)
        }
        for (k in keywords) {
            val kk = k.replace(" ", "")
            if (kk.length >= 3 && domain.contains(kk)) return true
        }
        return false
    }
}
