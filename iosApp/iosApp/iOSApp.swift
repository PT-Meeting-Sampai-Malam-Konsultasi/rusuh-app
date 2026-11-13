import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

@main
struct iOSApp: App {
    init() {
        // Debug builds use the live API by default. Scheme environment variables (Xcode: Edit Scheme > Run):
        //   MOCK_API=true       use the local Mockoon server (http://localhost:3000)
        //   BACKEND_MODE=Stub   offline demo data
        // Release builds always use the live API.
        #if DEBUG
        let environment = ProcessInfo.processInfo.environment
        KoinIosKt.doInitKoin(
            isDebug: true,
            backendModeName: environment["BACKEND_MODE"],
            useMockServer: environment["MOCK_API"] == "true"
        )
        #else
        KoinIosKt.doInitKoin(isDebug: false, backendModeName: nil, useMockServer: false)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
