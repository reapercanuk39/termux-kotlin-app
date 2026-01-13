package com.termux.shared.termux.theme

import android.content.Context
import com.termux.shared.termux.settings.properties.TermuxSharedProperties
import com.termux.shared.theme.NightMode

object TermuxThemeUtils {

    /**
     * Get the night mode value from the properties file on disk
     * and set it to app wide night mode value.
     */
    @JvmStatic
    fun setAppNightMode(context: Context) {
        NightMode.setAppNightMode(TermuxSharedProperties.getNightMode(context))
    }

    /**
     * Set name as app wide night mode value.
     */
    @JvmStatic
    fun setAppNightMode(name: String?) {
        NightMode.setAppNightMode(name)
    }
}
