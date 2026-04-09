package com.example.gpslogger.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var intervalSec by remember { mutableStateOf("1") }
    var defaultMap by remember { mutableStateOf("OSM") }
    val mapOptions = listOf("OSM", "Google", "地理院")

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
        Column(
            modifier = Modifier
                .padding(padding)
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
                supportingText = { Text("1〜3600の間で設定してください") }
            )

            HorizontalDivider()

            // デフォルト地図
            Text("デフォルト地図", style = MaterialTheme.typography.titleMedium)
            mapOptions.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(option, modifier = Modifier.padding(vertical = 8.dp))
                    RadioButton(
                        selected = defaultMap == option,
                        onClick = { defaultMap = option }
                    )
                }
            }

            HorizontalDivider()

            // 保存ボタン
            Button(
                onClick = { /* TODO: DataStore に保存 */ onBack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存して戻る")
            }
        }
    }
}
