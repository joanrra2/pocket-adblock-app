@file:OptIn(ExperimentalForeignApi::class)

package io.github.joanrra2.pocketadblock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.github.joanrra2.pocketadblock.board.BoardAddress
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodHTTPBasic
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLRequest
import platform.Foundation.create
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.UIKit.UIColor
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

/**
 * The board's page in a WKWebView. The board's 401 challenge is answered with the saved
 * login in webView(_:didReceive:completionHandler:); [fetchAuthScript] adds the same
 * header to fetch() calls as a safety net. Links that leave the board open in Safari.
 * (iOS has no back button: the top bar's Back closes the page.)
 */
@Composable
actual fun BoardWebView(
    address: BoardAddress,
    user: String,
    password: String,
    reloadKey: Int,
    onAuthRejected: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    val authRejected by rememberUpdatedState(onAuthRejected)
    val openExternal by rememberUpdatedState(onOpenExternal)
    val delegate = remember(address, user, password) {
        WebDelegate(address, user, password, onAuthRejected = { authRejected() }, onOpenExternal = { openExternal(it) })
    }
    val webView = remember(delegate) {
        val config = WKWebViewConfiguration()
        config.userContentController.addUserScript(
            WKUserScript(
                source = fetchAuthScript(address, user, password),
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = true,
            ),
        )
        WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
            navigationDelegate = delegate
            UIDelegate = delegate
            setOpaque(false)
            backgroundColor = UIColor(red = 11.0 / 255, green = 15.0 / 255, blue = 21.0 / 255, alpha = 1.0)
            NSURL.URLWithString(address.url())?.let { loadRequest(NSURLRequest(uRL = it)) }
        }
    }
    LaunchedEffect(webView, reloadKey) { if (reloadKey > 0) webView.reload() }
    UIKitView(factory = { webView }, modifier = modifier)
}

private class WebDelegate(
    private val address: BoardAddress,
    private val user: String,
    private val password: String,
    private val onAuthRejected: () -> Unit,
    private val onOpenExternal: (String) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {

    private fun isBoard(url: NSURL?): Boolean {
        if (url == null) return false
        val port = url.port?.intValue ?: 80
        return url.scheme == "http" && url.host.equals(address.host, ignoreCase = true) && port == address.port
    }

    override fun webView(
        webView: WKWebView,
        didReceiveAuthenticationChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        val space = didReceiveAuthenticationChallenge.protectionSpace
        val basicFromBoard = space.authenticationMethod == NSURLAuthenticationMethodHTTPBasic &&
            space.host.equals(address.host, ignoreCase = true)
        when {
            !basicFromBoard -> completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            didReceiveAuthenticationChallenge.previousFailureCount < 2 -> completionHandler(
                NSURLSessionAuthChallengeUseCredential,
                NSURLCredential.create(user = user, password = password, persistence = NSURLCredentialPersistence.NSURLCredentialPersistenceForSession),
            )
            else -> {
                completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                onAuthRejected()
            }
        }
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL
        if (isBoard(url) || url?.scheme == "about") {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            url?.absoluteString?.let(onOpenExternal)
        }
    }

    // window.open(): WKWebView ignores it unless the app handles it. Open it outside.
    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        forNavigationAction.request.URL?.absoluteString?.let(onOpenExternal)
        return null
    }
}
