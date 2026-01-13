package com.termux.app.fragments.settings.termux

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.termux.kotlin.R
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences

@Keep
class TerminalViewPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        preferenceManager.preferenceDataStore = TerminalViewPreferencesDataStore.getInstance(context)
        setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey)
    }
}

class TerminalViewPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val mContext: Context = context
    private val mPreferences: TermuxAppSharedPreferences? = TermuxAppSharedPreferences.build(context, true)

    companion object {
        @Volatile
        private var instance: TerminalViewPreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TerminalViewPreferencesDataStore {
            return instance ?: TerminalViewPreferencesDataStore(context).also { instance = it }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (mPreferences == null || key == null) return

        when (key) {
            "terminal_margin_adjustment" -> mPreferences.setTerminalMarginAdjustment(value)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (mPreferences == null) return false

        return when (key) {
            "terminal_margin_adjustment" -> mPreferences.isTerminalMarginAdjustmentEnabled()
            else -> false
        }
    }
}
