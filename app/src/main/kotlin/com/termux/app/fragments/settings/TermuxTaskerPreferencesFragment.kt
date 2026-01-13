package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.termux.kotlin.R
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences

@Keep
class TermuxTaskerPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        preferenceManager.preferenceDataStore = TermuxTaskerPreferencesDataStore.getInstance(context)
        setPreferencesFromResource(R.xml.termux_tasker_preferences, rootKey)
    }
}

class TermuxTaskerPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val mContext: Context = context
    private val mPreferences: TermuxTaskerAppSharedPreferences? = TermuxTaskerAppSharedPreferences.build(context, true)

    companion object {
        @Volatile
        private var instance: TermuxTaskerPreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TermuxTaskerPreferencesDataStore {
            return instance ?: TermuxTaskerPreferencesDataStore(context).also { instance = it }
        }
    }
}
