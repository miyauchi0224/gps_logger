import Foundation
import CoreLocation
import Combine

/// CoreLocation をラップして位置情報を提供するサービス
/// ObservableObject = SwiftUIのViewが変化を自動検知できるクラス
class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {

    private let manager = CLLocationManager()
    /// 新しいLogPointが届いたときに通知するPublisher
    let locationPublisher = PassthroughSubject<CLLocation, Never>()

    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var currentSourceType: SourceType = .gps

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.allowsBackgroundLocationUpdates = true  // バックグラウンド動作を許可
        manager.pausesLocationUpdatesAutomatically = false
    }

    func requestPermission() {
        manager.requestAlwaysAuthorization()
    }

    func startUpdates(intervalSeconds: Double = 1.0) {
        manager.distanceFilter = kCLDistanceFilterNone
        manager.startUpdatingLocation()
    }

    func stopUpdates() {
        manager.stopUpdatingLocation()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        // 精度が20m未満ならGPS、それ以上はWiFi測位とみなす
        currentSourceType = location.horizontalAccuracy < 20 ? .gps : .wifi
        locationPublisher.send(location)
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("LocationService Error: \(error.localizedDescription)")
    }
}
