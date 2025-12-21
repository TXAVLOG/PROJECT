package gc.txa.demo.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import gc.txa.demo.R
import gc.txa.demo.databinding.DialogChangelogBinding
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.core.TXAFormat

class TXAChangelogDialog(private val context: Context) {

    private var dialog: AlertDialog? = null
    private var binding: DialogChangelogBinding? = null

    fun show(
        title: String = "Changelog",
        changelog: String,
        versionName: String,
        updatedAt: String? = null,
        showDownloadButton: Boolean = false,
        onDownloadClick: (() -> Unit)? = null
    ) {
        binding = DialogChangelogBinding.inflate(LayoutInflater.from(context))
        
        // Setup WebView with CSS
        setupWebView(changelog)
        
        // Setup footer
        setupFooter(versionName, updatedAt)
        
        // Setup dialog buttons
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(binding?.root)
        
        if (showDownloadButton && onDownloadClick != null) {
            builder.setPositiveButton(TXATranslation.txa("txademo_update_download_now")) { _, _ ->
                onDownloadClick()
            }
            builder.setNegativeButton(TXATranslation.txa("txademo_update_later"), null)
        } else {
            builder.setPositiveButton(android.R.string.ok, null)
        }
        
        dialog?.dismiss()
        dialog = builder.create().also { it.show() }
    }

    private fun setupWebView(changelog: String) {
        val webView = binding?.webView ?: return
        
        // Handle empty changelog
        val changelogContent = if (changelog.isBlank()) {
            "<p style='color: #666; font-style: italic;'>${TXATranslation.txa("txademo_msg_info")}: No changelog available</p>"
        } else {
            changelog
        }
        
        // Try to load custom CSS from resources first
        val customCss = loadCustomCss()
        val defaultCss = getDefaultCss()
        val css = customCss.ifEmpty { defaultCss }
        
        // Wrap changelog in HTML with CSS
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>$css</style>
            </head>
            <body>
                <div class="changelog-content">$changelogContent</div>
            </body>
            </html>
        """.trimIndent()
        
        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = true
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun loadCustomCss(): String {
        return try {
            context.resources.openRawResource(R.raw.changelog_css)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getDefaultCss(): String {
        return """
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                font-size: 14px;
                line-height: 1.6;
                color: #333;
                margin: 0;
                padding: 16px;
                background-color: #fafafa;
            }
            
            .changelog-content {
                max-width: 100%;
                word-wrap: break-word;
            }
            
            h1, h2, h3, h4, h5, h6 {
                color: #1976d2;
                margin-top: 16px;
                margin-bottom: 8px;
            }
            
            h1 { font-size: 20px; }
            h2 { font-size: 18px; }
            h3 { font-size: 16px; }
            
            p {
                margin: 8px 0;
            }
            
            ul, ol {
                margin: 8px 0;
                padding-left: 24px;
            }
            
            li {
                margin: 4px 0;
            }
            
            strong, b {
                color: #1976d2;
                font-weight: 600;
            }
            
            code {
                background-color: #f5f5f5;
                padding: 2px 4px;
                border-radius: 3px;
                font-family: 'Courier New', monospace;
                font-size: 13px;
            }
            
            blockquote {
                border-left: 3px solid #1976d2;
                margin: 12px 0;
                padding-left: 12px;
                color: #666;
                background-color: #f9f9f9;
                padding: 8px 12px;
            }
            
            a {
                color: #1976d2;
                text-decoration: none;
            }
            
            a:hover {
                text-decoration: underline;
            }
        """.trimIndent()
    }

    private fun setupFooter(versionName: String, updatedAt: String?) {
        val formattedTime = TXAFormat.formatUpdateTime(updatedAt)
        
        val footerText = String.format(
            TXATranslation.txa("txademo_update_on"),
            formattedTime
        ) + " - v$versionName - ${TXATranslation.txa("txademo_powered_by")}"
        
        binding?.tvFooter?.text = footerText
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
    }
}
