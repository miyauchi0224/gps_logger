import XCTest
@testable import GPSLogger

/// Android版 CsvImporterTest と同じ内容
final class CsvImporterTests: XCTestCase {

    private let tokyo = TimeZone(identifier: "Asia/Tokyo")!
    private let sid = UUID()

    func testアプリが出力したCSVを読める() throws {
        let csv = """
        id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method
        a,2026-04-09T10:00:01+09:00,35.681236,139.767125,5.2,3.0,1.2,GPS,STANDARD
        b,2026-04-09T10:00:00+09:00,35.681300,139.767200,,45.0,,WIFI,LOW_POWER_AUTO
        """

        let result = try CsvImporter.parse(csv, sessionId: sid, timeZone: tokyo)

        XCTAssertEqual(result.points.count, 2)
        XCTAssertEqual(result.skippedRows, 0)
        // 日時順に並べ替える
        let first = result.points[0]
        XCTAssertEqual(first.timestamp, Date(timeIntervalSince1970: 1_775_696_400))
        XCTAssertEqual(first.latitude, 35.6813, accuracy: 1e-9)
        XCTAssertNil(first.altitude)
        XCTAssertEqual(first.sourceType, .wifi)
        XCTAssertEqual(first.recordMethod, .lowPowerAuto)
        XCTAssertEqual(first.sessionId, sid)
        let second = result.points[1]
        XCTAssertEqual(second.altitude!, 5.2, accuracy: 1e-9)
        XCTAssertEqual(second.speed!, 1.2, accuracy: 1e-9)
        XCTAssertEqual(second.recordMethod, .standard)
    }

    func test別名の列と時差なしの日時とBOMとCRLFを読める() throws {
        let csv = CsvImporter.bom + "Time,Lon,Lat\r\n2026-04-09 10:00:00,139.7,35.6\r\n"

        let result = try CsvImporter.parse(csv, sessionId: sid, timeZone: tokyo)

        XCTAssertEqual(result.points.count, 1)
        let p = result.points[0]
        XCTAssertEqual(p.timestamp, Date(timeIntervalSince1970: 1_775_696_400))
        XCTAssertEqual(p.latitude, 35.6, accuracy: 1e-9)
        XCTAssertEqual(p.longitude, 139.7, accuracy: 1e-9)
        XCTAssertEqual(p.sourceType, .gps)
        XCTAssertEqual(p.recordMethod, .standard)
    }

    func testUnix秒とミリ秒を読める() {
        XCTAssertEqual(CsvImporter.parseTimestamp("1775696400", timeZone: tokyo),
                       Date(timeIntervalSince1970: 1_775_696_400))
        XCTAssertEqual(CsvImporter.parseTimestamp("1775696400123", timeZone: tokyo)!.timeIntervalSince1970,
                       1_775_696_400.123, accuracy: 1e-6)
        XCTAssertNil(CsvImporter.parseTimestamp("not a date", timeZone: tokyo))
    }

    func test読めない行は除外して数える() throws {
        let csv = """
        timestamp,latitude,longitude
        2026-04-09T10:00:00+09:00,35.6,139.7
        bad,35.6,139.7
        2026-04-09T10:00:02+09:00,999,139.7
        2026-04-09T10:00:03+09:00,35.6
        """

        let result = try CsvImporter.parse(csv, sessionId: sid, timeZone: tokyo)

        XCTAssertEqual(result.points.count, 1)
        XCTAssertEqual(result.skippedRows, 3)
    }

    func testダブルクォートの列を分けられる() {
        XCTAssertEqual(CsvImporter.splitLine("a,\"b,c\",\"d\"\"e\","), ["a", "b,c", "d\"e", ""])
    }

    func test必須列がなければエラー() {
        XCTAssertThrowsError(try CsvImporter.parse("name,value\nx,1", sessionId: sid, timeZone: tokyo))
    }

    func testデータ行がなければエラー() {
        XCTAssertThrowsError(try CsvImporter.parse("timestamp,latitude,longitude\n", sessionId: sid, timeZone: tokyo))
    }
}
