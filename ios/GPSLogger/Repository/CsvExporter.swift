import Foundation

/// ログポイントをCSVにする。Android版と同じ列・同じ書式。
enum CsvExporter {

    // source: 精度から推定した測位手段（GPS/WIFI）、method: 記録モード（RecordMethod）
    static let header = "id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method"

    static func csv(_ points: [LogPoint], timeZone: TimeZone = .current) -> String {
        // XXX は時差（日本なら +09:00）。読み込み時にこの時差で正しい時刻に戻せる
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.timeZone = timeZone
        fmt.dateFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"

        let rows = points.map { p in
            [
                p.id.uuidString.lowercased(),
                fmt.string(from: p.timestamp),
                "\(p.latitude)",
                "\(p.longitude)",
                p.altitude.map { "\($0)" } ?? "",
                "\(p.accuracy)",
                p.speed.map { "\($0)" } ?? "",
                p.sourceType.rawValue,
                p.recordMethod.rawValue
            ].joined(separator: ",")
        }
        return ([header] + rows).joined(separator: "\n")
    }

    /// 入力されたファイル名を整える。使えない文字は _ にし、拡張子 .csv を付ける
    static func normalizeFileName(_ input: String) -> String {
        var base = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if base.hasSuffix(".csv") { base = String(base.dropLast(4)) }
        let invalid = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: "/\\:*?\"<>|"))
        base = String(base.unicodeScalars.map { invalid.contains($0) ? "_" : Character($0) })
        return "\(base.isEmpty ? "gpslog" : base).csv"
    }

    static func fileName(sessionName: String) -> String {
        normalizeFileName("gpslog_\(sessionName)")
    }
}
