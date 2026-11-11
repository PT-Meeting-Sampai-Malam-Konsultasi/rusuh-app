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
        #if DEBUG
        KoinIosKt.doInitKoin(environmentName: "Mock")
        #else
        KoinIosKt.doInitKoin(environmentName: "Production")
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
