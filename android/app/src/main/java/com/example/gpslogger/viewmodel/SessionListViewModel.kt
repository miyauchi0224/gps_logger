package com.example.gpslogger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val repo: LocationRepository
) : ViewModel() {

    /** DBの変更をリアルタイムで監視するFlow */
    val sessions = repo.allSessions

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repo.deleteSession(sessionId)
        }
    }
}
