import SwiftUI
import UniformTypeIdentifiers

/// セッション一覧。各行のボタンで地図・CSV表示・エクスポート・削除、右上のボタンでCSVの読み込み
struct SessionListView: View {
    @EnvironmentObject private var repo: LocationRepository
    @EnvironmentObject private var router: Router
    @StateObject private var vm = SessionListViewModel()
    @State private var deleteTarget: Session?
    @State private var showImporter = false

    var body: some View {
        List {
            if repo.sessions.isEmpty {
                ContentUnavailableView(
                    "記録がありません",
                    systemImage: "mappin.slash",
                    description: Text("右上のボタンからCSVを読み込めます")
                )
            } else {
                ForEach(repo.sessions) { session in
                    SessionRow(
                        session: session,
                        onMap: { router.path.append(.map(session.id)) },
                        onCsv: { router.path.append(.csv(session.id)) },
                        onExport: { router.path.append(.export(session.id)) },
                        onDelete: { deleteTarget = session }
                    )
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { deleteTarget = session } label: {
                            Label("削除", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .navigationTitle("セッション一覧")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if vm.importing {
                    ProgressView()
                } else {
                    Button { showImporter = true } label: {
                        Image(systemName: "square.and.arrow.down")
                    }
                    .accessibilityLabel("CSVを読み込む")
                }
            }
        }
        // CSVの種類は保存元のアプリによって text/csv 以外で届くことがあるので広めに受け付ける
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.commaSeparatedText, .plainText, .text, .data]
        ) { result in
            switch result {
            case .success(let url): vm.importCsv(from: url, repo: repo)
            case .failure(let error): vm.message = "読み込みに失敗しました: \(error.localizedDescription)"
            }
        }
        // 削除確認ダイアログ
        .alert("セッションを削除", isPresented: Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        ), presenting: deleteTarget) { target in
            Button("削除", role: .destructive) { repo.deleteSession(id: target.id) }
            Button("キャンセル", role: .cancel) {}
        } message: { target in
            Text("「\(target.name)」を削除しますか？この操作は元に戻せません。")
        }
        .alert("CSVの読み込み", isPresented: Binding(
            get: { vm.message != nil },
            set: { if !$0 { vm.message = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(vm.message ?? "")
        }
    }
}

private struct SessionRow: View {
    let session: Session
    let onMap: () -> Void
    let onCsv: () -> Void
    let onExport: () -> Void
    let onDelete: () -> Void

    private static let fmt: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ja_JP")
        f.dateFormat = "yyyy/MM/dd HH:mm"
        return f
    }()

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(session.name).fontWeight(.semibold)
                Text(SessionRow.fmt.string(from: session.startTime))
                    .font(.caption).foregroundStyle(.secondary)
                Text("\(session.pointCount)ポイント · \(session.durationFormatted)")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            // List の行の中で個別に押せるように borderless にする
            Group {
                Button(action: onMap) { Image(systemName: "map") }
                    .accessibilityLabel("地図を表示")
                Button(action: onCsv) { Image(systemName: "tablecells") }
                    .accessibilityLabel("CSVを表示")
                    .tint(Color.primary)
                Button(action: onExport) { Image(systemName: "square.and.arrow.up") }
                    .accessibilityLabel("エクスポート")
                    .tint(Color.primary)
                Button(action: onDelete) { Image(systemName: "trash") }
                    .accessibilityLabel("削除")
                    .tint(Color.red)
            }
            .buttonStyle(.borderless)
            .font(.title3)
        }
        .padding(.vertical, 4)
    }
}
