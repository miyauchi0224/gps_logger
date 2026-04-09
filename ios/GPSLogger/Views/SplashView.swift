import SwiftUI

/// アプリ起動時のスプラッシュ画面
struct SplashView: View {
    @ObservedObject var repo: LocationRepository
    @ObservedObject var locationService: LocationService
    @State private var isActive = false
    @State private var showResumeAlert = false
    @State private var incompleteSession: Session?

    var body: some View {
        if isActive {
            let vm = HomeViewModel(repo: repo, locationService: locationService)
            HomeView(vm: vm, locationService: locationService)
        } else {
            ZStack {
                Color.green.ignoresSafeArea()
                VStack(spacing: 16) {
                    Image(systemName: "location.fill")
                        .font(.system(size: 80))
                        .foregroundColor(.white)
                    Text("GPSLogger")
                        .font(.largeTitle.bold())
                        .foregroundColor(.white)
                }
            }
            .onAppear {
                incompleteSession = repo.incompleteSession()
                if incompleteSession != nil {
                    showResumeAlert = true
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        isActive = true
                    }
                }
            }
            .alert("前回の記録が残っています", isPresented: $showResumeAlert) {
                Button("続きから再開") { isActive = true }
                Button("破棄して新規開始") {
                    if let s = incompleteSession { repo.deleteSession(id: s.id) }
                    isActive = true
                }
            } message: {
                Text("「\(incompleteSession?.name ?? "")」が完了していません。")
            }
        }
    }
}
