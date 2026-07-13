/*
 * NextTraceroute, an Android traceroute app using NextTrace API.
 * Copyright (C) 2024-2026 surfaceocean
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.myriastra.nexttraceroute

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.xbill.DNS.config.AndroidResolverConfigProvider.setContext(this)
        setContent {
            var darkMode by remember { mutableStateOf(false) }
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                    ) { darkMode },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose { }
            }
            NextTracerouteApplication(onDarkModeChanged = { darkMode = it })
        }
    }
}

@androidx.compose.runtime.Composable
private fun NextTracerouteApplication(
    onDarkModeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { AppPreferencesStore(context.applicationContext) }
    var preferences by remember { mutableStateOf(store.load()) }

    NextTracerouteTheme(preferences = preferences) {
        val darkMode = nextTracerouteIsInDarkTheme()
        SideEffect { onDarkModeChanged(darkMode) }
        NextTracerouteNavigation(
            preferences = preferences,
            onPreferencesChange = { transform ->
                preferences = transform(preferences)
                store.save(preferences)
            },
        )
    }
}
