import Foundation

/**
 * 地図表示画面・CSV表示画面用のViewModel。
 * セッションを受け取り、そのセッションのログポイントをRepositoryから読み込んで保持する。
 */
final class MapViewModel: ObservableObject {

    let session: Session

    /// 地図に描画するログポイント（日時順）
    @Published private(set) var logPoints: [LogPoint] = []
    /// 読み込み中は true
    @Published private(set) var isLoading = true

    private let repo: LocationRepository

    init(session: Session, repo: LocationRepository) {
        self.session = session
        self.repo = repo
    }

    @MainActor
    func load() async {
        logPoints = await repo.loadPoints(sessionId: session.id)
        isLoading = false
    }
}
