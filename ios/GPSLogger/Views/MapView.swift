import SwiftUI
import MapKit
import WebKit

/**
 * 地図表示画面。
 * 上部のSegmentedPickerでMapKit / OpenStreetMap / 国土地理院地図を切り替えられる。
 *
 * - MapKit:         Apple標準。MKMapViewでポリライン（軌跡）を描画。
 * - OpenStreetMap:  WebView + Leaflet.js。APIキー不要。
 * - 国土地理院:      WebView + Leaflet.js + GSIタイル。APIキー不要・日本の詳細地図。
 */
struct MapView: View {
    @StateObject private var vm: MapViewModel
    @State private var selectedMap: MapType = .osm

    init(session: Session, repo: LocationRepository) {
        _vm = StateObject(wrappedValue: MapViewModel(session: session, repo: repo))
    }

    var body: some View {
        VStack(spacing: 0) {
            // 地図切替ピッカー（SegmentedPicker = iOSのセグメントコントロール）
            Picker("地図", selection: $selectedMap) {
                ForEach(MapType.allCases, id: \.self) { type in
                    Text(type.displayName).tag(type)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            // 地図本体
            Group {
                switch selectedMap {
                case .mapKit:
                    NativeMapView(logPoints: vm.logPoints)
                case .osm:
                    WebMapView(logPoints: vm.logPoints, useGsi: false)
                case .gsi:
                    WebMapView(logPoints: vm.logPoints, useGsi: true)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay {
                // データが空の場合のオーバーレイ表示
                if vm.logPoints.isEmpty {
                    ContentUnavailableView(
                        "記録データがありません",
                        systemImage: "map.fill"
                    )
                }
            }
        }
        .navigationTitle(vm.session.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - MapKit（ネイティブ地図）

/**
 * Apple MapKitをUIViewRepresentableでラップした地図ビュー。
 * UIViewRepresentable: UIKitのビュー（MKMapView）をSwiftUIで使うための仕組み。
 * MKMapViewを使うことでポリライン（軌跡の線）が描画できる。
 */
struct NativeMapView: UIViewRepresentable {
    let logPoints: [LogPoint]

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = false
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        // 既存のオーバーレイ・アノテーションをクリア
        mapView.removeOverlays(mapView.overlays)
        mapView.removeAnnotations(mapView.annotations)

        guard !logPoints.isEmpty else { return }

        let coordinates = logPoints.map {
            CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
        }

        // 軌跡ポリライン（青線）を追加
        if coordinates.count > 1 {
            let polyline = MKPolyline(coordinates: coordinates, count: coordinates.count)
            mapView.addOverlay(polyline)
        }

        // 開始地点のピン（緑）
        let startPin = MKPointAnnotation()
        startPin.coordinate = coordinates.first!
        startPin.title = "開始"
        mapView.addAnnotation(startPin)

        // 終了地点のピン（赤）
        if coordinates.count > 1 {
            let endPin = MKPointAnnotation()
            endPin.coordinate = coordinates.last!
            endPin.title = "終了"
            mapView.addAnnotation(endPin)
        }

        // 全ポイントが見えるようカメラを調整
        let region = MKCoordinateRegion(fitting: coordinates)
        mapView.setRegion(region, animated: false)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    /// MKMapViewのデリゲート。ポリラインの見た目（色・太さ）を設定する。
    class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = UIColor(red: 0.08, green: 0.39, blue: 0.75, alpha: 1.0)
                renderer.lineWidth = 4
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}

// MKCoordinateRegionの拡張。座標の配列から地図の表示範囲を計算する。
private extension MKCoordinateRegion {
    init(fitting coordinates: [CLLocationCoordinate2D]) {
        let lats = coordinates.map { $0.latitude }
        let lngs = coordinates.map { $0.longitude }
        let center = CLLocationCoordinate2D(
            latitude:  ((lats.min() ?? 0) + (lats.max() ?? 0)) / 2,
            longitude: ((lngs.min() ?? 0) + (lngs.max() ?? 0)) / 2
        )
        let span = MKCoordinateSpan(
            latitudeDelta:  max((lats.max() ?? 0) - (lats.min() ?? 0), 0.001) * 1.5,
            longitudeDelta: max((lngs.max() ?? 0) - (lngs.min() ?? 0), 0.001) * 1.5
        )
        self.init(center: center, span: span)
    }
}

// MARK: - WebView（Leaflet.js地図）

/**
 * WKWebViewをUIViewRepresentableでラップしたWebビュー。
 * Leaflet.js（地図表示ライブラリ）を使ってOpenStreetMapまたは国土地理院地図を表示する。
 * useGsi=false → OpenStreetMap
 * useGsi=true  → 国土地理院地図
 */
struct WebMapView: UIViewRepresentable {
    let logPoints: [LogPoint]
    let useGsi: Bool

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false  // マップ内のスクロールはLeafletが制御
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // baseURLにhttps://unpkg.comを指定することでCDNからLeafletを読み込める
        webView.loadHTMLString(
            buildHtml(),
            baseURL: URL(string: "https://unpkg.com")
        )
    }

    private func buildHtml() -> String {
        let tileUrl = useGsi
            ? "https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png"
            : "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        let attribution = useGsi
            ? "&copy; <a href='https://maps.gsi.go.jp/development/ichiran.html'>国土地理院</a>"
            : "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"

        let centerLat = logPoints.first?.latitude  ?? 35.681236
        let centerLng = logPoints.first?.longitude ?? 139.767125

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
            <style>html,body,#map{height:100%;margin:0;padding:0;}</style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([\(centerLat), \(centerLng)], 14);
                L.tileLayer('\(tileUrl)', {
                    attribution: '\(attribution)',
                    maxZoom: 18
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
}
