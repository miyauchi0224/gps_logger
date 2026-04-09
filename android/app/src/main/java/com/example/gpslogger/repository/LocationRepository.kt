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

    suspend fun getIncompleteSession(): Session? = sessionDao.getIncompleteSession()

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteById(sessionId)
    }

    /** CSV形式の文字列を生成して返す */
    suspend fun exportCsv(sessionId: String): String {
        val points = logPointDao.getAllBySession(sessionId)
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+09:00", Locale.JAPAN)
        val header = "id,timestamp,latitude,longitude,altitude,accuracy,speed,source\n"
        val rows = points.joinToString("\n") { p ->
            "${p.id},${fmt.format(Date(p.timestamp))},${p.latitude},${p.longitude}," +
            "${p.altitude ?: ""},${p.accuracy},${p.speed ?: ""},${p.sourceType}"
        }
        return header + rows
    }
}
