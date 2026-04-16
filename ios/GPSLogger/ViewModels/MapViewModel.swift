import Foundation

/**
 * 地図表示画面用のViewModel。
 * セッションを受け取り、そのセッションのログポイントをRepositoryから取得して保持する。
 * ObservableObject: SwiftUIがデータ変化を検知してUIを自動更新するためのプロトコル
 */
class MapViewModel: ObservableObject {

    /// 表示するセッション情報
    let session: Session

    /// 地図に描画するログポイントの配列
    /// @Published: この値が変わるとSwiftUIが自動的に画面を再描画する
    @Published var logPoints: [LogPoint] = []

    private let repo: LocationRepository

    init(session: Session, repo: LocationRepository) {
        self.session = session
        self.repo = repo
        loadPoints()
    }

    /// Repositoryからログポイントを取得する
    private func loadPoints() {
        logPoints = repo.getLogPoints(sessionId: session.id)
    }
}
