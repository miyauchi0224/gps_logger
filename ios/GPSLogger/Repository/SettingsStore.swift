import Foundation
import Combine

/// アプリの設定値
struct AppSettings: Codable, Equatable {
    var intervalSec: Int = 1
    var recordMode: RecordMode = .standard
    // 標準モードでも、バッテリー残量が少ないときや省電力モード中は低消費電力に切り替える
    var autoLowPower: Bool = true
    var lowBatteryPercent: Int = 20
    var defaultLayer: MapLayer = .defaultLayer
    // Apple マップ用
    var appleTraffic: Bool = false
    var apple3D: Bool = false

    static let intervalRange = 1...3600
    static let lowBatteryRange = 5...80

    init() {}

    // 項目が増えても古い保存データを読めるように、無い項目は既定値にする
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = AppSettings()
        intervalSec = (try? c.decode(Int.self, forKey: .intervalSec)) ?? d.intervalSec
        recordMode = (try? c.decode(RecordMode.self, forKey: .recordMode)) ?? d.recordMode
        autoLowPower = (try? c.decode(Bool.self, forKey: .autoLowPower)) ?? d.autoLowPower
        lowBatteryPercent = (try? c.decode(Int.self, forKey: .lowBatteryPercent)) ?? d.lowBatteryPercent
        defaultLayer = (try? c.decode(MapLayer.self, forKey: .defaultLayer)) ?? d.defaultLayer
        appleTraffic = (try? c.decode(Bool.self, forKey: .appleTraffic)) ?? d.appleTraffic
        apple3D = (try? c.decode(Bool.self, forKey: .apple3D)) ?? d.apple3D
    }

    /// 範囲外の値を範囲内に収める
    func clamped() -> AppSettings {
        var s = self
        s.intervalSec = min(max(intervalSec, AppSettings.intervalRange.lowerBound), AppSettings.intervalRange.upperBound)
        s.lowBatteryPercent = min(max(lowBatteryPercent, AppSettings.lowBatteryRange.lowerBound),
                                  AppSettings.lowBatteryRange.upperBound)
        return s
    }
}

/// 設定をUserDefaultsに保存・読み込みする
final class SettingsStore: ObservableObject {

    /// 変更するとすぐ保存される。記録中でも LocationService がすぐ反映する
    @Published var settings: AppSettings {
        didSet {
            if let data = try? JSONEncoder().encode(settings) {
                defaults.set(data, forKey: key)
            }
        }
    }

    private let defaults: UserDefaults
    private let key = "app_settings"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: key),
           let saved = try? JSONDecoder().decode(AppSettings.self, from: data) {
            settings = saved.clamped()
        } else {
            settings = AppSettings()
        }
    }
}
