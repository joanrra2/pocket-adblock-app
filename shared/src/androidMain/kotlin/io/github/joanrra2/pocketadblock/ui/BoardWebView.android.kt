package io.github.joanrra2.pocketadblock.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.joanrra2.pocketadblock.board.BoardAddress

private const val TAG = "PocketWebView"

/**
 * The board's page in an Android WebView. The page uses the browser's Basic auth (its
 * fetch() calls send no credentials), so the WebView answers the board's 401 challenge
 * with the saved user and password in [WebViewClient.onReceivedHttpAuthRequest].
 */
@SuppressLint("SetJavaScriptEnabled")
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
    val context = LocalContext.current
    val authRejected by rememberUpdatedState(onAuthRejected)
    val openExternal by rememberUpdatedState(onOpenExternal)
    val exit by rememberUpdatedState(onExit)

    val webView = remember(address, user, password) {
        WebView(context).apply {
            setBackgroundColor(Tokens.bg.toArgb())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                // A wrong password makes every request ask again; stop after a few.
                private var challenges = 0

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    challenges = 0
                }

                override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                    challenges++
                    Log.d(TAG, "auth challenge #$challenges from $host realm=$realm")
                    if (host.equals(address.host, ignoreCase = true) && challenges <= MAX_CHALLENGES) {
                        handler.proceed(user, password)
                    } else {
                        handler.cancel()
                        authRejected()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    val port = if (u.port == -1) 80 else u.port
                    val sameBoard = u.scheme == "http" && u.host.equals(address.host, ignoreCase = true) && port == address.port
                    if (sameBoard) return false
                    openExternal(u.toString())
                    return true
                }
            }
            loadUrl(address.url())
        }
    }

    LaunchedEffect(webView, reloadKey) { if (reloadKey > 0) webView.reload() }
    BackHandler { if (webView.canGoBack()) webView.goBack() else exit() }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private const val MAX_CHALLENGES = 12
