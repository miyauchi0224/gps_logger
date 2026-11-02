package com.example.gpslogger

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.gpslogger.service.LocationForegroundService
import com.example.gpslogger.ui.RecordingBanner
import com.example.gpslogger.ui.navigation.NavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // スプラッシュスクリーンを表示（AndroidのSplash Screen APIを使用）
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val status by LocationForegroundService.status.collectAsState()
                val recording = status != null

                // 記録中はステータスバーのアイコンを白にして、緑の帯の上でも見えるようにする
                LaunchedEffect(recording) {
                    enableEdgeToEdge(
                        statusBarStyle = if (recording) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // 記録中はどの画面でも最上部を緑にする
                    status?.let { RecordingBanner(it) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            // 帯がステータスバーの分を受け持つので、各画面の上部の余白を重ねない
                            .then(if (recording) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
                    ) {
                        NavGraph()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 設定画面で「他のアプリの上に重ねて表示」を許可して戻ってきた場合に、緑の帯を出し直す
        if (LocationForegroundService.status.value != null) {
            startService(
                Intent(this, LocationForegroundService::class.java)
                    .setAction(LocationForegroundService.ACTION_REFRESH_OVERLAY)
            )
        }
    }
}
