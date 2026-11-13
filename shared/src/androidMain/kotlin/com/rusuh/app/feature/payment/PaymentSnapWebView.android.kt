package com.rusuh.app.feature.payment

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
@Suppress("LongParameterList")
actual fun PaymentSnapWebView(
    url: String,
    bookingId: String,
    modifier: Modifier,
    onClose: () -> Unit,
    onLoading: (Boolean) -> Unit,
    onLoadError: () -> Unit,
    onNotice: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onProgressChanged(
                            view: WebView,
                            newProgress: Int,
                        ) {
                            onLoading(newProgress < 100)
                        }
                    }
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean =
                            when (val decision = decidePaymentNavigation(request.url.toString(), bookingId)) {
                                PaymentNavigationDecision.Stay -> false
                                PaymentNavigationDecision.Close -> {
                                    onClose()
                                    true
                                }
                                PaymentNavigationDecision.Block -> true
                                is PaymentNavigationDecision.OpenExternal -> {
                                    try {
                                        val uri = Uri.parse(decision.url)
                                        val externalIntent =
                                            if (uri.scheme == "intent") {
                                                Intent.parseUri(decision.url, Intent.URI_INTENT_SCHEME)
                                            } else {
                                                Intent(Intent.ACTION_VIEW, uri)
                                            }
                                        context.startActivity(externalIntent)
                                    } catch (_: Exception) {
                                        onNotice()
                                    }
                                    true
                                }
                            }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) onLoadError()
                        }

                        override fun onPageStarted(
                            view: WebView,
                            url: String,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            onLoading(true)
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String,
                        ) {
                            onLoading(false)
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: android.net.http.SslError,
                        ) {
                            handler.cancel()
                            onLoadError()
                        }
                    }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}
