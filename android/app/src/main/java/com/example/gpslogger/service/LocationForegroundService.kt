package com.example.gpslogger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import com.example.gpslogger.MainActivity
import com.example.gpslogger.R
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.RecordMethod
import com.example.gpslogger.model.RecordMode
import com.example.gpslogger.model.SourceType
import com.example.gpslogger.repository.AppSettings
import com.example.gpslogger.repository.LocationRepository
import com.example.gpslogger.repository.SettingsRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 記録中の状態。記録していないときは null */
data class RecordingStatus(val sessionId: String, val method: RecordMethod)

/**
 * バックグラウンドでGPS/WiFi位置情報を継続取得するForeground Service。
 *
 * Foreground Service = ユーザーに通知バーで存在を示しながら
 * バックグラウンドで動き続けられるサービス。
 * 取得した位置はService自身がDBに保存するので、画面を閉じても記録は続く。
 *
 * 標準モードで記録中も、バッテリー残量が設定値以下（充電中を除く）か
 * 省電力モードになったら低消費電力（WiFi・基地局）に自動で切り替える。
 *
 * 標準モードでは、Fused Location に加えて端末のGPSを直接使う。Fused Location は端末が
 * 止まっていると、GPSが毎秒測位していても数秒おきにしか位置を渡さないため。
 * GPSが測位できているあいだはGPSの位置だけを記録し、屋内などでGPSが途切れたら Fused Location の位置で補う。
 * さらにGPSチップ自体も止まっていると間欠動作（数秒おきの測位）になるので、Android 12以降は
 * 連続測位（full tracking）を要求して記録間隔どおりに測位させる。
 */
