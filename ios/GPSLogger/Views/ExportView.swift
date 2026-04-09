import SwiftUI

struct ExportView: View {
    let session: Session
    @ObservedObject var repo: LocationRepository
    @State private var exportMessage: String?
    @State private var isExporting = false

    var body: some View {
        VStack(spacing: 20) {
            Text(session.name)
                .font(.headline)
                .padding(.top)

            Text("エクスポート形式を選んでください")
                .foregroundColor(.secondary)

            Divider()

            // CSV保存（iOSの共有シートへ）
            Button(action: exportCsv) {
                Label("CSVとして共有・保存", systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(isExporting)

            // Google Drive（将来実装）
            Button(action: {}) {
                Label("Google Drive にアップロード（近日対応）", systemImage: "arrow.up.to.line")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(true)

            // OneDrive（将来実装）
            Button(action: {}) {
                Label("OneDrive にアップロード（近日対応）", systemImage: "arrow.up.to.line")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(true)

            if let msg = exportMessage {
                Text(msg)
                    .foregroundColor(.green)
                    .multilineTextAlignment(.center)
                    .padding()
            }

            Spacer()
        }
        .padding()
        .navigationTitle("エクスポート")
    }

    private func exportCsv() {
        isExporting = true
        let csv = repo.exportCsv(sessionId: session.id)
        let fileName = "gpslog_\(session.name.replacingOccurrences(of: " ", with: "_")).csv"
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try? csv.write(to: tempURL, atomically: true, encoding: .utf8)

        // iOS の共有シート（AirDrop・Files・メール等）で共有
        let activityVC = UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.windows.first?.rootViewController {
            root.present(activityVC, animated: true)
        }
        isExporting = false
    }
}
