package com.termux.shared.interact

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.termux.shared.R
import com.termux.shared.logger.Logger

object MessageDialogUtils {

    /**
     * Show a message in a dialog
     *
     * @param context The [Context] to use to start the dialog. An Activity Context
     *                must be passed, otherwise exceptions will be thrown.
     * @param titleText The title text of the dialog.
     * @param messageText The message text of the dialog.
     * @param onDismiss The [DialogInterface.OnDismissListener] to run when dialog is dismissed.
     */
    @JvmStatic
    fun showMessage(
        context: Context,
        titleText: String,
        messageText: String,
        onDismiss: DialogInterface.OnDismissListener?
    ) {
        showMessageFull(context, titleText, messageText, null, null, null, null, onDismiss)
    }

    /**
     * Show a message in a dialog
     *
     * @param context The [Context] to use to start the dialog. An Activity Context
     *                must be passed, otherwise exceptions will be thrown.
     * @param titleText The title text of the dialog.
     * @param messageText The message text of the dialog.
     * @param positiveText The positive button text of the dialog.
     * @param onPositiveButton The [DialogInterface.OnClickListener] to run when positive button is pressed.
     * @param negativeText The negative button text of the dialog. If this is null, then negative button will not be shown.
     * @param onNegativeButton The [DialogInterface.OnClickListener] to run when negative button is pressed.
     * @param onDismiss The [DialogInterface.OnDismissListener] to run when dialog is dismissed.
     */
    @JvmStatic
    fun showMessage(
        context: Context,
        titleText: String,
        messageText: String,
        positiveText: String?,
        onPositiveButton: DialogInterface.OnClickListener?,
        negativeText: String?,
        onNegativeButton: DialogInterface.OnClickListener?,
        onDismiss: DialogInterface.OnDismissListener?
    ) {
        showMessageFull(context, titleText, messageText, positiveText, onPositiveButton, negativeText, onNegativeButton, onDismiss)
    }

    private fun showMessageFull(
        context: Context,
        titleText: String,
        messageText: String,
        positiveText: String?,
        onPositiveButton: DialogInterface.OnClickListener?,
        negativeText: String?,
        onNegativeButton: DialogInterface.OnClickListener?,
        onDismiss: DialogInterface.OnDismissListener?
    ) {
        val builder = AlertDialog.Builder(context, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.dialog_show_message, null)
        if (view != null) {
            builder.setView(view)

            val titleView = view.findViewById<TextView>(R.id.dialog_title)
            titleView?.text = titleText

            val messageView = view.findViewById<TextView>(R.id.dialog_message)
            messageView?.text = messageText
        }

        val actualPositiveText = positiveText ?: context.getString(android.R.string.ok)
        builder.setPositiveButton(actualPositiveText, onPositiveButton)

        if (negativeText != null) {
            builder.setNegativeButton(negativeText, onNegativeButton)
        }

        if (onDismiss != null) {
            builder.setOnDismissListener(onDismiss)
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            Logger.logError("dialog")
            var button: Button? = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button?.setTextColor(Color.BLACK)
            button = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            button?.setTextColor(Color.BLACK)
        }

        dialog.show()
    }

    @JvmStatic
    fun exitAppWithErrorMessage(context: Context, titleText: String, messageText: String) {
        showMessage(context, titleText, messageText) { System.exit(0) }
    }
}
