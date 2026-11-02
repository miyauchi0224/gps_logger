import XCTest
@testable import GPSLogger

final class CsvExporterTests: XCTestCase {

    private let tokyo = TimeZone(identifier: "Asia/Tokyo")!

    func testAndroid版と同じ形式で出力する() {
        let point = LogPoint(
            id: UUID(uuidString: "2F1D3C4B-0000-4000-8000-000000000001")!,
            sessionId: UUID(),
            timestamp: Date(timeIntervalSince1970: 1_775_696_400),
            latitude: 35.681236,
            longitude: 139.767125,
            altitude: 5.2,
            accuracy: 3.0,
            speed: nil,
            sourceType: .gps,
            recordMethod: .lowPowerAuto
        )

        let csv = CsvExporter.csv([point], timeZone: tokyo)

        XCTAssertEqual(csv, """
        id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method
        2f1d3c4b-0000-4000-8000-000000000001,2026-04-09T10:00:00+09:00,35.681236,139.767125,5.2,3.0,,GPS,LOW_POWER_AUTO
        """)
    }

    func test出力したCSVを読み込むと元に戻る() throws {
        let sid = UUID()
        let points = (0..<3).map { i in
            LogPoint(
                sessionId: sid,
                timestamp: Date(timeIntervalSince1970: 1_775_696_400 + Double(i)),
                latitude: 35.6 + Double(i) * 0.001,
                longitude: 139.7,
                accuracy: 45,
                sourceType: .wifi,
                recordMethod: .lowPower
            )
        }

        let result = try CsvImporter.parse(CsvExporter.csv(points, timeZone: tokyo), sessionId: sid, timeZone: tokyo)

        XCTAssertEqual(result.points.map(\.timestamp), points.map(\.timestamp))
        XCTAssertEqual(result.points.map(\.latitude), points.map(\.latitude))
        XCTAssertEqual(result.points.map(\.recordMethod), points.map(\.recordMethod))
        XCTAssertEqual(result.points.map(\.sourceType), points.map(\.sourceType))
    }

    func testファイル名を整える() {
        XCTAssertEqual(CsvExporter.fileName(sessionName: "2026-04-09 10:00"), "gpslog_2026-04-09_10_00.csv")
        XCTAssertEqual(CsvExporter.normalizeFileName(" walk.csv "), "walk.csv")
        XCTAssertEqual(CsvExporter.normalizeFileName("a/b?c"), "a_b_c.csv")
        XCTAssertEqual(CsvExporter.normalizeFileName(""), "gpslog.csv")
    }
}
