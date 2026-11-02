package com.example.gpslogger.ui.session

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gpslogger.model.Session
import com.example.gpslogger.viewmodel.SessionListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onNavigateToExport: (String, String) -> Unit,
    onNavigateToMap: (String, String) -> Unit,
    onNavigateToCsv: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: SessionListViewModel = hiltViewModel()
) {
    val sessions by vm.sessions.collectAsState(initial = emptyList())
    var deleteTarget by remember { mutableStateOf<Session?>(null) }
    val importing by vm.importing.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // CSVファイルを選んで、新しいセッションとして読み込む。
    // CSVの種類は保存元のアプリによって text/csv 以外で届くことがあるので広めに受け付ける
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(context, uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("セッション一覧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            openCsv.launch(
                                arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
                            )
                        },
                        enabled = !importing
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "CSVを読み込む")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (importing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(padding))
        }
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "記録がありません\n右上のボタンからCSVを読み込めます",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onMap = { onNavigateToMap(session.id, session.name) },
                        onCsv = { onNavigateToCsv(session.id, session.name) },
                        onExport = { onNavigateToExport(session.id, session.name) },
                        onDelete = { deleteTarget = session }
                    )
                }
            }
        }
    }

    // 削除確認ダイアログ
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("セッションを削除") },
            text = { Text("「${target.name}」を削除しますか？この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(target.id)
                    deleteTarget = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun SessionCard(
    session: Session,
    onMap: () -> Unit,
    onCsv: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = session.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = fmt.format(Date(session.startTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${session.pointCount}ポイント · ${session.durationFormatted()}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // 地図ボタン
            IconButton(onClick = onMap) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "地図を表示",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // CSVの表で中身を見る
            IconButton(onClick = onCsv) {
                Icon(Icons.Default.TableChart, contentDescription = "CSVを表示")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Share, contentDescription = "エクスポート")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
