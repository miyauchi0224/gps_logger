package com.example.gpslogger.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.Session
import com.example.gpslogger.repository.LocationRepository
import com.example.gpslogger.service.LocationForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repo: LocationRepository
) : AndroidViewModel(application) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _latestPoint = MutableStateFlow<LogPoint?>(null)
    val latestPoint: StateFlow<LogPoint?> = _latestPoint.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    // 設定から読む記録間隔（秒）
    var intervalSec: Long = 1L

    init {
        // アプリ再起動時に未完了セッションがあれば復元
        viewModelScope.launch {
            val incomplete = repo.getIncompleteSession()
            if (incomplete != null) {
                _currentSession.value = incomplete
                // 注意: 復元ダイアログはUI側で表示する
            }
        }
        // Serviceから流れてくるログポイントを購読・保存
        viewModelScope.launch {
            LocationForegroundService.logPointFlow.collect { point ->
                repo.saveLogPoint(point)
                _latestPoint.value = point
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            val session = repo.startSession()
            _currentSession.value = session
            _isRecording.value = true
            _elapsedSeconds.value = 0L

            // Foreground Service を起動
            val intent = Intent(getApplication(), LocationForegroundService::class.java).apply {
                action = LocationForegroundService.ACTION_START
                putExtra(LocationForegroundService.EXTRA_SESSION_ID, session.id)
                putExtra(LocationForegroundService.EXTRA_INTERVAL_SEC, intervalSec)
            }
            ContextCompat.startForegroundService(getApplication(), intent)

            // 経過時間カウンター
            launch {
                while (_isRecording.value) {
                    delay(1000)
                    _elapsedSeconds.value++
                }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val sessionId = _currentSession.value?.id ?: return@launch
            // Foreground Service を停止
            val intent = Intent(getApplication(), LocationForegroundService::class.java).apply {
                action = LocationForegroundService.ACTION_STOP
            }
            getApplication<Application>().stopService(intent)

            repo.stopSession(sessionId)
            _isRecording.value = false
        }
    }

    /** 経過時間を "HH:MM:SS" 形式で返す */
    fun formatElapsed(seconds: Long): String {
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
