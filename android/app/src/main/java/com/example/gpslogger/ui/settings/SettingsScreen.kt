package com.example.gpslogger.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gpslogger.BuildConfig
import com.example.gpslogger.model.MapLayer
import com.example.gpslogger.repository.AppSettings
import com.example.gpslogger.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val loaded by vm.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        // 保存済みの設定を読み込んでからフォームを出す
        loaded?.let { initial ->
            SettingsForm(
                initial = initial,
                onSave = { vm.save(it, onSaved = onBack) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun SettingsForm(
    initial: AppSettings,
    onSave: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var intervalSec by remember { mutableStateOf(initial.intervalSec.toString()) }
    var defaultLayer by remember { mutableStateOf(initial.defaultLayer) }
    var googleTraffic by remember { mutableStateOf(initial.googleTraffic) }
    var googleBuildings by remember { mutableStateOf(initial.googleBuildings) }
    var googleZoomControls by remember { mutableStateOf(initial.googleZoomControls) }

    val interval = intervalSec.toLongOrNull()
    val intervalValid = interval != null && interval in AppSettings.INTERVAL_RANGE

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 記録間隔
        Text("記録間隔（秒）", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = intervalSec,
            onValueChange = { intervalSec = it.filter { c -> c.isDigit() } },
            label = { Text("秒数（デフォルト: 1）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            isError = !intervalValid,
            supportingText = { Text("1〜3600の間で設定してください") }
        )

        HorizontalDivider()

        // デフォルト地図
        Text("デフォルト地図", style = MaterialTheme.typography.titleMedium)
        MapLayerDropdown(selected = defaultLayer, onSelect = { defaultLayer = it })

        HorizontalDivider()

        // Google Maps
        Text("Google マップ", style = MaterialTheme.typography.titleMedium)
        GoogleApiKeyStatus()
        SwitchRow("交通状況を表示", googleTraffic) { googleTraffic = it }
        SwitchRow("3D の建物を表示", googleBuildings) { googleBuildings = it }
        SwitchRow("ズームボタンを表示", googleZoomControls) { googleZoomControls = it }

        HorizontalDivider()

        // 保存ボタン
        Button(
            onClick = {
                onSave(
                    AppSettings(
                        intervalSec = interval ?: initial.intervalSec,
                        defaultLayer = defaultLayer,
                        googleTraffic = googleTraffic,
                        googleBuildings = googleBuildings,
                        googleZoomControls = googleZoomControls
                    )
                )
            },
            enabled = intervalValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存して戻る")
        }
    }
}

/** 地図を「提供元 / 種類」の一覧から選ぶドロップダウン */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapLayerDropdown(selected: MapLayer, onSelect: (MapLayer) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName(),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapLayer.entries.forEach { layer ->
                DropdownMenuItem(
                    text = { Text(layer.displayName()) },
                    onClick = {
                        onSelect(layer)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun MapLayer.displayName() = "${provider.label} / $label"

/** APIキーはビルド時に local.properties から埋め込むため、ここでは状態だけ表示する */
@Composable
private fun GoogleApiKeyStatus() {
    val keySet = BuildConfig.MAPS_API_KEY_SET
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (keySet) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (keySet) "APIキー: 設定済み" else "APIキー: 未設定",
                style = MaterialTheme.typography.titleSmall
            )
            if (!keySet) {
                Text(
                    "android/local.properties に MAPS_API_KEY=取得したキー を追加して、" +
                        "ビルドし直してください。未設定の間 Google の地図は表示されません。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
