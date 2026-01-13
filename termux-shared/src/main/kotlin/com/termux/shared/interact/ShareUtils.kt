package com.termux.shared.interact

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import com.termux.kotlin.shared.R
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.android.PermissionUtils
import java.nio.charset.Charset

object ShareUtils {

    private const val LOG_TAG = "ShareUtils"

    /**
     * Open the system app chooser that allows the user to select which app to send the intent.
     */
    @JvmStatic
    fun openSystemAppChooser(context: Context?, intent: Intent, title: String?) {
        if (context == null) return

        val chooserIntent = Intent(Intent.ACTION_CHOOSER)
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent)
        chooserIntent.putExtra(Intent.EXTRA_TITLE, title)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open system chooser for:\n${IntentUtils.getIntentString(chooserIntent)}", e)
        }
    }

    /**
     * Share text.
     */
    @JvmStatic
    fun shareText(context: Context?, subject: String?, text: String?) {
        shareText(context, subject, text, null)
    }

    /**
     * Share text.
     */
    @JvmStatic
    fun shareText(context: Context?, subject: String?, text: String?, title: String?) {
        if (context == null || text == null) return

        val shareTextIntent = Intent(Intent.ACTION_SEND)
        shareTextIntent.type = "text/plain"
        shareTextIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
        shareTextIntent.putExtra(Intent.EXTRA_TEXT, DataUtils.getTruncatedCommandOutput(
            text, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, true, false, false
        ))

        openSystemAppChooser(context, shareTextIntent, if (DataUtils.isNullOrEmpty(title)) context.getString(R.string.title_share_with) else title)
    }

    /** Wrapper for [copyTextToClipboard] with null clipDataLabel and toastString. */
    @JvmStatic
    fun copyTextToClipboard(context: Context?, text: String?) {
        copyTextToClipboard(context, null, text, null)
    }

    /** Wrapper for [copyTextToClipboard] with null clipDataLabel. */
    @JvmStatic
    fun copyTextToClipboard(context: Context?, text: String?, toastString: String?) {
        copyTextToClipboard(context, null, text, toastString)
    }

    /**
     * Copy the text to primary clip of the clipboard.
     */
    @JvmStatic
    fun copyTextToClipboard(context: Context?, clipDataLabel: String?, text: String?, toastString: String?) {
        if (context == null || text == null) return

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return

        clipboardManager.setPrimaryClip(ClipData.newPlainText(clipDataLabel,
            DataUtils.getTruncatedCommandOutput(text, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES,
                true, false, false)))

        if (!toastString.isNullOrEmpty()) {
            Logger.showToast(context, toastString, true)
        }
    }

    /**
     * Wrapper for [getTextFromClipboard] that returns primary text [String]
     * if its set and not empty.
     */
    @JvmStatic
    fun getTextStringFromClipboardIfSet(context: Context?, coerceToText: Boolean): String? {
        val textCharSequence = getTextFromClipboard(context, coerceToText) ?: return null
        val textString = textCharSequence.toString()
        return if (textString.isNotEmpty()) textString else null
    }

    /**
     * Get the text from primary clip of the clipboard.
     */
    @JvmStatic
    fun getTextFromClipboard(context: Context?, coerceToText: Boolean): CharSequence? {
        if (context == null) return null

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clipData = clipboardManager.primaryClip ?: return null
        val clipItem = clipData.getItemAt(0) ?: return null

        return if (coerceToText) clipItem.coerceToText(context) else clipItem.text
    }

    /**
     * Open a url.
     */
    @JvmStatic
    fun openUrl(context: Context?, url: String?) {
        if (context == null || url.isNullOrEmpty()) return
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // If no activity found to handle intent, show system chooser
            openSystemAppChooser(context, intent, context.getString(R.string.title_open_url_with))
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open url \"$url\"", e)
        }
    }

    /**
     * Save a file at the path.
     */
    @JvmStatic
    fun saveTextToFile(
        context: Context?,
        label: String?,
        filePath: String?,
        text: String?,
        showToast: Boolean,
        storagePermissionRequestCode: Int
    ) {
        if (context == null || filePath.isNullOrEmpty() || text == null) return

        // If path is under primary external storage directory, then check for missing permissions.
        if ((FileUtils.isPathInDirPath(filePath, Environment.getExternalStorageDirectory().absolutePath, true) ||
                FileUtils.isPathInDirPath(filePath, "/sdcard", true)) &&
            !PermissionUtils.checkPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            Logger.logErrorAndShowToast(context, LOG_TAG, context.getString(R.string.msg_storage_permission_not_granted))

            if (storagePermissionRequestCode >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                when (context) {
                    is AppCompatActivity -> PermissionUtils.requestPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE, storagePermissionRequestCode)
                    is Activity -> PermissionUtils.requestPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE, storagePermissionRequestCode)
                }
            }

            return
        }

        val error = FileUtils.writeTextToFile(label, filePath, Charset.defaultCharset(), text, false)
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString())
            Logger.showToast(context, com.termux.shared.errors.Error.getMinimalErrorString(error), true)
        } else {
            if (showToast) {
                Logger.showToast(context, context.getString(R.string.msg_file_saved_successfully, label, filePath), true)
            }
        }
    }
}
