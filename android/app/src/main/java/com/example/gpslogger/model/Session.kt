package com.example.gpslogger.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 1回の記録セッション（記録開始〜停止の1まとまり） */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val name: String = defaultName(),
    val pointCount: Int = 0,
    val fileIndex: Int = 0,
    val isCompleted: Boolean = false
) {
    /** 記録時間（秒）を返す */
    fun durationSeconds(): Long {
        val end = endTime ?: System.currentTimeMillis()
        return (end - startTime) / 1000
    }

    /** "HH:MM:SS" 形式の経過時間文字列 */
    fun durationFormatted(): String {
        val sec = durationSeconds()
        return "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
    }
}

private fun defaultName(): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN)
    return fmt.format(Date())
}
