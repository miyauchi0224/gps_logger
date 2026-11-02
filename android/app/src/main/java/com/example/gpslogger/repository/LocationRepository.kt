package com.example.gpslogger.repository

import com.example.gpslogger.database.LogPointDao
import com.example.gpslogger.database.SessionDao
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.Session
import com.example.gpslogger.service.LocationForegroundService
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository = UIとDBの間に立つ仲介役。
 * ViewModelはRepositoryを通じてだけデータにアクセスする。
 * これにより、DBの詳細をUIから隠せる（関心の分離）。
 */
@Singleton
class LocationRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val logPointDao: LogPointDao
) {
    val allSessions: Flow<List<Session>> = sessionDao.observeAll()

    fun observeLogPoints(sessionId: String): Flow<List<LogPoint>> =
        logPointDao.observeBySession(sessionId)

    suspend fun startSession(): Session {
        val session = Session()
        sessionDao.insert(session)
        return session
    }

    suspend fun stopSession(sessionId: String) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(
            session.copy(
                endTime = System.currentTimeMillis(),
                isCompleted = true
            )
        )
    }

    suspend fun saveLogPoint(logPoint: LogPoint) {
        logPointDao.insert(logPoint)
        // ポイント数をインクリメント
        val session = sessionDao.getById(logPoint.sessionId) ?: return
        sessionDao.update(session.copy(pointCount = session.pointCount + 1))
    }

    /**
     * CSVから読み込んだポイントを新しいセッションとして追加する。
     * 期間は最初と最後のポイントの日時。ポイントの追加に失敗したらセッションも消す。
     */
    suspend fun importSession(name: String, csv: String): Pair<Session, CsvImporter.Result> {
        val session = Session(name = name, isCompleted = true)
        val result = CsvImporter.parse(csv, session.id)
        val imported = session.copy(
            startTime = result.points.first().timestamp,
            endTime = result.points.last().timestamp,
            pointCount = result.points.size
        )
        sessionDao.insert(imported)
        try {
            logPointDao.insertAll(result.points)
        } catch (e: Exception) {
            sessionDao.deleteById(imported.id)
            throw e
        }
        return imported to result
    }

    suspend fun getSession(sessionId: String): Session? = sessionDao.getById(sessionId)

    suspend fun getIncompleteSession(): Session? = sessionDao.getIncompleteSession()

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteById(sessionId)
    }

    /** CSV形式の文字列を生成して返す */
    suspend fun exportCsv(sessionId: String): String {
        val points = logPointDao.getAllBySession(sessionId)
        // XXX は端末のタイムゾーンの時差（日本なら +09:00）。読み込み時にこの時差で正しい時刻に戻せる
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.JAPAN)
        // source: 精度から推定した測位手段（GPS/WIFI）、method: 記録モード（RecordMethod）
        val header = "id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method\n"
        val rows = points.joinToString("\n") { p ->
            "${p.id},${fmt.format(Date(p.timestamp))},${p.latitude},${p.longitude}," +
            "${p.altitude ?: ""},${p.accuracy},${p.speed ?: ""},${p.sourceType},${p.recordMethod}"
        }
        return header + rows
    }
}
