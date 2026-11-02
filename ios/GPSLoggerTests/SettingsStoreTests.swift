import XCTest
@testable import GPSLogger
final class SettingsStoreTests: XCTestCase {
    func test保存した設定を読み込める_古いデータは既定値で補う() throws {
        let d = UserDefaults(suiteName: UUID().uuidString)!
        let store = SettingsStore(defaults: d)
        store.settings.recordMode = .lowPower
        store.settings.intervalSec = 5
        XCTAssertEqual(SettingsStore(defaults: d).settings.recordMode, .lowPower)
        XCTAssertEqual(SettingsStore(defaults: d).settings.intervalSec, 5)
        d.set(Data(#"{"intervalSec":99999,"recordMode":"LOW_POWER"}"#.utf8), forKey: "app_settings")
        let old = SettingsStore(defaults: d).settings
        XCTAssertEqual(old.intervalSec, 3600)
        XCTAssertEqual(old.autoLowPower, true)
        XCTAssertEqual(old.defaultLayer, .osmStandard)
    }
}
