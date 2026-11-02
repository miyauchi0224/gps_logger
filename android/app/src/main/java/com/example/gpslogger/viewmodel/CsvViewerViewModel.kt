package com.example.gpslogger.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.repository.CsvImporter
import com.example.gpslogger.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** CSVを表として表示するためのデータ。widths は列ごとの最大文字数（列幅の計算用） */
data class CsvTable(
    val header: List<String>,
    val rows: List<List<String>>,
    val widths: List<Int>
)

/**
 * CsvViewerScreen用のViewModel。
 * エクスポートと同じCSVを作って列に分け、そのまま表として見せる。
 */
@HiltViewModel
class CsvViewerViewModel @Inject constructor(
    repo: LocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val sessionName: String = savedStateHandle["sessionName"] ?: ""

    /** 読み込み完了までは null */
    private val _table = MutableStateFlow<CsvTable?>(null)
    val table: StateFlow<CsvTable?> = _table.asStateFlow()

    init {
        viewModelScope.launch {
            val csv = repo.exportCsv(sessionId)
            _table.value = withContext(Dispatchers.Default) {
                val lines = csv.lines().filter { it.isNotEmpty() }.map { CsvImporter.splitLine(it) }
                val header = lines.firstOrNull().orEmpty()
                val rows = lines.drop(1)
                val widths = header.indices.map { col ->
                    maxOf(header[col].length, rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0)
                }
                CsvTable(header, rows, widths)
            }
        }
    }
}
