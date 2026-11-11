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
        // Mock or real host: NetworkConfig.MOCK_API in the shared module (debug only; release is always real).
        #if DEBUG
        // Optional offline demo: set BACKEND_MODE=Stub in the Xcode scheme environment.
        KoinIosKt.doInitKoin(
            isDebug: true,
            backendModeName: ProcessInfo.processInfo.environment["BACKEND_MODE"]
        )
        #else
        KoinIosKt.doInitKoin(isDebug: false, backendModeName: nil)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
