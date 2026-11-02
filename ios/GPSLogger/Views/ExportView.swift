import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// エクスポート画面。ファイル名を決めて、保存先を選んで保存・共有・このiPhone内に保存ができる
struct ExportView: View {
    @StateObject private var vm: ExportViewModel

    init(session: Session, repo: LocationRepository) {
        _vm = StateObject(wrappedValue: ExportViewModel(session: session, repo: repo))
    }

    var body: some View {
        Form {
            Section {
                Text(vm.session.name).font(.headline)
                TextField("ファイル名", text: $vm.fileName)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            } footer: {
                Text("保存されるファイル名: \(vm.normalizedFileName)")
            }

            Section {
                Button {
                    Task { await vm.prepareDocument() }
                } label: {
                    Label("保存先を選んで保存", systemImage: "folder")
                }
                .disabled(vm.isBusy)
            } footer: {
                Text("保存先の画面で、iCloud Drive・このiPhone内のほか、Google Drive や OneDrive も選べます" +
                     "（各アプリのインストールとサインインが必要。表示されないときは「ブラウズ」の「…」→「編集」でオンにします）。")
            }

            Section {
                Button {
                    Task { await vm.prepareShare() }
                } label: {
                    Label("共有して保存（OneDrive など）", systemImage: "square.and.arrow.up")
                }
                .disabled(vm.isBusy)
            } footer: {
                Text("共有先で OneDrive などのアプリを選ぶと、そのアプリで保存先フォルダを選べます。メールや AirDrop でも送れます。")
            }

            Section {
                Button {
                    Task { await vm.saveToDocuments() }
                } label: {
                    Label("このiPhone内に保存", systemImage: "iphone")
                }
                .disabled(vm.isBusy)
            } footer: {
                Text("ファイルアプリの「このiPhone内 > GPSLogger」に保存します。")
            }

            stateSection
        }
        .navigationTitle("エクスポート")
        .navigationBarTitleDisplayMode(.inline)
        // 保存先ピッカー。保存せずに閉じたら（キャンセル）「取り消しました」と表示する。
        // 保存したときは onCompletion で成功の表示に変わる
        .fileExporter(
            isPresented: Binding(
                get: { vm.document != nil },
                set: { if !$0 && vm.document != nil { vm.documentCancelled() } }
            ),
            document: vm.document,
            contentType: .commaSeparatedText,
            defaultFilename: vm.baseFileName,
            onCompletion: { vm.documentSaved($0) }
        )
        .sheet(item: $vm.shareItem) { item in
            ActivityView(url: item.url) { completed, error in
                vm.shareFinished(completed: completed, error: error)
            }
            .presentationDetents([.medium, .large])
        }
    }

    @ViewBuilder
    private var stateSection: some View {
        switch vm.state {
        case .idle:
            EmptyView()
        case .loading:
            Section { HStack { ProgressView(); Text("準備中...") } }
        case .success(let message):
            Section { Label(message, systemImage: "checkmark.circle").foregroundStyle(.green) }
        case .info(let message):
            Section { Label(message, systemImage: "info.circle").foregroundStyle(.secondary) }
        case .error(let message):
            Section { Label(message, systemImage: "exclamationmark.triangle").foregroundStyle(.red) }
        }
    }
}

/// iOS の共有シート（OneDrive・メール・AirDrop・ファイルに保存 など）
private struct ActivityView: UIViewControllerRepresentable {
    let url: URL
    let onComplete: (_ completed: Bool, _ error: Error?) -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        controller.completionWithItemsHandler = { _, completed, _, error in
            onComplete(completed, error)
        }
        return controller
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
