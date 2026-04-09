package com.example.gpslogger.ui.home

import android.Manifest
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gpslogger.model.SourceType
import com.example.gpslogger.viewmodel.HomeViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToSessions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val isRecording by vm.isRecording.collectAsState()
    val latestPoint by vm.latestPoint.collectAsState()
    val elapsedSeconds by vm.elapsedSeconds.collectAsState()

    // GPS権限の確認
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // ロギング中はステータスバー領域を緑に
    val statusBarColor by animateColorAsState(
        if (isRecording) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surface,
        label = "statusBarColor"
    )

    Scaffold(
        topBar = {
            Column {
                // ロギング中インジケーター（緑バー）
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color(0xFF2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GPSロギング中",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TopAppBar(
                    title = { Text("GPSLogger") },
                    actions = {
                        IconButton(onClick = onNavigateToSessions) {
                            Icon(Icons.Default.List, contentDescription = "セッション一覧")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "設定")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 経過時間表示
            if (isRecording) {
                Text(
                    text = vm.formatElapsed(elapsedSeconds),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            } else {
                Text(
                    text = "記録を開始してください",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 現在の測位情報カード
            latestPoint?.let { point ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("最新ポイント", fontWeight = FontWeight.Bold)
                        Text("緯度: %.6f".format(point.latitude))
                        Text("経度: %.6f".format(point.longitude))
                        Text("精度: ${point.accuracy}m")
                        point.speed?.let { Text("速度: ${"%.1f".format(it * 3.6)} km/h") }
                        Text(
                            text = if (point.sourceType == SourceType.GPS) "GPS測位" else "WiFi測位",
                            color = if (point.sourceType == SourceType.GPS) Color(0xFF1565C0) else Color(0xFFE65100)
                        )
                    }
                }
            }

            // 記録開始・停止ボタン
            if (!locationPermissions.allPermissionsGranted) {
                Button(onClick = { locationPermissions.launchMultiplePermissionRequest() }) {
                    Text("GPS権限を許可する")
                }
            } else {
                Button(
                    onClick = { if (isRecording) vm.stopRecording() else vm.startRecording() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFC62828) else Color(0xFF2E7D32)
                    ),
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (isRecording) "停止" else "記録開始",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
