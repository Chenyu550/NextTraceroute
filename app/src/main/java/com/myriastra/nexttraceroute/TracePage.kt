/*
 * NextTraceroute, an Android traceroute app using NextTrace API.
 * Copyright (C) 2024-2026 surfaceocean
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.myriastra.nexttraceroute

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TracePage(
    preferences: AppPreferences,
    historyDao: HistoryDao,
    database: AppDatabase,
    padding: PaddingValues,
    isWideScreen: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val handler = remember { TracerouteHandler() }

    val currentLanguage = remember(preferences.currentLanguage) { mutableStateOf(preferences.currentLanguage) }
    val isTraceMapEnabled = remember(preferences.isTraceMapEnabled) { mutableStateOf(preferences.isTraceMapEnabled) }
    val maxTraceTTL = remember(preferences.maxTraceTTL) { mutableIntStateOf(preferences.maxTraceTTL) }
    val traceTimeout = remember(preferences.traceTimeout) { mutableStateOf(preferences.traceTimeout) }
    val traceCount = remember(preferences.traceCount) { mutableStateOf(preferences.traceCount) }
    val currentDNSMode = remember(preferences.currentDNSMode) { mutableStateOf(preferences.currentDNSMode) }
    val currentDOHServer = remember(preferences.currentDOHServer) { mutableStateOf(preferences.currentDOHServer) }
    val tracerouteDNSServer = remember(preferences.tracerouteDNSServer) { mutableStateOf(preferences.tracerouteDNSServer) }
    val apiHostNamePOW = remember(preferences.apiHostNamePOW) { mutableStateOf(preferences.apiHostNamePOW) }
    val apiDNSNamePOW = remember(preferences.apiDNSNamePOW) { mutableStateOf(preferences.apiDNSNamePOW) }
    val apiHostName = remember(preferences.apiHostName) { mutableStateOf(preferences.apiHostName) }
    val apiDNSName = remember(preferences.apiDNSName) { mutableStateOf(preferences.apiDNSName) }

    val threadMutex = remember { Mutex() }
    val tracerouteThreads = remember { mutableStateListOf<Int>() }
    val traceMapThreads = remember { mutableListOf<List<MutableMap<String, Any?>>>() }
    val traceMapUrl = remember { mutableStateOf("") }
    val multipleIps = remember { mutableStateListOf<MutableState<String>>() }
    val isDnsInProgress = remember { mutableStateOf(false) }
    val isSearchEnabled = remember { mutableStateOf(true) }
    val input = remember { mutableStateOf("") }
    val currentDomain = remember { mutableStateOf("") }
    val insertError = remember { mutableStateOf("") }
    val nativePingError = remember { mutableStateOf("") }
    val nativeIpv4Available = remember { mutableStateOf(true) }
    val nativeIpv6Available = remember { mutableStateOf(true) }
    val apiStatus = remember { mutableStateOf("") }
    val preferredApiIp = remember { mutableStateOf("") }
    val preferredApiIpPow = remember { mutableStateOf("") }
    val apiToken = remember { mutableStateOf("") }
    val apiDnsList = remember { mutableListOf<String>() }
    val apiDnsListPow = remember { mutableListOf<String>() }
    val isApiFinished = remember { mutableStateOf(false) }
    val singleHopCursor = remember(maxTraceTTL.intValue) {
        MutableList(maxTraceTTL.intValue) { mutableStateOf("") }
    }
    val gridData = remember(maxTraceTTL.intValue) { createGridData(maxTraceTTL.intValue) }
    val suggestions = remember { mutableStateListOf<String>() }
    var activeTraceId by remember { mutableIntStateOf(0) }

    fun startTrace(target: String = input.value) {
        val normalized = target.trim()
        if (normalized.isBlank()) {
            insertError.value = "请输入要追踪的域名、IPv4 或 IPv6 地址。"
            return
        }
        if (handler.identifyInput(normalized) == ERROR_IDENTIFIER) {
            insertError.value = "输入格式无效，请输入域名、IPv4 或 IPv6 地址。"
            return
        }
        input.value = normalized
        isSearchEnabled.value = false
        keyboardController?.hide()
        tracerouteThreads.clear()
        clearTraceData(
            multipleIps = multipleIps,
            insertError = insertError,
            nativePingError = nativePingError,
            singleHopCursor = singleHopCursor,
            gridData = gridData,
            apiStatus = apiStatus,
            preferredApiIp = preferredApiIp,
            apiDnsList = apiDnsList,
            preferredApiIpPow = preferredApiIpPow,
            apiDnsListPow = apiDnsListPow,
            apiToken = apiToken,
            traceMapThreads = traceMapThreads,
            traceMapUrl = traceMapUrl,
            isApiFinished = isApiFinished,
        )
        activeTraceId = if (activeTraceId == Int.MAX_VALUE) 1 else activeTraceId + 1
        scope.launch { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(input.value, isSearchEnabled.value) {
        if (!isSearchEnabled.value || input.value.isBlank()) {
            suggestions.clear()
            return@LaunchedEffect
        }
        val matches = withContext(Dispatchers.IO) {
            database.withTransaction {
                (historyDao.findInputIP(input.value) + historyDao.findInputDomain(input.value)).distinct()
            }
        }
        suggestions.clear()
        suggestions.addAll(matches.filterNot { it == input.value })
    }

    if (activeTraceId > 0) {
        key(activeTraceId) {
            handler.InsertHandler(
                threadMutex = threadMutex,
                tracerouteThreadsIntList = tracerouteThreads,
                insertion = input,
                insertErrorText = insertError,
                gridDataList = gridData,
                scope = scope,
                tracerouteDNSServer = tracerouteDNSServer,
                count = traceCount,
                maxTTL = maxTraceTTL,
                timeout = traceTimeout,
                multipleIps = multipleIps,
                context = context,
                isDNSInProgress = isDnsInProgress,
                testAPIText = apiStatus,
                currentDOHServer = currentDOHServer,
                currentDNSMode = currentDNSMode,
                isTraceMapEnabled = isTraceMapEnabled,
                traceMapURL = traceMapUrl,
                apiHostName = apiHostName,
                preferredAPIIp = preferredApiIp,
                traceMapThreadsMapList = traceMapThreads,
                isSearchBarEnabled = isSearchEnabled,
                isAPIFinished = isApiFinished,
                apiToken = apiToken,
                currentLanguage = currentLanguage,
                apiHostNamePOW = apiHostNamePOW,
                apiDNSNamePOW = apiDNSNamePOW,
                preferredAPIIpPOW = preferredApiIpPow,
                apiDNSListPOW = apiDnsListPow,
                apiDNSList = apiDnsList,
                apiDNSName = apiDNSName,
            )
        }
    }

    handler.EachHopHandler(
        threadMutex = threadMutex,
        tracerouteThreadsIntList = tracerouteThreads,
        singleHopCursor = singleHopCursor,
        gridDataList = gridData,
        scope = scope,
        tracerouteDNSServer = tracerouteDNSServer,
        count = traceCount,
        timeout = traceTimeout,
        currentDOHServer = currentDOHServer,
        currentDNSMode = currentDNSMode,
    )

    LaunchedEffect(activeTraceId) {
        val runId = activeTraceId
        if (runId == 0) return@LaunchedEffect
        nativeIpv4Available.value = true
        nativeIpv6Available.value = true
        withContext(Dispatchers.IO) {
            handler.testNativePing(
                v4Status = nativeIpv4Available,
                v6Status = nativeIpv6Available,
                errorText = nativePingError,
            )
        }
    }

    LaunchedEffect(activeTraceId) {
        val runId = activeTraceId
        if (runId == 0) return@LaunchedEffect
        val startTime = SystemClock.elapsedRealtime()
        var sawWork = false
        while (activeTraceId == runId) {
            val busy = threadMutex.withLock { tracerouteThreads.any { it != 0 } } || isDnsInProgress.value
            if (busy) sawWork = true
            if (!busy && (sawWork || SystemClock.elapsedRealtime() - startTime >= 1_500L)) {
                delay(650)
                val stillBusy = threadMutex.withLock { tracerouteThreads.any { it != 0 } } || isDnsInProgress.value
                if (!stillBusy) break
            }
            delay(150)
        }
        if (activeTraceId != runId) return@LaunchedEffect
        threadMutex.withLock { tracerouteThreads.removeAll { it == 0 } }
        activeTraceId = 0

        if (multipleIps.isEmpty()) {
            isSearchEnabled.value = true
            val result = buildTraceResult(input.value, currentDomain.value, gridData)
            val hasHop = gridData.any { it[0][0].value.isNotBlank() }
            if (hasHop) {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                        historyDao.insertHistory(
                            HistoryData(
                                ip = input.value,
                                domain = currentDomain.value,
                                history = result,
                            ),
                        )
                    }
                }
            } else if (insertError.value.isBlank()) {
                insertError.value = "没有收到追踪结果，请检查网络和追踪设置后重试。"
            }
            currentDomain.value = ""
        }
    }

    LaunchedEffect(nativePingError.value) {
        if (nativePingError.value.isNotBlank()) {
            Toast.makeText(context, nativePingError.value, Toast.LENGTH_LONG).show()
        }
    }

    val backdrop = rememberAppBlurBackdrop(preferences.enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    title = "追踪",
                    subtitle = "NextTrace 网络路径分析",
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    actions = {
                        IconButton(
                            onClick = { startTrace() },
                            enabled = isSearchEnabled.value && input.value.isNotBlank(),
                        ) {
                            Icon(MiuixIcons.Refresh, contentDescription = "重新追踪")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPadding(innerPadding, padding, isWideScreen)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .pageScrollModifiers(topAppBarScrollBehavior),
            contentPadding = contentPadding,
        ) {
            item(key = "traceInput") {
                Card(modifier = Modifier.padding(12.dp)) {
                    TextField(
                        value = input.value,
                        onValueChange = { input.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        label = "域名、IPv4 或 IPv6 地址",
                        useLabelAsPlaceholder = true,
                        enabled = isSearchEnabled.value,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { startTrace() }),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { startTrace() },
                            modifier = Modifier.weight(1f),
                            enabled = isSearchEnabled.value,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Icon(MiuixIcons.Search, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("开始追踪")
                        }
                        if (traceMapUrl.value.isNotBlank() && Patterns.WEB_URL.matcher(traceMapUrl.value).matches()) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, traceMapUrl.value.toUri()))
                                },
                            ) {
                                Icon(MiuixIcons.Link, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("地图")
                            }
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                item(key = "suggestionTitle") { SmallTitle("历史输入") }
                item(key = "suggestions") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        suggestions.forEach { suggestion ->
                            ArrowPreference(
                                title = suggestion,
                                onClick = { input.value = suggestion },
                            )
                        }
                    }
                }
            }

            if (activeTraceId > 0) {
                item(key = "traceProgress") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Column {
                                Text("正在追踪网络路径", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "正在解析节点、反向 DNS 与延迟信息",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            if (insertError.value.isNotBlank() || apiStatus.value.isNotBlank()) {
                item(key = "traceStatus") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            if (insertError.value.isNotBlank()) {
                                Text(insertError.value, color = MiuixTheme.colorScheme.error)
                            }
                            if (apiStatus.value.isNotBlank()) Text(apiStatus.value)
                        }
                    }
                }
            }

            if (multipleIps.isNotEmpty() && activeTraceId == 0) {
                item(key = "addressTitle") { SmallTitle("选择目标地址") }
                item(key = "addresses") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        multipleIps.forEach { address ->
                            ArrowPreference(
                                title = address.value,
                                onClick = {
                                    currentDomain.value = input.value
                                    val selectedAddress = address.value
                                    multipleIps.clear()
                                    startTrace(selectedAddress)
                                },
                            )
                        }
                    }
                }
            }

            if (gridData.any { it[0][0].value.isNotBlank() }) {
                item(key = "resultTitle") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallTitle("追踪结果")
                        IconButton(
                            onClick = {
                                val result = buildTraceResult(input.value, currentDomain.value, gridData)
                                clipboard.setPrimaryClip(ClipData.newPlainText("追踪结果", result))
                                Toast.makeText(context, "已复制追踪结果", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(MiuixIcons.Copy, contentDescription = "复制追踪结果")
                        }
                    }
                }
                items(
                    items = gridData.filter { it[0][0].value.isNotBlank() },
                    key = { it[0][0].value },
                ) { hop ->
                    HopCard(
                        hop = hop,
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        onCopy = { value ->
                            clipboard.setPrimaryClip(ClipData.newPlainText("节点信息", value))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        onOpenLookup = { value ->
                            if (value.isNotBlank() && value != "*") {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "https://bgp.tools/search?q=$value".toUri()))
                            }
                        },
                    )
                }
            }

            item(key = "traceBottomSpace") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HopCard(
    hop: MutableList<MutableList<MutableState<String>>>,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit,
    onOpenLookup: (String) -> Unit,
) {
    val hopNumber = hop[0].getOrNull(0)?.value.orEmpty()
    val address = hop[0].getOrNull(1)?.value.orEmpty()
    val asn = hop[0].getOrNull(2)?.value.orEmpty()
    val owner = hop[0].getOrNull(3)?.value.orEmpty()
    val location = hop[1].firstOrNull()?.value.orEmpty()
    val reverseDns = hop[2].firstOrNull()?.value.orEmpty()
    val latency = hop[2].getOrNull(1)?.value.orEmpty()
    val metadata = listOf(asn, owner)
        .filter { it.isNotBlank() && it != "*" }
        .distinct()
    Card(
        modifier = modifier.fillMaxWidth(),
        onLongPress = { onCopy(hop.joinToString("\n") { row -> row.joinToString("  ") { it.value } }) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = listOf("第 $hopNumber 跳", address).filter { it.isNotBlank() }.joinToString("  "),
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
            if (metadata.isNotEmpty()) {
                Text(metadata.joinToString("  "))
            }
            if (location.isNotBlank() && location != "*" && location !in metadata) Text(location)
            if (reverseDns.isNotBlank() && reverseDns != "*") {
                TextButton(text = reverseDns, onClick = { onOpenLookup(address) })
            }
            if (latency.isNotBlank() && latency != "*") {
                Text("延迟：$latency", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

private fun createGridData(maxTtl: Int) = mutableStateListOf<MutableList<MutableList<MutableState<String>>>>().apply {
    repeat(maxTtl) {
        add(
            mutableListOf(
                mutableListOf(mutableStateOf(""), mutableStateOf(""), mutableStateOf(""), mutableStateOf("")),
                mutableListOf(mutableStateOf("")),
                mutableListOf(mutableStateOf(""), mutableStateOf("")),
            ),
        )
    }
}

private fun clearTraceData(
    multipleIps: MutableList<MutableState<String>>,
    insertError: MutableState<String>,
    nativePingError: MutableState<String>,
    singleHopCursor: MutableList<MutableState<String>>,
    gridData: MutableList<MutableList<MutableList<MutableState<String>>>>,
    apiStatus: MutableState<String>,
    preferredApiIp: MutableState<String>,
    apiDnsList: MutableList<String>,
    preferredApiIpPow: MutableState<String>,
    apiDnsListPow: MutableList<String>,
    apiToken: MutableState<String>,
    traceMapThreads: MutableList<List<MutableMap<String, Any?>>>,
    traceMapUrl: MutableState<String>,
    isApiFinished: MutableState<Boolean>,
) {
    isApiFinished.value = false
    traceMapUrl.value = ""
    apiStatus.value = ""
    preferredApiIp.value = ""
    preferredApiIpPow.value = ""
    apiToken.value = ""
    apiDnsList.clear()
    apiDnsListPow.clear()
    multipleIps.clear()
    nativePingError.value = ""
    insertError.value = ""
    traceMapThreads.clear()
    singleHopCursor.forEach { it.value = "" }
    gridData.forEach { layer -> layer.forEach { row -> row.forEach { it.value = "" } } }
}

private fun buildTraceResult(
    input: String,
    domain: String,
    gridData: List<MutableList<MutableList<MutableState<String>>>>,
): String = buildString {
    appendLine("路由追踪结果")
    appendLine("目标：$input")
    if (domain.isNotBlank()) appendLine("域名：$domain")
    gridData.filter { it[0][0].value.isNotBlank() }.forEach { layer ->
        layer.forEach { row -> appendLine(row.joinToString("  ") { it.value }) }
    }
}.trim()
