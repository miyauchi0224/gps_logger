import Foundation

/// CSVを読み込んでログポイントに変換する。
///
/// このアプリが出力したCSV（id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method）のほか、
/// 列の並びが違うものや、lat / lon / time など別名の列名のCSVも読める。
/// 必須は 日時・緯度・経度 の3列。読めない行は飛ばして件数だけ数える。
/// Android版の CsvImporter と同じ仕様。
enum CsvImporter {

    struct ImportError: LocalizedError, Equatable {
        let message: String
        var errorDescription: String? { message }
    }

    struct Result {
        let points: [LogPoint]
        let skippedRows: Int
    }

    /// UTF-8 の BOM（U+FEFF）
    static let bom = "\u{FEFF}"

    // 列名の別名（小文字で比較）
    private static let timestampNames: Set<String> = ["timestamp", "time", "datetime", "date_time", "date"]
    private static let latitudeNames: Set<String> = ["latitude", "lat"]
    private static let longitudeNames: Set<String> = ["longitude", "lon", "lng", "long"]
    private static let altitudeNames: Set<String> = ["altitude", "alt", "elevation", "ele"]
    private static let accuracyNames: Set<String> = ["accuracy", "acc", "hacc"]
    private static let speedNames: Set<String> = ["speed"]
    private static let sourceNames: Set<String> = ["source"]
    private static let methodNames: Set<String> = ["method"]

    /// - Parameters:
    ///   - sessionId: 読み込んだポイントを入れるセッション
    ///   - timeZone: 日時に時差（+09:00 など）が書かれていないときに使うタイムゾーン
    static func parse(_ text: String, sessionId: UUID, timeZone: TimeZone = .current) throws -> Result {
        // 先頭のBOM（Excelなどが付ける）を除く
        var body = text
        if body.unicodeScalars.first == "\u{FEFF}" {
            body = String(body.unicodeScalars.dropFirst())
        }
        // "\r\n" も1つの改行として分かれる
        let lines = body.split(whereSeparator: \.isNewline)
            .map(String.init)
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard let headerLine = lines.first else { throw ImportError(message: "ファイルが空です") }

        let header = splitLine(headerLine).map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
        func column(_ names: Set<String>) -> Int? { header.firstIndex { names.contains($0) } }

        guard let timeCol = column(timestampNames),
              let latCol = column(latitudeNames),
              let lonCol = column(longitudeNames) else {
            throw ImportError(message: "日時・緯度・経度の列が見つかりません（1行目に列名が必要です）")
        }
        let altCol = column(altitudeNames)
        let accCol = column(accuracyNames)
        let speedCol = column(speedNames)
        let sourceCol = column(sourceNames)
        let methodCol = column(methodNames)

        var skipped = 0
        var points: [LogPoint] = []
        for line in lines.dropFirst() {
            let cells = splitLine(line)
            func cell(_ index: Int?) -> String? {
                guard let index, index < cells.count else { return nil }
                let value = cells[index].trimmingCharacters(in: .whitespaces)
                return value.isEmpty ? nil : value
            }

            guard let timestamp = cell(timeCol).flatMap({ parseTimestamp($0, timeZone: timeZone) }),
                  let lat = cell(latCol).flatMap(Double.init), (-90.0...90.0).contains(lat),
                  let lon = cell(lonCol).flatMap(Double.init), (-180.0...180.0).contains(lon) else {
                skipped += 1
                continue
            }
            let accuracy = cell(accCol).flatMap(Double.init)
            // 測位方式の列がなければ、記録時と同じく精度から推定する
            let source = cell(sourceCol).flatMap { SourceType(rawValue: $0.uppercased()) }
                ?? ((accuracy ?? 0) >= 20 ? .wifi : .gps)
            points.append(LogPoint(
                sessionId: sessionId,
                timestamp: timestamp,
                latitude: lat,
                longitude: lon,
                altitude: cell(altCol).flatMap(Double.init),
                accuracy: accuracy ?? 0,
                speed: cell(speedCol).flatMap(Double.init),
                sourceType: source,
                recordMethod: cell(methodCol).flatMap { RecordMethod(rawValue: $0.uppercased()) } ?? .standard
            ))
        }
        points.sort { $0.timestamp < $1.timestamp }

        if points.isEmpty { throw ImportError(message: "読み込めるデータ行がありません") }
        return Result(points: points, skippedRows: skipped)
    }

    /// 日時を Date に変換する。読めなければ nil。
    /// 2026-04-09T10:00:00+09:00 / 2026-04-09 10:00:00（timeZone で解釈）/ Unix 秒・ミリ秒 に対応。
    static func parseTimestamp(_ value: String, timeZone: TimeZone) -> Date? {
        if let n = Int64(value) {
            // 10桁程度なら秒、それより大きければミリ秒とみなす
            let ms = n < 100_000_000_000 ? n * 1000 : n
            return Date(timeIntervalSince1970: Double(ms) / 1000)
        }
        let iso = value.replacingOccurrences(of: " ", with: "T")
        for formatter in offsetFormatters {
            if let date = formatter.date(from: iso) { return date }
        }
        for pattern in localPatterns {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = timeZone
            formatter.dateFormat = pattern
            if let date = formatter.date(from: iso) { return date }
        }
        return nil
    }

    /// 時差（+09:00 や Z）付きの日時。小数秒あり・なし
    private static let offsetFormatters: [ISO8601DateFormatter] = {
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return [plain, fractional]
    }()

    /// 時差なしの日時（timeZone で解釈する）
    private static let localPatterns = [
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm"
    ]

    /// CSVの1行を列に分ける。ダブルクォートで囲んだ列（中のカンマや "" を含む）にも対応する
    static func splitLine(_ line: String) -> [String] {
        var cells: [String] = []
        var current = ""
        var quoted = false
        let chars = Array(line)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if quoted && c == "\"" && i + 1 < chars.count && chars[i + 1] == "\"" {
                current.append("\"")
                i += 1
            } else if c == "\"" {
                quoted.toggle()
            } else if c == "," && !quoted {
                cells.append(current)
                current = ""
            } else {
                current.append(c)
            }
            i += 1
        }
        cells.append(current)
        return cells
    }
}
