import Foundation

/// 記録モード（設定画面や記録画面で選ぶ）。
/// accuracyText / powerText は画面に出す目安。実際の値は端末・電波環境・記録間隔で大きく変わる。
enum RecordMode: String, Codable, CaseIterable, Identifiable {
    case standard = "STANDARD"
    case lowPower = "LOW_POWER"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .standard: return "標準（GPS）"
        case .lowPower: return "低消費電力（WiFi・基地局）"
        }
    }

    var accuracyText: String {
        switch self {
        case .standard: return "約5〜20m（屋外）"
        case .lowPower: return "約40〜100m（市街地ほど正確）"
        }
    }

    var powerText: String {
        switch self {
        case .standard: return "大（1時間でバッテリー約5〜10%）"
        case .lowPower: return "小（1時間でバッテリー約1〜3%）"
        }
    }

    var description: String {
        switch self {
        case .standard: return "GPSで測位します。屋内や地下では精度が落ちます。"
        case .lowPower: return "主にWiFiと携帯電話の基地局で測位します。長時間の記録向けです。"
        }
    }

    /// もう一方のモード（タップで切り替えるときに使う）
    var other: RecordMode { self == .standard ? .lowPower : .standard }
}

/// 各ポイントを実際にどの手段で記録したか（CSVの method 列に出す）
enum RecordMethod: String, Codable, CaseIterable {
    case standard = "STANDARD"
    case lowPower = "LOW_POWER"
    /// 標準モードだったが、バッテリー残量や省電力モードで自動的に低消費電力に切り替えた
    case lowPowerAuto = "LOW_POWER_AUTO"

    var label: String {
        switch self {
        case .standard: return "標準（GPS）"
        case .lowPower: return "低消費電力"
        case .lowPowerAuto: return "低消費電力（自動切替）"
        }
    }

    var mode: RecordMode { self == .standard ? .standard : .lowPower }
}
