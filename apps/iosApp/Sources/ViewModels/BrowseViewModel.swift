import Foundation
import SharedKit

@MainActor
final class BrowseViewModel: ObservableObject {
    @Published private(set) var items: [ContentSummary] = []
    @Published var selectedType: ContentType?
    @Published private(set) var isLoading = true
    @Published private(set) var isLoadingMore = false
    @Published private(set) var hasMore = false
    @Published private(set) var error: String?

    private var cursor: String?
    private let contentRepository: ContentRepository

    init(contentRepository: ContentRepository) {
        self.contentRepository = contentRepository
        load()
    }

    func selectType(_ type: ContentType?) {
        selectedType = type
        items = []
        cursor = nil
        hasMore = false
        load()
    }

    func load() {
        Task {
            isLoading = true
            error = nil
            do {
                let query = ContentQuery(type: selectedType, genre: nil, releaseYear: nil, cursor: nil, limit: 24)
                let page = try await ThrowingWrappersKt.listOrThrow(contentRepository, query: query)
                items = (page.items as! [ContentSummary])
                hasMore = page.hasMore
                cursor = page.nextCursor
                isLoading = false
            } catch {
                self.error = readableMessage(error)
                isLoading = false
            }
        }
    }

    func loadMore() {
        guard let cursor, !isLoadingMore, hasMore else { return }
        Task {
            isLoadingMore = true
            do {
                let query = ContentQuery(type: selectedType, genre: nil, releaseYear: nil, cursor: cursor, limit: 24)
                let page = try await ThrowingWrappersKt.listOrThrow(contentRepository, query: query)
                items += (page.items as! [ContentSummary])
                hasMore = page.hasMore
                self.cursor = page.nextCursor
                isLoadingMore = false
            } catch {
                isLoadingMore = false
            }
        }
    }
}
