package com.termux.shared.termux.settings.preferences

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.android.PackageUtils
import com.termux.shared.settings.preferences.AppSharedPreferences
import com.termux.shared.settings.preferences.SharedPreferenceUtils
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_API_APP
import com.termux.shared.termux.TermuxConstants

class TermuxAPIAppSharedPreferences private constructor(context: Context) : AppSharedPreferences(
    context,
    SharedPreferenceUtils.getPrivateSharedPreferences(
        context,
        TermuxConstants.TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    ),
    SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(
        context,
        TermuxConstants.TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    )
) {
    fun getLogLevel(readFromFile: Boolean): Int {
        return if (readFromFile) {
            SharedPreferenceUtils.getInt(mMultiProcessSharedPreferences, TERMUX_API_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        } else {
            SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_API_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        }
    }

    fun setLogLevel(context: Context?, logLevel: Int, commitToFile: Boolean) {
        val level = Logger.setLogLevel(context, logLevel)
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_API_APP.KEY_LOG_LEVEL, level, commitToFile)
    }

    val lastPendingIntentRequestCode: Int
        get() = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_API_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, TERMUX_API_APP.DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE)

    fun setLastPendingIntentRequestCode(lastPendingIntentRequestCode: Int) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_API_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, lastPendingIntentRequestCode, true)
    }

    companion object {
        private const val LOG_TAG = "TermuxAPIAppSharedPreferences"

        @JvmStatic
        fun build(context: Context): TermuxAPIAppSharedPreferences? {
            val termuxAPIPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_API_PACKAGE_NAME)
                ?: return null
            return TermuxAPIAppSharedPreferences(termuxAPIPackageContext)
        }

        @JvmStatic
        fun build(context: Context, exitAppOnError: Boolean): TermuxAPIAppSharedPreferences? {
            val termuxAPIPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_API_PACKAGE_NAME, exitAppOnError)
                ?: return null
            return TermuxAPIAppSharedPreferences(termuxAPIPackageContext)
        }
    }
}
