package com.example.gpslogger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gpslogger.service.RecordingOverlay
import com.example.gpslogger.service.RecordingStatus

/**
 * 記録中にすべての画面の最上部に出す緑の帯。ステータスバーの裏まで緑で塗る。
 * アプリの外では、RecordingOverlay（他のアプリの上の緑の帯）と通知で記録中を示す。
 */
@Composable
fun RecordingBanner(status: RecordingStatus) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(RecordingOverlay.COLOR))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "● 記録中・${status.method.label}・アプリを閉じても記録を続けます",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
