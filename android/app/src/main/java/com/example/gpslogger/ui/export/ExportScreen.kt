package com.example.gpslogger.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gpslogger.viewmodel.ExportState
import com.example.gpslogger.viewmodel.ExportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    sessionId: String,
    sessionName: String,
    onBack: () -> Unit,
    vm: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val exportState by vm.exportState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("エクスポート") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sessionName,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "エクスポート形式を選んでください",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // CSV保存ボタン（端末のDownloadsフォルダへ）
            Button(
                onClick = { vm.exportToCsv(context, sessionId, sessionName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = exportState !is ExportState.Loading
            ) {
                Text("CSVとして端末に保存")
            }

            // Google Drive（将来実装）
            OutlinedButton(
                onClick = { /* TODO: Google Drive OAuth → アップロード */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            ) {
                Text("Google Drive にアップロード（近日対応）")
            }

            // OneDrive（将来実装）
            OutlinedButton(
                onClick = { /* TODO: OneDrive OAuth → アップロード */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            ) {
                Text("OneDrive にアップロード（近日対応）")
            }

            // 状態表示
            when (val state = exportState) {
                is ExportState.Loading -> CircularProgressIndicator()
                is ExportState.Success -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LaunchedEffect(state) {
                        kotlinx.coroutines.delay(3000)
                        vm.resetState()
                    }
                }
                is ExportState.Error -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
