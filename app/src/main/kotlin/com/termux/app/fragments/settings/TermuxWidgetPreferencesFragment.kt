package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.termux.kotlin.R
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences

@Keep
class TermuxWidgetPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        preferenceManager.preferenceDataStore = TermuxWidgetPreferencesDataStore.getInstance(context)
        setPreferencesFromResource(R.xml.termux_widget_preferences, rootKey)
    }
}

class TermuxWidgetPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val mContext: Context = context
    private val mPreferences: TermuxWidgetAppSharedPreferences? = TermuxWidgetAppSharedPreferences.build(context, true)

    companion object {
        @Volatile
        private var instance: TermuxWidgetPreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TermuxWidgetPreferencesDataStore {
            return instance ?: TermuxWidgetPreferencesDataStore(context).also { instance = it }
        }
    }
}
