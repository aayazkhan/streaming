import Foundation
import SharedKit

@MainActor
final class SearchViewModel: ObservableObject {
    @Published var query = "" {
        didSet { onQueryChange() }
    }
    @Published private(set) var results: [ContentSummary] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?

    private let searchRepository: SearchRepository
    private var debounceTask: Task<Void, Never>?

    init(searchRepository: SearchRepository) {
        self.searchRepository = searchRepository
    }

    private func onQueryChange() {
        debounceTask?.cancel()
        let text = query
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            results = []
            isLoading = false
            return
        }
        debounceTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            isLoading = true
            error = nil
            do {
                let searchQuery = SearchQuery(query: text, type: nil, genre: nil, releaseYear: nil, cursor: nil, limit: 20)
                let result = try await ThrowingWrappersKt.searchOrThrow(searchRepository, query: searchQuery, userId: nil)
                results = (result.items as! [ContentSummary])
                isLoading = false
            } catch {
                self.error = readableMessage(error)
                isLoading = false
            }
        }
    }
}
