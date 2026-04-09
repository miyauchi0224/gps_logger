package com.example.gpslogger.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.gpslogger.model.LogPoint
import kotlinx.coroutines.flow.Flow

/** DAO = Data Access Object。DBへのCRUD操作を定義するインターフェース */
@Dao
interface LogPointDao {

    @Insert
    suspend fun insert(logPoint: LogPoint)

    /** セッションIDに紐づくログ一覧をリアルタイムで監視 */
    @Query("SELECT * FROM log_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: String): Flow<List<LogPoint>>

    /** CSV出力用：セッションの全ログを取得 */
    @Query("SELECT * FROM log_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllBySession(sessionId: String): List<LogPoint>

    @Query("DELETE FROM log_points WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
