import Foundation
import Combine

/// DBとサービスの仲介役。ViewModelはこれを通じてデータを操作する。
class LocationRepository: ObservableObject {

    @Published var sessions: [Session] = []
    @Published var logPoints: [String: [LogPoint]] = [:]  // key: sessionId

    private let sessionsKey = "sessions_data"
    private var cancellables = Set<AnyCancellable>()

    init() {
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
        logPoints.removeValue(forKey: id.uuidString)
        saveSessions()
    }

    func incompleteSession() -> Session? {
        return sessions.first { !$0.isCompleted }
    }

    // MARK: - LogPoint

    func saveLogPoint(_ point: LogPoint) {
        let key = point.sessionId.uuidString
        if logPoints[key] == nil { logPoints[key] = [] }
        logPoints[key]?.append(point)
        // ポイント数を更新
        if let index = sessions.firstIndex(where: { $0.id == point.sessionId }) {
            sessions[index].pointCount += 1
        }
        saveSessions()
    }

    func getLogPoints(sessionId: UUID) -> [LogPoint] {
        return logPoints[sessionId.uuidString] ?? []
    }

    // MARK: - CSV Export

    func exportCsv(sessionId: UUID) -> String {
        let points = getLogPoints(sessionId: sessionId)
        let fmt = ISO8601DateFormatter()
        let header = "id,timestamp,latitude,longitude,altitude,accuracy,speed,source\n"
        let rows = points.map { p in
            "\(p.id),\(fmt.string(from: p.timestamp)),\(p.latitude),\(p.longitude)," +
            "\(p.altitude.map { String($0) } ?? ""),\(p.accuracy)," +
            "\(p.speed.map { String($0) } ?? ""),\(p.sourceType.rawValue)"
        }.joined(separator: "\n")
        return header + rows
    }

    // MARK: - Persistence（UserDefaults に保存）

    private func saveSessions() {
        if let data = try? JSONEncoder().encode(sessions) {
            UserDefaults.standard.set(data, forKey: sessionsKey)
        }
    }

    private func loadSessions() {
        guard let data = UserDefaults.standard.data(forKey: sessionsKey),
              let saved = try? JSONDecoder().decode([Session].self, from: data) else { return }
        sessions = saved
    }
}
