import SwiftUI
import AVKit
import SharedKit

struct PlayerView: View {
    @StateObject var viewModel: PlayerViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var adapter = AVPlayerPlaybackAdapter()
    @State private var reportTask: Task<Void, Never>?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            switch viewModel.uiState {
            case .loading:
                LoadingView()
            case .error(let message):
                ErrorView(message: message)
            case .ready(let grant):
                VideoPlayer(player: adapter.player)
                    .ignoresSafeArea()
                    .onAppear {
                        adapter.prepare(grant: grant)
                        adapter.play()
                        startReporting()
                    }
                    .onDisappear {
                        reportTask?.cancel()
                        adapter.release()
                    }
            }

            VStack {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.white)
                            .font(.title2)
                    }
                    .padding()
                    Spacer()
                }
                Spacer()
            }
        }
    }

    private func startReporting() {
        reportTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                guard !Task.isCancelled else { return }
                viewModel.reportPosition(positionSeconds: adapter.currentPositionSeconds, durationSeconds: adapter.durationSeconds)
            }
        }
    }
}
