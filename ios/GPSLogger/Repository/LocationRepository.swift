import Foundation
import Combine

/// 記録データの保存先。ViewModelやServiceはこれを通じてデータを操作する。
///
/// 保存先は Application Support/GPSLogger/
/// - sessions.json: セッションの一覧
/// - points/<セッションID>.jsonl: ログポイント（1行に1ポイントのJSON。記録中は末尾に追記する）
///
/// メインスレッドから使う。ファイルの読み書きは専用のキューで順番に行う。
final class LocationRepository: ObservableObject {

    @Published private(set) var sessions: [Session] = []

    private let baseURL: URL
    private let ioQueue = DispatchQueue(label: "com.example.gpslogger.repository")
    /// 記録中はポイント数が毎秒変わるので、セッション一覧の保存はこの間隔にまとめる
    private let sessionsSaveInterval: TimeInterval = 5
    private var lastSessionsSave = Date.distantPast

    private var sessionsURL: URL { baseURL.appendingPathComponent("sessions.json") }
    private var pointsDir: URL { baseURL.appendingPathComponent("points", isDirectory: true) }

    init(baseURL: URL? = nil) {
        self.baseURL = baseURL ?? FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("GPSLogger", isDirectory: true)
        try? FileManager.default.createDirectory(at: pointsDir, withIntermediateDirectories: true)
        loadSessions()
    }

    // MARK: - Session

    func startSession() -> Session {
        let session = Session()
        sessions.insert(session, at: 0)
        saveSessions()
        return session
    }

    func stopSession(id: UUID) {
        guard let index = sessions.firstIndex(where: { $0.id == id }) else { return }
        sessions[index].endTime = Date()
        sessions[index].isCompleted = true
        saveSessions()
    }

    func deleteSession(id: UUID) {
        sessions.removeAll { $0.id == id }
        saveSessions()
        let url = pointsURL(id)
        ioQueue.async { try? FileManager.default.removeItem(at: url) }
    }

    func session(id: UUID) -> Session? {
        sessions.first { $0.id == id }
    }

    func incompleteSession() -> Session? {
        sessions.first { !$0.isCompleted }
    }

    // MARK: - LogPoint

    /// ポイントを末尾に追記する
    func saveLogPoint(_ point: LogPoint) {
        guard let index = sessions.firstIndex(where: { $0.id == point.sessionId }) else { return }
        sessions[index].pointCount += 1
        if Date().timeIntervalSince(lastSessionsSave) >= sessionsSaveInterval { saveSessions() }

        let url = pointsURL(point.sessionId)
        ioQueue.async {
            guard var line = try? LocationRepository.encoder.encode(point) else { return }
            line.append(0x0A)  // 改行
            LocationRepository.append(line, to: url)
        }
    }

    /// セッションのポイントを日時順に読み込む（それまでに保存したポイントをすべて含む）
    func loadPoints(sessionId: UUID) async -> [LogPoint] {
        let url = pointsURL(sessionId)
        return await withCheckedContinuation { continuation in
            ioQueue.async {
                continuation.resume(returning: LocationRepository.readPoints(url))
            }
        }
    }

    func exportCsv(sessionId: UUID) async -> String {
        CsvExporter.csv(await loadPoints(sessionId: sessionId))
    }

    /// CSVを読み込んで、完了済みのセッションとして追加する。読み込みと変換は別スレッドで行う
    @MainActor
    func importSession(name: String, csv: String) async throws -> (Session, CsvImporter.Result) {
        var session = Session(name: name, isCompleted: true)
        let sessionId = session.id
        let url = pointsURL(sessionId)
        let queue = ioQueue
        let result: CsvImporter.Result = try await withCheckedThrowingContinuation { continuation in
            queue.async {
                do {
                    let result = try CsvImporter.parse(csv, sessionId: sessionId)
                    var data = Data()
                    for point in result.points {
                        data.append(try LocationRepository.encoder.encode(point))
                        data.append(0x0A)
                    }
                    try data.write(to: url, options: .atomic)
                    continuation.resume(returning: result)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
        session.startTime = result.points.first!.timestamp
        session.endTime = result.points.last!.timestamp
        session.pointCount = result.points.count

        sessions.insert(session, at: 0)
        sessions.sort { $0.startTime > $1.startTime }
        saveSessions()
        return (session, result)
    }

    /// 間引いているセッション一覧の保存をすぐ行う（アプリがバックグラウンドに入るときなど）
    func flush() {
        saveSessions()
    }

    // MARK: - Persistence

    private func pointsURL(_ sessionId: UUID) -> URL {
        pointsDir.appendingPathComponent("\(sessionId.uuidString).jsonl")
    }

    private func saveSessions() {
        lastSessionsSave = Date()
        let snapshot = sessions
        let url = sessionsURL
        ioQueue.async {
            if let data = try? JSONEncoder().encode(snapshot) {
                try? data.write(to: url, options: .atomic)
            }
        }
    }

    private func loadSessions() {
        if let data = try? Data(contentsOf: sessionsURL),
           let saved = try? JSONDecoder().decode([Session].self, from: data) {
            sessions = saved
        } else if let data = UserDefaults.standard.data(forKey: LocationRepository.legacySessionsKey),
                  let saved = try? JSONDecoder().decode([Session].self, from: data) {
            // 以前の版は UserDefaults にセッションだけを保存していた（ポイントは保存されていなかった）
            sessions = saved.map { s in
                var s = s
                s.pointCount = 0
                s.isCompleted = true
                if s.endTime == nil { s.endTime = s.startTime }
                return s
            }
            UserDefaults.standard.removeObject(forKey: LocationRepository.legacySessionsKey)
            saveSessions()
        }

        // 強制終了などでポイント数の保存が間に合わなかった記録は、ファイルの行数に合わせる
        for i in sessions.indices where !sessions[i].isCompleted {
            sessions[i].pointCount = LocationRepository.countLines(pointsURL(sessions[i].id))
        }
    }

    private static let legacySessionsKey = "sessions_data"

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .millisecondsSince1970
        return e
    }()

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .millisecondsSince1970
        return d
    }()

    private static func append(_ data: Data, to url: URL) {
        if let handle = try? FileHandle(forWritingTo: url) {
            defer { try? handle.close() }
            _ = try? handle.seekToEnd()
            try? handle.write(contentsOf: data)
        } else {
            try? data.write(to: url)
        }
    }

    private static func readPoints(_ url: URL) -> [LogPoint] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        // 途中で強制終了して最後の行が壊れていても、読める行だけ使う
        let points = data.split(separator: 0x0A).compactMap { try? decoder.decode(LogPoint.self, from: Data($0)) }
        return points.sorted { $0.timestamp < $1.timestamp }
    }

    private static func countLines(_ url: URL) -> Int {
        guard let data = try? Data(contentsOf: url) else { return 0 }
        return data.reduce(0) { $1 == 0x0A ? $0 + 1 : $0 }
    }
}
