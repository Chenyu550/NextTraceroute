/*
 * NextTraceroute, an Android traceroute app using NextTrace API.
 * Copyright (C) 2024-2026 surfaceocean
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.myriastra.nexttraceroute

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.myriastra.nexttraceroute.component.effect.BgEffectBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PROJECT_URL = "https://github.com/Chenyu550/NextTraceroute"
private const val ISSUE_URL = "$PROJECT_URL/issues"
private const val GROUP_URL = "https://t.me/nexttraceroute"
private const val LICENSE_URL = "$PROJECT_URL/blob/master/LICENSE"
private const val PRIVACY_URL = "$PROJECT_URL/blob/master/PrivacyPolicy.md"

@Composable
fun AboutPage(
    preferences: AppPreferences,
    onBack: () -> Unit,
    onShowLicenses: () -> Unit,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (listState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    // The official page uses a separate backdrop for the scene and for the cards below.
    // Keeping those recording layers separate prevents a recursive blur graph on HyperOS.
    val sceneBackdrop = rememberAppBlurBackdrop(preferences.enableBlur)
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(sceneBackdrop) {
        derivedStateOf { sceneBackdrop != null && scrollProgress == 1f }
    }

    Scaffold(
        topBar = {
            val barColor = if (blurActive) {
                Color.Transparent
            } else if (collapsed) {
                MiuixTheme.colorScheme.surface
            } else {
                Color.Transparent
            }
            val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            BlurredBar(sceneBackdrop, blurActive) {
                SmallTopAppBar(
                    title = "关于",
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = titleColor,
                    navigationIcon = { BackNavigationIcon(onClick = onBack) },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = if (sceneBackdrop != null) Modifier.layerBackdrop(sceneBackdrop) else Modifier) {
            AboutContent(
                preferences = preferences,
                padding = PaddingValues(top = innerPadding.calculateTopPadding()),
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                listState = listState,
                scrollProgressProvider = { scrollProgress },
                onShowLicenses = onShowLicenses,
            )
        }
    }
}

@Composable
private fun AboutContent(
    preferences: AppPreferences,
    padding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    listState: LazyListState,
    scrollProgressProvider: () -> Float,
    onShowLicenses: () -> Unit,
) {
    val isWideScreen = shouldShowSplitPane()
    val uriHandler = LocalUriHandler.current
    val cardBackdrop = rememberAppBlurBackdrop(preferences.enableBlur)
    val scrollPadding = pageContentPadding(
        padding,
        padding,
        isWideScreen,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )
    val logoPadding = pageContentPadding(
        padding,
        padding,
        isWideScreen,
        extraTop = 40.dp,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )
    val isDark = nextTracerouteIsInDarkTheme()
    val cardBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }
    }
    val density = LocalDensity.current
    var logoHeight by remember { mutableStateOf(300.dp) }

    BgEffectBackground(
        dynamicBackground = true,
        isOs3Effect = true,
        isFullSize = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = if (cardBackdrop != null) Modifier.layerBackdrop(cardBackdrop) else Modifier,
        alpha = { 1f - scrollProgressProvider() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = logoPadding.calculateRightPadding(LayoutDirection.Ltr),
                )
                .onSizeChanged { size -> with(density) { logoHeight = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val progress = ((scrollProgressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        clip = true
                        shape = RoundedCornerShape(24.dp)
                        alpha = 1f - progress
                        scaleX = 1f - progress * 0.05f
                        scaleY = 1f - progress * 0.05f
                    }
                    .background(Color.White),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "NextTraceroute 图标",
                    modifier = Modifier.size(74.dp),
                )
            }
            Text(
                text = "NextTraceroute",
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        val progress = ((scrollProgressProvider() - 0.20f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - progress
                        scaleX = 1f - progress * 0.05f
                        scaleY = 1f - progress * 0.05f
                    }
                    .then(
                        if (cardBackdrop != null) {
                            Modifier.textureBlur(
                                backdrop = cardBackdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurDefaults.blurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val progress = ((scrollProgressProvider() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - progress
                        scaleX = 1f - progress * 0.05f
                        scaleY = 1f - progress * 0.05f
                    },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().pageScrollModifiers(topAppBarScrollBehavior),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeight + 52.dp + logoPadding.calculateTopPadding() -
                                scrollPadding.calculateTopPadding() + 126.dp,
                        ),
                )
            }
            item(key = "about") {
                Box {
                    Spacer(Modifier.fillParentMaxHeight())
                    Column(modifier = Modifier.padding(bottom = scrollPadding.calculateBottomPadding())) {
                        AboutGlassCard(cardBackdrop, cardBlend) {
                            ArrowPreference(
                                title = "查看源代码",
                                endActions = { AboutValueText("GitHub") },
                                onClick = { uriHandler.openUri(PROJECT_URL) },
                            )
                            ArrowPreference(
                                title = "问题反馈",
                                endActions = { AboutValueText("GitHub Issues") },
                                onClick = { uriHandler.openUri(ISSUE_URL) },
                            )
                            ArrowPreference(
                                title = "交流频道",
                                endActions = { AboutValueText("Telegram") },
                                onClick = { uriHandler.openUri(GROUP_URL) },
                            )
                        }
                        AboutGlassCard(
                            backdrop = cardBackdrop,
                            blendColors = cardBlend,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            ArrowPreference(
                                title = "隐私政策",
                                endActions = { AboutValueText("GitHub") },
                                onClick = { uriHandler.openUri(PRIVACY_URL) },
                            )
                            ArrowPreference(
                                title = "开源许可证",
                                endActions = { AboutValueText("GPL-3.0-or-later") },
                                onClick = { uriHandler.openUri(LICENSE_URL) },
                            )
                            ArrowPreference(
                                title = "第三方许可证",
                                onClick = onShowLicenses,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = scrollPadding,
        )
    }
}

@Composable
private fun AboutGlassCard(
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop?,
    blendColors: List<BlendColorEntry>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .then(modifier)
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurDefaults.blurColors(blendColors = blendColors),
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.defaultColors(
            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            Color.Transparent,
        ),
        content = content,
    )
}

@Composable
private fun AboutValueText(value: String) {
    Text(
        text = value,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

@Composable
private fun BackNavigationIcon(
    onClick: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
        )
    }
}

private data class LicenseCatalog(
    val libraries: List<LibraryInfo> = emptyList(),
)

private data class LibraryInfo(
    val uniqueId: String = "",
    val artifactVersion: String? = null,
    val name: String? = null,
    val description: String? = null,
    val website: String? = null,
    val licenses: List<String> = emptyList(),
)

@Composable
fun LicensesPage(
    preferences: AppPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isWideScreen = shouldShowSplitPane()
    val backdrop = rememberAppBlurBackdrop(preferences.enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val libraries by produceState<List<LibraryInfo>?>(initialValue = null) {
        try {
            value = withContext(Dispatchers.IO) {
                context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { reader ->
                    Gson().fromJson(reader, LicenseCatalog::class.java).libraries
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            value = emptyList()
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    title = "第三方许可证",
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    navigationIcon = { BackNavigationIcon(onClick = onBack) },
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPadding(
            innerPadding = innerPadding,
            outerPadding = PaddingValues(),
            isWideScreen = isWideScreen,
            extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
            extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
        )
        val listState = rememberLazyListState()
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = contentPadding,
            ) {
                when (val items = libraries) {
                    null -> item(key = "loading") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        if (items.isEmpty()) {
                            item(key = "empty") {
                                Card(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                    Text(
                                        "未能读取依赖许可清单。",
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        } else {
                            items(items, key = { it.uniqueId }) { library ->
                                Card(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .padding(top = 12.dp),
                                ) {
                                    ArrowPreference(
                                        title = library.name?.ifBlank { library.uniqueId } ?: library.uniqueId,
                                        summary = listOfNotNull(
                                            library.artifactVersion,
                                            library.licenses.firstOrNull(),
                                        ).joinToString(" · "),
                                        onClick = {
                                            library.website?.let(uriHandler::openUri)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "licenseBottomSpace") { Spacer(Modifier.height(12.dp)) }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
