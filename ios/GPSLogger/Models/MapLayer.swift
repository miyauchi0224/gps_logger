import Foundation

/// 地図の提供元。地図画面では上のタブとして並ぶ
enum MapProvider: String, CaseIterable, Identifiable {
    case apple = "Apple"
    case osm = "OSM"
    case gsi = "地理院"
    case other = "その他"

    var id: String { rawValue }
    var label: String { rawValue }
}

/// 選択できる地図の種類。
/// Apple以外はタイル画像のURLを持ち、WKWebView上のLeaflet.jsで表示する。
/// tileUrl の {s} はサブドメインにLeafletが置き換える。
/// tileFilter はタイル画像にかけるCSSフィルタ（モノクロ表示などに使う）。
enum MapLayer: String, Codable, CaseIterable, Identifiable {
    // Apple マップ（MapKit。APIキー不要）
    case appleStandard, appleSatellite, appleHybrid, appleMuted
    // OpenStreetMap系
    case osmStandard, osmHumanitarian, osmTopo
    // 国土地理院
    case gsiStd, gsiPale, gsiBlank, gsiPhoto, gsiRelief
    // その他
    case monoLight, monoDark, esriImagery

    static let defaultLayer: MapLayer = .osmStandard

    static func of(_ provider: MapProvider) -> [MapLayer] {
        allCases.filter { $0.provider == provider }
    }

    var id: String { rawValue }

    var isApple: Bool { provider == .apple }

    /// 「提供元 / 種類」の形の名前（設定画面の一覧用）
    var displayName: String { "\(provider.label) / \(label)" }

    var provider: MapProvider {
        switch self {
        case .appleStandard, .appleSatellite, .appleHybrid, .appleMuted: return .apple
        case .osmStandard, .osmHumanitarian, .osmTopo: return .osm
        case .gsiStd, .gsiPale, .gsiBlank, .gsiPhoto, .gsiRelief: return .gsi
        case .monoLight, .monoDark, .esriImagery: return .other
        }
    }

    var label: String {
        switch self {
        case .appleStandard: return "標準"
        case .appleSatellite: return "衛星"
        case .appleHybrid: return "衛星+地名"
        case .appleMuted: return "控えめ"
        case .osmStandard: return "標準"
        case .osmHumanitarian: return "人道支援"
        case .osmTopo: return "地形"
        case .gsiStd: return "標準"
        case .gsiPale: return "淡色"
        case .gsiBlank: return "白地図"
        case .gsiPhoto: return "航空写真"
        case .gsiRelief: return "色別標高"
        case .monoLight: return "モノクロ（明）"
        case .monoDark: return "モノクロ（暗）"
        case .esriImagery: return "衛星（Esri）"
        }
    }

    var tileUrl: String? {
        switch self {
        case .appleStandard, .appleSatellite, .appleHybrid, .appleMuted:
            return nil
        // モノクロはOSM標準タイルをCSSフィルタで加工する
        case .osmStandard, .monoLight, .monoDark:
            return "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        case .osmHumanitarian: return "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
        case .osmTopo: return "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png"
        case .gsiStd: return "https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png"
        case .gsiPale: return "https://cyberjapandata.gsi.go.jp/xyz/pale/{z}/{x}/{y}.png"
        case .gsiBlank: return "https://cyberjapandata.gsi.go.jp/xyz/blank/{z}/{x}/{y}.png"
        case .gsiPhoto: return "https://cyberjapandata.gsi.go.jp/xyz/seamlessphoto/{z}/{x}/{y}.jpg"
        case .gsiRelief: return "https://cyberjapandata.gsi.go.jp/xyz/relief/{z}/{x}/{y}.png"
        case .esriImagery:
            return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        }
    }

    var attribution: String {
        switch self {
        case .appleStandard, .appleSatellite, .appleHybrid, .appleMuted:
            return ""
        case .osmStandard, .monoLight, .monoDark:
            return MapLayer.osmAttribution
        case .osmHumanitarian:
            return "\(MapLayer.osmAttribution), Tiles style by <a href=\"https://www.hotosm.org/\">HOT</a>"
        case .osmTopo:
            return "\(MapLayer.osmAttribution), SRTM | &copy; <a href=\"https://opentopomap.org\">OpenTopoMap</a> (CC-BY-SA)"
        case .gsiStd, .gsiPale, .gsiBlank, .gsiPhoto, .gsiRelief:
            return MapLayer.gsiAttribution
        case .esriImagery:
            return "Tiles &copy; Esri &mdash; Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
        }
    }

    /// タイルが用意されている最大のズーム。これより先はタイルを拡大して表示する
    var maxNativeZoom: Int {
        switch self {
        case .osmStandard, .osmHumanitarian, .monoLight, .monoDark: return 19
        case .osmTopo: return 17
        case .gsiBlank: return 14
        case .gsiRelief: return 15
        default: return 18
        }
    }

    var subdomains: String { "abc" }

    var tileFilter: String? {
        switch self {
        case .monoLight: return "grayscale(1) contrast(0.9) brightness(1.05)"
        case .monoDark: return "grayscale(1) invert(1) brightness(0.85) contrast(0.9)"
        default: return nil
        }
    }

    private static let osmAttribution =
        "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"
    private static let gsiAttribution =
        "<a href=\"https://maps.gsi.go.jp/development/ichiran.html\">&copy; 国土地理院</a>"
}
