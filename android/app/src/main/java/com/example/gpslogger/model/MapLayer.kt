package com.example.gpslogger.model

/** 地図の提供元。地図画面ではタブとして並ぶ */
enum class MapProvider(val label: String) {
    GOOGLE("Google"),
    OSM("OSM"),
    GSI("地理院"),
    OTHER("その他")
}

/**
 * 選択できる地図の種類。
 * Google以外はタイル画像のURLを持ち、WebView上のLeaflet.jsで表示する。
 * tileUrl の {s} はサブドメインにLeafletが置き換える。
 * tileFilter はタイル画像にかけるCSSフィルタ（モノクロ表示などに使う）。
 */
enum class MapLayer(
    val provider: MapProvider,
    val label: String,
    val tileUrl: String? = null,
    val attribution: String = "",
    val maxNativeZoom: Int = 18,
    val subdomains: String = "abc",
    val tileFilter: String? = null
) {
    // Google Maps（APIキー必要）。表示は maps-compose で行う
    GOOGLE_NORMAL(MapProvider.GOOGLE, "標準"),
    GOOGLE_SATELLITE(MapProvider.GOOGLE, "衛星"),
    GOOGLE_HYBRID(MapProvider.GOOGLE, "衛星+地名"),
    GOOGLE_TERRAIN(MapProvider.GOOGLE, "地形"),

    // OpenStreetMap系（APIキー不要）
    OSM_STANDARD(
        MapProvider.OSM, "標準",
        tileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attribution = OSM_ATTRIBUTION,
        maxNativeZoom = 19
    ),
    OSM_HUMANITARIAN(
        MapProvider.OSM, "人道支援",
        tileUrl = "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
        attribution = "$OSM_ATTRIBUTION, Tiles style by <a href=\"https://www.hotosm.org/\">HOT</a>",
        maxNativeZoom = 19
    ),
    OSM_TOPO(
        MapProvider.OSM, "地形",
        tileUrl = "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png",
        attribution = "$OSM_ATTRIBUTION, SRTM | &copy; <a href=\"https://opentopomap.org\">OpenTopoMap</a> (CC-BY-SA)",
        maxNativeZoom = 17
    ),

    // 国土地理院（APIキー不要）
    GSI_STD(
        MapProvider.GSI, "標準",
        tileUrl = "https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png",
        attribution = GSI_ATTRIBUTION
    ),
    GSI_PALE(
        MapProvider.GSI, "淡色",
        tileUrl = "https://cyberjapandata.gsi.go.jp/xyz/pale/{z}/{x}/{y}.png",
        attribution = GSI_ATTRIBUTION
    ),
    GSI_BLANK(
        MapProvider.GSI, "白地図",
        tileUrl = "https://cyberjapandata.gsi.go.jp/xyz/blank/{z}/{x}/{y}.png",
        attribution = GSI_ATTRIBUTION,
        maxNativeZoom = 14
    ),
    GSI_PHOTO(
        MapProvider.GSI, "航空写真",
        tileUrl = "https://cyberjapandata.gsi.go.jp/xyz/seamlessphoto/{z}/{x}/{y}.jpg",
        attribution = GSI_ATTRIBUTION
    ),
    GSI_RELIEF(
        MapProvider.GSI, "色別標高",
        tileUrl = "https://cyberjapandata.gsi.go.jp/xyz/relief/{z}/{x}/{y}.png",
        attribution = GSI_ATTRIBUTION,
        maxNativeZoom = 15
    ),

    // その他（APIキー不要）
    // モノクロはOSM標準タイルをCSSフィルタで加工する（タイル提供元のAPIキー不要）
    MONO_LIGHT(
        MapProvider.OTHER, "モノクロ（明）",
        tileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attribution = OSM_ATTRIBUTION,
        maxNativeZoom = 19,
        tileFilter = "grayscale(1) contrast(0.9) brightness(1.05)"
    ),
    MONO_DARK(
        MapProvider.OTHER, "モノクロ（暗）",
        tileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attribution = OSM_ATTRIBUTION,
        maxNativeZoom = 19,
        tileFilter = "grayscale(1) invert(1) brightness(0.85) contrast(0.9)"
    ),
    ESRI_IMAGERY(
        MapProvider.OTHER, "衛星（Esri）",
        tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        attribution = "Tiles &copy; Esri &mdash; Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
    );

    val isGoogle: Boolean get() = provider == MapProvider.GOOGLE

    companion object {
        val DEFAULT = OSM_STANDARD

        fun of(provider: MapProvider): List<MapLayer> = entries.filter { it.provider == provider }
    }
}

// 帰属表示はJSのシングルクォート文字列に埋め込むため、HTML属性はダブルクォートにする
private const val OSM_ATTRIBUTION =
    "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"
private const val GSI_ATTRIBUTION =
    "<a href=\"https://maps.gsi.go.jp/development/ichiran.html\">&copy; 国土地理院</a>"
