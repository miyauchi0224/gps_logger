import Foundation

/// 1回の記録セッション（記録開始〜停止の1まとまり）
struct Session: Identifiable, Codable {
    let id: UUID
    var startTime: Date
    var endTime: Date?
    var name: String
    var pointCount: Int
    var fileIndex: Int
    var isCompleted: Bool

    init() {
        self.id = UUID()
        self.startTime = Date()
        self.endTime = nil
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd HH:mm"
        self.name = fmt.string(from: Date())
        self.pointCount = 0
        self.fileIndex = 0
        self.isCompleted = false
    }

    /// 記録時間（秒）
    var durationSeconds: TimeInterval {
        let end = endTime ?? Date()
        return end.timeIntervalSince(startTime)
    }

    /// "HH:MM:SS" 形式
    var durationFormatted: String {
        let s = Int(durationSeconds)
        return String(format: "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}
