import Foundation

/// 測位手段（精度から推定する。20m未満をGPSとみなす）
enum SourceType: String, Codable {
    case gps = "GPS"
    case wifi = "WIFI"
}

/// GPS/WiFiで取得した1件の位置情報ログ
struct LogPoint: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var sessionId: UUID
    /// 測位した時刻
    var timestamp: Date
    var latitude: Double
    var longitude: Double
    var altitude: Double? = nil
    /// 水平精度（メートル）
    var accuracy: Double
    /// 速度（m/s）
    var speed: Double? = nil
    var sourceType: SourceType = .gps
    var recordMethod: RecordMethod = .standard
}
