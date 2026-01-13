package com.termux.shared.termux.settings.preferences

import android.content.Context
import com.termux.shared.data.DataUtils
import com.termux.shared.logger.Logger
import com.termux.shared.android.PackageUtils
import com.termux.shared.settings.preferences.AppSharedPreferences
import com.termux.shared.settings.preferences.SharedPreferenceUtils
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_FLOAT_APP
import com.termux.shared.termux.TermuxConstants

class TermuxFloatAppSharedPreferences private constructor(context: Context) : AppSharedPreferences(
    context,
    SharedPreferenceUtils.getPrivateSharedPreferences(
        context,
        TermuxConstants.TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    ),
    SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(
        context,
        TermuxConstants.TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    )
) {
    private var MIN_FONTSIZE = 0
    private var MAX_FONTSIZE = 0
    private var DEFAULT_FONTSIZE = 0

    init {
        setFontVariables(context)
    }

    var windowX: Int
        get() = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_X, 200)
        set(value) = SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_X, value, false)

    var windowY: Int
        get() = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_Y, 200)
        set(value) = SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_Y, value, false)

    var windowWidth: Int
        get() = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_WIDTH, 500)
        set(value) = SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_WIDTH, value, false)

    var windowHeight: Int
        get() = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_HEIGHT, 500)
        set(value) = SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_WINDOW_HEIGHT, value, false)

    fun setFontVariables(context: Context) {
        val sizes = TermuxAppSharedPreferences.getDefaultFontSizes(context)
        DEFAULT_FONTSIZE = sizes[0]
        MIN_FONTSIZE = sizes[1]
        MAX_FONTSIZE = sizes[2]
    }

    var fontSize: Int
        get() {
            val fontSize = SharedPreferenceUtils.getIntStoredAsString(
                mSharedPreferences, TERMUX_FLOAT_APP.KEY_FONTSIZE, DEFAULT_FONTSIZE
            )
            return DataUtils.clamp(fontSize, MIN_FONTSIZE, MAX_FONTSIZE)
        }
        set(value) = SharedPreferenceUtils.setIntStoredAsString(
            mSharedPreferences, TERMUX_FLOAT_APP.KEY_FONTSIZE, value, false
        )

    fun changeFontSize(increase: Boolean) {
        var currentFontSize = fontSize
        currentFontSize += (if (increase) 1 else -1) * 2
        currentFontSize = currentFontSize.coerceIn(MIN_FONTSIZE, MAX_FONTSIZE)
        fontSize = currentFontSize
    }

    fun getLogLevel(readFromFile: Boolean): Int {
        return if (readFromFile) {
            SharedPreferenceUtils.getInt(
                mMultiProcessSharedPreferences, TERMUX_FLOAT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL
            )
        } else {
            SharedPreferenceUtils.getInt(
                mSharedPreferences, TERMUX_FLOAT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL
            )
        }
    }

    fun setLogLevel(context: Context, logLevel: Int, commitToFile: Boolean) {
        val level = Logger.setLogLevel(context, logLevel)
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_FLOAT_APP.KEY_LOG_LEVEL, level, commitToFile)
    }

    fun isTerminalViewKeyLoggingEnabled(readFromFile: Boolean): Boolean {
        return if (readFromFile) {
            SharedPreferenceUtils.getBoolean(
                mMultiProcessSharedPreferences,
                TERMUX_FLOAT_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED,
                TERMUX_FLOAT_APP.DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED
            )
        } else {
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                TERMUX_FLOAT_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED,
                TERMUX_FLOAT_APP.DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED
            )
        }
    }

    fun setTerminalViewKeyLoggingEnabled(value: Boolean, commitToFile: Boolean) {
        SharedPreferenceUtils.setBoolean(
            mSharedPreferences, TERMUX_FLOAT_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, value, commitToFile
        )
    }

    companion object {
        @Suppress("unused")
        private const val LOG_TAG = "TermuxFloatAppSharedPreferences"

        /**
         * Get [TermuxFloatAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME].
         * @return Returns the [TermuxFloatAppSharedPreferences]. This will be null if an exception is raised.
         */
        @JvmStatic
        fun build(context: Context): TermuxFloatAppSharedPreferences? {
            val termuxFloatPackageContext = PackageUtils.getContextForPackage(
                context, TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME
            ) ?: return null
            return TermuxFloatAppSharedPreferences(termuxFloatPackageContext)
        }

        /**
         * Get [TermuxFloatAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME].
         * @param exitAppOnError If true and failed to get package context, then a dialog will
         *                       be shown which when dismissed will exit the app.
         * @return Returns the [TermuxFloatAppSharedPreferences]. This will be null if an exception is raised.
         */
        @JvmStatic
        fun build(context: Context, exitAppOnError: Boolean): TermuxFloatAppSharedPreferences? {
            val termuxFloatPackageContext = TermuxUtils.getContextForPackageOrExitApp(
                context, TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME, exitAppOnError
            ) ?: return null
            return TermuxFloatAppSharedPreferences(termuxFloatPackageContext)
        }
    }
}
