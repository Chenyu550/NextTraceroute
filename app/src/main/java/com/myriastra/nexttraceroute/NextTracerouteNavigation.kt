/*
 * NextTraceroute, an Android traceroute app using NextTrace API.
 * Copyright (C) 2024-2026 surfaceocean
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.myriastra.nexttraceroute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.room.Room
import com.myriastra.nexttraceroute.component.liquid.IosLiquidGlassNavigationBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private data object MainDestination : NavKey
private data object AboutDestination : NavKey
private data object LicensesDestination : NavKey

private class AppNavigator(
    val backStack: MutableList<NavKey>,
) {
    fun navigate(destination: NavKey) {
        backStack.add(destination)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

@Composable
fun NextTracerouteNavigation(
    preferences: AppPreferences,
    onPreferencesChange: ((AppPreferences) -> AppPreferences) -> Unit,
) {
    val context = LocalContext.current
    val database = remember(context.applicationContext) {
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app-database").build()
    }
    DisposableEffect(database) {
        onDispose { database.close() }
    }

    val backStack = remember { mutableStateListOf<NavKey>(MainDestination) }
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val entryProvider = entryProvider<NavKey> {
        entry<MainDestination> {
            MainDestinationContent(
                preferences = preferences,
                onPreferencesChange = onPreferencesChange,
                historyDao = database.historyDao(),
                database = database,
                onShowAbout = { navigator.navigate(AboutDestination) },
            )
        }
        entry<AboutDestination> {
            AboutPage(
                preferences = preferences,
                onBack = navigator::pop,
                onShowLicenses = { navigator.navigate(LicensesDestination) },
            )
        }
        entry<LicensesDestination> {
            LicensesPage(
                preferences = preferences,
                onBack = navigator::pop,
            )
        }
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider,
    )
    NavDisplay(
        entries = entries,
        onBack = navigator::pop,
    )
}

@Composable
private fun MainDestinationContent(
    preferences: AppPreferences,
    onPreferencesChange: ((AppPreferences) -> AppPreferences) -> Unit,
    historyDao: HistoryDao,
    database: AppDatabase,
    onShowAbout: () -> Unit,
) {
    val navigationItems = remember {
        listOf(
            NavigationItem("追踪", MiuixIcons.Search),
            NavigationItem("历史", MiuixIcons.Recent),
            NavigationItem("设置", MiuixIcons.Settings),
        )
    }
    val pagerState = rememberPagerState(pageCount = { navigationItems.size })
    val mainPagerState = rememberMainPagerState(pagerState)
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val backEnabled by remember {
        derivedStateOf { mainPagerState.selectedPage != 0 }
    }
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = backEnabled,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )

    val isWideScreen = shouldShowSplitPane()
    if (isWideScreen) {
        WideScreenContent(
            navigationItems = navigationItems,
            mainPagerState = mainPagerState,
            preferences = preferences,
            onPreferencesChange = onPreferencesChange,
            historyDao = historyDao,
            database = database,
            onShowAbout = onShowAbout,
        )
    } else {
        CompactScreenContent(
            navigationItems = navigationItems,
            mainPagerState = mainPagerState,
            preferences = preferences,
            onPreferencesChange = onPreferencesChange,
            historyDao = historyDao,
            database = database,
            onShowAbout = onShowAbout,
        )
    }
}

@Composable
private fun WideScreenContent(
    navigationItems: List<NavigationItem>,
    mainPagerState: MainPagerState,
    preferences: AppPreferences,
    onPreferencesChange: ((AppPreferences) -> AppPreferences) -> Unit,
    historyDao: HistoryDao,
    database: AppDatabase,
    onShowAbout: () -> Unit,
) {
    val expandRail = shouldExpandNavigationRail()
    val railState = rememberNavigationRailState(
        initialValue = if (expandRail) NavigationRailValue.Expanded else NavigationRailValue.Collapsed,
    )
    LaunchedEffect(expandRail) {
        if (expandRail) railState.expand() else railState.collapse()
    }
    val layoutDirection = LocalLayoutDirection.current
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(state = railState) {
            navigationItems.forEachIndexed { index, item ->
                NavigationRailItem(
                    selected = mainPagerState.selectedPage == index,
                    onClick = { mainPagerState.animateToPage(index) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.systemBars.union(
                WindowInsets.displayCutout.exclude(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Start),
                ),
            ),
        ) { padding ->
            MainPager(
                padding = PaddingValues(top = padding.calculateTopPadding()),
                pagerState = mainPagerState.pagerState,
                preferences = preferences,
                onPreferencesChange = onPreferencesChange,
                historyDao = historyDao,
                database = database,
                onShowAbout = onShowAbout,
                isWideScreen = true,
                modifier = Modifier
                    .imePadding()
                    .padding(end = padding.calculateEndPadding(layoutDirection)),
            )
        }
    }
}

@Composable
private fun CompactScreenContent(
    navigationItems: List<NavigationItem>,
    mainPagerState: MainPagerState,
    preferences: AppPreferences,
    onPreferencesChange: ((AppPreferences) -> AppPreferences) -> Unit,
    historyDao: HistoryDao,
    database: AppDatabase,
    onShowAbout: () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppNavigationBar(
                preferences = preferences,
                navigationItems = navigationItems,
                mainPagerState = mainPagerState,
                backdrop = backdrop,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            MainPager(
                padding = innerPadding,
                pagerState = mainPagerState.pagerState,
                preferences = preferences,
                onPreferencesChange = onPreferencesChange,
                historyDao = historyDao,
                database = database,
                onShowAbout = onShowAbout,
                isWideScreen = false,
                modifier = Modifier.imePadding(),
            )
        }
    }
}

@Composable
private fun AppNavigationBar(
    preferences: AppPreferences,
    navigationItems: List<NavigationItem>,
    mainPagerState: MainPagerState,
    backdrop: LayerBackdrop?,
) {
    val blurActive = preferences.enableBlur && backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val page = mainPagerState.selectedPage

    if (!preferences.useFloatingNavigationBar) {
        Box(
            modifier = Modifier
                .then(
                    if (blurActive) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = 25f,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                ),
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
                .background(barColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            NavigationBar(
                color = barColor,
                mode = NavigationBarDisplayMode.entries.getOrElse(preferences.navigationBarMode) {
                    NavigationBarDisplayMode.IconAndText
                },
            ) {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = page == index,
                        onClick = { mainPagerState.animateToPage(index) },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        }
    } else {
        val floatingBarColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
        val floatingBarShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
        val isDark = nextTracerouteIsInDarkTheme()
        val floatingHighlight = remember(isDark) {
            if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
        }
        if (preferences.floatingNavigationBarStyle == 1) {
            IosLiquidGlassNavigationBar(
                items = navigationItems,
                selectedIndex = page,
                onItemClick = { mainPagerState.animateToPage(it) },
                backdrop = backdrop,
                isBlurActive = blurActive,
                badge = { null },
            )
        } else {
            FloatingNavigationBar(
                modifier = if (blurActive) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = floatingBarShape,
                        blurRadius = 25f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)),
                            ),
                        ),
                        highlight = floatingHighlight,
                    )
                } else {
                    Modifier
                },
                color = floatingBarColor,
                horizontalAlignment = when (preferences.floatingNavigationBarPosition) {
                    1 -> Alignment.Start
                    2 -> Alignment.End
                    else -> Alignment.CenterHorizontally
                },
            ) {
                navigationItems.forEachIndexed { index, item ->
                    FloatingNavigationBarItem(
                        selected = page == index,
                        onClick = { mainPagerState.animateToPage(index) },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainPager(
    padding: PaddingValues,
    pagerState: PagerState,
    preferences: AppPreferences,
    onPreferencesChange: ((AppPreferences) -> AppPreferences) -> Unit,
    historyDao: HistoryDao,
    database: AppDatabase,
    onShowAbout: () -> Unit,
    isWideScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = true,
        verticalAlignment = Alignment.Top,
    ) { page ->
        when (page) {
            0 -> TracePage(
                preferences = preferences,
                historyDao = historyDao,
                database = database,
                padding = padding,
                isWideScreen = isWideScreen,
            )
            1 -> HistoryPage(
                historyDao = historyDao,
                database = database,
                padding = padding,
                isWideScreen = isWideScreen,
                blurEnabled = preferences.enableBlur,
            )
            else -> SettingsPage(
                preferences = preferences,
                onPreferencesChange = onPreferencesChange,
                onShowAbout = onShowAbout,
                padding = padding,
                isWideScreen = isWideScreen,
            )
        }
    }
}

@Stable
private class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    private var isNavigating by mutableStateOf(false)
    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
                    val duration = 100 * distance + 100
                    val layoutInfo = pagerState.layoutInfo
                    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                    val currentDistance = targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val scrollPixels = currentDistance * pageSize
                    var previousValue = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = scrollPixels,
                        animationSpec = tween(durationMillis = duration, easing = EaseInOut),
                    ) { currentValue, _ ->
                        previousValue += scrollBy(currentValue - previousValue)
                    }
                }
                if (pagerState.currentPage != targetIndex) pagerState.scrollToPage(targetIndex)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) selectedPage = pagerState.currentPage
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState = remember(pagerState, coroutineScope) {
    MainPagerState(pagerState, coroutineScope)
}
