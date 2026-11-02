package com.example.gpslogger.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** GPS/WiFiで取得した1件の位置情報ログ */
@Entity(
    tableName = "log_points",
    foreignKeys = [ForeignKey(
        entity = Session::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE  // セッション削除時にログも自動削除
    )],
    indices = [Index("sessionId")]
)
data class LogPoint(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),  // Unix時刻（ミリ秒）
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float,
    val speed: Float? = null,
    val sourceType: SourceType = SourceType.GPS,
    // DBバージョン2で追加。既存の行は STANDARD になる
    @ColumnInfo(defaultValue = "STANDARD")
    val recordMethod: RecordMethod = RecordMethod.STANDARD
)

enum class SourceType { GPS, WIFI }
