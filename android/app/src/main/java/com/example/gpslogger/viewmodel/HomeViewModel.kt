package com.example.gpslogger.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.RecordMode
import com.example.gpslogger.model.Session
import com.example.gpslogger.repository.AppSettings
import com.example.gpslogger.repository.LocationRepository
import com.example.gpslogger.repository.SettingsRepository
import com.example.gpslogger.service.LocationForegroundService
import com.example.gpslogger.service.RecordingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ホーム画面用のViewModel。
 * 記録とDBへの保存は LocationForegroundService が行い、ここではその状態を表示用に公開する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repo: LocationRepository,
    private val settingsRepo: SettingsRepository
) : AndroidViewModel(application) {

    /** 記録中の状態（記録手段を含む）。記録していないときは null */
    val status: StateFlow<RecordingStatus?> = LocationForegroundService.status

    val isRecording: StateFlow<Boolean> = status
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, status.value != null)

    val latestPoint: StateFlow<LogPoint?> = LocationForegroundService.latestPoint

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentSession: StateFlow<Session?> = status
        .map { it?.sessionId }
        .distinctUntilChanged()
        .mapLatest { id -> id?.let { repo.getSession(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 記録開始からの経過秒数。アプリを開き直しても開始時刻から計算する */
    val elapsedSeconds: StateFlow<Long> = currentSession
        .flatMapLatest { session ->
            if (session == null) flowOf(0L)
            else flow {
                while (true) {
                    emit((System.currentTimeMillis() - session.startTime) / 1000)
                    delay(1000)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        // 停止しないままアプリが終了していた場合は、未完了のセッションの記録を再開する
        viewModelScope.launch {
            if (status.value == null && repo.getIncompleteSession() != null) {
                sendToService(LocationForegroundService.ACTION_START)
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            val session = repo.startSession()
            sendToService(LocationForegroundService.ACTION_START, session.id)
        }
    }

    fun stopRecording() {
        // セッションを閉じるのはServiceが行う（保存待ちのポイントを書き終えてから）
        sendToService(LocationForegroundService.ACTION_STOP)
    }

    /** 記録モードを 標準 ⇔ 低消費電力 で切り替える。記録中ならServiceがすぐに反映する */
    fun toggleRecordMode() {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            val next = if (s.recordMode == RecordMode.STANDARD) RecordMode.LOW_POWER else RecordMode.STANDARD
            settingsRepo.save(s.copy(recordMode = next))
        }
    }

    private fun sendToService(action: String, sessionId: String? = null) {
        val intent = Intent(getApplication(), LocationForegroundService::class.java).apply {
            this.action = action
            sessionId?.let { putExtra(LocationForegroundService.EXTRA_SESSION_ID, it) }
        }
        if (action == LocationForegroundService.ACTION_START) {
            ContextCompat.startForegroundService(getApplication(), intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    /** 経過時間を "HH:MM:SS" 形式で返す */
    fun formatElapsed(seconds: Long): String {
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
