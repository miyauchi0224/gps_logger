package com.example.gpslogger.ui.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

    // Android標準の保存先ピッカー。Google Drive のアプリが入っていれば保存先に選べる。
    // ピッカーで戻る（←）を押すと uri が null で返る
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) vm.exportToUri(context, sessionId, uri) else vm.pickerCancelled()
    }

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
            // ファイル名（保存先ピッカーでも変更できる）
            var fileName by rememberSaveable { mutableStateOf(ExportViewModel.csvFileName(sessionName)) }
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("ファイル名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val normalizedName = ExportViewModel.normalizeFileName(fileName)

            Spacer(modifier = Modifier.height(8.dp))

            // 保存先を選んで保存（端末内のフォルダ、Google Drive など）
            Button(
                onClick = { createDocument.launch(normalizedName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = exportState !is ExportState.Loading
            ) {
                Text("保存先を選んで保存")
            }
            Text(
                text = "保存先の画面で、左上のメニューから端末内のフォルダや Google Drive を選べます。" +
                    "ファイル名もそこで変更できます。保存をやめるときは、保存先の画面で戻る（←）を押してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 共有で保存（OneDrive は保存先ピッカーに出ないので、共有から渡す）
            OutlinedButton(
                onClick = {
                    vm.prepareShare(context, sessionId, normalizedName) { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TITLE, normalizedName)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "CSVを保存するアプリ"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = exportState !is ExportState.Loading
            ) {
                Text("共有して保存（OneDrive など）")
            }
            Text(
                text = "共有先で OneDrive を選ぶと、OneDrive のアップロード画面で保存先フォルダを選べます。" +
                    "やめるときは共有先を選ばずに閉じてください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // CSV保存ボタン（端末のDownloadsフォルダへ、選択なしで保存）
            OutlinedButton(
                onClick = { vm.exportToCsv(context, sessionId, normalizedName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = exportState !is ExportState.Loading
            ) {
                Text("ダウンロードフォルダに保存")
            }

            // 状態表示
            when (val state = exportState) {
                is ExportState.Loading -> {
                    CircularProgressIndicator()
                    TextButton(onClick = { vm.cancel() }) { Text("キャンセル") }
                }
                is ExportState.Info -> {
                    Card {
                        Text(text = state.message, modifier = Modifier.padding(16.dp))
                    }
                    LaunchedEffect(state) {
                        kotlinx.coroutines.delay(3000)
                        vm.resetState()
                    }
                }
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
