package com.txapp.musicplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.txapp.musicplayer.R
import com.txapp.musicplayer.ui.SplashActivity
import com.txapp.musicplayer.util.TXACrashHandler
import java.io.File
import com.txapp.musicplayer.util.TXATranslation
import android.widget.Toast

class TXAErrorActivity : AppCompatActivity() {

    private var errorLog: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_txa_error)

        errorLog = intent.getStringExtra(TXACrashHandler.INTENT_DATA_ERROR_LOG)
        val errorCode = intent.getStringExtra(TXACrashHandler.INTENT_DATA_ERROR_CODE) ?: "TXAAPP-UNK-XXXX"
        val errorType = intent.getStringExtra(TXACrashHandler.INTENT_DATA_SUGGESTION) ?: "unknown"

        // 0. Send Error to Server
        if (errorLog != null) {
            TXACrashHandler.sendErrorToServer(this, errorLog!!, errorCode) {
                runOnUiThread {
                    com.txapp.musicplayer.util.TXAToast.success(this, TXATranslation.txa("txamusic_error_report_sent"))
                }
            }
        }

        val btnRestart = findViewById<MaterialButton>(R.id.btnRestart)
        val btnDetails = findViewById<MaterialButton>(R.id.btnViewDetails)
        val btnShare = findViewById<MaterialButton>(R.id.btnShare)
        val txtMessage = findViewById<TextView>(R.id.msgError)
        val txtTitle = findViewById<TextView>(R.id.titleError)

        // 1. Set Title
        txtTitle.text = getString(R.string.txamusic_error_crash_title)

        // 2. Set Message dynamically
        val msgResName = "txamusic_error_friendly_$errorType"
        val resId = resources.getIdentifier(msgResName, "string", packageName)
        val friendlyMessage = if (resId != 0) getString(resId) else getString(R.string.txamusic_error_friendly_unknown)
        txtMessage.text = "$friendlyMessage\n\n[ $errorCode ]"

        // 3. Set Buttons
        btnRestart.text = getString(R.string.txamusic_error_action_restart)
        btnDetails.text = getString(R.string.txamusic_error_action_details)
        // Ensure translation for Contact button
        val btnContact = findViewById<MaterialButton>(R.id.btnContact)
        btnContact.text = TXATranslation.txa("txamusic_error_contact_btn")
        btnShare.text = getString(R.string.txamusic_error_action_share)

        btnRestart.setOnClickListener {
            restartApp()
        }

        btnDetails.setOnClickListener {
            showErrorDetails()
        }

        btnContact.setOnClickListener {
            showContactOptions()
        }

        btnShare.setOnClickListener {
            shareErrorReport()
        }
    }

    private fun showContactOptions() {
        val options = arrayOf(
            TXATranslation.txa("txamusic_social_facebook"),
            "Email" 
        )

        AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("txamusic_contact_option_title"))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> contactViaFacebook()
                    1 -> contactViaEmail()
                }
            }
            .setNegativeButton(TXATranslation.txa("txamusic_btn_cancel"), null)
            .show()
    }

    private fun contactViaFacebook() {
        // 1. Prepare message
        val prefix = TXATranslation.txa("txamusic_contact_facebook_msg")
        val fullMessage = "$prefix$errorLog"

        // 2. Copy to clipboard
        copyToClipboard(fullMessage, showToast = false)
        com.txapp.musicplayer.util.TXAToast.info(this, TXATranslation.txa("txamusic_contact_copied_fb"))

        // 3. Open Facebook
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fb.com/vlog.txa.2311"))
            startActivity(intent)
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXAToast.error(this, TXATranslation.txa("txamusic_browser_not_found"))
        }
    }

    private fun contactViaEmail() {
        try {
            val errorCode = intent.getStringExtra(TXACrashHandler.INTENT_DATA_ERROR_CODE) ?: "Unknown"
            
            val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
            val bodyTemplate = TXATranslation.txa("txamusic_contact_email_body", deviceInfo)
            val fullBody = "$bodyTemplate\n\n-----------------\nError Code: $errorCode\n\n$errorLog"

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("txavlog7@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, TXATranslation.txa("txamusic_contact_email_subject") + " [$errorCode]")
                putExtra(Intent.EXTRA_TEXT, fullBody)
            }
            startActivity(Intent.createChooser(intent, "Email"))
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXAToast.error(this, "Could not open email client")
        }
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
        Runtime.getRuntime().exit(0)
    }

    private fun showErrorDetails() {
        val scrollView = android.widget.ScrollView(this)
        val textView = TextView(this)
        textView.text = errorLog ?: "No details available."
        textView.setPadding(32, 32, 32, 32)
        textView.setTextIsSelectable(true)
        textView.typeface = android.graphics.Typeface.MONOSPACE
        textView.textSize = 12f
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.txamusic_error_action_details))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.txamusic_error_btn_ok), null)
            .setNeutralButton(getString(R.string.txamusic_error_copy_hint)) { _, _ ->
                copyToClipboard(errorLog ?: "")
            }
            .show()
    }

    private fun copyToClipboard(text: String, showToast: Boolean = true) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Crash Log", text)
        clipboard.setPrimaryClip(clip)
        if (showToast) {
            com.txapp.musicplayer.util.TXAToast.success(this, getString(R.string.txamusic_error_copied))
        }
    }

    private fun shareErrorReport() {
        try {
            val external = getExternalFilesDir(null)
            val logDir = if (external != null) {
                val dir = File(external, "bug/reports")
                if (dir.exists() && !dir.listFiles().isNullOrEmpty()) dir 
                else File(filesDir, "bug/reports")
            } else {
                File(filesDir, "bug/reports")
            }
            
            val files = logDir.listFiles()
            if (files.isNullOrEmpty()) {
                // Try old path just in case
                val oldDir = File(external ?: filesDir, "log/reports")
                val oldFiles = oldDir.listFiles()
                if (oldFiles.isNullOrEmpty()) {
                    throw Exception("No log files found")
                }
            }
            
            val allFiles = (logDir.listFiles() ?: emptyArray()) + 
                           (File(external ?: filesDir, "log/reports").listFiles() ?: emptyArray())
            
            if (allFiles.isEmpty()) return
            
            // Get the latest file
            val latestFile = allFiles.sortedByDescending { it.lastModified() }.first()
            
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                latestFile
            )

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            startActivity(Intent.createChooser(intent, getString(R.string.txamusic_error_action_share)))
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to sharing text if file fails
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, errorLog)
            startActivity(Intent.createChooser(intent, getString(R.string.txamusic_error_action_share)))
        }
    }
}
