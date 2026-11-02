package com.example.gpslogger.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gpslogger.model.RecordMethod
import com.example.gpslogger.model.RecordMode
import com.example.gpslogger.model.SourceType
import com.example.gpslogger.repository.AppSettings
import com.example.gpslogger.service.RecordingOverlay
import com.example.gpslogger.viewmodel.HomeViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSessions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val isRecording by vm.isRecording.collectAsState()
    val latestPoint by vm.latestPoint.collectAsState()
    val elapsedSeconds by vm.elapsedSeconds.collectAsState()
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()

    // GPS権限の確認。通知（Android 13以降）は記録中の表示用で、許可しなくても記録はできる
    val locationPermissions = rememberMultiplePermissionsState(
        listOfNotNull(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        )
    )
    val locationGranted = locationPermissions.permissions
        .filter { it.permission != Manifest.permission.POST_NOTIFICATIONS }
        .all { it.status.isGranted }
    val notificationGranted = locationPermissions.permissions
        .firstOrNull { it.permission == Manifest.permission.POST_NOTIFICATIONS }
        ?.status?.isGranted ?: true

    // 他のアプリの上に緑の帯を重ねる許可。設定画面から戻ったときに確認し直す
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(RecordingOverlay.canShow(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) overlayGranted = RecordingOverlay.canShow(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            // 記録中の緑の帯は MainActivity が全画面共通で出す
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

            // 通知がないとバックグラウンド記録中かどうかが分からないので知らせる
            if (isRecording && !notificationGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "通知が許可されていないため、アプリを閉じると記録中の表示が出ません。" +
                            "記録は続きます。端末の設定で GPSLogger の通知を許可してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 他のアプリ使用中にも緑の帯を出すには「他のアプリの上に重ねて表示」の許可が必要
            if (isRecording && settings?.showOverlay == true && !overlayGranted) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "他のアプリを使っているときも画面上部を緑にするには、" +
                                "「他のアプリの上に重ねて表示」を許可してください。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }) { Text("許可する") }
                    }
                }
            }

            // 記録モードと精度・消費電力の目安
            settings?.let { RecordModeCard(it, status?.method) }

            // 記録開始・停止ボタン
            if (!locationGranted) {
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

/**
 * 記録モードの説明カード。
 * 記録中は実際の記録手段（自動切替を含む）、停止中は設定中のモードを表示する。
 */
@Composable
private fun RecordModeCard(settings: AppSettings, method: RecordMethod?) {
    val mode = when (method) {
        null -> settings.recordMode
        RecordMethod.STANDARD -> RecordMode.STANDARD
        RecordMethod.LOW_POWER, RecordMethod.LOW_POWER_AUTO -> RecordMode.LOW_POWER
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (method != null) "記録手段: ${method.label}" else "記録モード: ${mode.label}",
                fontWeight = FontWeight.Bold
            )
            Text("精度の目安: ${mode.accuracy}")
            Text("消費電力の目安: ${mode.power}")
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.recordMode == RecordMode.STANDARD && settings.autoLowPower) {
                Text(
                    text = "バッテリー残量が${settings.lowBatteryPercent}%以下（充電中を除く）か" +
                        "省電力モードのときは、低消費電力に自動で切り替えます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
