package com.termux.shared.termux.terminal.io

import android.os.Build
import android.view.KeyEvent
import android.view.View
import com.google.android.material.button.MaterialButton
import com.termux.shared.termux.extrakeys.ExtraKeyButton
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.extrakeys.SpecialButton
import com.termux.shared.termux.extrakeys.ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS
import com.termux.view.TerminalView

open class TerminalExtraKeys(private val mTerminalView: TerminalView) : ExtraKeysView.IExtraKeysView {

    override fun onExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        if (buttonInfo.isMacro) {
            val keys = buttonInfo.key.split(" ")
            var ctrlDown = false
            var altDown = false
            var shiftDown = false
            var fnDown = false
            for (key in keys) {
                when {
                    SpecialButton.CTRL.key == key -> ctrlDown = true
                    SpecialButton.ALT.key == key -> altDown = true
                    SpecialButton.SHIFT.key == key -> shiftDown = true
                    SpecialButton.FN.key == key -> fnDown = true
                    else -> {
                        onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown)
                        ctrlDown = false
                        altDown = false
                        shiftDown = false
                        fnDown = false
                    }
                }
            }
        } else {
            onTerminalExtraKeyButtonClick(view, buttonInfo.key, false, false, false, false)
        }
    }

    protected open fun onTerminalExtraKeyButtonClick(
        view: View,
        key: String,
        ctrlDown: Boolean,
        altDown: Boolean,
        shiftDown: Boolean,
        fnDown: Boolean
    ) {
        if (PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(key)) {
            val keyCode = PRIMARY_KEY_CODES_FOR_STRINGS[key] ?: return
            var metaState = 0
            if (ctrlDown) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            if (altDown) metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            if (shiftDown) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            if (fnDown) metaState = metaState or KeyEvent.META_FUNCTION_ON

            val keyEvent = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState)
            mTerminalView.onKeyDown(keyCode, keyEvent)
        } else {
            // not a control char
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                key.codePoints().forEach { codePoint ->
                    mTerminalView.inputCodePoint(TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, codePoint, ctrlDown, altDown)
                }
            } else {
                val session = mTerminalView.currentSession
                if (session != null && key.isNotEmpty()) {
                    session.write(key)
                }
            }
        }
    }

    override fun performExtraKeyButtonHapticFeedback(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton): Boolean {
        return false
    }
}
