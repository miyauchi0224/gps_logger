import SwiftUI
import CoreLocation

/// 設定画面。「保存して戻る」で保存する（記録中でもすぐ反映される）
struct SettingsView: View {
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject private var locationService: LocationService
    @Environment(\.dismiss) private var dismiss

    @State private var draft: AppSettings
    @State private var intervalText: String

    init(initial: AppSettings) {
        _draft = State(initialValue: initial)
        _intervalText = State(initialValue: String(initial.intervalSec))
    }

    private var interval: Int? {
        Int(intervalText).flatMap { AppSettings.intervalRange.contains($0) ? $0 : nil }
    }

    var body: some View {
        Form {
            Section {
                TextField("秒数（デフォルト: 1）", text: $intervalText)
                    .keyboardType(.numberPad)
                    .onChange(of: intervalText) { _, value in
                        let digits = value.filter(\.isNumber)
                        if digits != value { intervalText = digits }
                    }
            } header: {
                Text("記録間隔（秒）")
            } footer: {
                Text("1〜3600の間で設定してください。短いほど電池を使います")
                    .foregroundStyle(interval == nil ? Color.red : Color.secondary)
            }

            Section("記録モード") {
                Picker("記録モード", selection: $draft.recordMode) {
                    ForEach(RecordMode.allCases) { mode in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(mode.label)
                            Text("精度: \(mode.accuracyText)\n消費電力: \(mode.powerText)\n\(mode.description)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .tag(mode)
                    }
                }
                .pickerStyle(.inline)
                .labelsHidden()

                if draft.recordMode == .standard {
                    Toggle("バッテリーが少ないときは低消費電力に切り替える", isOn: $draft.autoLowPower)
                    if draft.autoLowPower {
                        VStack(alignment: .leading) {
                            Text("切り替える残量: \(draft.lowBatteryPercent)%以下（低電力モード中も切り替え）")
                            Slider(
                                value: Binding(
                                    get: { Double(draft.lowBatteryPercent) },
                                    set: { draft.lowBatteryPercent = Int($0.rounded()) }
                                ),
                                in: Double(AppSettings.lowBatteryRange.lowerBound)...Double(AppSettings.lowBatteryRange.upperBound),
                                step: 5
                            )
                        }
                    }
                }
            }

            Section {
                Text("記録中は、アプリの画面上部に緑の帯を表示します。")
                Text("アプリを閉じていても、記録中はステータスバーに位置情報の使用中表示（青い帯やアイコン）が出ます。")
            } header: {
                Text("記録中の表示")
            }

            Section("デフォルト地図") {
                Picker("地図", selection: $draft.defaultLayer) {
                    ForEach(MapLayer.allCases) { layer in
                        Text(layer.displayName).tag(layer)
                    }
                }
            }

            Section {
                Toggle("交通状況", isOn: $draft.appleTraffic)
                Toggle("3D（建物・地形の立体表示）", isOn: $draft.apple3D)
            } header: {
                Text("Apple マップ")
            } footer: {
                Text("Apple マップは APIキー不要で使えます。")
            }

            Section("位置情報の許可") {
                HStack {
                    Text(authorizationText)
                    Spacer()
                    Button("設定を開く") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .buttonStyle(.borderless)
                }
            }

            Section {
                Button("保存して戻る") {
                    var saved = draft
                    saved.intervalSec = interval ?? settingsStore.settings.intervalSec
                    settingsStore.settings = saved.clamped()
                    dismiss()
                }
                .disabled(interval == nil)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("設定")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var authorizationText: String {
        switch locationService.authorizationStatus {
        case .authorizedAlways: return "常に許可"
        case .authorizedWhenInUse: return "使用中のみ許可"
        case .denied, .restricted: return "許可されていません"
        default: return "未設定"
        }
    }
}
