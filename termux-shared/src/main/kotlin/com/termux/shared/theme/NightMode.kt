package com.termux.shared.theme

import androidx.appcompat.app.AppCompatDelegate
import com.termux.shared.logger.Logger

/** The modes used by to decide night mode for themes. */
enum class NightMode(
    val modeName: String,
    @AppCompatDelegate.NightMode val mode: Int
) {
    /** Night theme should be enabled. */
    TRUE("true", AppCompatDelegate.MODE_NIGHT_YES),

    /** Dark theme should be enabled. */
    FALSE("false", AppCompatDelegate.MODE_NIGHT_NO),

    /**
     * Use night or dark theme depending on system night mode.
     * https://developer.android.com/guide/topics/resources/providing-resources#NightQualifier
     */
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    @Deprecated("Use modeName instead", ReplaceWith("modeName"))
    fun getName(): String = modeName

    companion object {
        private const val LOG_TAG = "NightMode"

        /** The current app wide night mode used by various libraries. Defaults to [SYSTEM]. */
        private var APP_NIGHT_MODE: NightMode? = null

        /** Get [NightMode] for `name` if found, otherwise null. */
        @JvmStatic
        fun modeOf(name: String?): NightMode? {
            for (v in values()) {
                if (v.modeName == name) {
                    return v
                }
            }
            return null
        }

        /** Get [NightMode] for `name` if found, otherwise `def`. */
        @JvmStatic
        fun modeOf(name: String?, def: NightMode): NightMode {
            return modeOf(name) ?: def
        }

        /** Set [APP_NIGHT_MODE]. */
        @JvmStatic
        fun setAppNightMode(name: String?) {
            if (name.isNullOrEmpty()) {
                APP_NIGHT_MODE = SYSTEM
            } else {
                val nightMode = modeOf(name)
                if (nightMode == null) {
                    Logger.logError(LOG_TAG, "Invalid APP_NIGHT_MODE \"$name\"")
                    return
                }
                APP_NIGHT_MODE = nightMode
            }
            Logger.logVerbose(LOG_TAG, "Set APP_NIGHT_MODE to \"${APP_NIGHT_MODE?.modeName}\"")
        }

        /** Get [APP_NIGHT_MODE]. */
        @JvmStatic
        fun getAppNightMode(): NightMode {
            if (APP_NIGHT_MODE == null) {
                APP_NIGHT_MODE = SYSTEM
            }
            return APP_NIGHT_MODE!!
        }
    }
}
