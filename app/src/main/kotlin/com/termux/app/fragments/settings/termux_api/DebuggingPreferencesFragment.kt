package com.termux.app.fragments.settings.termux_api

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.termux.kotlin.R
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences

@Keep
class DebuggingPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        preferenceManager.preferenceDataStore = DebuggingPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_api_debugging_preferences, rootKey)

        configureLoggingPreferences(context)
    }

    private fun configureLoggingPreferences(context: Context) {
        val loggingCategory: PreferenceCategory = findPreference("logging") ?: return

        val logLevelListPreference: ListPreference = findPreference("log_level") ?: return
        val preferences = TermuxAPIAppSharedPreferences.build(context, true) ?: return

        com.termux.app.fragments.settings.termux.DebuggingPreferencesFragment
            .setLogLevelListPreferenceData(logLevelListPreference, context, preferences.getLogLevel(true))
        loggingCategory.addPreference(logLevelListPreference)
    }
}

internal class DebuggingPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val mContext: Context = context
    private val mPreferences: TermuxAPIAppSharedPreferences? = TermuxAPIAppSharedPreferences.build(context, true)

    override fun getString(key: String?, defValue: String?): String? {
        if (mPreferences == null || key == null) return null

        return when (key) {
            "log_level" -> mPreferences.getLogLevel(true).toString()
            else -> null
        }
    }

    override fun putString(key: String?, value: String?) {
        if (mPreferences == null || key == null) return

        when (key) {
            "log_level" -> {
                value?.let {
                    mPreferences.setLogLevel(mContext, it.toInt(), true)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var mInstance: DebuggingPreferencesDataStore? = null

        @Synchronized
        fun getInstance(context: Context): DebuggingPreferencesDataStore {
            return mInstance ?: DebuggingPreferencesDataStore(context).also { mInstance = it }
        }
    }
}
