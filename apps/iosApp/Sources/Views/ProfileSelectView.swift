import SwiftUI
import SharedKit

struct ProfileSelectView: View {
    @StateObject var viewModel: ProfileViewModel
    @State private var newProfileName = ""

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 24) {
                Spacer()
                Text("Who's watching?")
                    .font(.largeTitle.bold())
                    .foregroundColor(.white)

                switch viewModel.uiState {
                case .loading:
                    ProgressView().tint(.brand)
                case .error(let message):
                    Text(message).foregroundColor(.red)
                case .loaded(let profiles):
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 16) {
                            ForEach(profiles, id: \.id) { profile in
                                Button { viewModel.selectProfile(profile.id) } label: {
                                    VStack {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.appSurface)
                                            .frame(width: 96, height: 96)
                                            .overlay(Text(String(profile.name.prefix(1))).font(.title).foregroundColor(.white))
                                        Text(profile.name).foregroundColor(.white).font(.footnote)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 24)
                    }
                }

                VStack(spacing: 12) {
                    TextField("", text: $newProfileName, prompt: Text("New profile name").foregroundColor(.gray))
                        .textFieldStyle(.plain)
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 8).stroke(Color.gray))
                        .foregroundColor(.white)
                        .frame(maxWidth: 320)

                    Button {
                        viewModel.createProfile(name: newProfileName) { _ in newProfileName = "" }
                    } label: {
                        Text("Add profile").frame(maxWidth: 320).padding()
                    }
                    .background(RoundedRectangle(cornerRadius: 24).fill(Color.appSurface))
                    .foregroundColor(.white)
                    .disabled(viewModel.isCreating || newProfileName.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(.horizontal, 24)

                Spacer()
                Spacer()
            }
        }
    }
}
