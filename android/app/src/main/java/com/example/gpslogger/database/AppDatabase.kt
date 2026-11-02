package com.example.gpslogger.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.Session

/**
 * Room Database のメインクラス。
 * アプリ全体でシングルトン（1つだけ）として使われる。
 */
@Database(
    entities = [Session::class, LogPoint::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun logPointDao(): LogPointDao

    companion object {
        /** v2: ログに記録手段（recordMethod）の列を追加 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_points ADD COLUMN recordMethod TEXT NOT NULL DEFAULT 'STANDARD'"
                )
            }
        }
    }
}
