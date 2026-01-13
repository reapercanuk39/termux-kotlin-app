package com.termux.shared.termux.settings.preferences

/*
 * Version: v0.16.0
 *
 * Changelog
 *
 * - 0.1.0 (2021-03-12)
 *      - Initial Release.
 * ...
 * - 0.16.0 (2022-06-11)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_APP_SHELL_NUMBER_SINCE_BOOT` and `KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT`.
 */

/**
 * A class that defines shared constants of the SharedPreferences used by Termux app and its plugins.
 * This class will be hosted by termux-shared lib and should be imported by other termux plugin
 * apps as is instead of copying constants to random classes. The 3rd party apps can also import
 * it for interacting with termux apps. If changes are made to this file, increment the version number
 * and add an entry in the Changelog section above.
 */
object TermuxPreferenceConstants {

    /**
     * Termux app constants.
     */
    object TERMUX_APP {
        /**
         * Defines the key for whether terminal view margin adjustment that is done to prevent soft
         * keyboard from covering bottom part of terminal view on some devices is enabled or not.
         * Margin adjustment may cause screen flickering on some devices and so should be disabled.
         */
        const val KEY_TERMINAL_MARGIN_ADJUSTMENT: String = "terminal_margin_adjustment"
        const val DEFAULT_TERMINAL_MARGIN_ADJUSTMENT: Boolean = true

        /**
         * Defines the key for whether to show terminal toolbar containing extra keys and text input field.
         */
        const val KEY_SHOW_TERMINAL_TOOLBAR: String = "show_extra_keys"
        const val DEFAULT_VALUE_SHOW_TERMINAL_TOOLBAR: Boolean = true

        /**
         * Defines the key for whether the soft keyboard will be enabled, for cases where users want
         * to use a hardware keyboard instead.
         */
        const val KEY_SOFT_KEYBOARD_ENABLED: String = "soft_keyboard_enabled"
        const val DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED: Boolean = true

        /**
         * Defines the key for whether the soft keyboard will be enabled only if no hardware keyboard
         * attached, for cases where users want to use a hardware keyboard instead.
         */
        const val KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE: String = "soft_keyboard_enabled_only_if_no_hardware"
        const val DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE: Boolean = false

        /**
         * Defines the key for whether to always keep screen on.
         */
        const val KEY_KEEP_SCREEN_ON: String = "screen_always_on"
        const val DEFAULT_VALUE_KEEP_SCREEN_ON: Boolean = false

        /**
         * Defines the key for font size of termux terminal view.
         */
        const val KEY_FONTSIZE: String = "fontsize"

        /**
         * Defines the key for current termux terminal session.
         */
        const val KEY_CURRENT_SESSION: String = "current_session"

        /**
         * Defines the key for current log level.
         */
        const val KEY_LOG_LEVEL: String = "log_level"

        /**
         * Defines the key for last used notification id.
         */
        const val KEY_LAST_NOTIFICATION_ID: String = "last_notification_id"
        const val DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID: Int = 0

        /**
         * The [com.termux.shared.shell.command.ExecutionCommand.Runner.APP_SHELL] number after termux app process since boot.
         */
        const val KEY_APP_SHELL_NUMBER_SINCE_BOOT: String = "app_shell_number_since_boot"
        const val DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT: Int = 0

        /**
         * The [com.termux.shared.shell.command.ExecutionCommand.Runner.TERMINAL_SESSION] number after termux app process since boot.
         */
        const val KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT: String = "terminal_session_number_since_boot"
        const val DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT: Int = 0

        /**
         * Defines the key for whether termux terminal view key logging is enabled or not
         */
        const val KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED: String = "terminal_view_key_logging_enabled"
        const val DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED: Boolean = false

        /**
         * Defines the key for whether flashes and notifications for plugin errors are enabled or not.
         */
        const val KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED: String = "plugin_error_notifications_enabled"
        const val DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED: Boolean = true

        /**
         * Defines the key for whether notifications for crash reports are enabled or not.
         */
        const val KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED: String = "crash_report_notifications_enabled"
        const val DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED: Boolean = true
    }

    /**
     * Termux:API app constants.
     */
    object TERMUX_API_APP {
        /**
         * Defines the key for current log level.
         */
        const val KEY_LOG_LEVEL: String = "log_level"

        /**
         * Defines the key for last used PendingIntent request code.
         */
        const val KEY_LAST_PENDING_INTENT_REQUEST_CODE: String = "last_pending_intent_request_code"
        const val DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE: Int = 0
    }

    /**
     * Termux:Boot app constants.
     */
    object TERMUX_BOOT_APP {
        /**
         * Defines the key for current log level.
         */
        const val KEY_LOG_LEVEL: String = "log_level"
    }

    /**
     * Termux:Float app constants.
     */
    object TERMUX_FLOAT_APP {
        /**
         * The float window x coordinate.
         */
        const val KEY_WINDOW_X: String = "window_x"

        /**
         * The float window y coordinate.
         */
        const val KEY_WINDOW_Y: String = "window_y"

        /**
         * The float window width.
         */
        const val KEY_WINDOW_WIDTH: String = "window_width"

        /**
         * The float window height.
         */
        const val KEY_WINDOW_HEIGHT: String = "window_height"

        /**
         * Defines the key for font size of termux terminal view.
         */
        const val KEY_FONTSIZE: String = "fontsize"

        /**
         * Defines the key for current log level.
         */
        const val KEY_LOG_LEVEL: String = "log_level"

        /**
         * Defines the key for whether termux terminal view key logging is enabled or not
         */
        const val KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED: String = "terminal_view_key_logging_enabled"
        const val DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED: Boolean = false
    }

    /**
     * Termux:Styling app constants.
     */
    object TERMUX_STYLING_APP {
        /**
         * Defines the key for current log level.
         */
        const val KEY_LOG_LEVEL: String = "log_level"
    }

    /**
     * Termux:Tasker app constants.
     */
    object TERMUX_TASKER_APP {
        /**
         * Defines the key for current log level.
         */
        const val KEY_LOG_LEVEL: String = "log_level"

        /**
         * Defines the key for last used PendingIntent request code.
         */
        const val KEY_LAST_PENDING_INTENT_REQUEST_CODE: String = "last_pending_intent_request_code"
        const val DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE: Int = 0
    }

    /**
     * Termux:Widget app constants.
     */
    object TERMUX_WIDGET_APP {
        /**
         * Defines the key for current log level.
         */
        const val KEY_LOG_LEVEL: String = "log_level"

        /**
         * Defines the key for current token for shortcuts.
         */
        const val KEY_TOKEN: String = "token"
    }
}
