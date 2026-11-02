# GPS Logger

GPS位置データをリアルタイムで記録・エクスポート・可視化するモバイルアプリです。iOS と Android の両プラットフォームに対応しています。

---

## 概要

バックグラウンドでGPS/WiFiによる位置情報を継続的に記録し、CSV形式でエクスポートしたり、地図上に可視化したりできます。ハイキング・ドライブ・ジョギングなど、移動ルートを記録したいあらゆるシーンで活用できます。

---

## 主な機能

| 機能 | 説明 |
|------|------|
| バックグラウンドGPS記録 | アプリを閉じていても位置情報を継続記録（停止せずに終了した記録は、次にアプリを開いたときに再開） |
| 記録中の表示 | 記録中はアプリの全画面の上部と通知を緑で表示。他のアプリ使用中も画面上部に緑の帯を重ねて表示（「他のアプリの上に重ねて表示」の許可が必要、設定でオフ可） |
| 記録モード | 標準（GPS）／低消費電力（WiFi・基地局）を選択。精度と消費電力の目安を画面に表示 |
| 低消費電力への自動切替 | 標準モードでも、バッテリー残量が設定値以下（充電中を除く）か省電力モードのときは低消費電力に切替 |
| 複数地図対応 | Google Maps / OpenStreetMap / 国土地理院地図 |
| CSVエクスポート | タイムスタンプ・緯度経度・高度・速度・記録手段を出力。ファイル名と保存先を選択可能 |
| CSV読み込み | CSVを読み込んでセッション一覧に追加（このアプリのCSVのほか、lat/lon/time などの列名やUnix時刻のCSVにも対応） |
| クラウド保存 | Google Drive / OneDrive へCSVを保存（Android: 標準の保存先ピッカーから選択。各アプリのインストールとサインインが必要） |
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

`android/local.properties`（git管理外）に、取得したAPIキーを追記してビルドし直してください：

```properties
MAPS_API_KEY=取得したAPIキー
```

ビルド時に `AndroidManifest.xml` へ埋め込まれます。未設定の場合、地図画面の Google タブには設定方法の案内が表示され、設定画面にも「APIキー: 未設定」と表示されます。Google 以外の地図（OSM・地理院・その他）はAPIキー不要です。

#### 選べる地図

| 提供元 | 種類 |
|--------|------|
| Google（要APIキー） | 標準 / 衛星 / 衛星+地名 / 地形 |
| OpenStreetMap | 標準 / 人道支援 / 地形（OpenTopoMap） |
| 国土地理院 | 標準 / 淡色 / 白地図 / 航空写真 / 色別標高 |
| その他 | モノクロ（明・暗、OSMをグレースケール化） / 衛星（Esri） |

設定画面では、デフォルト地図と Google マップの表示（交通状況・3D の建物・ズームボタン）を設定できます。

---

## CSV出力フォーマット

```csv
id,timestamp,latitude,longitude,altitude,accuracy,speed,source,method
xxxxxxxx-...,2026-04-09T10:00:00+09:00,35.681236,139.767125,5.2,3.0,1.2,GPS,STANDARD
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
| source | 測位方式（精度20m未満をGPSとみなす推定） | GPS / WIFI |
| method | 記録手段 | STANDARD（標準・GPS）/ LOW_POWER（低消費電力）/ LOW_POWER_AUTO（標準から自動で低消費電力に切替） |

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
- `INTERNET` — 地図タイルの取得

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
