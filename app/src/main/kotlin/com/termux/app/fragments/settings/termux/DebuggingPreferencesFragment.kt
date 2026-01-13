package com.termux.app.fragments.settings.termux

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.ListPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.termux.kotlin.R
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.logger.Logger

@Keep
class DebuggingPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = DebuggingPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_debugging_preferences, rootKey)

        configureLoggingPreferences(context)
    }

    private fun configureLoggingPreferences(context: Context) {
        val loggingCategory = findPreference<androidx.preference.PreferenceCategory>("logging") ?: return

        val logLevelListPreference = findPreference<ListPreference>("log_level")
        if (logLevelListPreference != null) {
            val preferences = TermuxAppSharedPreferences.build(context, true) ?: return

            setLogLevelListPreferenceData(logLevelListPreference, context, preferences.getLogLevel())
            loggingCategory.addPreference(logLevelListPreference)
        }
    }

    companion object {
        @JvmStatic
        fun setLogLevelListPreferenceData(
            logLevelListPreference: ListPreference?,
            context: Context,
            logLevel: Int
        ): ListPreference {
            val pref = logLevelListPreference ?: ListPreference(context)

            val logLevels = Logger.getLogLevelsArray()
            val logLevelLabels = Logger.getLogLevelLabelsArray(context, logLevels, true)

            pref.entryValues = logLevels
            pref.entries = logLevelLabels

            pref.value = logLevel.toString()
            pref.setDefaultValue(Logger.DEFAULT_LOG_LEVEL)

            return pref
        }
    }
}

class DebuggingPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val mContext: Context = context
    private val mPreferences: TermuxAppSharedPreferences? = TermuxAppSharedPreferences.build(context, true)

    override fun getString(key: String?, defValue: String?): String? {
        if (mPreferences == null) return null
        if (key == null) return null

        return when (key) {
            "log_level" -> mPreferences.getLogLevel().toString()
            else -> null
        }
    }

    override fun putString(key: String?, value: String?) {
        if (mPreferences == null) return
        if (key == null) return

        when (key) {
            "log_level" -> {
                if (value != null) {
                    mPreferences.setLogLevel(mContext, value.toInt())
                }
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (mPreferences == null) return
        if (key == null) return

        when (key) {
            "terminal_view_key_logging_enabled" -> mPreferences.setTerminalViewKeyLoggingEnabled(value)
            "plugin_error_notifications_enabled" -> mPreferences.setPluginErrorNotificationsEnabled(value)
            "crash_report_notifications_enabled" -> mPreferences.setCrashReportNotificationsEnabled(value)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (mPreferences == null) return false
        return when (key) {
            "terminal_view_key_logging_enabled" -> mPreferences.isTerminalViewKeyLoggingEnabled()
            "plugin_error_notifications_enabled" -> mPreferences.arePluginErrorNotificationsEnabled(false)
            "crash_report_notifications_enabled" -> mPreferences.areCrashReportNotificationsEnabled(false)
            else -> false
        }
    }

    companion object {
        @Volatile
        private var mInstance: DebuggingPreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): DebuggingPreferencesDataStore {
            if (mInstance == null) {
                mInstance = DebuggingPreferencesDataStore(context)
            }
            return mInstance!!
        }
    }
}
