import SwiftUI
import SharedKit

struct LoadingView: View {
    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            ProgressView().tint(.brand)
        }
    }
}

struct ErrorView: View {
    let message: String
    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            Text(message)
                .foregroundColor(.red)
                .multilineTextAlignment(.center)
                .padding(24)
        }
    }
}

struct ContentCardView: View {
    let content: ContentSummary
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack {
                RoundedRectangle(cornerRadius: 6).fill(Color.appSurface)
                if let posterUrl = content.posterUrl, let url = URL(string: posterUrl) {
                    AsyncImage(url: url) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.appSurface
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                } else {
                    Text(content.title)
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .padding(8)
                        .foregroundColor(.white)
                }
            }
            .frame(width: 120, height: 180)
            Text(content.title)
                .font(.caption)
                .foregroundColor(.white)
                .lineLimit(1)
                .frame(width: 120, alignment: .leading)
        }
    }
}

struct ContentRowView: View {
    let title: String
    let items: [ContentSummary]
    let onSelect: (ContentSummary) -> Void

    var body: some View {
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .font(.headline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(items, id: \.id) { item in
                            Button { onSelect(item) } label: {
                                ContentCardView(content: item)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
            .padding(.vertical, 8)
        }
    }
}
