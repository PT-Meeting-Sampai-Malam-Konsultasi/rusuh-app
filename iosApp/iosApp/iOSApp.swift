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
        let infoPlist = Bundle.main.infoDictionary ?? [:]
        let environmentName = infoPlist["RusuhEnvironment"] as? String ?? "Staging"
        let allowDebugOptionsString = infoPlist["RusuhAllowDebugOptions"] as? String ?? "NO"
        let allowDebugOptions = allowDebugOptionsString == "YES"

        // Debug builds can use scheme environment variables to switch backend modes:
        //   MOCK_API=true       use the local Mockoon server (http://localhost:3000)
        //   BACKEND_MODE=Stub   offline demo data
        // These only work when the scheme allows debug options (Staging); Production ignores them.
        // Release builds always use the production API.
        #if DEBUG
        let environment = ProcessInfo.processInfo.environment
        KoinIosKt.doInitKoin(
            isDebug: true,
            environmentName: environmentName,
            allowDebugOptions: allowDebugOptions,
            backendModeName: allowDebugOptions ? environment["BACKEND_MODE"] : nil,
            useMockServer: allowDebugOptions && environment["MOCK_API"] == "true"
        )
        #else
        KoinIosKt.doInitKoin(
            isDebug: false,
            environmentName: environmentName,
            allowDebugOptions: false,
            backendModeName: nil,
            useMockServer: false
        )
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    PaymentReturnLinkKt.handlePaymentReturnUrl(url: url.absoluteString)
                }
        }
    }
}
