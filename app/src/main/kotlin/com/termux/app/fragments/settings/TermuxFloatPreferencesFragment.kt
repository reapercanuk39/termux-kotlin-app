package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.termux.kotlin.R
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences

@Keep
class TermuxFloatPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        preferenceManager.preferenceDataStore = TermuxFloatPreferencesDataStore.getInstance(context)
        setPreferencesFromResource(R.xml.termux_float_preferences, rootKey)
    }
}

class TermuxFloatPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val mContext: Context = context
    private val mPreferences: TermuxFloatAppSharedPreferences? = TermuxFloatAppSharedPreferences.build(context, true)

    companion object {
        @Volatile
        private var instance: TermuxFloatPreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TermuxFloatPreferencesDataStore {
            return instance ?: TermuxFloatPreferencesDataStore(context).also { instance = it }
        }
    }
}
