package com.example.gpslogger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.repository.AppSettings
import com.example.gpslogger.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    // 読み込み完了までは null（画面側で既定値が一瞬見えないようにする）
    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(settings: AppSettings, onSaved: () -> Unit) {
        viewModelScope.launch {
            settingsRepo.save(settings)
            onSaved()
        }
    }
}
