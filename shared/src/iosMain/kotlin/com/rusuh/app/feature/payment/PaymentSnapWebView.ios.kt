package com.rusuh.app.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@Composable
@Suppress("LongParameterList")
@OptIn(ExperimentalForeignApi::class)
actual fun PaymentSnapWebView(
    url: String,
    bookingId: String,
    modifier: Modifier,
    onClose: () -> Unit,
    onLoading: (Boolean) -> Unit,
    onLoadError: () -> Unit,
    onNotice: () -> Unit,
) {
    UIKitView(
        modifier = modifier,
        factory = {
            val config = WKWebViewConfiguration()
            config.defaultWebpagePreferences.allowsContentJavaScript = true
            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config)
            webView.navigationDelegate =
                object : NSObject(), WKNavigationDelegateProtocol {
                    override fun webView(
                        webView: WKWebView,
                        didStartProvisionalNavigation: platform.WebKit.WKNavigation?,
                    ) {
                        onLoading(true)
                    }

                    override fun webView(
                        webView: WKWebView,
                        didFinishNavigation: platform.WebKit.WKNavigation?,
                    ) {
                        onLoading(false)
                    }

                    override fun webView(
                        webView: WKWebView,
                        didFailProvisionalNavigation: platform.WebKit.WKNavigation?,
                        withError: platform.Foundation.NSError,
                    ) {
                        onLoading(false)
                        onLoadError()
                    }

                    override fun webView(
                        webView: WKWebView,
                        decidePolicyForNavigationAction: WKNavigationAction,
                        decisionHandler: (WKNavigationActionPolicy) -> Unit,
                    ) {
                        val target =
                            decidePolicyForNavigationAction.request.URL?.absoluteString ?: run {
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                                return
                            }
                        when (val decision = decidePaymentNavigation(target, bookingId)) {
                            PaymentNavigationDecision.Stay ->
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                            PaymentNavigationDecision.Close -> {
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                                onClose()
                            }
                            PaymentNavigationDecision.Block ->
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                            is PaymentNavigationDecision.OpenExternal -> {
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                                val external = NSURL.URLWithString(decision.url)
                                if (external != null) {
                                    UIApplication.sharedApplication.openURL(
                                        external,
                                        emptyMap<Any?, Any?>(),
                                    ) { opened ->
                                        if (!opened) onNotice()
                                    }
                                } else {
                                    onNotice()
                                }
                            }
                        }
                    }
                }
            webView.UIDelegate =
                object : NSObject(), WKUIDelegateProtocol {
                    override fun webView(
                        webView: WKWebView,
                        createWebViewWithConfiguration: WKWebViewConfiguration,
                        forNavigationAction: WKNavigationAction,
                        windowFeatures: platform.WebKit.WKWindowFeatures,
                    ): WKWebView? {
                        forNavigationAction.request.URL?.let {
                            webView.loadRequest(platform.Foundation.NSURLRequest(it))
                        }
                        return null
                    }
                }
            webView.loadRequest(platform.Foundation.NSURLRequest(NSURL.URLWithString(url)!!))
            webView
        },
    )
}
