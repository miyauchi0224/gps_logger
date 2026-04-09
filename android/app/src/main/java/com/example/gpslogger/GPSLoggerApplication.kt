package com.example.gpslogger

import android.app.Application
import androidx.room.Room
import com.example.gpslogger.database.AppDatabase
import dagger.hilt.android.HiltAndroidApp

/** Hilt（依存性注入フレームワーク）を有効にするアプリクラス */
@HiltAndroidApp
class GPSLoggerApplication : Application()