@AndroidEntryPoint
class LocationForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        /** 重ね表示の許可が変わったかもしれないので、緑の帯を出し直す（記録中のみ有効） */
        const val ACTION_REFRESH_OVERLAY = "ACTION_REFRESH_OVERLAY"
        /** 省略すると未完了のセッションの記録を再開する */
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val CHANNEL_ID = "gps_logging_channel"
        private const val NOTIFICATION_ID = 1
        /** 通知のポイント数を更新する最短間隔（通知の更新回数を抑える） */
        private const val NOTIFICATION_UPDATE_MS = 5_000L
        /** 自動切替から標準に戻すときの余裕（残量が閾値付近で行き来しないように） */
        private const val BATTERY_HYSTERESIS = 5
        /** GPSの位置がこの時間（記録間隔に足す）届かなければ、GPSが途切れたとみなして Fused Location の位置を使う */
        private const val GPS_TIMEOUT_MS = 2_000L

        private val _status = MutableStateFlow<RecordingStatus?>(null)
        val status: StateFlow<RecordingStatus?> = _status.asStateFlow()

        private val _latestPoint = MutableStateFlow<LogPoint?>(null)
        val latestPoint: StateFlow<LogPoint?> = _latestPoint.asStateFlow()
    }

    @Inject lateinit var repo: LocationRepository
    @Inject lateinit var settingsRepo: SettingsRepository

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var overlay: RecordingOverlay

    // DB書き込みを1本の流れにして、ポイント数の更新や停止処理の順序を保つ
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // 以下はメインスレッドからだけ触る
    private var sessionId: String? = null
    private var settings = AppSettings()
    private var method: RecordMethod? = null
    private var batteryPercent = 100
    private var charging = false
    private var receiverRegistered = false
    private var sessionStartTime = 0L
    private var pointCount = 0
    private var lastNotifiedAt = 0L
    /** GPSから最後に位置が届いた時刻（elapsedRealtime）。0 は未受信 */
    private var lastGpsAt = 0L
    /** 最後に記録した位置の測位時刻。Fused Location とGPSから同じ位置が届いたときに二重に記録しない */
    private var lastSavedTime = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            // GPSを直接使っていて、GPSが測位できているあいだは重複になるので記録しない
            val gpsActive = lastGpsAt > 0 &&
                SystemClock.elapsedRealtime() - lastGpsAt < settings.intervalSec * 1000 + GPS_TIMEOUT_MS
            if (!gpsActive) savePoint(location, if (location.accuracy < 20f) SourceType.GPS else SourceType.WIFI)
        }
    }

    // LocationListenerCompat は onProviderEnabled などに既定の実装があり、古いAndroidでも落ちない
    private val gpsListener = LocationListenerCompat { location ->
        lastGpsAt = SystemClock.elapsedRealtime()
        savePoint(location, SourceType.GPS)
    }

    // 連続測位を要求するために登録するだけで、測定データ自体は使わない
    private val gnssMeasurementsCallback = object : GnssMeasurementsEvent.Callback() {}

    private fun savePoint(location: Location, source: SourceType) {
        val sid = sessionId ?: return
        if (location.time == lastSavedTime) return
        lastSavedTime = location.time
        val point = LogPoint(
            sessionId = sid,
            // 受け取った時刻ではなく測位した時刻
            timestamp = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed else null,
            sourceType = source,
            recordMethod = method ?: RecordMethod.STANDARD
        )
        _latestPoint.value = point
        serviceScope.launch { repo.saveLogPoint(point) }

        pointCount++
        val now = System.currentTimeMillis()
        if (now - lastNotifiedAt >= NOTIFICATION_UPDATE_MS) updateNotification()
    }

    /** バッテリー残量・充電状態・省電力モードの変化で記録手段を選び直す */
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) updateBattery(intent)
            applyLocationRequest()
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LocationManager::class.java)
        overlay = RecordingOverlay(this)
        createNotificationChannel()
        // 記録モードや緑の帯などの設定は、記録中に変えてもすぐ反映する
        serviceScope.launch {
            settingsRepo.settings.collect { s ->
                withContext(Dispatchers.Main) {
                    // 記録間隔が変わったら、記録手段が同じでも位置情報のリクエストを出し直す
                    if (s.intervalSec != settings.intervalSec) method = null
                    settings = s
                    applyLocationRequest()
                    updateOverlay()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH_OVERLAY) {
            if (sessionId == null) stopSelf() else updateOverlay()
            return START_STICKY
        }

        // ACTION_START、またはシステムによる再起動（intent == null）
        // startForeground は起動から数秒以内に呼ぶ必要がある
        if (!startForegroundNotification()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val requestedId = intent?.getStringExtra(EXTRA_SESSION_ID)
        serviceScope.launch {
            val session = requestedId?.let { repo.getSession(it) } ?: repo.getIncompleteSession()
            val loaded = settingsRepo.settings.first()
            withContext(Dispatchers.Main) {
                if (session == null) {
                    stopSelf()
                } else if (session.id != sessionId) {
                    sessionId = session.id
                    sessionStartTime = session.startTime
                    pointCount = session.pointCount
                    settings = loaded
                    method = null
                    registerPowerReceiver()
                    applyLocationRequest()
                    updateOverlay()
                }
            }
        }
        return START_STICKY  // システムに強制終了されても自動再起動し、未完了のセッションを再開する
    }

    private fun stopRecording() {
        val sid = sessionId
        removeLocationUpdates()
        sessionId = null
        _status.value = null
        overlay.hide()
        // 書き込み待ちのポイントを保存し終えてからセッションを閉じる
        serviceScope.launch {
            if (sid != null) repo.stopSession(sid)
            withContext(Dispatchers.Main) { stopSelf() }
        }
    }

    private fun chooseMethod(): RecordMethod {
        if (settings.recordMode == RecordMode.LOW_POWER) return RecordMethod.LOW_POWER
        if (!settings.autoLowPower) return RecordMethod.STANDARD

        val powerSave = getSystemService(PowerManager::class.java).isPowerSaveMode
        val threshold = settings.lowBatteryPercent +
            if (method == RecordMethod.LOW_POWER_AUTO) BATTERY_HYSTERESIS else 0
        val lowBattery = !charging && batteryPercent <= threshold
        return if (powerSave || lowBattery) RecordMethod.LOW_POWER_AUTO else RecordMethod.STANDARD
    }

    /** 記録手段が変わったときだけ位置情報のリクエストを出し直す */
    private fun applyLocationRequest() {
        val sid = sessionId ?: return
        val next = chooseMethod()
        if (next == method) return
        method = next

        val mode = if (next == RecordMethod.STANDARD) RecordMode.STANDARD else RecordMode.LOW_POWER
        val intervalMs = settings.intervalSec * 1000
        val request = LocationRequest.Builder(mode.priority, intervalMs).build()
        removeLocationUpdates()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            if (next == RecordMethod.STANDARD && locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, intervalMs, 0f, gpsListener, Looper.getMainLooper()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.registerGnssMeasurementsCallback(
                        GnssMeasurementRequest.Builder().setFullTracking(true).build(),
                        ContextCompat.getMainExecutor(this),
                        gnssMeasurementsCallback
                    )
                }
            }
        } catch (e: SecurityException) {
            stopRecording()
            return
        }
        _status.value = RecordingStatus(sid, next)
        updateNotification()
    }

    private fun removeLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
        locationManager.removeUpdates(gpsListener)
        locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
        lastGpsAt = 0L
    }

    /** 記録中で設定が有効なら、他のアプリの上に緑の帯を出す */
    private fun updateOverlay() {
        if (sessionId != null && settings.showOverlay) overlay.show() else overlay.hide()
    }

    private fun updateNotification() {
        lastNotifiedAt = System.currentTimeMillis()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun registerPowerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // ACTION_BATTERY_CHANGED はstickyなので、登録時に現在の残量が返る。
        // どちらもシステムしか送れない保護されたブロードキャスト。NOT_EXPORTED にすると
        // Android 12 以前では互換用の権限が付いてシステムからの配信まで拒否される
        ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            ?.let { updateBattery(it) }
        receiverRegistered = true
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) batteryPercent = level * 100 / scale
        // 電源につながっているか（EXTRA_STATUS は外した直後も FULL のままのことがあるので使わない）
        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    /** 位置情報の権限がない状態でのシステム再起動などで開始できなければ false */
    private fun startForegroundNotification(): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
        true
    } catch (e: Exception) {
        false
    }

    /**
     * 記録中の通知。アプリを閉じていても、通知バーでバックグラウンド記録中だと分かるようにする。
     * 時刻の欄は記録開始からの経過時間（クロノメーター）を表示する。
     */
    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LocationForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (sessionId == null) "準備中..." else "${method?.label ?: "準備中"}・${pointCount}ポイント"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("バックグラウンドで記録中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_recording)
            // 通知全体を緑にして、記録中だとひと目で分かるようにする（Foreground Serviceの通知のみ可能）
            .setColor(RecordingOverlay.COLOR)
            .setColorized(true)
            // ロック画面にも表示する（位置などの詳細は含まないので公開してよい）
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)  // ユーザーがスワイプで消せないようにする
            .setOnlyAlertOnce(true)
            // Android 12以降は既定で表示が最大10秒遅れるので、すぐに出す
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (sessionStartTime > 0) {
                    setWhen(sessionStartTime)
                    setShowWhen(true)
                    setUsesChronometer(true)
                }
            }
            .setContentIntent(openAppIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPSロギング",
            NotificationManager.IMPORTANCE_LOW  // 音なし・バッジなし
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        removeLocationUpdates()
        if (receiverRegistered) unregisterReceiver(powerReceiver)
        overlay.hide()
        _status.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
