package com.example.gpslogger.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val message: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repo: LocationRepository
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    /** 端末のDownloadsフォルダにCSVを保存する */
    fun exportToCsv(context: Context, sessionId: String, sessionName: String) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val csv = repo.exportCsv(sessionId)
                val fileName = "gpslog_${sessionName.replace(" ", "_")}.csv"
                saveToDownloads(context, fileName, csv)
                _exportState.value = ExportState.Success("$fileName を保存しました")
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("エクスポートに失敗しました: ${e.message}")
            }
        }
    }

    private fun saveToDownloads(context: Context, fileName: String, content: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("ファイル作成に失敗しました")

        resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    fun resetState() {
        _exportState.value = ExportState.Idle
    }
}
