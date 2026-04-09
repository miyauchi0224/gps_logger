import SwiftUI

struct HomeView: View {
    @ObservedObject var vm: HomeViewModel
    @ObservedObject var locationService: LocationService

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                // ロギング中インジケーター（緑バー）
                if vm.isRecording {
                    HStack {
                        Image(systemName: "location.fill")
                        Text("GPSロギング中")
                            .fontWeight(.bold)
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(Color.green.gradient)
                }

                Spacer()

                // 経過時間
                if vm.isRecording {
                    Text(vm.formatElapsed())
                        .font(.system(size: 56, weight: .bold, design: .monospaced))
                        .foregroundColor(.green)
                } else {
                    Text("記録を開始してください")
                        .foregroundColor(.secondary)
                        .font(.headline)
                }

                // 最新ポイント情報カード
                if let point = vm.latestPoint {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("最新ポイント", systemImage: "mappin.circle.fill")
                            .fontWeight(.bold)
                        Divider()
                        HStack {
                            Text("緯度: \(String(format: "%.6f", point.latitude))")
                            Spacer()
                            Text("経度: \(String(format: "%.6f", point.longitude))")
                        }
                        if let speed = point.speed {
                            Text("速度: \(String(format: "%.1f", speed * 3.6)) km/h")
                        }
                        Text(point.sourceType == .gps ? "GPS測位" : "WiFi測位")
                            .foregroundColor(point.sourceType == .gps ? .blue : .orange)
                            .fontWeight(.semibold)
                    }
                    .padding()
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal)
                }

                Spacer()

                // 記録開始・停止ボタン
                if locationService.authorizationStatus == .notDetermined {
                    Button("GPS権限を許可する") {
                        locationService.requestPermission()
                    }
                    .buttonStyle(.borderedProminent)
                } else {
                    Button(action: {
                        if vm.isRecording { vm.stopRecording() } else { vm.startRecording() }
                    }) {
                        Text(vm.isRecording ? "停止" : "記録開始")
                            .font(.title2.bold())
                            .foregroundColor(.white)
                            .frame(width: 140, height: 140)
                            .background(vm.isRecording ? Color.red : Color.green)
                            .clipShape(Circle())
                    }
                }

                Spacer()
            }
            .navigationTitle("GPSLogger")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink(destination: SessionListView(repo: vm.repo)) {
                        Image(systemName: "list.bullet")
                    }
                }
            }
            .ignoresSafeArea(edges: .top)
        }
    }
}
