import Foundation
import CoreLocation
import Combine
import UIKit

/// 記録中の状態
struct RecordingStatus: Equatable {
    let sessionId: UUID
    let method: RecordMethod
}

/// CoreLocation で位置を取得し、記録中はそのままRepositoryに保存するサービス。
///
/// - 記録はこのサービスが行うので、画面を閉じたりバックグラウンドに入っても続く
///   （Info.plist の UIBackgroundModes: location と allowsBackgroundLocationUpdates が必要）。
/// - バックグラウンドで記録中は、ステータスバーに位置情報の表示（青い帯・アイコン）が出る。
/// - 停止せずにアプリが終了した記録は、次にアプリが起動したときに再開する。
///   「常に許可」のときは大きく移動するとシステムがアプリを起動するので、そこでも再開する。
/// - 標準モードで記録中も、バッテリー残量が設定値以下（充電中を除く）か
///   低電力モードになったら低消費電力（WiFi・基地局）に自動で切り替える。
///
/// CLLocationManager はメインスレッドで作るので、通知もすべてメインスレッドに届く。
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {

    /// 自動切替から標準に戻すときの余裕（残量が閾値付近で行き来しないように）
    private static let batteryHysteresis = 5

    /// 記録中の状態（記録手段を含む）。記録していないときは nil
    @Published private(set) var status: RecordingStatus?
    @Published private(set) var latestPoint: LogPoint?
    @Published private(set) var authorizationStatus: CLAuthorizationStatus

    private let manager: CLLocationManager
    private let repo: LocationRepository
    private let settingsStore: SettingsStore
    private var settings: AppSettings
    private var sessionId: UUID?
    private var method: RecordMethod?
    private var lastSavedTime: Date?
    private var cancellables = Set<AnyCancellable>()

    init(repo: LocationRepository, settingsStore: SettingsStore) {
        self.repo = repo
        self.settingsStore = settingsStore
        self.settings = settingsStore.settings
        let manager = CLLocationManager()
        self.manager = manager
        self.authorizationStatus = manager.authorizationStatus
        super.init()

        manager.delegate = self
        manager.activityType = .other
        manager.distanceFilter = kCLDistanceFilterNone
        manager.allowsBackgroundLocationUpdates = true   // バックグラウンド動作を許可
        manager.pausesLocationUpdatesAutomatically = false
        manager.showsBackgroundLocationIndicator = true  // バックグラウンドで記録中だとステータスバーで分かるようにする

        // バッテリー残量・充電状態・低電力モードの変化で記録手段を選び直す
        UIDevice.current.isBatteryMonitoringEnabled = true
        let center = NotificationCenter.default
        Publishers.MergeMany(
            center.publisher(for: UIDevice.batteryLevelDidChangeNotification),
            center.publisher(for: UIDevice.batteryStateDidChangeNotification),
            center.publisher(for: .NSProcessInfoPowerStateDidChange)
        )
        .receive(on: DispatchQueue.main)
        .sink { [weak self] _ in self?.applyLocationRequest() }
        .store(in: &cancellables)

        // 記録モードなどの設定は、記録中に変えてもすぐ反映する
        settingsStore.$settings
            .dropFirst()
            .sink { [weak self] s in
                self?.settings = s
                self?.applyLocationRequest()
            }
            .store(in: &cancellables)
    }

    var isRecording: Bool { status != nil }

    func requestPermission() {
        manager.requestAlwaysAuthorization()
    }

    func startRecording() {
        guard sessionId == nil else { return }
        begin(repo.startSession())
    }

    /// 停止しないまま終了していた記録があれば再開する
    func resumeIfNeeded() {
        guard sessionId == nil, let session = repo.incompleteSession() else { return }
        begin(session)
    }

    func stopRecording() {
        guard let sid = sessionId else { return }
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        sessionId = nil
        method = nil
        status = nil
        repo.stopSession(id: sid)
    }

    private func begin(_ session: Session) {
        sessionId = session.id
        method = nil
        lastSavedTime = nil
        latestPoint = nil
        settings = settingsStore.settings
        applyLocationRequest()
        manager.startUpdatingLocation()
        // 「常に許可」なら、システムにアプリが終了されても大きく移動したときに起動され、記録を再開できる
        if manager.authorizationStatus == .authorizedAlways {
            manager.startMonitoringSignificantLocationChanges()
        }
    }

    private func chooseMethod() -> RecordMethod {
        if settings.recordMode == .lowPower { return .lowPower }
        if !settings.autoLowPower { return .standard }

        let device = UIDevice.current
        let level = device.batteryLevel  // 不明なときは -1
        let percent = level < 0 ? 100 : Int(level * 100)
        let charging = device.batteryState == .charging || device.batteryState == .full
        let threshold = settings.lowBatteryPercent + (method == .lowPowerAuto ? LocationService.batteryHysteresis : 0)
        let lowBattery = !charging && percent <= threshold
        let powerSave = ProcessInfo.processInfo.isLowPowerModeEnabled
        return (powerSave || lowBattery) ? .lowPowerAuto : .standard
    }

    /// 記録手段が変わったときだけ測位の設定を変える
    private func applyLocationRequest() {
        guard let sid = sessionId else { return }
        let next = chooseMethod()
        guard next != method else { return }
        method = next
        manager.desiredAccuracy = next == .standard ? kCLLocationAccuracyBest : kCLLocationAccuracyHundredMeters
        status = RecordingStatus(sessionId: sid, method: next)
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let sid = sessionId else { return }
        let interval = Double(settings.intervalSec)
        for location in locations where location.horizontalAccuracy >= 0 {
            // 記録間隔より短い間隔で届いた位置は使わない（届く時刻の揺れの分だけ余裕を持たせる）
            if let last = lastSavedTime, location.timestamp.timeIntervalSince(last) < interval - 0.1 { continue }
            lastSavedTime = location.timestamp

            let point = LogPoint(
                sessionId: sid,
                timestamp: location.timestamp,  // 受け取った時刻ではなく測位した時刻
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                altitude: location.verticalAccuracy > 0 ? location.altitude : nil,
                accuracy: location.horizontalAccuracy,
                speed: location.speed >= 0 ? location.speed : nil,
                sourceType: location.horizontalAccuracy < 20 ? .gps : .wifi,
                recordMethod: method ?? .standard
            )
            repo.saveLogPoint(point)
            latestPoint = point
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        if sessionId != nil, manager.authorizationStatus == .authorizedAlways {
            manager.startMonitoringSignificantLocationChanges()
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // 一時的に測位できないだけ（kCLErrorLocationUnknown）なら、そのまま待てば再び届く
        print("LocationService Error: \(error.localizedDescription)")
    }
}
