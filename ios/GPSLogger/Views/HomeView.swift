import SwiftUI
import CoreLocation

/// 記録開始画面
struct HomeView: View {
    @EnvironmentObject private var locationService: LocationService
    @EnvironmentObject private var repo: LocationRepository
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject private var router: Router

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                elapsedSection
                    .padding(.top, 24)

                if let point = locationService.latestPoint {
                    LatestPointCard(point: point)
                }

                permissionSection

                // 記録モードと精度・消費電力の目安。タップで切り替える
                RecordModeCard(
                    settings: settingsStore.settings,
                    method: locationService.status?.method,
                    onTap: { settingsStore.settings.recordMode = settingsStore.settings.recordMode.other }
                )

                recordButton
                    .padding(.bottom, 24)
            }
            .padding(.horizontal)
        }
        .navigationTitle("GPSLogger")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { router.path.append(.sessions) } label: {
                    Image(systemName: "list.bullet")
                }
                .accessibilityLabel("セッション一覧")
                Button { router.path.append(.settings) } label: {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel("設定")
            }
        }
    }

    // MARK: - 経過時間

    @ViewBuilder
    private var elapsedSection: some View {
        if let status = locationService.status, let session = repo.session(id: status.sessionId) {
            VStack(spacing: 4) {
                // アプリを開き直しても記録開始の時刻から計算する
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text(formatElapsed(context.date.timeIntervalSince(session.startTime)))
                        .font(.system(size: 56, weight: .bold, design: .monospaced))
                        .foregroundStyle(Color.recording)
                }
                Text("\(session.pointCount)ポイント")
                    .foregroundStyle(.secondary)
            }
        } else {
            Text("記録を開始してください")
                .font(.headline)
                .foregroundStyle(.secondary)
                .padding(.vertical, 24)
        }
    }

    private func formatElapsed(_ interval: TimeInterval) -> String {
        let s = max(Int(interval), 0)
        return String(format: "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    // MARK: - 位置情報の許可

    @ViewBuilder
    private var permissionSection: some View {
        switch locationService.authorizationStatus {
        case .denied, .restricted:
            NoticeCard(
                text: "位置情報が許可されていないため記録できません。設定アプリで GPSLogger の位置情報を許可してください。",
                buttonTitle: "設定を開く",
                isWarning: true,
                action: openAppSettings
            )
        case .authorizedWhenInUse:
            NoticeCard(
                text: "位置情報を「常に」許可すると、アプリがシステムに終了されても、移動したときに記録を再開できます。" +
                    "「使用中のみ」でも、記録中はバックグラウンドで記録を続けます。",
                buttonTitle: "設定を開く",
                isWarning: false,
                action: openAppSettings
            )
        default:
            EmptyView()
        }
    }

    private func openAppSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    // MARK: - 記録開始・停止ボタン

    @ViewBuilder
    private var recordButton: some View {
        switch locationService.authorizationStatus {
        case .notDetermined:
            Button("GPS権限を許可する") { locationService.requestPermission() }
                .buttonStyle(.borderedProminent)
        case .denied, .restricted:
            EmptyView()
        default:
            let recording = locationService.isRecording
            Button {
                if recording { locationService.stopRecording() } else { locationService.startRecording() }
            } label: {
                Text(recording ? "停止" : "記録開始")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                    .frame(width: 140, height: 140)
                    .background(recording ? Color(red: 0xC6 / 255, green: 0x28 / 255, blue: 0x28 / 255) : Color.recording)
                    .clipShape(Circle())
            }
        }
    }
}

/// 最新ポイントの情報
private struct LatestPointCard: View {
    let point: LogPoint

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("最新ポイント", systemImage: "mappin.circle.fill")
                .fontWeight(.bold)
            Divider()
            Text("緯度: \(String(format: "%.6f", point.latitude))")
            Text("経度: \(String(format: "%.6f", point.longitude))")
            Text("精度: \(String(format: "%.1f", point.accuracy))m")
            if let speed = point.speed {
                Text("速度: \(String(format: "%.1f", speed * 3.6)) km/h")
            }
            Text(point.sourceType == .gps ? "GPS測位" : "WiFi測位")
                .foregroundStyle(point.sourceType == .gps ? Color.blue : Color.orange)
                .fontWeight(.semibold)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}

/// 記録モードの説明カード。
/// 記録中は実際の記録手段（自動切替を含む）、停止中は設定中のモードを表示する。
/// タップすると記録モードを切り替える（記録中もすぐ反映される）。
private struct RecordModeCard: View {
    let settings: AppSettings
    let method: RecordMethod?
    let onTap: () -> Void

    var body: some View {
        let mode = method?.mode ?? settings.recordMode
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(method.map { "記録手段: \($0.label)" } ?? "記録モード: \(mode.label)")
                        .fontWeight(.bold)
                    Spacer()
                    Image(systemName: "arrow.left.arrow.right")
                        .foregroundStyle(.tint)
                        .accessibilityLabel("記録モードを切り替える")
                }
                Text("精度の目安: \(mode.accuracyText)")
                Text("消費電力の目安: \(mode.powerText)")
                Text(mode.description)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if settings.recordMode == .standard && settings.autoLowPower {
                    Text("バッテリー残量が\(settings.lowBatteryPercent)%以下（充電中を除く）か低電力モードのときは、低消費電力に自動で切り替えます。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Text("タップで「\(settings.recordMode.other.label)」に切り替え")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.tint)
            }
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

/// 説明とボタンのカード（位置情報の許可の案内など）
private struct NoticeCard: View {
    let text: String
    let buttonTitle: String
    let isWarning: Bool
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(text)
                .font(.callout)
            Button(buttonTitle, action: action)
                .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(
            (isWarning ? Color.red : Color.orange).opacity(0.12),
            in: RoundedRectangle(cornerRadius: 12)
        )
    }
}
