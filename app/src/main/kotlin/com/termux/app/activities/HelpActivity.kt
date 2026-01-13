package com.termux.app.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.termux.shared.termux.TermuxConstants

/**
 * Basic embedded browser for viewing help pages.
 */
class HelpActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val progressLayout = RelativeLayout(this)
        val lParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = lParams
        }
        progressLayout.addView(progressBar)

        webView = WebView(this).apply {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            clearCache(true)
        }
        setContentView(progressLayout)

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url == TermuxConstants.TERMUX_WIKI_URL || 
                    url.startsWith("${TermuxConstants.TERMUX_WIKI_URL}/")) {
                    // Inline help
                    setContentView(progressLayout)
                    return false
                }

                return try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    true
                } catch (e: ActivityNotFoundException) {
                    // Android TV does not have a system browser
                    setContentView(progressLayout)
                    false
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                setContentView(webView)
            }
        }
        webView.loadUrl(TermuxConstants.TERMUX_WIKI_URL)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
