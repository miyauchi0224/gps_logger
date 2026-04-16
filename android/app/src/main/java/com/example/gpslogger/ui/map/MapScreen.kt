package com.example.gpslogger.ui.map

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.viewmodel.MapType
import com.example.gpslogger.viewmodel.MapViewModel

/**
 * 地図表示画面。
 * 上部のタブでGoogle Maps / OpenStreetMap / 国土地理院地図を切り替えられる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    vm: MapViewModel = hiltViewModel()
) {
    val logPoints by vm.logPoints.collectAsState()
    var selectedMap by remember { mutableStateOf(MapType.OSM) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.sessionName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 地図切替タブ（TabRow = 横並びのタブUI）
            TabRow(selectedTabIndex = selectedMap.ordinal) {
                MapType.values().forEach { mapType ->
                    Tab(
                        selected = selectedMap == mapType,
                        onClick = { selectedMap = mapType },
                        text = {
                            Text(
                                when (mapType) {
                                    MapType.GOOGLE -> "Google"
                                    MapType.OSM -> "OpenStreetMap"
                                    MapType.GSI -> "地理院地図"
                                }
                            )
                        }
                    )
                }
            }

            // 地図表示エリア
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedMap) {
                    MapType.GOOGLE -> GoogleMapView(logPoints)
                    MapType.OSM    -> WebMapView(logPoints, useGsi = false)
                    MapType.GSI    -> WebMapView(logPoints, useGsi = true)
                }

                // データが空の場合のメッセージ
                if (logPoints.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "記録データがありません",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Google Maps表示コンポーネント。
 * maps-compose ライブラリを使用。APIキーが必要。
 * 軌跡をポリライン（線）で、開始・終了地点をマーカーで表示。
 */
@Composable
private fun GoogleMapView(logPoints: List<LogPoint>) {
    if (logPoints.isEmpty()) return

    val latLngs = logPoints.map { LatLng(it.latitude, it.longitude) }

    // 全ポイントが入る範囲を計算
    val boundsBuilder = LatLngBounds.builder()
    latLngs.forEach { boundsBuilder.include(it) }
    val bounds = boundsBuilder.build()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bounds.center, 14f)
    }

    // 地図読み込み後に全ポイントが見えるようズーム調整
    LaunchedEffect(logPoints) {
        if (latLngs.size > 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 80)
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        // 軌跡ポリライン（青線）
        if (latLngs.size > 1) {
            Polyline(
                points = latLngs,
                color = Color(0xFF1565C0),
                width = 8f
            )
        }
        // 開始地点マーカー
        latLngs.firstOrNull()?.let { first ->
            Marker(
                state = MarkerState(position = first),
                title = "開始"
            )
        }
        // 終了地点マーカー
        if (latLngs.size > 1) {
            latLngs.lastOrNull()?.let { last ->
                Marker(
                    state = MarkerState(position = last),
                    title = "終了"
                )
            }
        }
    }
}

/**
 * WebViewを使ったLeaflet.js地図コンポーネント。
 * useGsi=false → OpenStreetMap
 * useGsi=true  → 国土地理院地図
 * どちらもAPIキー不要で使用可能。
 */
@Composable
private fun WebMapView(logPoints: List<LogPoint>, useGsi: Boolean) {
    val html = remember(logPoints, useGsi) {
        buildWebMapHtml(logPoints, useGsi)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true   // Leaflet.jsの実行に必要
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }
        },
        update = { webView ->
            // baseURLをhttps://unpkg.comにすることでCDNリソースの読み込みを許可
            webView.loadDataWithBaseURL(
                "https://unpkg.com",
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    )
}

/**
 * Leaflet.jsを使ったHTML地図を生成する関数。
 * - ログポイントを青いポリラインで繋ぐ
 * - 開始地点（緑丸）と終了地点（赤丸）にマーカーを表示
 * - 全ポイントが画面に収まるようズーム自動調整
 */
private fun buildWebMapHtml(logPoints: List<LogPoint>, useGsi: Boolean): String {
    val tileUrl = if (useGsi) {
        "https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png"
    } else {
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    }
    val attribution = if (useGsi) {
        "<a href='https://maps.gsi.go.jp/development/ichiran.html'>&copy; 国土地理院</a>"
    } else {
        "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
    }

    val centerLat = logPoints.firstOrNull()?.latitude ?: 35.681236
    val centerLng = logPoints.firstOrNull()?.longitude ?: 139.767125

    val pointsJson = if (logPoints.isNotEmpty()) {
        logPoints.joinToString(",") { p ->
            """{"lat":${p.latitude},"lng":${p.longitude}}"""
        }
    } else {
        ""
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map { height: 100%; margin: 0; padding: 0; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([$centerLat, $centerLng], 14);
                L.tileLayer('$tileUrl', {
                    attribution: '$attribution',
                    maxZoom: 18
                }).addTo(map);

                var points = [$pointsJson];
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
    """.trimIndent()
}
