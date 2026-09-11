import SwiftUI

struct RegisterView: View {
    @ObservedObject var authViewModel: AuthViewModel
    let onNavigateToLogin: () -> Void

    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Spacer()
                Text("Create an account")
                    .font(.largeTitle.bold())
                    .foregroundColor(.brand)

                TextField("", text: $displayName, prompt: Text("Display name").foregroundColor(.gray))
                    .textFieldStyle(.plain)
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 8).stroke(Color.gray))
                    .foregroundColor(.white)

                TextField("", text: $email, prompt: Text("Email").foregroundColor(.gray))
                    .textFieldStyle(.plain)
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 8).stroke(Color.gray))
                    .foregroundColor(.white)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
                    .autocorrectionDisabled()

                SecureField("", text: $password, prompt: Text("Password").foregroundColor(.gray))
                    .textFieldStyle(.plain)
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 8).stroke(Color.gray))
                    .foregroundColor(.white)

                if let error = authViewModel.formError {
                    Text(error).foregroundColor(.red).font(.footnote)
                }

                Button {
                    authViewModel.register(email: email, password: password, displayName: displayName)
                } label: {
                    Text("Create account")
                        .frame(maxWidth: .infinity)
                        .padding()
                }
                .background(RoundedRectangle(cornerRadius: 24).fill(Color.appSurface))
                .foregroundColor(.white)
                .disabled(authViewModel.isSubmitting || email.isEmpty || password.isEmpty || displayName.isEmpty)

                Button("Already have an account? Sign in") { onNavigateToLogin() }
                    .foregroundColor(.brand)
                    .frame(maxWidth: .infinity, alignment: .center)

                Spacer()
                Spacer()
            }
            .padding(24)
        }
    }
}
