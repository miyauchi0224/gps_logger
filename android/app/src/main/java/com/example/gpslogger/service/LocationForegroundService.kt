package com.example.gpslogger.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.gpslogger.MainActivity
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.SourceType
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * バックグラウンドでGPS/WiFi位置情報を継続取得するForeground Service。
 *
 * Foreground Service = ユーザーに通知バーで存在を示しながら
 * バックグラウンドで動き続けられるサービス。
 * GPSのような「常時動作が必要な処理」に使う。
 */
@AndroidEntryPoint
class LocationForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_INTERVAL_SEC = "EXTRA_INTERVAL_SEC"
        const val CHANNEL_ID = "gps_logging_channel"

        /** Serviceの外からログを受け取るためのSharedFlow（バス）*/
        val logPointFlow = MutableSharedFlow<LogPoint>(extraBufferCapacity = 100)
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentSessionId: String = ""

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val point = LogPoint(
                sessionId = currentSessionId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = location.accuracy,
                speed = if (location.hasSpeed()) location.speed else null,
                sourceType = if (location.accuracy < 20f) SourceType.GPS else SourceType.WIFI
            )
            serviceScope.launch {
                logPointFlow.emit(point)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentSessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                val intervalSec = intent.getLongExtra(EXTRA_INTERVAL_SEC, 1L)
                startForegroundNotification()
                startLocationUpdates(intervalSec)
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopSelf()
            }
        }
        return START_STICKY  // システムに強制終了されても自動再起動する
    }

    private fun startLocationUpdates(intervalSec: Long) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalSec * 1000  // ミリ秒に変換
        ).build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun startForegroundNotification() {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPSLogger")
            .setContentText("GPSロギング中...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)  // ユーザーがスワイプで消せないようにする
            .setContentIntent(openAppIntent)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPSロギング",
            NotificationManager.IMPORTANCE_LOW  // 音なし・バッジなし
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
