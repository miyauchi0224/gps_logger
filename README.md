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
| 記録モード | 標準（GPS）／低消費電力（WiFi・基地局）を選択。精度と消費電力の目安を画面に表示。記録画面のカードをタップしても切替でき、記録中もすぐ反映 |
| 記録間隔どおりの測位 | 標準モードではGPSを直接使い、止まっているときも記録間隔（既定1秒）ごとに記録（Android 12以降。屋内でGPSが途切れたらFused Locationで補完） |
| 低消費電力への自動切替 | 標準モードでも、バッテリー残量が設定値以下（充電中を除く）か省電力モードのときは低消費電力に切替 |
| 複数地図対応 | Google Maps（Android）/ Apple マップ（iOS）/ OpenStreetMap / 国土地理院地図 など |
| CSVエクスポート | タイムスタンプ・緯度経度・高度・速度・記録手段を出力。ファイル名と保存先を選択可能 |
| CSV読み込み | CSVを読み込んでセッション一覧に追加（このアプリのCSVのほか、lat/lon/time などの列名やUnix時刻のCSVにも対応） |
| CSV表示 | セッションのデータを、エクスポートと同じCSVの表としてアプリ内で表示 |
| クラウド保存 | Google Drive / OneDrive へCSVを保存（Android・iOS とも標準の保存先ピッカーまたは共有から選択。各アプリのインストールとサインインが必要） |
| セッション管理 | 複数の記録セッションを管理・最大365日間の連続記録 |

### iOS 版の違い

iOS 版も上と同じ機能を持ちますが、iOS の仕組み上、次の点が Android 版と異なります。

| 機能 | iOS 版 |
|------|--------|
| 記録中の表示 | アプリ内は画面上部の緑の帯。アプリを閉じているときは、ステータスバーに位置情報の使用中表示（青い帯・アイコン）が出る（iOS では他のアプリの上に重ねて表示できないため） |
| 停止せずに終了した記録 | 次にアプリを開いたときに再開。位置情報を「常に」許可していれば、システムにアプリが終了されても、大きく移動したときに自動で起動して再開 |
| 記録間隔どおりの測位 | CoreLocation が毎秒届ける位置から、記録間隔ごとに記録 |
| 低消費電力への自動切替 | 「低電力モード」のときも切替 |
| 地図 | Google の代わりに Apple マップ（標準 / 衛星 / 衛星+地名 / 控えめ。APIキー不要） |
| 保存先 | 「保存先を選んで保存」（iCloud Drive・このiPhone内・Google Drive・OneDrive）、「共有して保存」、「このiPhone内に保存」（ファイルアプリの「このiPhone内 > GPSLogger」） |

---

## 技術スタック

### iOS
| 項目 | 技術 |
|------|------|
| 言語 | Swift |
| UIフレームワーク | SwiftUI |
| 位置情報 | CoreLocation |
| 地図 | MapKit / WKWebView + Leaflet.js |
| リアクティブ | Combine |
| データ保存 | ファイル（セッション一覧は JSON、ログポイントは JSON Lines で追記）。設定は UserDefaults |
| プロジェクト生成 | XcodeGen（`project.yml`） |
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
            └─ Database         ←  ファイル（JSON / JSON Lines） / Room (SQLite)
            └─ 保存・共有        ←  保存先ピッカー・共有（Google Drive / OneDrive などのアプリが送信）
