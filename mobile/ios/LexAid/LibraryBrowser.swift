import Combine
import WebKit

struct SourcePage: Identifiable {
    let url: URL
    var id: URL { url }
}

@MainActor
final class LibraryBrowser: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    private static let home = URL(string: "https://154.36.185.110:18443/")!
    let webView: WKWebView
    @Published private(set) var loading = true
    @Published private(set) var failure: String?
    @Published var externalPage: SourcePage?
    private var retryURL = home

    override init() {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.keyboardDismissMode = .interactive
    }

    func startIfNeeded() {
        if webView.url == nil && !webView.isLoading { load(Self.home) }
    }

    func retry() {
        failure = nil
        loading = true
        load(retryURL)
    }

    private func load(_ url: URL) {
        webView.load(URLRequest(url: url, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 35))
    }

    private func isSiteURL(_ url: URL) -> Bool {
        url.scheme == Self.home.scheme && url.host == Self.home.host && url.port == Self.home.port
            && url.user == nil && url.password == nil
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction) async -> WKNavigationActionPolicy {
        guard let url = action.request.url else { return .cancel }
        if isSiteURL(url) {
            if action.targetFrame == nil {
                load(url)
                return .cancel
            }
            if action.targetFrame?.isMainFrame == true { retryURL = url }
            return .allow
        }
        if action.navigationType == .linkActivated, action.sourceFrame.isMainFrame,
           action.targetFrame?.isMainFrame != false,
           url.scheme == "https", url.host != nil, url.user == nil, url.password == nil {
            externalPage = SourcePage(url: url)
        }
        return .cancel
    }

    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        if response.isForMainFrame {
            guard let url = response.response.url, isSiteURL(url) else { return .cancel }
            if let http = response.response as? HTTPURLResponse, http.statusCode >= 400 {
                showFailure("服务器返回了 HTTP \(http.statusCode)，可以稍后重试。")
                return .cancel
            }
        }
        return .allow
    }

    @available(iOS 15.0, *)
    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin,
                 initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType,
                 decisionHandler: @escaping (WKPermissionDecision) -> Void) {
        if type == .microphone, origin.host == Self.home.host, origin.port == (Self.home.port ?? -1) {
            decisionHandler(.grant)
        } else {
            decisionHandler(.deny)
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loading = false
        failure = nil
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        report(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        report(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        retryURL = webView.url.flatMap { isSiteURL($0) ? $0 : nil } ?? Self.home
        showFailure("页面已中断，重新加载即可继续。")
    }

    private func report(_ error: Error) {
        let networkError = error as NSError
        if networkError.domain == NSURLErrorDomain && networkError.code == NSURLErrorCancelled { return }
        showFailure("请检查网络连接，然后重新加载。")
    }

    private func showFailure(_ message: String) {
        loading = false
        failure = message
    }
}
