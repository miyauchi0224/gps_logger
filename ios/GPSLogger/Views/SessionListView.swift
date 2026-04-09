import SwiftUI

struct SessionListView: View {
    @ObservedObject var repo: LocationRepository
    @State private var deleteTarget: Session?

    var body: some View {
        List {
            if repo.sessions.isEmpty {
                ContentUnavailableView("記録がありません", systemImage: "mappin.slash")
            } else {
                ForEach(repo.sessions) { session in
                    SessionRow(session: session)
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                deleteTarget = session
                            } label: {
                                Label("削除", systemImage: "trash")
                            }
                            NavigationLink(destination: ExportView(session: session, repo: repo)) {
                                Label("エクスポート", systemImage: "square.and.arrow.up")
                            }
                            .tint(.blue)
                        }
                }
            }
        }
        .navigationTitle("セッション一覧")
        .alert("削除の確認", isPresented: Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        )) {
            Button("削除", role: .destructive) {
                if let t = deleteTarget { repo.deleteSession(id: t.id) }
            }
            Button("キャンセル", role: .cancel) {}
        } message: {
            Text("「\(deleteTarget?.name ?? "")」を削除しますか？")
        }
    }
}

struct SessionRow: View {
    let session: Session
    private let fmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy/MM/dd HH:mm"
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(session.name).fontWeight(.semibold)
            Text(fmt.string(from: session.startTime))
                .font(.caption).foregroundColor(.secondary)
            Text("\(session.pointCount)ポイント · \(session.durationFormatted)")
                .font(.caption2).foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }
}
