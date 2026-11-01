package com.example.gpslogger.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.repository.AppSettings
import com.example.gpslogger.repository.LocationRepository
import com.example.gpslogger.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * MapScreen用のViewModel。
 * セッションIDを受け取り、そのセッションのログポイントを読み込む。
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repo: LocationRepository,
    settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // NavGraphから渡されるセッション情報
    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val sessionName: String = savedStateHandle["sessionName"] ?: ""

    // ログポイントをFlowで監視（データ変化を自動的にUIに反映）
    val logPoints = repo.observeLogPoints(sessionId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 地図の既定やGoogle Mapsの表示設定。読み込み完了までは null
    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
