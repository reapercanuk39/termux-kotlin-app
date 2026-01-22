package com.termux.shared.termux.settings.preferences

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.android.PackageUtils
import com.termux.shared.settings.preferences.AppSharedPreferences
import com.termux.shared.settings.preferences.SharedPreferenceUtils
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_WIDGET_APP
import com.termux.shared.termux.TermuxConstants
import java.util.UUID

class TermuxWidgetAppSharedPreferences private constructor(context: Context) : AppSharedPreferences(
    context,
    SharedPreferenceUtils.getPrivateSharedPreferences(
        context,
        TermuxConstants.TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    ),
    SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(
        context,
        TermuxConstants.TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    )
) {
    val generatedToken: String
        get() {
            var token = SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_WIDGET_APP.KEY_TOKEN, null, true)
            if (token == null) {
                token = UUID.randomUUID().toString()
                SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_WIDGET_APP.KEY_TOKEN, token, true)
            }
            return token
        }

    fun getLogLevel(readFromFile: Boolean): Int {
        return if (readFromFile) {
            SharedPreferenceUtils.getInt(mMultiProcessSharedPreferences, TERMUX_WIDGET_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        } else {
            SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_WIDGET_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        }
    }

    fun setLogLevel(context: Context?, logLevel: Int, commitToFile: Boolean) {
        val level = Logger.setLogLevel(context, logLevel)
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_WIDGET_APP.KEY_LOG_LEVEL, level, commitToFile)
    }

    companion object {
        private const val LOG_TAG = "TermuxWidgetAppSharedPreferences"

        @JvmStatic
        fun getGeneratedToken(context: Context): String? {
            val preferences = build(context, true) ?: return null
            return preferences.generatedToken
        }

        @JvmStatic
        fun build(context: Context): TermuxWidgetAppSharedPreferences? {
            // Termux:Widget is built-in since v2.0.5 - use current app context
            // First try the external package (for backwards compatibility), then fall back to current context
            val termuxWidgetPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME)
                ?: context  // Use current app context since Widget is built-in
            return TermuxWidgetAppSharedPreferences(termuxWidgetPackageContext)
        }

        @JvmStatic
        fun build(context: Context, exitAppOnError: Boolean): TermuxWidgetAppSharedPreferences? {
            // Termux:Widget is built-in since v2.0.5 - use current app context if external package not found
            val termuxWidgetPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME)
                ?: context  // Use current app context since Widget is built-in
            return TermuxWidgetAppSharedPreferences(termuxWidgetPackageContext)
        }
    }
}
