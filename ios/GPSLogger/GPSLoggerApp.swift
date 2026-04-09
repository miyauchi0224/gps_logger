import SwiftUI

@main
struct GPSLoggerApp: App {
    // アプリ全体で共有するオブジェクトをここで生成
    @StateObject private var repo = LocationRepository()
    @StateObject private var locationService = LocationService()

    var body: some Scene {
        WindowGroup {
            SplashView(repo: repo, locationService: locationService)
        }
    }
}
