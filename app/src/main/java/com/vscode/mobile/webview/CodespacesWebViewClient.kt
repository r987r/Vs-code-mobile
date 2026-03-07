package com.vscode.mobile.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URI

/**
 * Custom [WebViewClient] that:
 *  1. Enforces an allowlist of trusted domains — blocks navigation to unexpected hosts.
 *  2. Denies all SSL errors (no bypassing certificate issues).
 *  3. Fires progress callbacks to the hosting activity.
 *  4. Injects mobile-optimisation JavaScript after a page finishes loading.
 *
 * Security design:
 *  - [ALLOWED_HOSTS] is the single source of truth for trusted domains.
 *  - Any redirect/navigation outside these hosts is cancelled and the URL is opened
 *    in the system browser via [onBlockedNavigation].
 *  - SSL errors are always denied; the handler is never called with proceed().
 */
class CodespacesWebViewClient(
    private val onPageStarted: (url: String) -> Unit = {},
    private val onPageFinished: (url: String) -> Unit = {},
    private val onPageError: (errorCode: Int, description: String, url: String) -> Unit = { _, _, _ -> },
    private val onBlockedNavigation: (url: String) -> Unit = {},
    private val onInjectJavaScript: (webView: WebView) -> Unit = {}
) : WebViewClient() {

    companion object {
        private const val TAG = "CodespacesWebViewClient"

        /**
         * Allowlist of trusted hostname suffixes.
         * Navigation to any host NOT in this list is blocked.
         */
        private val ALLOWED_HOSTS = setOf(
            "github.com",
            "github.dev",
            "app.github.dev",
            "githubpreview.dev",
            "github.githubassets.com",
            "avatars.githubusercontent.com",
            "raw.githubusercontent.com",
            "objects.githubusercontent.com",
            // Codespace-specific subdomains follow *.app.github.dev or *.githubpreview.dev
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return true
        val scheme = request.url?.scheme ?: ""

        // Allow non-HTTP schemes to fall through (mailto:, etc.) — they will be handled by system
        if (scheme != "https" && scheme != "http") {
            onBlockedNavigation(url)
            return true
        }

        // Block plain HTTP
        if (scheme == "http") {
            Log.w(TAG, "Blocked HTTP navigation: $url")
            return true
        }

        return if (isTrustedHost(request.url?.host)) {
            false // Allow navigation within the WebView
        } else {
            Log.w(TAG, "Blocked navigation to untrusted host: $url")
            onBlockedNavigation(url)
            true
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Inject our mobile JS bridge once the page DOM is ready
        onInjectJavaScript(view)
        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onPageError(error.errorCode, error.description?.toString() ?: "Unknown error", request.url?.toString() ?: "")
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            onPageError(errorResponse.statusCode, "HTTP ${errorResponse.statusCode}", request.url?.toString() ?: "")
        }
    }

    /**
     * NEVER bypass SSL errors — deny unconditionally.
     * This ensures certificate-pinning failures also result in a hard stop.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        Log.e(TAG, "SSL error on ${error.url}: primary error ${error.primaryError}")
        handler.cancel()
        onPageError(-1, "SSL certificate error", error.url ?: "")
    }

    /** Returns true if [host] (or a parent domain) is in [ALLOWED_HOSTS]. */
    private fun isTrustedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return ALLOWED_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }
}
