# GPS Logger

GPS位置データをリアルタイムで記録・エクスポート・可視化するモバイルアプリです。iOS と Android の両プラットフォームに対応しています。

---

## 概要

バックグラウンドでGPS/WiFiによる位置情報を継続的に記録し、CSV形式でエクスポートしたり、地図上に可視化したりできます。ハイキング・ドライブ・ジョギングなど、移動ルートを記録したいあらゆるシーンで活用できます。

---

## 主な機能

| 機能 | 説明 |
|------|------|
| バックグラウンドGPS記録 | アプリを閉じていても位置情報を継続記録 |
| 測位モード自動切替 | GPS精度が低下時にWiFi測位へ自動フォールバック |
| 複数地図対応 | Google Maps / OpenStreetMap / 国土地理院地図 |
| CSVエクスポート | タイムスタンプ・緯度経度・高度・速度を出力 |
| クラウドアップロード | Google Drive / OneDrive へのOAuth認証付きアップロード |
| セッション管理 | 複数の記録セッションを管理・最大365日間の連続記録 |

---

## 技術スタック

### iOS
| 項目 | 技術 |
|------|------|
| 言語 | Swift |
| UIフレームワーク | SwiftUI |
| 位置情報 | CoreLocation |
| リアクティブ | Combine |
| ローカルDB | UserDefaults（JSONエンコード） |
| アーキテクチャ | MVVM |

### Android
| 項目 | 技術 |
|------|------|
| 言語 | Kotlin |
| UIフレームワーク | Jetpack Compose + Material3 |
| 位置情報 | Play Services Location |
| ローカルDB | Room（SQLite） |
| 依存性注入 | Hilt（Dagger） |
| 非同期処理 | Coroutines + Flow |
| アーキテクチャ | MVVM |

---

## アーキテクチャ

```
View (SwiftUI / Jetpack Compose)
  └─ ViewModel (ObservableObject / StateFlow)
       └─ Repository
            └─ LocationService  ←  CoreLocation / Play Services
            └─ Database         ←  UserDefaults / Room (SQLite)
            └─ UploadService    ←  Google Drive / OneDrive API
```

---

## セットアップ

### 前提条件

- **iOS**: macOS + Xcode 15以上
- **Android**: Android Studio + JDK 17以上
- **Google Maps** を使う場合: [Google Maps Platform](https://developers.google.com/maps) の APIキー

---

### iOS

```bash
cd ios
open GPSLogger.xcodeproj
```

1. Xcode でプロジェクトを開く
2. `Signing & Capabilities` で自分の Apple Developer アカウントを設定
3. 実機またはシミュレータでビルド・実行

---

### Android

```bash
cd android
./gradlew assembleDebug
```

または Android Studio で `android/` フォルダを開いてそのままビルドできます。

#### Google Maps APIキーの設定

`android/app/src/main/AndroidManifest.xml` を開き、以下の箇所を自分のAPIキーに書き換えてください：

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY_HERE" />  <!-- ← ここを変更 -->
```

> **注意**: APIキーは `.gitignore` に追加するか、環境変数で管理してください。リポジトリに直接コミットしないようにしてください。

---

## CSV出力フォーマット

```csv
id,timestamp,latitude,longitude,altitude,accuracy,speed,source
xxxxxxxx-...,2026-04-09T10:00:00+09:00,35.681236,139.767125,5.2,3.0,1.2,GPS
```

| カラム | 内容 | 単位 |
|--------|------|------|
| id | レコードの一意ID | UUID |
| timestamp | 記録日時（ISO 8601形式） | - |
| latitude | 緯度 | 度（十進法） |
| longitude | 経度 | 度（十進法） |
| altitude | 高度 | メートル |
| accuracy | 水平精度 | メートル |
| speed | 速度 | m/s |
| source | 測位方式 | GPS / WiFi |

---

## 必要な権限

### iOS
- `NSLocationAlwaysAndWhenInUseUsageDescription` — バックグラウンド測位
- `NSLocationWhenInUseUsageDescription` — フォアグラウンド測位
- Background Modes: **Location updates**

### Android
- `ACCESS_FINE_LOCATION` — 高精度GPS測位
- `ACCESS_COARSE_LOCATION` — WiFi/基地局測位
- `ACCESS_BACKGROUND_LOCATION` — バックグラウンド測位
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` — 常駐サービス
- `INTERNET` — クラウドアップロード

---

## プロジェクト構成

```
gps_logger/
├── ios/
│   └── GPSLogger/
│       ├── Models/          # データモデル（LogPoint, Session, MapType）
│       ├── Services/        # 位置情報・アップロードサービス
│       ├── Repository/      # データアクセス層
│       ├── ViewModels/      # MVVM ViewModel
│       └── Views/           # SwiftUI 画面
├── android/
│   └── app/src/main/java/com/example/gpslogger/
│       ├── model/           # データモデル
│       ├── database/        # Room DB（DAO・スキーマ）
│       ├── di/              # Hilt 依存性注入モジュール
│       ├── repository/      # データアクセス層
│       ├── service/         # Foreground Service（バックグラウンド測位）
│       ├── ui/              # Jetpack Compose 画面
│       └── viewmodel/       # MVVM ViewModel
└── .claude/                 # 設計ドキュメント・デプロイ手順
```

---


## ライセンス

このプロジェクトのライセンスは未定です。
