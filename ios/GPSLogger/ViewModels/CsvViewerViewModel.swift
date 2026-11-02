import Foundation

/// CSVを表として表示するためのデータ。widths は列ごとの最大文字数（列幅の計算用）
struct CsvTable {
    let header: [String]
    let rows: [[String]]
    let widths: [Int]

    /// エクスポートと同じCSVを列に分ける
    init(csv: String) {
        let lines = csv.split(whereSeparator: \.isNewline).map { CsvImporter.splitLine(String($0)) }
        // init の途中では self をクロージャで使えないので、ローカル変数で計算してから入れる
        let header = lines.first ?? []
        let rows = Array(lines.dropFirst())
        self.header = header
        self.rows = rows
        widths = header.indices.map { col in
            rows.reduce(header[col].count) { width, row in
                col < row.count ? max(width, row[col].count) : width
            }
        }
    }
}

/**
 * CsvViewerView用のViewModel。
 * エクスポートと同じCSVを作って列に分け、そのまま表として見せる。
 */
final class CsvViewerViewModel: ObservableObject {

    let session: Session

    /// 読み込み完了までは nil
    @Published private(set) var table: CsvTable?

    private let repo: LocationRepository

    init(session: Session, repo: LocationRepository) {
        self.session = session
        self.repo = repo
    }

    @MainActor
    func load() async {
        let csv = await repo.exportCsv(sessionId: session.id)
        // 長い記録では分割に時間がかかるので、メインスレッドの外で行う
        table = await Task.detached(priority: .userInitiated) { CsvTable(csv: csv) }.value
    }
}
