package com.termux.shared.termux.interact

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.text.Selection
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

object TextInputDialogUtils {

    fun interface TextSetListener {
        fun onTextSet(text: String)
    }

    @JvmStatic
    fun textInput(
        activity: Activity,
        titleText: Int,
        initialText: String?,
        positiveButtonText: Int,
        onPositive: TextSetListener,
        neutralButtonText: Int,
        onNeutral: TextSetListener?,
        negativeButtonText: Int,
        onNegative: TextSetListener?,
        onDismiss: DialogInterface.OnDismissListener?
    ) {
        val input = EditText(activity).apply {
            isSingleLine = true
            if (initialText != null) {
                setText(initialText)
                Selection.setSelection(text, initialText.length)
            }
        }

        val dialogHolder = arrayOfNulls<AlertDialog>(1)
        input.setImeActionLabel(activity.resources.getString(positiveButtonText), KeyEvent.KEYCODE_ENTER)
        input.setOnEditorActionListener { _, _, _ ->
            onPositive.onTextSet(input.text.toString())
            dialogHolder[0]?.dismiss()
            true
        }

        val dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, activity.resources.displayMetrics)
        // https://www.google.com/design/spec/components/dialogs.html#dialogs-specs
        val paddingTopAndSides = (16 * dipInPixels).toInt()
        val paddingBottom = (24 * dipInPixels).toInt()

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(paddingTopAndSides, paddingTopAndSides, paddingTopAndSides, paddingBottom)
            addView(input)
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(titleText)
            .setView(layout)
            .setPositiveButton(positiveButtonText) { _, _ -> onPositive.onTextSet(input.text.toString()) }

        if (onNeutral != null) {
            builder.setNeutralButton(neutralButtonText) { _, _ -> onNeutral.onTextSet(input.text.toString()) }
        }

        if (onNegative == null) {
            builder.setNegativeButton(android.R.string.cancel, null)
        } else {
            builder.setNegativeButton(negativeButtonText) { _, _ -> onNegative.onTextSet(input.text.toString()) }
        }

        if (onDismiss != null) {
            builder.setOnDismissListener(onDismiss)
        }

        dialogHolder[0] = builder.create().apply {
            setCanceledOnTouchOutside(false)
            show()
        }
    }
}
