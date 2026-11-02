import SwiftUI

@main
struct GPSLoggerApp: App {
    // アプリ全体で共有するオブジェクトをここで生成
    @StateObject private var settingsStore: SettingsStore
    @StateObject private var repo: LocationRepository
    @StateObject private var locationService: LocationService
    @StateObject private var router = Router()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let settingsStore = SettingsStore()
        let repo = LocationRepository()
        let locationService = LocationService(repo: repo, settingsStore: settingsStore)
        // 停止しないまま終了していた記録を再開する。
        // 大きく移動したときにシステムがバックグラウンドで起動した場合もここを通る
        locationService.resumeIfNeeded()

        _settingsStore = StateObject(wrappedValue: settingsStore)
        _repo = StateObject(wrappedValue: repo)
        _locationService = StateObject(wrappedValue: locationService)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settingsStore)
                .environmentObject(repo)
                .environmentObject(locationService)
                .environmentObject(router)
        }
        .onChange(of: scenePhase) { _, phase in
            // 間引いて保存しているセッション一覧（ポイント数など）を書き出しておく
            if phase != .active { repo.flush() }
        }
    }
}
