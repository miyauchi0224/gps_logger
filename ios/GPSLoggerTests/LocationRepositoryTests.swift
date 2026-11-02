import XCTest
@testable import GPSLogger

final class LocationRepositoryTests: XCTestCase {

    private var dir: URL!

    override func setUp() {
        super.setUp()
        dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: dir)
        super.tearDown()
    }

    func test記録したポイントはアプリを再起動しても残る() async {
        let repo = LocationRepository(baseURL: dir)
        let session = repo.startSession()
        for i in 0..<5 {
            repo.saveLogPoint(LogPoint(
                sessionId: session.id,
                timestamp: Date(timeIntervalSince1970: 1_775_696_400 + Double(i)),
                latitude: 35.6, longitude: 139.7, accuracy: 3
            ))
        }
        // 書き込みが終わるのを待つ
        _ = await repo.loadPoints(sessionId: session.id)

        // 停止しないまま終了した想定。読み込み直すとポイント数はファイルの行数に合わせる
        let reopened = LocationRepository(baseURL: dir)
        XCTAssertEqual(reopened.incompleteSession()?.id, session.id)
        XCTAssertEqual(reopened.incompleteSession()?.pointCount, 5)
        let points = await reopened.loadPoints(sessionId: session.id)
        XCTAssertEqual(points.count, 5)
    }

    @MainActor
    func testCSVを読み込んでセッションを追加できる() async throws {
        let repo = LocationRepository(baseURL: dir)
        let csv = "timestamp,latitude,longitude\n2026-04-09T10:00:00+09:00,35.6,139.7\n2026-04-09T10:00:05+09:00,35.7,139.8"

        let (session, result) = try await repo.importSession(name: "walk", csv: csv)

        XCTAssertEqual(result.points.count, 2)
        XCTAssertTrue(session.isCompleted)
        XCTAssertEqual(session.pointCount, 2)
        XCTAssertEqual(session.durationSeconds, 5)
        XCTAssertEqual(repo.sessions.first?.name, "walk")
        let points = await repo.loadPoints(sessionId: session.id)
        XCTAssertEqual(points.count, 2)
    }

    func test削除するとポイントも消える() async {
        let repo = LocationRepository(baseURL: dir)
        let session = repo.startSession()
        repo.saveLogPoint(LogPoint(sessionId: session.id, timestamp: Date(), latitude: 35.6, longitude: 139.7, accuracy: 3))
        repo.deleteSession(id: session.id)

        XCTAssertNil(repo.session(id: session.id))
        let points = await repo.loadPoints(sessionId: session.id)
        XCTAssertTrue(points.isEmpty)
    }
}
