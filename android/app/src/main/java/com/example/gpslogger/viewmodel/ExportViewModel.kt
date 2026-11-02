package com.example.gpslogger.viewmodel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpslogger.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val message: String) : ExportState()
    /** 取り消しなど、成功でも失敗でもないお知らせ */
    data class Info(val message: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repo: LocationRepository
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    /** 実行中の保存処理（キャンセル用） */
    private var job: Job? = null

    /** 端末のDownloadsフォルダにCSVを保存する */
    fun exportToCsv(context: Context, sessionId: String, fileName: String) {
        job = viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val csv = repo.exportCsv(sessionId)
                withContext(Dispatchers.IO) { saveToDownloads(context, fileName, csv) }
                _exportState.value = ExportState.Success("$fileName を保存しました")
            } catch (e: CancellationException) {
                _exportState.value = ExportState.Info("保存を取り消しました")
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("エクスポートに失敗しました: ${e.message}")
            }
        }
    }

    /**
     * 保存先ピッカー（ACTION_CREATE_DOCUMENT）で選ばれた場所にCSVを書き込む。
     * Google Drive を選んだ場合、クラウドへの送信は各アプリが行う。
     * 途中で取り消した・失敗した場合は、ピッカーが作った空のファイルを消す。
     */
    fun exportToUri(context: Context, sessionId: String, uri: Uri) {
        job = viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val csv = repo.exportCsv(sessionId)
                withContext(Dispatchers.IO) {
                    ensureActive()
                    // ピッカーが新しく作った空のファイルなので "w" で書く（"wt" 非対応のクラウドアプリがある）
                    context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("保存先を開けません")
                }
                _exportState.value = ExportState.Success("保存しました（クラウドへの送信は保存先のアプリが行います）")
            } catch (e: CancellationException) {
                withContext(NonCancellable) { deleteDocument(context, uri) }
                _exportState.value = ExportState.Info("保存を取り消しました")
                throw e
            } catch (e: Exception) {
                deleteDocument(context, uri)
                _exportState.value = ExportState.Error("保存に失敗しました: ${e.message}")
            }
        }
    }

    /**
     * 共有用のCSVを一時ファイルに書き出し、他のアプリに渡せるURIを返す。
     * OneDrive は保存先ピッカーに出ないため、共有（ACTION_SEND）から保存してもらう。
     */
    fun prepareShare(context: Context, sessionId: String, fileName: String, onReady: (Uri) -> Unit) {
        job = viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val csv = repo.exportCsv(sessionId)
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports")
                    // 前回共有した一時ファイルは不要なので消す
                    dir.deleteRecursively()
                    dir.mkdirs()
                    File(dir, fileName).apply { writeText(csv, Charsets.UTF_8) }
                }
                _exportState.value = ExportState.Idle
                onReady(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            } catch (e: CancellationException) {
                _exportState.value = ExportState.Info("共有を取り消しました")
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("共有の準備に失敗しました: ${e.message}")
            }
        }
    }

    /** 保存中の処理を取り消す */
    fun cancel() {
        job?.cancel()
    }

    /** 保存先ピッカーで保存せずに戻った */
    fun pickerCancelled() {
        _exportState.value = ExportState.Info("保存を取り消しました")
    }

    private fun deleteDocument(context: Context, uri: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            // 削除に対応していない保存先もある。空のファイルが残るだけなので無視する
        }
    }

    private fun saveToDownloads(context: Context, fileName: String, content: String) {
        // MediaStore.Downloads は API 29 から。それより前はストレージ権限が不要なアプリ専用のDownloadsに保存する
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw Exception("保存先が利用できません")
            File(dir, fileName).writeText(content, Charsets.UTF_8)
            return
        }
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

    companion object {
        private val INVALID_CHARS = Regex("""[\s/\\:*?"<>|]""")

        /** 既定の保存ファイル名 */
        fun csvFileName(sessionName: String) = normalizeFileName("gpslog_$sessionName")

        /** 入力されたファイル名を整える。使えない文字は _ にし、拡張子 .csv を付ける */
        fun normalizeFileName(input: String): String {
            val base = input.trim().removeSuffix(".csv").replace(INVALID_CHARS, "_")
            return "${base.ifEmpty { "gpslog" }}.csv"
        }
    }
}
