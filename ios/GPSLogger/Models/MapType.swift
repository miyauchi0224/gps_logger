/// 地図の種類を表す列挙型（enum）
/// CaseIterable: 全ケースを配列として取得できるようにする（SegmentedPickerで使用）
enum MapType: String, CaseIterable {
    case mapKit = "MapKit"          // Apple標準地図（ルート表示対応）
    case osm    = "OpenStreetMap"   // 無料オープンソース地図
    case gsi    = "地理院地図"       // 国土地理院（日本の詳細地図）

    /// 画面に表示するラベル文字列
    var displayName: String { rawValue }
}
