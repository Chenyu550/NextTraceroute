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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Entity(tableName = "history")
data class HistoryData(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "timestamp") val timeStamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "ip") val ip: String,
    @ColumnInfo(name = "domain") val domain: String,
    @ColumnInfo(name = "history ") val history: String,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryData>>

    @Query("SELECT DISTINCT ip FROM history WHERE ip LIKE '%' || :inputIP || '%'")
    suspend fun findInputIP(inputIP: String): List<String>

    @Query("SELECT DISTINCT domain FROM history WHERE domain LIKE '%' || :inputDomain || '%'")
    suspend fun findInputDomain(inputDomain: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(historyData: HistoryData): Long

    @Query("DELETE FROM history WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String): Int

    @Query("DELETE FROM history")
    suspend fun deleteAll(): Int
}

@Database(entities = [HistoryData::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

@Composable
fun HistoryPage(
    historyDao: HistoryDao,
    database: AppDatabase,
    padding: PaddingValues,
    isWideScreen: Boolean,
    blurEnabled: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val records by historyDao.observeAll().collectAsState(initial = emptyList())
    var selectedRecord by remember { mutableStateOf<HistoryData?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val listState = rememberLazyListState()
    val backdrop = rememberAppBlurBackdrop(blurEnabled)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    title = "历史",
                    subtitle = "已保存 ${records.size} 条追踪记录",
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    actions = {
                        IconButton(
                            onClick = { showClearConfirmation = true },
                            enabled = records.isNotEmpty(),
                        ) {
                            Icon(MiuixIcons.Delete, contentDescription = "清空历史")
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
            if (records.isEmpty()) {
                item(key = "emptyHistory") {
                    Card(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("暂无历史记录")
                            Text(
                                "完成一次路由追踪后，结果会自动保存在这里。",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                item(key = "historyTitle") { SmallTitle("追踪记录") }
                items(records, key = { it.uuid }) { record ->
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = record.domain.ifBlank { record.ip },
                            summary = buildString {
                                if (record.domain.isNotBlank()) append(record.ip).append(" · ")
                                append(dateFormatter.format(Date(record.timeStamp)))
                            },
                            onClick = { selectedRecord = record },
                        )
                    }
                }
            }
            item(key = "historyBottomSpace") { Spacer(Modifier.height(12.dp)) }
        }
    }

    val record = selectedRecord
    OverlayDialog(
        show = record != null,
        title = "追踪记录",
        summary = record?.let { "${it.ip} · ${dateFormatter.format(Date(it.timeStamp))}" },
        onDismissRequest = { selectedRecord = null },
        onDismissFinished = {},
    ) {
        if (record != null) {
            Column {
                Text(
                    text = record.history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = "复制",
                        onClick = {
                            clipboard.setPrimaryClip(ClipData.newPlainText("追踪结果", record.history))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            selectedRecord = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    TextButton(
                        text = "分享",
                        onClick = {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, record.history)
                                    },
                                    "分享追踪结果",
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "删除",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    database.withTransaction { historyDao.deleteByUuid(record.uuid) }
                                }
                                selectedRecord = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    OverlayDialog(
        show = showClearConfirmation,
        title = "清空全部历史记录？",
        summary = "此操作无法撤销。",
        onDismissRequest = { showClearConfirmation = false },
        onDismissFinished = {},
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showClearConfirmation = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "清空",
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            database.withTransaction { historyDao.deleteAll() }
                        }
                        showClearConfirmation = false
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
