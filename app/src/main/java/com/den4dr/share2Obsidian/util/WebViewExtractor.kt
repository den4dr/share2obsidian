package com.den4dr.share2Obsidian.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.den4dr.share2Obsidian.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

open class WebViewExtractor(
    private val context: Context,
    private val timeoutMs: Long = AppConfig.WEBVIEW_TIMEOUT_MS
) {
    @SuppressLint("SetJavaScriptEnabled")
    open suspend fun extract(url: String): WebViewExtractionResult {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = true

                val jsInterface = object {
                    @JavascriptInterface
                    fun onTextExtracted(text: String) {
                        if (continuation.isActive) {
                            continuation.resume(WebViewExtractionResult(bodyText = text))
                        }
                    }
                }
                webView.addJavascriptInterface(jsInterface, "AndroidBridge")

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "AndroidBridge.onTextExtracted(document.body.innerText)"
                        ) { }
                    }
                }
                webView.loadUrl(url)

                continuation.invokeOnCancellation {
                    webView.destroy()
                }
            }
        } ?: WebViewExtractionResult(bodyText = null)
    }
}
