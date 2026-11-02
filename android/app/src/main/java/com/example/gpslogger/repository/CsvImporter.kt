package com.example.gpslogger.repository

import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.RecordMethod
import com.example.gpslogger.model.SourceType
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * CSVを読み込んでログポイントに変換する。
 *
 * このアプリが出力したCSV（id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method）のほか、
 * 列の並びが違うものや、lat / lon / time など別名の列名のCSVも読める。
 * 必須は 日時・緯度・経度 の3列。読めない行は飛ばして件数だけ数える。
 */
object CsvImporter {

    class ImportException(message: String) : Exception(message)

    data class Result(val points: List<LogPoint>, val skippedRows: Int)

    /** UTF-8 の BOM（U+FEFF） */
    val BOM = Char(0xFEFF).toString()

    // 列名の別名（小文字で比較）
    private val TIMESTAMP = setOf("timestamp", "time", "datetime", "date_time", "date")
    private val LATITUDE = setOf("latitude", "lat")
    private val LONGITUDE = setOf("longitude", "lon", "lng", "long")
    private val ALTITUDE = setOf("altitude", "alt", "elevation", "ele")
    private val ACCURACY = setOf("accuracy", "acc", "hacc")
    private val SPEED = setOf("speed")
    private val SOURCE = setOf("source")
    private val METHOD = setOf("method")

    /**
     * @param sessionId 読み込んだポイントを入れるセッション
     * @param zone 日時に時差（+09:00 など）が書かれていないときに使うタイムゾーン
     */
    fun parse(text: String, sessionId: String, zone: ZoneId = ZoneId.systemDefault()): Result {
        // 先頭のBOM（Excelなどが付ける）を除く
        val lines = text.removePrefix(BOM).lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) throw ImportException("ファイルが空です")

        val header = splitLine(lines.first()).map { it.trim().lowercase() }
        fun column(names: Set<String>) = header.indexOfFirst { it in names }.takeIf { it >= 0 }

        val timeCol = column(TIMESTAMP)
        val latCol = column(LATITUDE)
        val lonCol = column(LONGITUDE)
        if (timeCol == null || latCol == null || lonCol == null) {
            throw ImportException("日時・緯度・経度の列が見つかりません（1行目に列名が必要です）")
        }
        val altCol = column(ALTITUDE)
        val accCol = column(ACCURACY)
        val speedCol = column(SPEED)
        val sourceCol = column(SOURCE)
        val methodCol = column(METHOD)

        var skipped = 0
        val points = lines.drop(1).mapNotNull { line ->
            val cells = splitLine(line)
            fun cell(i: Int?) = i?.let { cells.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }

            val timestamp = cell(timeCol)?.let { parseTimestamp(it, zone) }
            val lat = cell(latCol)?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
            val lon = cell(lonCol)?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
            if (timestamp == null || lat == null || lon == null) {
                skipped++
                return@mapNotNull null
            }
            val accuracy = cell(accCol)?.toFloatOrNull()
            LogPoint(
                sessionId = sessionId,
                timestamp = timestamp,
                latitude = lat,
                longitude = lon,
                altitude = cell(altCol)?.toDoubleOrNull(),
                accuracy = accuracy ?: 0f,
                speed = cell(speedCol)?.toFloatOrNull(),
                // 測位方式の列がなければ、記録時と同じく精度から推定する
                sourceType = cell(sourceCol)?.let { s -> SourceType.entries.find { it.name.equals(s, true) } }
                    ?: if (accuracy != null && accuracy >= 20f) SourceType.WIFI else SourceType.GPS,
                recordMethod = cell(methodCol)?.let { m -> RecordMethod.entries.find { it.name.equals(m, true) } }
                    ?: RecordMethod.STANDARD
            )
        }.sortedBy { it.timestamp }

        if (points.isEmpty()) throw ImportException("読み込めるデータ行がありません")
        return Result(points, skipped)
    }

    /**
     * 日時を Unix ミリ秒に変換する。読めなければ null。
     * 2026-04-09T10:00:00+09:00 / 2026-04-09 10:00:00（zone で解釈）/ Unix 秒・ミリ秒 に対応。
     */
    fun parseTimestamp(value: String, zone: ZoneId): Long? {
        value.toLongOrNull()?.let { n ->
            // 10桁程度なら秒、それより大きければミリ秒とみなす
            return if (n < 100_000_000_000L) n * 1000 else n
        }
        val iso = value.replace(' ', 'T')
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    /** CSVの1行を列に分ける。ダブルクォートで囲んだ列（中のカンマや "" を含む）にも対応する */
    fun splitLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cells.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        cells.add(sb.toString())
        return cells
    }
}