```

---

## セットアップ

### 前提条件

- **iOS**: macOS + Xcode 15以上 + [XcodeGen](https://github.com/yonaskolb/XcodeGen)（iOS 17以上で動作）
- **Android**: Android Studio + JDK 17以上
- **Google Maps** を使う場合: [Google Maps Platform](https://developers.google.com/maps) の APIキー（ビルドする開発者が用意。[設定方法](#google-maps-apiキーの設定)）

---

### iOS

```bash
brew install xcodegen   # 初回のみ
cd ios
xcodegen generate       # project.yml から GPSLogger.xcodeproj を作る
open GPSLogger.xcodeproj
```

1. `xcodegen generate` で Xcode プロジェクトを作る（`.xcodeproj` は git に入れていません。ファイルを追加・削除したら作り直してください）
2. Xcode でプロジェクトを開き、`Signing & Capabilities` で自分の Apple Developer アカウント（チーム）を選ぶ
3. 実機またはシミュレータでビルド・実行（`⌘U` でユニットテスト）

Apple マップを使うので、iOS 版では APIキーの設定は不要です。GitHub Actions（`.github/workflows/ios.yml`）でも、macOS 上でビルドとユニットテストを行います。

---

### Android

```bash
cd android
./gradlew assembleDebug
```

または Android Studio で `android/` フォルダを開いてそのままビルドできます。

#### Google Maps APIキーの設定

Google の地図（標準・衛星・衛星+地名・地形）は Google Maps SDK for Android で表示するため、**Google Maps Platform の APIキーが必要**です。Google 以外の地図（OpenStreetMap・国土地理院・その他）は APIキーなしで使えます。

**開発者（ビルドする人）の APIキーを使います。** キーはビルド時にアプリへ埋め込まれ、アプリを使う人がキーを用意・入力することはありません（Google Maps SDK はキーを `AndroidManifest.xml` からしか読まないため、アプリの設定画面では変更できません）。地図の利用は開発者の Google Cloud プロジェクトに記録されます。

##### 料金

Maps SDK for Android の地図表示（モバイルの Dynamic Maps）は、2026年時点で回数の上限なく無料です。ただし APIキーの作成には、Google Cloud で請求先アカウント（クレジットカード）の登録が必要です。このアプリは地図表示以外の有料 API（Directions・Places・Geocoding など）を使いません。料金は変わることがあるため、[Google Maps Platform の料金](https://mapsplatform.google.com/pricing/) で最新の情報を確認してください。

##### キーの取得手順

1. [Google Cloud Console](https://console.cloud.google.com/) でプロジェクトを作成し、請求先アカウントを設定する
2. 「API とサービス」→「ライブラリ」で **Maps SDK for Android** を有効にする
3. 「API とサービス」→「認証情報」→「認証情報を作成」→「API キー」でキーを作成する
4. 作成したキーに制限をかける（他人に流用されないように強く推奨）
   - アプリケーションの制限: **Android アプリ** — パッケージ名 `com.example.gpslogger` と、署名証明書の SHA-1 フィンガープリントを登録
   - API の制限: **Maps SDK for Android** のみ
   - デバッグ用の SHA-1 は `./gradlew signingReport` で確認できます（リリース用の署名鍵を使う場合は、その SHA-1 も追加）

##### アプリへの設定

`android/local.properties`（git管理外）に、取得したAPIキーを追記してビルドし直してください：

```properties
MAPS_API_KEY=取得したAPIキー
```

ビルド時に `AndroidManifest.xml` へ埋め込まれます（`app/build.gradle` が `local.properties` から読み込みます）。キーをリポジトリにコミットしないでください。未設定の場合、地図画面の Google タブには設定方法の案内が表示され、設定画面にも「APIキー: 未設定」と表示されます。

#### 選べる地図

Google 以外の地図は、WebView 上の [Leaflet](https://leafletjs.com/) 1.9.4（BSD 2-Clause License、unpkg.com から読み込み）でタイル画像を表示します。各地図の帰属表示（クレジット）は地図の右下に表示されます。

| 提供元 | 種類 | タイルURL | 最大ズーム ※ | APIキー |
|--------|------|-----------|:---:|:---:|
| Google（Android版） | 標準 / 衛星 / 衛星+地名 / 地形 | Maps SDK for Android（URL指定なし） | - | 必要 |
| Apple（iOS版） | 標準 / 衛星 / 衛星+地名 / 控えめ | MapKit（URL指定なし） | - | 不要 |
| OpenStreetMap | 標準 | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | 19 | 不要 |
| OpenStreetMap | 人道支援（HOT） | `https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png` | 19 | 不要 |
| OpenStreetMap | 地形（OpenTopoMap） | `https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png` | 17 | 不要 |
| 国土地理院 | 標準 | `https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png` | 18 | 不要 |
| 国土地理院 | 淡色 | `https://cyberjapandata.gsi.go.jp/xyz/pale/{z}/{x}/{y}.png` | 18 | 不要 |
| 国土地理院 | 白地図 | `https://cyberjapandata.gsi.go.jp/xyz/blank/{z}/{x}/{y}.png` | 14 | 不要 |
| 国土地理院 | 航空写真（全国最新写真・シームレス） | `https://cyberjapandata.gsi.go.jp/xyz/seamlessphoto/{z}/{x}/{y}.jpg` | 18 | 不要 |
| 国土地理院 | 色別標高 | `https://cyberjapandata.gsi.go.jp/xyz/relief/{z}/{x}/{y}.png` | 15 | 不要 |
| その他 | モノクロ（明・暗） | OpenStreetMap 標準と同じタイルを CSS フィルタでグレースケール化（暗は白黒反転） | 19 | 不要 |
| その他 | 衛星（Esri World Imagery） | `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}` | 18 | 不要 |

※ タイルが用意されている最大のズームレベル。それより拡大すると、そのレベルのタイルを引き伸ばして表示します。`{s}` は a / b / c のサブドメインに置き換わります。

#### 地図のライセンスと利用条件

