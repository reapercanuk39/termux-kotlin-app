package com.termux.app.terminal.io

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import com.termux.app.TermuxActivity
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalViewClient
import com.termux.shared.logger.Logger
import com.termux.shared.termux.extrakeys.ExtraKeysConstants
import com.termux.shared.termux.extrakeys.ExtraKeysInfo
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.settings.properties.TermuxSharedProperties
import com.termux.shared.termux.terminal.io.TerminalExtraKeys
import com.termux.view.TerminalView
import org.json.JSONException

class TermuxTerminalExtraKeys(
    private val mActivity: TermuxActivity,
    terminalView: TerminalView,
    private val mTermuxTerminalViewClient: TermuxTerminalViewClient?,
    private val mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient?
) : TerminalExtraKeys(terminalView) {

    private var mExtraKeysInfo: ExtraKeysInfo? = null

    init {
        setExtraKeys()
    }

    /**
     * Set the terminal extra keys and style.
     */
    private fun setExtraKeys() {
        mExtraKeysInfo = null

        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            val extrakeys = mActivity.properties?.getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true) as? String
            var extraKeysStyle = mActivity.properties?.getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true) as? String

            val extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle)
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY == extraKeyDisplayMap && 
                TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE != extraKeysStyle) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"$extraKeysStyle\" for the key \"${TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE}\" is invalid. Using default style instead.")
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE
            }

            mExtraKeysInfo = ExtraKeysInfo(
                extrakeys ?: TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
                extraKeysStyle,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES
            )
        } catch (e: JSONException) {
            Logger.showToast(mActivity, "Could not load and set the \"${TermuxPropertyConstants.KEY_EXTRA_KEYS}\" property from the properties file: $e", true)
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"${TermuxPropertyConstants.KEY_EXTRA_KEYS}\" property from the properties file: ", e)

            try {
                mExtraKeysInfo = ExtraKeysInfo(
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
                    ExtraKeysConstants.CONTROL_CHARS_ALIASES
                )
            } catch (e2: JSONException) {
                Logger.showToast(mActivity, "Can't create default extra keys", true)
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e)
                mExtraKeysInfo = null
            }
        }
    }

    fun getExtraKeysInfo(): ExtraKeysInfo? {
        return mExtraKeysInfo
    }

    @SuppressLint("RtlHardcoded")
    override fun onTerminalExtraKeyButtonClick(
        view: View,
        key: String,
        ctrlDown: Boolean,
        altDown: Boolean,
        shiftDown: Boolean,
        fnDown: Boolean
    ) {
        when (key) {
            "KEYBOARD" -> {
                mTermuxTerminalViewClient?.onToggleSoftKeyboardRequest()
            }
            "DRAWER" -> {
                val drawerLayout = mTermuxTerminalViewClient?.activity?.drawer
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                        drawerLayout.closeDrawer(Gravity.LEFT)
                    } else {
                        drawerLayout.openDrawer(Gravity.LEFT)
                    }
                }
            }
            "PASTE" -> {
                mTermuxTerminalSessionActivityClient?.onPasteTextFromClipboard(null)
            }
            "SCROLL" -> {
                val terminalView = mTermuxTerminalViewClient?.activity?.terminalView
                terminalView?.mEmulator?.toggleAutoScrollDisabled()
            }
            else -> {
                super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown)
            }
        }
    }

    companion object {
        private const val LOG_TAG = "TermuxTerminalExtraKeys"
    }
}
