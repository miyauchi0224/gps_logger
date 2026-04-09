package com.example.gpslogger.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.Session

/**
 * Room Database のメインクラス。
 * アプリ全体でシングルトン（1つだけ）として使われる。
 */
@Database(
    entities = [Session::class, LogPoint::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun logPointDao(): LogPointDao
}
