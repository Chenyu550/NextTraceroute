/*
 * NextTraceroute, an Android traceroute app using NextTrace API.
 * Copyright (C) 2024-2026 surfaceocean
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.myriastra.nexttraceroute

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private val LocalColorMode = compositionLocalOf { 0 }

val NextTracerouteKeyColors = listOf(
    "蓝色" to Color(0xFF3482FF),
    "绿色" to Color(0xFF36D167),
    "紫色" to Color(0xFF7C4DFF),
    "黄色" to Color(0xFFFFB21D),
    "橙色" to Color(0xFFFF5722),
    "粉色" to Color(0xFFE91E63),
    "青色" to Color(0xFF00BCD4),
)

@Composable
fun NextTracerouteTheme(
    preferences: AppPreferences,
    content: @Composable () -> Unit,
) {
    val keyColor = NextTracerouteKeyColors.getOrNull(preferences.seedIndex - 1)?.second
    val colorSpec = ThemeColorSpec.entries.getOrNull(preferences.colorSpec) ?: ThemeColorSpec.Spec2021
    val paletteStyle = ThemePaletteStyle.entries.getOrNull(preferences.paletteStyle) ?: ThemePaletteStyle.Content
    val controller = remember(preferences.colorMode, keyColor, colorSpec, paletteStyle) {
        when (preferences.colorMode) {
            1 -> ThemeController(ColorSchemeMode.Light)
            2 -> ThemeController(ColorSchemeMode.Dark)
            3 -> ThemeController(
                ColorSchemeMode.MonetSystem,
                keyColor = keyColor,
                colorSpec = colorSpec,
                paletteStyle = paletteStyle,
            )
            4 -> ThemeController(
                ColorSchemeMode.MonetLight,
                keyColor = keyColor,
                colorSpec = colorSpec,
                paletteStyle = paletteStyle,
            )
            5 -> ThemeController(
                ColorSchemeMode.MonetDark,
                keyColor = keyColor,
                colorSpec = colorSpec,
                paletteStyle = paletteStyle,
            )
            else -> ThemeController(ColorSchemeMode.System)
        }
    }

    CompositionLocalProvider(LocalColorMode provides preferences.colorMode) {
        MiuixTheme(controller = controller, content = content)
    }
}

@Composable
fun nextTracerouteIsInDarkTheme(): Boolean = when (LocalColorMode.current) {
    1, 4 -> false
    2, 5 -> true
    else -> isSystemInDarkTheme()
}
