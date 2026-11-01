package com.example.gpslogger.ui.map

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.example.gpslogger.BuildConfig
import com.example.gpslogger.model.LogPoint
import com.example.gpslogger.model.MapLayer
import com.example.gpslogger.model.MapProvider
import com.example.gpslogger.repository.AppSettings
import com.example.gpslogger.viewmodel.MapViewModel

/**
 * 地図表示画面。
 * 上段のタブで提供元（Google / OSM / 地理院 / その他）を、
 * 下段のチップでその提供元の地図の種類（標準・衛星・モノクロなど）を切り替えられる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    vm: MapViewModel = hiltViewModel()
) {
    val logPoints by vm.logPoints.collectAsState()
    val settings by vm.settings.collectAsState()

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
        // 設定（デフォルト地図）を読み込んでから表示する
        val loaded = settings ?: return@Scaffold
        // 画面回転などで選択が戻らないよう名前で保存する
        var selectedName by rememberSaveable { mutableStateOf(loaded.defaultLayer.name) }
        val selected = MapLayer.valueOf(selectedName)

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 提供元タブ
            TabRow(selectedTabIndex = selected.provider.ordinal) {
                MapProvider.entries.forEach { provider ->
                    Tab(
                        selected = selected.provider == provider,
                        onClick = {
                            if (selected.provider != provider) {
                                // デフォルト地図の提供元ならその地図、それ以外は先頭の地図を開く
                                val layer = loaded.defaultLayer.takeIf { it.provider == provider }
                                    ?: MapLayer.of(provider).first()
                                selectedName = layer.name
                            }
                        },
                        text = { Text(provider.label) }
                    )
                }
            }

            // 地図の種類チップ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapLayer.of(selected.provider).forEach { layer ->
                    FilterChip(
                        selected = layer == selected,
                        onClick = { selectedName = layer.name },
                        label = { Text(layer.label) }
                    )
                }
            }

            // 地図表示エリア
            Box(modifier = Modifier.fillMaxSize()) {
                if (selected.isGoogle && !BuildConfig.MAPS_API_KEY_SET) {
                    CenterMessage(
                        "Google マップのAPIキーが設定されていません。\n" +
                            "android/local.properties に MAPS_API_KEY を追加してビルドし直してください。"
                    )
                    return@Box
                }

                if (selected.isGoogle) {
                    GoogleMapView(logPoints, selected, loaded)
                } else {
                    WebMapView(logPoints, selected)
                }

                // データが空の場合のメッセージ
                if (logPoints.isEmpty()) {
                    CenterMessage("記録データがありません")
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // 暗い地図や航空写真の上でも読めるよう半透明の背景を敷く
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

/**
 * Google Maps表示コンポーネント。
 * maps-compose ライブラリを使用。APIキーが必要。
 * 軌跡をポリライン（線）で、開始・終了地点をマーカーで表示。
 */
@Composable
private fun GoogleMapView(logPoints: List<LogPoint>, layer: MapLayer, settings: AppSettings) {
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
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = when (layer) {
                MapLayer.GOOGLE_SATELLITE -> MapType.SATELLITE
                MapLayer.GOOGLE_HYBRID -> MapType.HYBRID
                MapLayer.GOOGLE_TERRAIN -> MapType.TERRAIN
                else -> MapType.NORMAL
            },
            isTrafficEnabled = settings.googleTraffic,
            isBuildingEnabled = settings.googleBuildings
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = settings.googleZoomControls)
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
 * Google以外の地図（タイルURLを持つ MapLayer）を表示する。どれもAPIキー不要。
 */
@Composable
private fun WebMapView(logPoints: List<LogPoint>, layer: MapLayer) {
    val html = remember(logPoints, layer) {
        buildWebMapHtml(logPoints, layer)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // AndroidViewの既定はWRAP_CONTENTで、WebViewではCSSのheight:100%が0pxになり地図が見えなくなる
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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

/** JSのシングルクォート文字列リテラルに埋め込めるようエスケープする */
private fun String.jsQuoted(): String =
    "'" + replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"

/**
 * Leaflet.jsを使ったHTML地図を生成する関数。
 * - ログポイントを青いポリラインで繋ぐ
 * - 開始地点（緑丸）と終了地点（赤丸）にマーカーを表示
 * - 全ポイントが画面に収まるようズーム自動調整
 */
private fun buildWebMapHtml(logPoints: List<LogPoint>, layer: MapLayer): String {
    val tileUrl = requireNotNull(layer.tileUrl) { "${layer.name} はタイル地図ではありません" }

    val centerLat = logPoints.firstOrNull()?.latitude ?: 35.681236
    val centerLng = logPoints.firstOrNull()?.longitude ?: 139.767125

    // モノクロ表示などはタイルの層だけにフィルタをかける（軌跡やマーカーの色は変えない）
    val tileFilterCss = layer.tileFilter?.let { ".leaflet-tile-pane { filter: $it; }" } ?: ""

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
                $tileFilterCss
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([$centerLat, $centerLng], 14);
                // maxNativeZoomより先はタイルを拡大して表示する
                L.tileLayer(${tileUrl.jsQuoted()}, {
                    attribution: ${layer.attribution.jsQuoted()},
                    subdomains: ${layer.subdomains.jsQuoted()},
                    maxNativeZoom: ${layer.maxNativeZoom},
                    maxZoom: 19
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
