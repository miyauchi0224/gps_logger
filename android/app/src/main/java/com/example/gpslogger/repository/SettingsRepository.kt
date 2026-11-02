package com.example.gpslogger.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.gpslogger.model.MapLayer
import com.example.gpslogger.model.RecordMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** アプリの設定値 */
data class AppSettings(
    val intervalSec: Long = 1L,
    val recordMode: RecordMode = RecordMode.STANDARD,
    // 標準モードでも、バッテリー残量が少ないときや省電力モード中は低消費電力に切り替える
    val autoLowPower: Boolean = true,
    val lowBatteryPercent: Int = 20,
    // 記録中、他のアプリを使っているときも画面上部に緑の帯を重ねる（「他のアプリの上に重ねて表示」の許可が必要）
    val showOverlay: Boolean = true,
    val defaultLayer: MapLayer = MapLayer.DEFAULT,
    // Google Maps 用
    val googleTraffic: Boolean = false,
    val googleBuildings: Boolean = true,
    val googleZoomControls: Boolean = true
) {
    companion object {
        val INTERVAL_RANGE = 1L..3600L
        val LOW_BATTERY_RANGE = 5..80
    }
}

/** 設定をDataStoreに保存・読み込みするRepository */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val INTERVAL_SEC = longPreferencesKey("interval_sec")
        val RECORD_MODE = stringPreferencesKey("record_mode")
        val AUTO_LOW_POWER = booleanPreferencesKey("auto_low_power")
        val LOW_BATTERY_PERCENT = intPreferencesKey("low_battery_percent")
        val SHOW_OVERLAY = booleanPreferencesKey("show_overlay")
        val DEFAULT_LAYER = stringPreferencesKey("default_layer")
        val GOOGLE_TRAFFIC = booleanPreferencesKey("google_traffic")
        val GOOGLE_BUILDINGS = booleanPreferencesKey("google_buildings")
        val GOOGLE_ZOOM_CONTROLS = booleanPreferencesKey("google_zoom_controls")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            intervalSec = p[Keys.INTERVAL_SEC] ?: defaults.intervalSec,
            recordMode = RecordMode.entries.find { it.name == p[Keys.RECORD_MODE] }
                ?: defaults.recordMode,
            autoLowPower = p[Keys.AUTO_LOW_POWER] ?: defaults.autoLowPower,
            lowBatteryPercent = p[Keys.LOW_BATTERY_PERCENT] ?: defaults.lowBatteryPercent,
            showOverlay = p[Keys.SHOW_OVERLAY] ?: defaults.showOverlay,
            // 保存済みの名前が無くなった地図（バージョンアップで削除など）は既定に戻す
            defaultLayer = MapLayer.entries.find { it.name == p[Keys.DEFAULT_LAYER] }
                ?: defaults.defaultLayer,
            googleTraffic = p[Keys.GOOGLE_TRAFFIC] ?: defaults.googleTraffic,
            googleBuildings = p[Keys.GOOGLE_BUILDINGS] ?: defaults.googleBuildings,
            googleZoomControls = p[Keys.GOOGLE_ZOOM_CONTROLS] ?: defaults.googleZoomControls
        )
    }

    suspend fun save(settings: AppSettings) {
        dataStore.edit { p ->
            p[Keys.INTERVAL_SEC] = settings.intervalSec.coerceIn(AppSettings.INTERVAL_RANGE)
            p[Keys.RECORD_MODE] = settings.recordMode.name
            p[Keys.AUTO_LOW_POWER] = settings.autoLowPower
            p[Keys.LOW_BATTERY_PERCENT] = settings.lowBatteryPercent.coerceIn(AppSettings.LOW_BATTERY_RANGE)
            p[Keys.SHOW_OVERLAY] = settings.showOverlay
            p[Keys.DEFAULT_LAYER] = settings.defaultLayer.name
            p[Keys.GOOGLE_TRAFFIC] = settings.googleTraffic
            p[Keys.GOOGLE_BUILDINGS] = settings.googleBuildings
            p[Keys.GOOGLE_ZOOM_CONTROLS] = settings.googleZoomControls
        }
    }
}
