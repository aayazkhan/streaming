import SwiftUI

struct LoginView: View {
    @ObservedObject var authViewModel: AuthViewModel
    let onNavigateToRegister: () -> Void

    @State private var email = ""
    @State private var password = ""

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Spacer()
                Text("Sign in")
                    .font(.largeTitle.bold())
                    .foregroundColor(.brand)

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
                    authViewModel.login(email: email, password: password)
                } label: {
                    Text("Sign in")
                        .frame(maxWidth: .infinity)
                        .padding()
                }
                .background(RoundedRectangle(cornerRadius: 24).fill(Color.appSurface))
                .foregroundColor(.white)
                .disabled(authViewModel.isSubmitting || email.isEmpty || password.isEmpty)

                Button("New here? Create an account") { onNavigateToRegister() }
                    .foregroundColor(.brand)
                    .frame(maxWidth: .infinity, alignment: .center)

                Spacer()
                Spacer()
            }
            .padding(24)
        }
    }
}
