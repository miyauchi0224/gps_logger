import SwiftUI

/// 等幅フォント12ptの1文字あたりの幅の目安（列幅の計算用）
private let charWidth: CGFloat = 7.5
private let cellPadding: CGFloat = 8
private let rowNumberWidth: CGFloat = 56

/**
 * セッションのデータをCSVの表として表示する画面。
 * 見出し行は固定し、縦横にスクロールすると全列が見られる。文字は長押しで選択・コピーできる。
 */
struct CsvViewerView: View {
    @EnvironmentObject private var router: Router
    @StateObject private var vm: CsvViewerViewModel

    init(session: Session, repo: LocationRepository) {
        _vm = StateObject(wrappedValue: CsvViewerViewModel(session: session, repo: repo))
    }

    var body: some View {
        Group {
            if let table = vm.table {
                if table.rows.isEmpty {
                    ContentUnavailableView("記録データがありません", systemImage: "tablecells")
                } else {
                    CsvTableView(table: table)
                }
            } else {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(vm.session.name).font(.headline).lineLimit(1)
                    if let table = vm.table {
                        Text("CSV・\(table.rows.count)行")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { router.path.append(.export(vm.session.id)) } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .accessibilityLabel("エクスポート")
            }
        }
        .task { await vm.load() }
    }
}

private struct CsvTableView: View {
    let table: CsvTable

    var body: some View {
        let widths = table.widths.map { CGFloat($0) * charWidth + cellPadding * 2 }
        // 縦横どちらにもスクロールする。見出しは上に固定し、行は見えている分だけ描画する
        ScrollView([.horizontal, .vertical]) {
            LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                Section {
                    ForEach(table.rows.indices, id: \.self) { index in
                        CsvRowView(number: "\(index + 1)", cells: table.rows[index], widths: widths)
                            // 1行おきに色を変えて読みやすくする
                            .background(index % 2 == 1 ? Color(.secondarySystemBackground) : Color(.systemBackground))
                    }
                } header: {
                    VStack(spacing: 0) {
                        CsvRowView(number: "#", cells: table.header, widths: widths, bold: true)
                        Divider()
                    }
                    .background(Color(.tertiarySystemBackground))
                }
            }
        }
        .textSelection(.enabled)
    }
}

private struct CsvRowView: View {
    let number: String
    let cells: [String]
    let widths: [CGFloat]
    var bold = false

    var body: some View {
        HStack(spacing: 0) {
            Text(number)
                .foregroundStyle(.secondary)
                .frame(width: rowNumberWidth, alignment: .leading)
                .padding(.leading, cellPadding)
            ForEach(widths.indices, id: \.self) { i in
                Text(i < cells.count ? cells[i] : "")
                    .frame(width: widths[i], alignment: .leading)
            }
        }
        .font(.system(size: 12, weight: bold ? .bold : .regular, design: .monospaced))
        .lineLimit(1)
        .padding(.vertical, 6)
    }
}
