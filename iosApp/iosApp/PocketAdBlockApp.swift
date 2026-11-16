import SwiftUI
import Shared

@main
struct PocketAdBlockApp: App {
    var body: some Scene {
        WindowGroup {
            // Compose draws edge to edge and keeps clear of the notch with its own insets.
            ComposeView().ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
