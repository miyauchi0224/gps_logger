import Foundation

/// SessionListView用のViewModel。CSVの読み込みを行う
final class SessionListViewModel: ObservableObject {

    @Published var importing = false
    /// 読み込みの結果（アラートで表示する）
    @Published var message: String?

    /// CSVを読み込んで新しいセッションにする。ファイル名（拡張子を除く）をセッション名にする
    func importCsv(from url: URL, repo: LocationRepository) {
        importing = true
        Task { @MainActor in
            defer { importing = false }
            do {
                let text = try SessionListViewModel.readText(url)
                let base = url.deletingPathExtension().lastPathComponent
                let name = base.trimmingCharacters(in: .whitespaces).isEmpty ? "読み込んだ記録" : base
                let (session, result) = try await repo.importSession(name: name, csv: text)
                var msg = "「\(session.name)」を読み込みました（\(result.points.count)ポイント"
                if result.skippedRows > 0 { msg += "、読めない\(result.skippedRows)行を除外" }
                message = msg + "）"
            } catch let error as CsvImporter.ImportError {
                message = "読み込めませんでした: \(error.message)"
            } catch {
                message = "読み込みに失敗しました: \(error.localizedDescription)"
            }
        }
    }

    private static func readText(_ url: URL) throws -> String {
        // ファイルアプリなど他の場所のファイルは、読む間だけアクセス許可を得る
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        // Excel で保存した日本語のCSVは Shift_JIS のことがある
        guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .shiftJIS) else {
            throw CsvImporter.ImportError(message: "ファイルの文字コードを読み取れません")
        }
        return text
    }
}
