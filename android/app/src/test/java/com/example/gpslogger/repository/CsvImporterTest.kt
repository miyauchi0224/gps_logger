package com.example.gpslogger.repository

import com.example.gpslogger.model.RecordMethod
import com.example.gpslogger.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class CsvImporterTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun アプリが出力したCSVを読める() {
        val csv = """
            id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method
            a,2026-04-09T10:00:01+09:00,35.681236,139.767125,5.2,3.0,1.2,GPS,STANDARD
            b,2026-04-09T10:00:00+09:00,35.681300,139.767200,,45.0,,WIFI,LOW_POWER_AUTO
        """.trimIndent()

        val result = CsvImporter.parse(csv, "s1", tokyo)

        assertEquals(2, result.points.size)
        assertEquals(0, result.skippedRows)
        // 日時順に並べ替える
        val first = result.points[0]
        assertEquals(1775696400000L, first.timestamp)
        assertEquals(35.6813, first.latitude, 1e-9)
        assertNull(first.altitude)
        assertEquals(SourceType.WIFI, first.sourceType)
        assertEquals(RecordMethod.LOW_POWER_AUTO, first.recordMethod)
        assertEquals("s1", first.sessionId)
        val second = result.points[1]
        assertEquals(5.2, second.altitude!!, 1e-9)
        assertEquals(1.2f, second.speed!!, 1e-6f)
        assertEquals(RecordMethod.STANDARD, second.recordMethod)
    }

    @Test
    fun 別名の列と時差なしの日時とBOMとCRLFを読める() {
        val csv = CsvImporter.BOM + "Time,Lon,Lat\r\n2026-04-09 10:00:00,139.7,35.6\r\n"

        val result = CsvImporter.parse(csv, "s1", tokyo)

        assertEquals(1, result.points.size)
        val p = result.points[0]
        assertEquals(1775696400000L, p.timestamp)
        assertEquals(35.6, p.latitude, 1e-9)
        assertEquals(139.7, p.longitude, 1e-9)
        assertEquals(SourceType.GPS, p.sourceType)
        assertEquals(RecordMethod.STANDARD, p.recordMethod)
    }

    @Test
    fun Unix秒とミリ秒を読める() {
        assertEquals(1775696400000L, CsvImporter.parseTimestamp("1775696400", tokyo))
        assertEquals(1775696400123L, CsvImporter.parseTimestamp("1775696400123", tokyo))
        assertNull(CsvImporter.parseTimestamp("not a date", tokyo))
    }

    @Test
    fun 読めない行は除外して数える() {
        val csv = """
            timestamp,latitude,longitude
            2026-04-09T10:00:00+09:00,35.6,139.7
            bad,35.6,139.7
            2026-04-09T10:00:02+09:00,999,139.7
            2026-04-09T10:00:03+09:00,35.6
        """.trimIndent()

        val result = CsvImporter.parse(csv, "s1", tokyo)

        assertEquals(1, result.points.size)
        assertEquals(3, result.skippedRows)
    }

    @Test
    fun ダブルクォートの列を分けられる() {
        assertEquals(listOf("a", "b,c", "d\"e", ""), CsvImporter.splitLine("a,\"b,c\",\"d\"\"e\","))
    }

    @Test(expected = CsvImporter.ImportException::class)
    fun 必須列がなければエラー() {
        CsvImporter.parse("name,value\nx,1", "s1", tokyo)
    }

    @Test(expected = CsvImporter.ImportException::class)
    fun データ行がなければエラー() {
        CsvImporter.parse("timestamp,latitude,longitude\n", "s1", tokyo)
    }
}
