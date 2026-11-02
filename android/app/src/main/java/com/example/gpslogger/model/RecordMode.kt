package com.example.gpslogger.model

import com.google.android.gms.location.Priority

/**
 * 記録モード（設定画面で選ぶ）。
 * accuracy / power は画面に出す目安。実際の値は端末・電波環境・記録間隔で大きく変わる。
 */
enum class RecordMode(
    val label: String,
    val priority: Int,
    val accuracy: String,
    val power: String,
    val description: String
) {
    STANDARD(
        "標準（GPS）",
        Priority.PRIORITY_HIGH_ACCURACY,
        accuracy = "約5〜20m（屋外）",
        power = "大（1時間でバッテリー約5〜10%）",
        description = "GPSで測位します。屋内や地下では精度が落ちます。"
    ),
    LOW_POWER(
        "低消費電力（WiFi・基地局）",
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        accuracy = "約40〜100m（市街地ほど正確）",
        power = "小（1時間でバッテリー約1〜3%）",
        description = "主にWiFiと携帯電話の基地局で測位します。長時間の記録向けです。"
    )
}

/** 各ポイントを実際にどの手段で記録したか（CSVの method 列に出す） */
enum class RecordMethod(val label: String) {
    STANDARD("標準（GPS）"),
    LOW_POWER("低消費電力"),
    /** 標準モードだったが、バッテリー残量や省電力モードで自動的に低消費電力に切り替えた */
    LOW_POWER_AUTO("低消費電力（自動切替）")
}
