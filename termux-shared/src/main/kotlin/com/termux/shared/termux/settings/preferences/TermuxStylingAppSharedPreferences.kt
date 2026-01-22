package com.termux.shared.termux.settings.preferences

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.android.PackageUtils
import com.termux.shared.settings.preferences.AppSharedPreferences
import com.termux.shared.settings.preferences.SharedPreferenceUtils
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_STYLING_APP
import com.termux.shared.termux.TermuxConstants

class TermuxStylingAppSharedPreferences private constructor(context: Context) : AppSharedPreferences(
    context,
    SharedPreferenceUtils.getPrivateSharedPreferences(
        context,
        TermuxConstants.TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    ),
    SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(
        context,
        TermuxConstants.TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    )
) {
    fun getLogLevel(readFromFile: Boolean): Int {
        return if (readFromFile) {
            SharedPreferenceUtils.getInt(mMultiProcessSharedPreferences, TERMUX_STYLING_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        } else {
            SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_STYLING_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        }
    }

    fun setLogLevel(context: Context?, logLevel: Int, commitToFile: Boolean) {
        val level = Logger.setLogLevel(context, logLevel)
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_STYLING_APP.KEY_LOG_LEVEL, level, commitToFile)
    }

    companion object {
        private const val LOG_TAG = "TermuxStylingAppSharedPreferences"

        @JvmStatic
        fun build(context: Context): TermuxStylingAppSharedPreferences? {
            // Termux:Styling is built-in since v2.0.5 - use current app context
            // First try the external package (for backwards compatibility), then fall back to current context
            val termuxStylingPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_STYLING_PACKAGE_NAME)
                ?: context  // Use current app context since Styling is built-in
            return TermuxStylingAppSharedPreferences(termuxStylingPackageContext)
        }

        @JvmStatic
        fun build(context: Context, exitAppOnError: Boolean): TermuxStylingAppSharedPreferences? {
            // Termux:Styling is built-in since v2.0.5 - use current app context if external package not found
            val termuxStylingPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_STYLING_PACKAGE_NAME)
                ?: context  // Use current app context since Styling is built-in
            return TermuxStylingAppSharedPreferences(termuxStylingPackageContext)
        }
    }
}
