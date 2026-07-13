/*
 * NextTraceroute, an Android traceroute app using NextTrace API.
 * Copyright (C) 2024-2026 surfaceocean
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.myriastra.nexttraceroute

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import kotlin.math.roundToInt

private val colorModeOptions = listOf(
    "跟随系统",
    "浅色",
    "深色",
    "莫奈·跟随系统",
    "莫奈·浅色",
    "莫奈·深色",
)
private val keyColorOptions get() = listOf("默认") + NextTracerouteKeyColors.map { it.first }
private val paletteStyleOptions = ThemePaletteStyle.entries.map {
    when (it) {
        ThemePaletteStyle.TonalSpot -> "柔和色调 (TonalSpot)"
        ThemePaletteStyle.Neutral -> "中性 (Neutral)"
        ThemePaletteStyle.Vibrant -> "鲜艳 (Vibrant)"
        ThemePaletteStyle.Expressive -> "表现力 (Expressive)"
        ThemePaletteStyle.Rainbow -> "彩虹 (Rainbow)"
        ThemePaletteStyle.FruitSalad -> "水果沙拉 (FruitSalad)"
        ThemePaletteStyle.Monochrome -> "单色 (Monochrome)"
        ThemePaletteStyle.Fidelity -> "忠实 (Fidelity)"
        ThemePaletteStyle.Content -> "内容 (Content)"
    }
}
private val colorSpecOptions = ThemeColorSpec.entries.map {
    when (it) {
        ThemeColorSpec.Spec2021 -> "2021 规范"
        ThemeColorSpec.Spec2025 -> "2025 规范"
    }
}
private val navigationModeOptions = listOf("图标和文字", "仅图标", "仅选中项显示文字")
private val floatingStyleOptions = listOf("默认", "iOS 样式")
private val floatingPositionOptions = listOf("居中", "靠左", "靠右")
private val languageOptions = listOf("默认", "中文", "English")
private val dnsModeOptions = listOf("UDP", "TCP", "DNS over HTTPS")
private val dohServerOptions = listOf(
    "https://1.1.1.1/dns-query",
    "https://[2606:4700:4700::1111]/dns-query",
    "https://8.8.8.8/dns-query",
    "https://[2001:4860:4860::8888]/dns-query",
    "https://223.5.5.5/dns-query",
    "https://doh.pub/dns-query",
    "https://dns.cloudflare.com/dns-query",
    "https://dns.adguard-dns.com/dns-query",
    "https://doh.opendns.com/dns-query",
    "https://dns.google/dns-query",
    "https://ordns.he.net/dns-query",
    "https://dns.quad9.net/dns-query",
)

private enum class EditableSetting(val title: String) {
    DnsServer("DNS 服务器"),
    ApiHost("API 主机"),
    ApiDnsName("API DNS 主机"),
    PowApiHost("PoW API 主机"),
    PowApiDnsName("PoW API DNS 主机"),
}

@Composable
fun SettingsPage(
    preferences: AppPreferences,
    onPreferencesChange: ((AppPreferences) -> AppPreferences) -> Unit,
    onShowAbout: () -> Unit,
    padding: PaddingValues,
    isWideScreen: Boolean,
) {
    val context = LocalContext.current
    val handler = remember { TracerouteHandler() }
    val listState = rememberLazyListState()
    val backdrop = rememberAppBlurBackdrop(preferences.enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var editingSetting by remember { mutableStateOf<EditableSetting?>(null) }
    var editingValue by remember { mutableStateOf("") }

    fun beginEdit(setting: EditableSetting, value: String) {
        editingSetting = setting
        editingValue = value
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    title = "设置",
                    subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPadding(innerPadding, padding, isWideScreen)
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = contentPadding,
            ) {
                item(key = "appearanceTitle") { SmallTitle("界面") }
                item(key = "appearance") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        OverlayDropdownPreference(
                            title = "颜色模式",
                            items = colorModeOptions,
                            selectedIndex = preferences.colorMode,
                            onSelectedIndexChange = { index ->
                                onPreferencesChange { it.copy(colorMode = index) }
                            },
                        )
                        AnimatedVisibility(visible = preferences.colorMode in 3..5) {
                            OverlayDropdownPreference(
                                title = "主色",
                                items = keyColorOptions,
                                selectedIndex = preferences.seedIndex,
                                onSelectedIndexChange = { index ->
                                    onPreferencesChange { it.copy(seedIndex = index) }
                                },
                            )
                        }
                        AnimatedVisibility(visible = preferences.colorMode in 3..5 && preferences.seedIndex > 0) {
                            Column {
                                OverlayDropdownPreference(
                                    title = "调色板风格",
                                    items = paletteStyleOptions,
                                    selectedIndex = preferences.paletteStyle,
                                    onSelectedIndexChange = { index ->
                                        onPreferencesChange { it.copy(paletteStyle = index) }
                                    },
                                )
                                OverlayDropdownPreference(
                                    title = "颜色规范",
                                    items = colorSpecOptions,
                                    selectedIndex = preferences.colorSpec,
                                    onSelectedIndexChange = { index ->
                                        onPreferencesChange { it.copy(colorSpec = index) }
                                    },
                                )
                            }
                        }
                        AnimatedVisibility(visible = isRuntimeShaderSupported()) {
                            SwitchPreference(
                                title = "启用模糊效果",
                                checked = preferences.enableBlur,
                                onCheckedChange = { enabled ->
                                    onPreferencesChange { it.copy(enableBlur = enabled) }
                                },
                            )
                        }
                        AnimatedVisibility(visible = !isWideScreen && !preferences.useFloatingNavigationBar) {
                            OverlayDropdownPreference(
                                title = "导航栏模式",
                                items = navigationModeOptions,
                                selectedIndex = preferences.navigationBarMode,
                                onSelectedIndexChange = { index ->
                                    onPreferencesChange { it.copy(navigationBarMode = index) }
                                },
                            )
                        }
                        AnimatedVisibility(visible = !isWideScreen) {
                            Column {
                                SwitchPreference(
                                    title = "使用浮动导航栏",
                                    checked = preferences.useFloatingNavigationBar,
                                    onCheckedChange = { enabled ->
                                        onPreferencesChange { it.copy(useFloatingNavigationBar = enabled) }
                                    },
                                )
                                AnimatedVisibility(visible = preferences.useFloatingNavigationBar) {
                                    Column {
                                        OverlayDropdownPreference(
                                            title = "浮动导航栏样式",
                                            items = floatingStyleOptions,
                                            selectedIndex = preferences.floatingNavigationBarStyle,
                                            onSelectedIndexChange = { index ->
                                                onPreferencesChange { it.copy(floatingNavigationBarStyle = index) }
                                            },
                                        )
                                        AnimatedVisibility(visible = preferences.floatingNavigationBarStyle == 0) {
                                            OverlayDropdownPreference(
                                                title = "浮动导航栏位置",
                                                items = floatingPositionOptions,
                                                selectedIndex = preferences.floatingNavigationBarPosition,
                                                onSelectedIndexChange = { index ->
                                                    onPreferencesChange { it.copy(floatingNavigationBarPosition = index) }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "traceTitle") { SmallTitle("追踪") }
                item(key = "traceSettings") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        OverlayDropdownPreference(
                            title = "API 返回语言",
                            items = languageOptions,
                            selectedIndex = when (preferences.currentLanguage) {
                                "zh" -> 1
                                "en" -> 2
                                else -> 0
                            },
                            onSelectedIndexChange = { index ->
                                onPreferencesChange {
                                    it.copy(currentLanguage = listOf("Default", "zh", "en")[index])
                                }
                            },
                        )
                        SwitchPreference(
                            title = "启用路径地图",
                            summary = "追踪完成后显示 NextTrace 地图入口",
                            checked = preferences.isTraceMapEnabled,
                            onCheckedChange = { enabled ->
                                onPreferencesChange { it.copy(isTraceMapEnabled = enabled) }
                            },
                        )
                        SliderPreference(
                            value = preferences.maxTraceTTL.toFloat(),
                            onValueChange = { value ->
                                onPreferencesChange { it.copy(maxTraceTTL = value.roundToInt().coerceIn(1, 255)) }
                            },
                            title = "最大跳数",
                            valueText = "${preferences.maxTraceTTL} 跳",
                            valueRange = 1f..255f,
                            steps = 253,
                        )
                        SliderPreference(
                            value = preferences.traceTimeout.toFloatOrNull()?.coerceIn(1f, 10f) ?: 1f,
                            onValueChange = { value ->
                                onPreferencesChange { it.copy(traceTimeout = value.roundToInt().toString()) }
                            },
                            title = "单次超时",
                            valueText = "${preferences.traceTimeout} 秒",
                            valueRange = 1f..10f,
                            steps = 8,
                        )
                        SliderPreference(
                            value = preferences.traceCount.toFloatOrNull()?.coerceIn(1f, 10f) ?: 5f,
                            onValueChange = { value ->
                                onPreferencesChange { it.copy(traceCount = value.roundToInt().toString()) }
                            },
                            title = "每跳探测次数",
                            valueText = "${preferences.traceCount} 次",
                            valueRange = 1f..10f,
                            steps = 8,
                        )
                    }
                }

                item(key = "dnsTitle") { SmallTitle("DNS") }
                item(key = "dnsSettings") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        OverlayDropdownPreference(
                            title = "DNS 模式",
                            items = dnsModeOptions,
                            selectedIndex = when (preferences.currentDNSMode) {
                                "tcp" -> 1
                                "doh" -> 2
                                else -> 0
                            },
                            onSelectedIndexChange = { index ->
                                onPreferencesChange {
                                    it.copy(currentDNSMode = listOf("udp", "tcp", "doh")[index])
                                }
                            },
                        )
                        if (preferences.currentDNSMode == "doh") {
                            OverlayDropdownPreference(
                                title = "DoH 服务器",
                                items = dohServerOptions,
                                selectedIndex = dohServerOptions.indexOf(preferences.currentDOHServer).coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    onPreferencesChange { it.copy(currentDOHServer = dohServerOptions[index]) }
                                },
                            )
                        } else {
                            ArrowPreference(
                                title = "DNS 服务器",
                                summary = preferences.tracerouteDNSServer,
                                onClick = {
                                    beginEdit(EditableSetting.DnsServer, preferences.tracerouteDNSServer)
                                },
                            )
                        }
                    }
                }

                item(key = "apiTitle") { SmallTitle("NextTrace API") }
                item(key = "apiSettings") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "API 主机",
                            summary = preferences.apiHostName,
                            onClick = { beginEdit(EditableSetting.ApiHost, preferences.apiHostName) },
                        )
                        ArrowPreference(
                            title = "API DNS 主机",
                            summary = preferences.apiDNSName,
                            onClick = { beginEdit(EditableSetting.ApiDnsName, preferences.apiDNSName) },
                        )
                        ArrowPreference(
                            title = "PoW API 主机",
                            summary = preferences.apiHostNamePOW,
                            onClick = { beginEdit(EditableSetting.PowApiHost, preferences.apiHostNamePOW) },
                        )
                        ArrowPreference(
                            title = "PoW API DNS 主机",
                            summary = preferences.apiDNSNamePOW,
                            onClick = { beginEdit(EditableSetting.PowApiDnsName, preferences.apiDNSNamePOW) },
                        )
                    }
                }

                item(key = "aboutTitle") { SmallTitle("其他") }
                item(key = "about") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        ArrowPreference(
                            title = "关于 NextTraceroute",
                            summary = "版本 ${BuildConfig.VERSION_NAME}",
                            onClick = onShowAbout,
                        )
                    }
                }
                item(key = "settingsBottomSpace") { Spacer(Modifier.height(12.dp)) }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }

    val setting = editingSetting
    OverlayDialog(
        show = setting != null,
        title = setting?.title.orEmpty(),
        onDismissRequest = { editingSetting = null },
        onDismissFinished = {},
    ) {
        Column {
            TextField(
                value = editingValue,
                onValueChange = { editingValue = it.replace("\n", "").trim() },
                modifier = Modifier.fillMaxWidth(),
                label = setting?.title.orEmpty(),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { editingSetting = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "保存",
                    onClick = {
                        if (setting == null) return@TextButton
                        val value = editingValue.trim()
                        val inputType = handler.identifyInput(value)
                        val valid = when (setting) {
                            EditableSetting.DnsServer -> inputType == IPV4_IDENTIFIER || inputType == IPV6_IDENTIFIER
                            EditableSetting.ApiHost,
                            EditableSetting.PowApiHost,
                            -> inputType == HOSTNAME_IDENTIFIER
                            EditableSetting.ApiDnsName,
                            EditableSetting.PowApiDnsName,
                            -> inputType == HOSTNAME_IDENTIFIER || inputType == IPV4_IDENTIFIER || inputType == IPV6_IDENTIFIER
                        }
                        if (!valid) {
                            Toast.makeText(context, "输入格式无效", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        onPreferencesChange {
                            when (setting) {
                                EditableSetting.DnsServer -> it.copy(tracerouteDNSServer = value)
                                EditableSetting.ApiHost -> it.copy(apiHostName = value)
                                EditableSetting.ApiDnsName -> it.copy(apiDNSName = value)
                                EditableSetting.PowApiHost -> it.copy(apiHostNamePOW = value)
                                EditableSetting.PowApiDnsName -> it.copy(apiDNSNamePOW = value)
                            }
                        }
                        editingSetting = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
