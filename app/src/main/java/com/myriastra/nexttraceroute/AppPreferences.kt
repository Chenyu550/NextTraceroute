/*
 * NextTraceroute, an Android traceroute app using NextTrace API.
 * Copyright (C) 2024-2026 surfaceocean
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.myriastra.nexttraceroute

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

@Immutable
data class AppPreferences(
    // Miuix theme settings.
    val colorMode: Int = 0,
    val seedIndex: Int = 0,
    val paletteStyle: Int = 0,
    val colorSpec: Int = 0,
    val enableBlur: Boolean = true,
    // Miuix navigation settings explicitly exposed by the official demo.
    val navigationBarMode: Int = 0,
    val useFloatingNavigationBar: Boolean = true,
    val floatingNavigationBarStyle: Int = 1,
    val floatingNavigationBarPosition: Int = 0,
    // Traceroute settings retained from the original application.
    val currentLanguage: String = "Default",
    val isTraceMapEnabled: Boolean = true,
    val maxTraceTTL: Int = 30,
    val traceTimeout: String = "1",
    val traceCount: String = "5",
    val currentDNSMode: String = "udp",
    val currentDOHServer: String = "https://1.1.1.1/dns-query",
    val tracerouteDNSServer: String = "1.1.1.1",
    val apiHostNamePOW: String = "origin-fallback.nxtrace.org",
    val apiDNSNamePOW: String = "api.nxtrace.org",
    val apiHostName: String = "origin-fallback.nxtrace.org",
    val apiDNSName: String = "api.nxtrace.org",
)

/** Persists Miuix and tracing preferences without carrying over legacy UI color state. */
class AppPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("miuix_preferences", Context.MODE_PRIVATE)
    private val legacyFile = File(context.filesDir, "settings.json")
    private val gson = Gson()

    fun load(): AppPreferences {
        if (preferences.getBoolean(KEY_INITIALIZED, false)) {
            return readCurrent()
        }
        val migrated = migrateLegacyTracingPreferences()
        save(migrated)
        return migrated
    }

    fun save(value: AppPreferences) {
        preferences.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putInt("colorMode", value.colorMode)
            .putInt("seedIndex", value.seedIndex)
            .putInt("paletteStyle", value.paletteStyle)
            .putInt("colorSpec", value.colorSpec)
            .putBoolean("enableBlur", value.enableBlur)
            .putInt("navigationBarMode", value.navigationBarMode)
            .putBoolean("useFloatingNavigationBar", value.useFloatingNavigationBar)
            .putInt("floatingNavigationBarStyle", value.floatingNavigationBarStyle)
            .putInt("floatingNavigationBarPosition", value.floatingNavigationBarPosition)
            .putString("currentLanguage", value.currentLanguage)
            .putBoolean("isTraceMapEnabled", value.isTraceMapEnabled)
            .putInt("maxTraceTTL", value.maxTraceTTL)
            .putString("traceTimeout", value.traceTimeout)
            .putString("traceCount", value.traceCount)
            .putString("currentDNSMode", value.currentDNSMode)
            .putString("currentDOHServer", value.currentDOHServer)
            .putString("tracerouteDNSServer", value.tracerouteDNSServer)
            .putString("apiHostNamePOW", value.apiHostNamePOW)
            .putString("apiDNSNamePOW", value.apiDNSNamePOW)
            .putString("apiHostName", value.apiHostName)
            .putString("apiDNSName", value.apiDNSName)
            .apply()
    }

    private fun readCurrent() = AppPreferences(
        colorMode = preferences.getInt("colorMode", 0),
        seedIndex = preferences.getInt("seedIndex", 0),
        paletteStyle = preferences.getInt("paletteStyle", 0),
        colorSpec = preferences.getInt("colorSpec", 0),
        enableBlur = preferences.getBoolean("enableBlur", true),
        navigationBarMode = preferences.getInt("navigationBarMode", 0),
        useFloatingNavigationBar = preferences.getBoolean("useFloatingNavigationBar", true),
        floatingNavigationBarStyle = preferences.getInt("floatingNavigationBarStyle", 1),
        floatingNavigationBarPosition = preferences.getInt("floatingNavigationBarPosition", 0),
        currentLanguage = preferences.getString("currentLanguage", "Default") ?: "Default",
        isTraceMapEnabled = preferences.getBoolean("isTraceMapEnabled", true),
        maxTraceTTL = preferences.getInt("maxTraceTTL", 30),
        traceTimeout = preferences.getString("traceTimeout", "1") ?: "1",
        traceCount = preferences.getString("traceCount", "5") ?: "5",
        currentDNSMode = preferences.getString("currentDNSMode", "udp") ?: "udp",
        currentDOHServer = preferences.getString("currentDOHServer", "https://1.1.1.1/dns-query")
            ?: "https://1.1.1.1/dns-query",
        tracerouteDNSServer = preferences.getString("tracerouteDNSServer", "1.1.1.1") ?: "1.1.1.1",
        apiHostNamePOW = preferences.getString("apiHostNamePOW", "origin-fallback.nxtrace.org")
            ?: "origin-fallback.nxtrace.org",
        apiDNSNamePOW = preferences.getString("apiDNSNamePOW", "api.nxtrace.org") ?: "api.nxtrace.org",
        apiHostName = preferences.getString("apiHostName", "origin-fallback.nxtrace.org")
            ?: "origin-fallback.nxtrace.org",
        apiDNSName = preferences.getString("apiDNSName", "api.nxtrace.org") ?: "api.nxtrace.org",
    )

    private fun migrateLegacyTracingPreferences(): AppPreferences {
        val legacy = runCatching {
            if (!legacyFile.exists()) return@runCatching emptyMap<String, Any?>()
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson<Map<String, Any?>>(legacyFile.readText(), type)
        }.getOrDefault(emptyMap())

        fun string(key: String, default: String) = legacy[key] as? String ?: default
        fun boolean(key: String, default: Boolean) = legacy[key] as? Boolean ?: default
        fun integer(key: String, default: Int): Int = when (val value = legacy[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

        return AppPreferences(
            currentLanguage = string("currentLanguage", "Default"),
            isTraceMapEnabled = boolean("isTraceMapEnabled", true),
            maxTraceTTL = integer("maxTraceTTL", 30).coerceIn(1, 255),
            traceTimeout = string("traceTimeout", "1"),
            traceCount = string("traceCount", "5"),
            currentDNSMode = string("currentDNSMode", "udp"),
            currentDOHServer = string("currentDOHServer", "https://1.1.1.1/dns-query"),
            tracerouteDNSServer = string("tracerouteDNSServer", "1.1.1.1"),
            apiHostNamePOW = string("apiHostNamePOW", "origin-fallback.nxtrace.org"),
            apiDNSNamePOW = string("apiDNSNamePOW", "api.nxtrace.org"),
            apiHostName = string("apiHostName", "origin-fallback.nxtrace.org"),
            apiDNSName = string("apiDNSName", "api.nxtrace.org"),
        )
    }

    private companion object {
        const val KEY_INITIALIZED = "initialized"
    }
}
