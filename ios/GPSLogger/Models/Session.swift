import Foundation

/// 1回の記録セッション（記録開始〜停止の1まとまり）
struct Session: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var startTime: Date = Date()
    var endTime: Date? = nil
    var name: String = Session.defaultName()
    var pointCount: Int = 0
    var fileIndex: Int = 0
    var isCompleted: Bool = false

    /// 記録時間（秒）
    var durationSeconds: TimeInterval {
        let end = endTime ?? Date()
        return end.timeIntervalSince(startTime)
    }

    /// "HH:MM:SS" 形式
    var durationFormatted: String {
        let s = max(Int(durationSeconds), 0)
        return String(format: "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    static func defaultName(_ date: Date = Date()) -> String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "ja_JP")
        fmt.dateFormat = "yyyy-MM-dd HH:mm"
        return fmt.string(from: date)
    }
}
