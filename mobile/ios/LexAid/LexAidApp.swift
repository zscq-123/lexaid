import SafariServices
import SwiftUI
import WebKit

@main
struct LexAidApp: App {
    var body: some Scene {
        WindowGroup {
            LibraryScreen()
                .preferredColorScheme(.light)
        }
    }
}

private struct LibraryScreen: View {
    @StateObject private var browser = LibraryBrowser()

    var body: some View {
        ZStack {
            WebContent(browser: browser)
                .ignoresSafeArea(.keyboard, edges: .bottom)

            if let failure = browser.failure {
                VStack(spacing: 16) {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                    Text("暂时无法打开 LexAid").font(.headline)
                    Text(failure).multilineTextAlignment(.center).foregroundStyle(.secondary)
                    Button("重新加载", action: browser.retry)
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(.background)
            } else if browser.loading {
                ProgressView("正在打开 LexAid")
                    .padding()
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
            }
        }
        .sheet(item: $browser.externalPage) { page in
            SourceBrowser(url: page.url).ignoresSafeArea()
        }
    }
}

private struct WebContent: UIViewRepresentable {
    let browser: LibraryBrowser

    func makeUIView(context: Context) -> WKWebView {
        browser.startIfNeeded()
        return browser.webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

private struct SourceBrowser: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}
