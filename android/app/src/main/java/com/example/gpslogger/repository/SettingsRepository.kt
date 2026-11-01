package com.example.gpslogger.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.gpslogger.model.MapLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** アプリの設定値 */
data class AppSettings(
    val intervalSec: Long = 1L,
    val defaultLayer: MapLayer = MapLayer.DEFAULT,
    // Google Maps 用
    val googleTraffic: Boolean = false,
    val googleBuildings: Boolean = true,
    val googleZoomControls: Boolean = true
) {
    companion object {
        val INTERVAL_RANGE = 1L..3600L
    }
}

/** 設定をDataStoreに保存・読み込みするRepository */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val INTERVAL_SEC = longPreferencesKey("interval_sec")
        val DEFAULT_LAYER = stringPreferencesKey("default_layer")
        val GOOGLE_TRAFFIC = booleanPreferencesKey("google_traffic")
        val GOOGLE_BUILDINGS = booleanPreferencesKey("google_buildings")
        val GOOGLE_ZOOM_CONTROLS = booleanPreferencesKey("google_zoom_controls")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            intervalSec = p[Keys.INTERVAL_SEC] ?: defaults.intervalSec,
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
            p[Keys.DEFAULT_LAYER] = settings.defaultLayer.name
            p[Keys.GOOGLE_TRAFFIC] = settings.googleTraffic
            p[Keys.GOOGLE_BUILDINGS] = settings.googleBuildings
            p[Keys.GOOGLE_ZOOM_CONTROLS] = settings.googleZoomControls
        }
    }
}
