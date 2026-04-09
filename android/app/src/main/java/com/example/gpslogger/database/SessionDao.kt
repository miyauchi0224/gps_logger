package com.example.gpslogger.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.gpslogger.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: Session)

    @Update
    suspend fun update(session: Session)

    /** 全セッションを新しい順で監視 */
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): Session?

    /** 前回の未完了セッションを探す（アプリ再起動時の復元用） */
    @Query("SELECT * FROM sessions WHERE isCompleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getIncompleteSession(): Session?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}
