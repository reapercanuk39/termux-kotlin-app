package com.termux.app.fragments.settings.termux

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.termux.kotlin.R
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences

@Keep
class TerminalIOPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        preferenceManager.preferenceDataStore = TerminalIOPreferencesDataStore.getInstance(context)
        setPreferencesFromResource(R.xml.termux_terminal_io_preferences, rootKey)
    }
}

class TerminalIOPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val mContext: Context = context
    private val mPreferences: TermuxAppSharedPreferences? = TermuxAppSharedPreferences.build(context, true)

    companion object {
        @Volatile
        private var instance: TerminalIOPreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TerminalIOPreferencesDataStore {
            return instance ?: TerminalIOPreferencesDataStore(context).also { instance = it }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (mPreferences == null || key == null) return

        when (key) {
            "soft_keyboard_enabled" -> mPreferences.setSoftKeyboardEnabled(value)
            "soft_keyboard_enabled_only_if_no_hardware" -> mPreferences.setSoftKeyboardEnabledOnlyIfNoHardware(value)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (mPreferences == null) return false

        return when (key) {
            "soft_keyboard_enabled" -> mPreferences.isSoftKeyboardEnabled()
            "soft_keyboard_enabled_only_if_no_hardware" -> mPreferences.isSoftKeyboardEnabledOnlyIfNoHardware()
            else -> false
        }
    }
}
