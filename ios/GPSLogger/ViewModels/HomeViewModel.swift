import Foundation
import Combine

class HomeViewModel: ObservableObject {

    @Published var isRecording = false
    @Published var currentSession: Session?
    @Published var latestPoint: LogPoint?
    @Published var elapsedSeconds: Int = 0

    private let repo: LocationRepository
    private let locationService: LocationService
    private var cancellables = Set<AnyCancellable>()
    private var timer: Timer?
    private var intervalSeconds: Double = 1.0
    private var lastSavedTime: Date = Date()

    init(repo: LocationRepository, locationService: LocationService) {
        self.repo = repo
        self.locationService = locationService
        checkIncompleteSession()
        subscribeToLocation()
    }

    private func checkIncompleteSession() {
        if let session = repo.incompleteSession() {
            currentSession = session
            // UIで再開確認ダイアログを表示するためのフラグとして使える
        }
    }

    private func subscribeToLocation() {
        locationService.locationPublisher
            .sink { [weak self] location in
                guard let self, self.isRecording,
                      let session = self.currentSession else { return }
                // 記録間隔チェック
                guard Date().timeIntervalSince(self.lastSavedTime) >= self.intervalSeconds else { return }
                self.lastSavedTime = Date()
                let point = LogPoint(
                    sessionId: session.id,
                    latitude: location.coordinate.latitude,
                    longitude: location.coordinate.longitude,
                    altitude: location.verticalAccuracy > 0 ? location.altitude : nil,
                    accuracy: location.horizontalAccuracy,
                    speed: location.speed > 0 ? location.speed : nil,
                    sourceType: self.locationService.currentSourceType
                )
                self.repo.saveLogPoint(point)
                self.latestPoint = point
            }
            .store(in: &cancellables)
    }

    func startRecording() {
        let session = repo.startSession()
        currentSession = session
        isRecording = true
        elapsedSeconds = 0
        lastSavedTime = Date()
        locationService.startUpdates(intervalSeconds: intervalSeconds)
        startTimer()
    }

    func stopRecording() {
        guard let session = currentSession else { return }
        locationService.stopUpdates()
        repo.stopSession(id: session.id)
        isRecording = false
        stopTimer()
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.elapsedSeconds += 1
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    func formatElapsed() -> String {
        let s = elapsedSeconds
        return String(format: "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}