| 地図 | ライセンス・利用条件 | 帰属表示 |
|------|------------------|----------|
| Apple（iOS版） | [Apple マップの利用規約](https://www.apple.com/legal/internet-services/maps/terms-en.html)。MapKit 経由で表示 | Apple マップのロゴと「法的情報」リンク（MapKit が自動で表示） |
| Google | [Google Maps Platform 利用規約](https://cloud.google.com/maps-platform/terms)。表示は SDK 経由に限られ、タイルの保存や加工はできません | Google のロゴ・著作権表示（SDK が自動で表示） |
| OpenStreetMap 標準 | データ: [ODbL](https://opendatacommons.org/licenses/odbl/)（[著作権とライセンス](https://www.openstreetmap.org/copyright)）。タイル: [OSMF タイル利用ポリシー](https://operations.osmfoundation.org/policies/tiles/) | © OpenStreetMap contributors |
| OSM 人道支援 | データ: ODbL。スタイル: [Humanitarian OpenStreetMap Team](https://www.hotosm.org/)。タイル配信: [OpenStreetMap France](https://www.openstreetmap.fr/)（同団体の利用条件に従う） | © OpenStreetMap contributors, Tiles style by HOT |
| OpenTopoMap | データ: ODbL・SRTM。地図スタイル: [CC-BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/)（[OpenTopoMap について](https://opentopomap.org/about)） | © OpenStreetMap contributors, SRTM, © OpenTopoMap (CC-BY-SA) |
| 国土地理院（全種類） | [国土地理院コンテンツ利用規約](https://www.gsi.go.jp/kikakuchousei/kikakuchousei40182.html)（政府標準利用規約 第2.0版に準拠。CC BY 4.0 と互換）。出典を示せば申請なしで利用可（[地理院タイル一覧](https://maps.gsi.go.jp/development/ichiran.html)） | © 国土地理院（出典：国土地理院） |
| モノクロ（明・暗） | OpenStreetMap 標準と同じ（色の加工は ODbL・タイル利用ポリシーの範囲内） | © OpenStreetMap contributors |
| Esri World Imagery | [Esri の利用規約](https://www.esri.com/en-us/legal/terms/full-master-agreement)（[World Imagery の説明](https://www.arcgis.com/home/item.html?id=10df2279f9684e4a9f6a7f08febac2a9)） | Tiles © Esri — Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community |

**利用上の注意**

- **OpenStreetMap のタイルサーバー**（標準・モノクロ）は寄付で運営されており、大量アクセスやタイルの一括ダウンロード（オフライン用の事前取得）は利用ポリシーで禁止されています。多くの人に配布するアプリでは、商用のタイル配信サービスや自前のタイルサーバーへの切り替えを検討してください。
- **OpenStreetMap France・OpenTopoMap** も個人・団体が運営する無償サービスで、大量アクセスは想定されていません。
- **Esri World Imagery** は、ArcGIS のアカウント（APIキー）なしでの利用を、非商用・少量の範囲に限っています。商用で使う・公開して配布する場合は、Esri の利用条件と、アカウントが必要かどうかを確認してください。
- 帰属表示は消さないでください（どのライセンスでも表示が条件です）。
- 利用条件は変わることがあります。配布する前に、各リンク先で最新の内容を確認してください。

#### 地図の設定

設定画面では次の項目を設定できます。

- **デフォルト地図**: 地図画面を開いたときに最初に表示する地図（既定は OpenStreetMap 標準）
- **Google マップの表示**: 交通状況・3D の建物・ズームボタンのオン／オフ
- **APIキーの状態**: 「設定済み」か「未設定」かを表示（変更はビルド時のみ）
- **Apple マップの表示**（iOS版）: 交通状況・3D（建物・地形の立体表示）のオン／オフ

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
- `NSLocationAlwaysAndWhenInUseUsageDescription` — バックグラウンド測位・終了後の記録再開
- `NSLocationWhenInUseUsageDescription` — フォアグラウンド測位
- Background Modes: **Location updates**
- `UIFileSharingEnabled` / `LSSupportsOpeningDocumentsInPlace` — 保存したCSVをファイルアプリに表示

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
│   ├── project.yml          # XcodeGen の設定（Xcode プロジェクトはここから作る）
│   ├── GPSLogger/
│   │   ├── Models/          # データモデル（LogPoint, Session, RecordMode, MapLayer）
│   │   ├── Services/        # 位置情報の取得と記録（バックグラウンド測位）
│   │   ├── Repository/      # データ保存・設定・CSVの読み書き
│   │   ├── ViewModels/      # MVVM ViewModel
│   │   └── Views/           # SwiftUI 画面
│   └── GPSLoggerTests/      # ユニットテスト
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
