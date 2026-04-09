import Foundation
import CoreData

enum SourceType: String, Codable {
    case gps = "GPS"
    case wifi = "WIFI"
}

/// GPS/WiFiで取得した1件の位置情報ログ
struct LogPoint: Identifiable, Codable {
    let id: UUID
    let sessionId: UUID
    let timestamp: Date
    let latitude: Double
    let longitude: Double
    let altitude: Double?
    let accuracy: Double
    let speed: Double?
    let sourceType: SourceType

    init(sessionId: UUID, latitude: Double, longitude: Double,
         altitude: Double? = nil, accuracy: Double,
         speed: Double? = nil, sourceType: SourceType = .gps) {
        self.id = UUID()
        self.sessionId = sessionId
        self.timestamp = Date()
        self.latitude = latitude
        self.longitude = longitude
        self.altitude = altitude
        self.accuracy = accuracy
        self.speed = speed
        self.sourceType = sourceType
    }
}
