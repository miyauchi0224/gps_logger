import SwiftUI

/// 画面の行き先
enum Route: Hashable {
    case sessions
    case settings
    case map(UUID)
    case csv(UUID)
    case export(UUID)
}

/// 画面遷移の状態。どの画面からでも行き先を積めるように共有する
final class Router: ObservableObject {
    @Published var path: [Route] = []
}

/// アプリの一番外側の画面。記録中はどの画面でも上部に緑の帯を出す
struct RootView: View {
    @EnvironmentObject private var locationService: LocationService
    @EnvironmentObject private var repo: LocationRepository
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject private var router: Router

    var body: some View {
        VStack(spacing: 0) {
            if let status = locationService.status {
                RecordingBanner(method: status.method)
            }
            NavigationStack(path: $router.path) {
                HomeView()
                    .navigationDestination(for: Route.self) { route in
                        destination(route)
                    }
            }
        }
        .animation(.default, value: locationService.status)
    }

    @ViewBuilder
    private func destination(_ route: Route) -> some View {
        switch route {
        case .sessions:
            SessionListView()
        case .settings:
            SettingsView(initial: settingsStore.settings)
        case .map(let id):
            if let session = repo.session(id: id) {
                MapView(session: session, repo: repo, settings: settingsStore.settings)
            } else {
                MissingSessionView()
            }
        case .csv(let id):
            if let session = repo.session(id: id) {
                CsvViewerView(session: session, repo: repo)
            } else {
                MissingSessionView()
            }
        case .export(let id):
            if let session = repo.session(id: id) {
                ExportView(session: session, repo: repo)
            } else {
                MissingSessionView()
            }
        }
    }
}

private struct MissingSessionView: View {
    var body: some View {
        ContentUnavailableView("セッションが見つかりません", systemImage: "questionmark.folder")
    }
}

extension Color {
    /// 記録中の表示に使う緑（Android版と同じ色）
    static let recording = Color(red: 0x2E / 255, green: 0x7D / 255, blue: 0x32 / 255)
}

/// 記録中、画面上部（ステータスバーの裏まで）に出す緑の帯
struct RecordingBanner: View {
    let method: RecordMethod

    var body: some View {
        Text("● 記録中・\(method.label)・アプリを閉じても記録を続けます")
            .font(.footnote.bold())
            .foregroundStyle(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .padding(.horizontal, 12)
            .background(Color.recording.ignoresSafeArea(edges: .top))
    }
}
