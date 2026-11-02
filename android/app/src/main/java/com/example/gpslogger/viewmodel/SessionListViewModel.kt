package com.example.gpslogger.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val repo: LocationRepository
) : ViewModel() {

    /** DBの変更をリアルタイムで監視するFlow */
    val sessions = repo.allSessions

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** 読み込み結果のメッセージ。表示したら clearMessage() で消す */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repo.deleteSession(sessionId)
        }
    }

    /** ファイル選択画面で選んだCSVを、新しいセッションとして一覧に追加する */
    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val (name, text) = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: throw CsvImporter.ImportException("ファイルを開けません")
                    displayName(context, uri) to text
                }
                val (session, result) = repo.importSession(name, text)
                _message.value = buildString {
                    append("「${session.name}」を読み込みました（${result.points.size}ポイント")
                    if (result.skippedRows > 0) append("、読めない${result.skippedRows}行を除外")
                    append("）")
                }
            } catch (e: CsvImporter.ImportException) {
                _message.value = "読み込めませんでした: ${e.message}"
            } catch (e: Exception) {
                _message.value = "読み込みに失敗しました: ${e.message}"
            } finally {
                _importing.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** セッション名にするファイル名（拡張子なし） */
    private fun displayName(context: Context, uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "読み込んだ記録"
    }
}
