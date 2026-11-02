import Foundation
import SwiftUI
import UniformTypeIdentifiers

/// エクスポートの状態
enum ExportState: Equatable {
    case idle
    case loading
    case success(String)
    case info(String)
    case error(String)
}

/// 保存先ピッカー（ファイルアプリの画面）に渡すCSVファイル
struct CsvDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.commaSeparatedText] }

    var text: String

    init(text: String) {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.text = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

/// 共有シートに渡すファイル
struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
}

/**
 * ExportView用のViewModel。
 * - 保存先を選んで保存: ファイルアプリの保存画面（iCloud Drive・このiPhone内・Google Drive・OneDrive など）
 * - 共有して保存: 共有シート（OneDrive・メール・AirDrop など）
 * - このiPhone内に保存: アプリの書類フォルダ（ファイルアプリの「このiPhone内 > GPSLogger」）
 */
final class ExportViewModel: ObservableObject {

    let session: Session

    @Published var fileName: String
    @Published private(set) var state: ExportState = .idle
    /// 保存先ピッカーに渡すファイル。nil 以外になったらピッカーを開く
    @Published var document: CsvDocument?
    @Published var shareItem: ShareItem?

    private let repo: LocationRepository

    init(session: Session, repo: LocationRepository) {
        self.session = session
        self.repo = repo
        self.fileName = CsvExporter.fileName(sessionName: session.name)
    }

    var normalizedFileName: String { CsvExporter.normalizeFileName(fileName) }

    /// 拡張子を除いた名前（保存先ピッカーが拡張子を付ける）
    var baseFileName: String { String(normalizedFileName.dropLast(4)) }

    var isBusy: Bool { state == .loading }

    // MARK: - 保存先を選んで保存

    @MainActor
    func prepareDocument() async {
        state = .loading
        document = CsvDocument(text: await repo.exportCsv(sessionId: session.id))
    }

    func documentSaved(_ result: Result<URL, Error>) {
        document = nil
        switch result {
        case .success(let url):
            state = .success("\(url.lastPathComponent) を保存しました（クラウドへの送信は保存先のアプリが行います）")
        case .failure(let error):
            state = .error("保存に失敗しました: \(error.localizedDescription)")
        }
    }

    func documentCancelled() {
        document = nil
        state = .info("保存を取り消しました")
    }

    // MARK: - 共有して保存

    @MainActor
    func prepareShare() async {
        state = .loading
        do {
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let url = dir.appendingPathComponent(normalizedFileName)
            try await repo.exportCsv(sessionId: session.id).write(to: url, atomically: true, encoding: .utf8)
            shareItem = ShareItem(url: url)
        } catch {
            state = .error("共有の準備に失敗しました: \(error.localizedDescription)")
        }
    }

    func shareFinished(completed: Bool, error: Error?) {
        shareItem = nil
        if let error {
            state = .error("共有に失敗しました: \(error.localizedDescription)")
        } else if completed {
            state = .success("共有しました")
        } else {
            state = .info("共有を取り消しました")
        }
    }

    // MARK: - このiPhone内に保存

    @MainActor
    func saveToDocuments() async {
        state = .loading
        do {
            let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let url = ExportViewModel.uniqueURL(in: dir, fileName: normalizedFileName)
            try await repo.exportCsv(sessionId: session.id).write(to: url, atomically: true, encoding: .utf8)
            state = .success("ファイルアプリの「このiPhone内 > GPSLogger」に \(url.lastPathComponent) を保存しました")
        } catch {
            state = .error("エクスポートに失敗しました: \(error.localizedDescription)")
        }
    }

    /// 同じ名前のファイルがあれば「名前 (1).csv」のように番号を付ける
    private static func uniqueURL(in dir: URL, fileName: String) -> URL {
        let base = (fileName as NSString).deletingPathExtension
        let ext = (fileName as NSString).pathExtension
        var url = dir.appendingPathComponent(fileName)
        var n = 1
        while FileManager.default.fileExists(atPath: url.path) {
            url = dir.appendingPathComponent("\(base) (\(n)).\(ext)")
            n += 1
        }
        return url
    }
}
