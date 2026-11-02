import SwiftUI
import MapKit
import WebKit

/**
 * 地図表示画面。
 * 上のタブで提供元（Apple / OSM / 地理院 / その他）、その下のボタンで地図の種類を切り替える。
 *
 * - Apple:      MapKit。APIキー不要。軌跡を線で描画。
 * - それ以外:    WKWebView + Leaflet.js でタイル画像を表示。APIキー不要。
 */
struct MapView: View {
    @StateObject private var vm: MapViewModel
    @State private var layer: MapLayer
    private let settings: AppSettings

    init(session: Session, repo: LocationRepository, settings: AppSettings) {
        _vm = StateObject(wrappedValue: MapViewModel(session: session, repo: repo))
        _layer = State(initialValue: settings.defaultLayer)
        self.settings = settings
    }

    var body: some View {
        VStack(spacing: 0) {
            // 提供元タブ
            Picker("提供元", selection: providerBinding) {
                ForEach(MapProvider.allCases) { provider in
                    Text(provider.label).tag(provider)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 12)
            .padding(.top, 8)

            // 地図の種類
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(MapLayer.of(layer.provider)) { item in
                        Button(item.label) { layer = item }
                            .buttonStyle(.bordered)
                            .tint(item == layer ? Color.accentColor : Color.secondary)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }

            // 地図本体。全ポイントが収まる位置で表示するため、読み込みが終わってから作る
            Group {
                if vm.isLoading {
                    ProgressView()
                } else if vm.logPoints.isEmpty {
                    ContentUnavailableView("記録データがありません", systemImage: "map.fill")
                } else if layer.isApple {
                    AppleMapView(logPoints: vm.logPoints, layer: layer, settings: settings)
                } else {
                    WebMapView(logPoints: vm.logPoints, layer: layer)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle(vm.session.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
    }

    /// 提供元を切り替えたら、デフォルト地図の提供元ならその地図、それ以外は先頭の地図を開く
    private var providerBinding: Binding<MapProvider> {
        Binding(
            get: { layer.provider },
            set: { provider in
                guard provider != layer.provider else { return }
                layer = settings.defaultLayer.provider == provider
                    ? settings.defaultLayer
                    : (MapLayer.of(provider).first ?? .defaultLayer)
            }
        )
    }
}

// MARK: - Apple マップ（MapKit）

/// 軌跡（青線）と開始（緑）・終了（赤）の印を描く
private struct AppleMapView: View {
    let logPoints: [LogPoint]
    let layer: MapLayer
    let settings: AppSettings

    var body: some View {
        let coordinates = logPoints.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
        // .automatic は地図上のすべての線と印が収まる位置になる
        Map(initialPosition: .automatic) {
            if coordinates.count > 1 {
                MapPolyline(coordinates: coordinates)
                    .stroke(Color(red: 0x15 / 255, green: 0x65 / 255, blue: 0xC0 / 255), lineWidth: 4)
            }
            if let first = coordinates.first {
                Annotation("開始", coordinate: first) {
                    MarkerDot(color: Color(red: 0x4C / 255, green: 0xAF / 255, blue: 0x50 / 255))
                }
            }
            if coordinates.count > 1, let last = coordinates.last {
                Annotation("終了", coordinate: last) {
                    MarkerDot(color: Color(red: 0xF4 / 255, green: 0x43 / 255, blue: 0x36 / 255))
                }
            }
        }
        .mapStyle(mapStyle)
        .mapControls {
            MapCompass()
            MapScaleView()
            MapPitchToggle()
        }
    }

    private var mapStyle: MapStyle {
        let elevation: MapStyle.Elevation = settings.apple3D ? .realistic : .flat
        switch layer {
        case .appleSatellite:
            return .imagery(elevation: elevation)
        case .appleHybrid:
            return .hybrid(elevation: elevation, showsTraffic: settings.appleTraffic)
        case .appleMuted:
            return .standard(elevation: elevation, emphasis: .muted, showsTraffic: settings.appleTraffic)
        default:
            return .standard(elevation: elevation, showsTraffic: settings.appleTraffic)
        }
    }
}

private struct MarkerDot: View {
    let color: Color

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 20, height: 20)
            .overlay(Circle().stroke(Color.white, lineWidth: 2))
    }
}

// MARK: - WebView（Leaflet.js地図）

/**
 * WKWebViewでLeaflet.js（地図表示ライブラリ）を動かし、MapLayer のタイル地図を表示する。
 * 軌跡と開始・終了の印はAndroid版と同じ見た目。
 */
private struct WebMapView: UIViewRepresentable {
    let logPoints: [LogPoint]
    let layer: MapLayer

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false  // 地図のスクロールはLeafletが行う
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // 地図や点の数が変わったときだけ読み込み直す（画面の再描画ごとに読み込むと地図が初期位置に戻る）
        let key = "\(layer.rawValue)-\(logPoints.count)"
        guard context.coordinator.loadedKey != key else { return }
        context.coordinator.loadedKey = key
        // baseURLにhttps://unpkg.comを指定することでCDNからLeafletを読み込める
        webView.loadHTMLString(buildHtml(), baseURL: URL(string: "https://unpkg.com"))
    }

    final class Coordinator {
        var loadedKey: String?
    }

    private func buildHtml() -> String {
        let tileUrl = layer.tileUrl ?? ""
        let centerLat = logPoints.first?.latitude ?? 35.681236
        let centerLng = logPoints.first?.longitude ?? 139.767125
        // モノクロ表示などはタイルの層だけにフィルタをかける（軌跡や印の色は変えない）
        let tileFilterCss = layer.tileFilter.map { ".leaflet-tile-pane { filter: \($0); }" } ?? ""
        let pointsJson = logPoints
            .map { "{\"lat\":\($0.latitude),\"lng\":\($0.longitude)}" }
            .joined(separator: ",")

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map { height: 100%; margin: 0; padding: 0; }
                \(tileFilterCss)
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([\(centerLat), \(centerLng)], 14);
                // maxNativeZoomより先はタイルを拡大して表示する
                L.tileLayer(\(jsQuoted(tileUrl)), {
                    attribution: \(jsQuoted(layer.attribution)),
                    subdomains: \(jsQuoted(layer.subdomains)),
                    maxNativeZoom: \(layer.maxNativeZoom),
                    maxZoom: 19
                }).addTo(map);

                var points = [\(pointsJson)];
                if (points.length > 0) {
                    var latlngs = points.map(function(p) { return [p.lat, p.lng]; });

                    // 軌跡ポリライン（青線）
                    if (latlngs.length > 1) {
                        var polyline = L.polyline(latlngs, {
                            color: '#1565C0', weight: 4, opacity: 0.85
                        }).addTo(map);
                        // 全ポイントが収まるようズーム調整
                        map.fitBounds(polyline.getBounds(), {padding: [30, 30]});
                    }

                    // 開始マーカー（緑）
                    L.circleMarker(latlngs[0], {
                        radius: 10, fillColor: '#4CAF50',
                        color: '#fff', weight: 2, fillOpacity: 1
                    }).addTo(map).bindPopup('<b>開始</b>');

                    // 終了マーカー（赤）
                    if (latlngs.length > 1) {
                        L.circleMarker(latlngs[latlngs.length - 1], {
                            radius: 10, fillColor: '#F44336',
                            color: '#fff', weight: 2, fillOpacity: 1
                        }).addTo(map).bindPopup('<b>終了</b>');
                    }
                }
            </script>
        </body>
        </html>
        """
    }

    /// JavaScriptのシングルクォート文字列にする
    private func jsQuoted(_ value: String) -> String {
        let escaped = value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "</", with: "<\\/")
        return "'\(escaped)'"
    }
}
