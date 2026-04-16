package com.example.gpslogger.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 地図表示で選択できる地図の種類 */
enum class MapType {
    GOOGLE,  // Google Maps（APIキー必要）
    OSM,     // OpenStreetMap（無料・APIキー不要）
    GSI      // 国土地理院地図（無料・APIキー不要）
}

/**
 * MapScreen用のViewModel。
 * セッションIDを受け取り、そのセッションのログポイントを読み込む。
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repo: LocationRepository,
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
}
