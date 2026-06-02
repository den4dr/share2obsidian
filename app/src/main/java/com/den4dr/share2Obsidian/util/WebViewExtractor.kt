package com.den4dr.share2Obsidian.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.den4dr.share2Obsidian.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
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
                    fun onExtracted(json: String) {
                        if (continuation.isActive) {
                            val result = try {
                                val obj = JSONObject(json)
                                WebViewExtractionResult(
                                    bodyText = obj.optString("body").takeIf { it.isNotBlank() },
                                    ogTitle = obj.optString("ogTitle"),
                                    ogDescription = obj.optString("ogDescription"),
                                    publishedTime = obj.optString("publishedTime"),
                                    modifiedTime = obj.optString("modifiedTime"),
                                    author = obj.optString("author"),
                                )
                            } catch (e: Exception) {
                                WebViewExtractionResult(bodyText = null)
                            }
                            continuation.resume(result)
                        }
                    }
                }
                webView.addJavascriptInterface(jsInterface, "AndroidBridge")

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("""
                            (function() {
                                function getMeta(selectors) {
                                    for (var s of selectors) {
                                        var el = document.querySelector(s);
                                        if (el) {
                                            var v = el.getAttribute('content') || el.getAttribute('value') || el.innerText;
                                            if (v && v.trim()) return v.trim();
                                        }
                                    }
                                    return '';
                                }
                                var result = {
                                    body: document.body ? document.body.innerText : '',
                                    ogTitle: getMeta(["meta[property='og:title']","meta[name='twitter:title']","title"]),
                                    ogDescription: getMeta(["meta[property='og:description']","meta[name='description']"]),
                                    publishedTime: getMeta(["meta[property='article:published_time']","meta[itemprop='datePublished']"]),
                                    modifiedTime: getMeta(["meta[property='article:modified_time']","meta[itemprop='dateModified']"]),
                                    author: getMeta(["meta[name='author']","meta[property='article:author']","meta[property='og:site_name']"])
                                };
                                AndroidBridge.onExtracted(JSON.stringify(result));
                            })();
                        """.trimIndent()) { }
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
