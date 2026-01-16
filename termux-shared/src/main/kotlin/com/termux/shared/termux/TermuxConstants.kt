@file:Suppress("MemberVisibilityCanBePrivate")

package com.termux.shared.termux

import android.annotation.SuppressLint
import java.io.File

/**
 * A class that defines shared constants of the Termux app and its plugins.
 * Version: v0.53.0
 */
object TermuxConstants {

    /*
     * Termux organization variables.
     */

    /** Termux GitHub organization name */
    @JvmField val TERMUX_GITHUB_ORGANIZATION_NAME = "termux"
    /** Termux GitHub organization url */
    @JvmField val TERMUX_GITHUB_ORGANIZATION_URL = "https://github.com/$TERMUX_GITHUB_ORGANIZATION_NAME"

    /** F-Droid packages base url */
    @JvmField val FDROID_PACKAGES_BASE_URL = "https://f-droid.org/en/packages"

    /*
     * Termux and its plugin app and package names and urls.
     */

    /** Termux app name */
    @JvmField val TERMUX_APP_NAME = "Termux"
    /** Termux package name */
    @JvmField val TERMUX_PACKAGE_NAME = "com.termux.kotlin"
    /** Upstream Termux package name used in bootstrap binaries */
    @JvmField val TERMUX_UPSTREAM_PACKAGE_NAME = "com.termux"
    /** Termux GitHub repo name */
    @JvmField val TERMUX_GITHUB_REPO_NAME = "termux-app"
    /** Termux GitHub repo url */
    @JvmField val TERMUX_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_GITHUB_REPO_NAME"
    /** Termux GitHub issues repo url */
    @JvmField val TERMUX_GITHUB_ISSUES_REPO_URL = "$TERMUX_GITHUB_REPO_URL/issues"
    /** Termux F-Droid package url */
    @JvmField val TERMUX_FDROID_PACKAGE_URL = "$FDROID_PACKAGES_BASE_URL/$TERMUX_PACKAGE_NAME"

    /** Termux:API app name */
    @JvmField val TERMUX_API_APP_NAME = "Termux:API"
    /** Termux:API app package name */
    @JvmField val TERMUX_API_PACKAGE_NAME = "$TERMUX_PACKAGE_NAME.api"
    /** Termux:API GitHub repo name */
    @JvmField val TERMUX_API_GITHUB_REPO_NAME = "termux-api"
    /** Termux:API GitHub repo url */
    @JvmField val TERMUX_API_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_API_GITHUB_REPO_NAME"
    /** Termux:API GitHub issues repo url */
    @JvmField val TERMUX_API_GITHUB_ISSUES_REPO_URL = "$TERMUX_API_GITHUB_REPO_URL/issues"
    /** Termux:API F-Droid package url */
    @JvmField val TERMUX_API_FDROID_PACKAGE_URL = "$FDROID_PACKAGES_BASE_URL/$TERMUX_API_PACKAGE_NAME"

    /** Termux:Boot app name */
    @JvmField val TERMUX_BOOT_APP_NAME = "Termux:Boot"
    /** Termux:Boot app package name */
    @JvmField val TERMUX_BOOT_PACKAGE_NAME = "$TERMUX_PACKAGE_NAME.boot"
    /** Termux:Boot GitHub repo name */
    @JvmField val TERMUX_BOOT_GITHUB_REPO_NAME = "termux-boot"
    /** Termux:Boot GitHub repo url */
    @JvmField val TERMUX_BOOT_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_BOOT_GITHUB_REPO_NAME"
    /** Termux:Boot GitHub issues repo url */
    @JvmField val TERMUX_BOOT_GITHUB_ISSUES_REPO_URL = "$TERMUX_BOOT_GITHUB_REPO_URL/issues"
    /** Termux:Boot F-Droid package url */
    @JvmField val TERMUX_BOOT_FDROID_PACKAGE_URL = "$FDROID_PACKAGES_BASE_URL/$TERMUX_BOOT_PACKAGE_NAME"

    /** Termux:Float app name */
    @JvmField val TERMUX_FLOAT_APP_NAME = "Termux:Float"
    /** Termux:Float app package name */
    @JvmField val TERMUX_FLOAT_PACKAGE_NAME = "$TERMUX_PACKAGE_NAME.window"
    /** Termux:Float GitHub repo name */
    @JvmField val TERMUX_FLOAT_GITHUB_REPO_NAME = "termux-float"
    /** Termux:Float GitHub repo url */
    @JvmField val TERMUX_FLOAT_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_FLOAT_GITHUB_REPO_NAME"
    /** Termux:Float GitHub issues repo url */
    @JvmField val TERMUX_FLOAT_GITHUB_ISSUES_REPO_URL = "$TERMUX_FLOAT_GITHUB_REPO_URL/issues"
    /** Termux:Float F-Droid package url */
    @JvmField val TERMUX_FLOAT_FDROID_PACKAGE_URL = "$FDROID_PACKAGES_BASE_URL/$TERMUX_FLOAT_PACKAGE_NAME"

    /** Termux:Styling app name */
    @JvmField val TERMUX_STYLING_APP_NAME = "Termux:Styling"
    /** Termux:Styling app package name */
    @JvmField val TERMUX_STYLING_PACKAGE_NAME = "$TERMUX_PACKAGE_NAME.styling"
    /** Termux:Styling GitHub repo name */
    @JvmField val TERMUX_STYLING_GITHUB_REPO_NAME = "termux-styling"
    /** Termux:Styling GitHub repo url */
    @JvmField val TERMUX_STYLING_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_STYLING_GITHUB_REPO_NAME"
    /** Termux:Styling GitHub issues repo url */
    @JvmField val TERMUX_STYLING_GITHUB_ISSUES_REPO_URL = "$TERMUX_STYLING_GITHUB_REPO_URL/issues"
    /** Termux:Styling F-Droid package url */
    @JvmField val TERMUX_STYLING_FDROID_PACKAGE_URL = "$FDROID_PACKAGES_BASE_URL/$TERMUX_STYLING_PACKAGE_NAME"

    /** Termux:Tasker app name */
    @JvmField val TERMUX_TASKER_APP_NAME = "Termux:Tasker"
    /** Termux:Tasker app package name */
    @JvmField val TERMUX_TASKER_PACKAGE_NAME = "$TERMUX_PACKAGE_NAME.tasker"
    /** Termux:Tasker GitHub repo name */
    @JvmField val TERMUX_TASKER_GITHUB_REPO_NAME = "termux-tasker"
    /** Termux:Tasker GitHub repo url */
    @JvmField val TERMUX_TASKER_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_TASKER_GITHUB_REPO_NAME"
    /** Termux:Tasker GitHub issues repo url */
    @JvmField val TERMUX_TASKER_GITHUB_ISSUES_REPO_URL = "$TERMUX_TASKER_GITHUB_REPO_URL/issues"
    /** Termux:Tasker F-Droid package url */
    @JvmField val TERMUX_TASKER_FDROID_PACKAGE_URL = "$FDROID_PACKAGES_BASE_URL/$TERMUX_TASKER_PACKAGE_NAME"

    /** Termux:Widget app name */
    @JvmField val TERMUX_WIDGET_APP_NAME = "Termux:Widget"
    /** Termux:Widget app package name */
    @JvmField val TERMUX_WIDGET_PACKAGE_NAME = "$TERMUX_PACKAGE_NAME.widget"
    /** Termux:Widget GitHub repo name */
    @JvmField val TERMUX_WIDGET_GITHUB_REPO_NAME = "termux-widget"
    /** Termux:Widget GitHub repo url */
    @JvmField val TERMUX_WIDGET_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_WIDGET_GITHUB_REPO_NAME"
    /** Termux:Widget GitHub issues repo url */
    @JvmField val TERMUX_WIDGET_GITHUB_ISSUES_REPO_URL = "$TERMUX_WIDGET_GITHUB_REPO_URL/issues"
    /** Termux:Widget F-Droid package url */
    @JvmField val TERMUX_WIDGET_FDROID_PACKAGE_URL = "$FDROID_PACKAGES_BASE_URL/$TERMUX_WIDGET_PACKAGE_NAME"

    /*
     * Termux plugin apps lists.
     */

    @JvmField val TERMUX_PLUGIN_APP_NAMES_LIST: List<String> = listOf(
        TERMUX_API_APP_NAME,
        TERMUX_BOOT_APP_NAME,
        TERMUX_FLOAT_APP_NAME,
        TERMUX_STYLING_APP_NAME,
        TERMUX_TASKER_APP_NAME,
        TERMUX_WIDGET_APP_NAME
    )

    @JvmField val TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST: List<String> = listOf(
        TERMUX_API_PACKAGE_NAME,
        TERMUX_BOOT_PACKAGE_NAME,
        TERMUX_FLOAT_PACKAGE_NAME,
        TERMUX_STYLING_PACKAGE_NAME,
        TERMUX_TASKER_PACKAGE_NAME,
        TERMUX_WIDGET_PACKAGE_NAME
    )

    /*
     * Termux APK releases.
     */

    /** F-Droid APK release */
    @JvmField val APK_RELEASE_FDROID = "F-Droid"
    @JvmField val APK_RELEASE_FDROID_SIGNING_CERTIFICATE_SHA256_DIGEST = "228FB2CFE90831C1499EC3CCAF61E96E8E1CE70766B9474672CE427334D41C42"

    /** GitHub APK release */
    @JvmField val APK_RELEASE_GITHUB = "Github"
    @JvmField val APK_RELEASE_GITHUB_SIGNING_CERTIFICATE_SHA256_DIGEST = "B6DA01480EEFD5FBF2CD3771B8D1021EC791304BDD6C4BF41D3FAABAD48EE5E1"

    /** Google Play Store APK release */
    @JvmField val APK_RELEASE_GOOGLE_PLAYSTORE = "Google Play Store"
    @JvmField val APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST = "738F0A30A04D3C8A1BE304AF18D0779BCF3EA88FB60808F657A3521861C2EBF9"

    /** Termux Devs APK release */
    @JvmField val APK_RELEASE_TERMUX_DEVS = "Termux Devs"
    @JvmField val APK_RELEASE_TERMUX_DEVS_SIGNING_CERTIFICATE_SHA256_DIGEST = "F7A038EB551F1BE8FDF388686B784ABAB4552A5D82DF423E3D8F1B5CBE1C69AE"

    /*
     * Termux packages urls.
     */

    @JvmField val TERMUX_PACKAGES_GITHUB_REPO_NAME = "termux-packages"
    @JvmField val TERMUX_PACKAGES_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_PACKAGES_GITHUB_REPO_NAME"
    @JvmField val TERMUX_PACKAGES_GITHUB_ISSUES_REPO_URL = "$TERMUX_PACKAGES_GITHUB_REPO_URL/issues"

    @JvmField val TERMUX_API_APT_PACKAGE_NAME = "termux-api"
    @JvmField val TERMUX_API_APT_GITHUB_REPO_NAME = "termux-api-package"
    @JvmField val TERMUX_API_APT_GITHUB_REPO_URL = "$TERMUX_GITHUB_ORGANIZATION_URL/$TERMUX_API_APT_GITHUB_REPO_NAME"
    @JvmField val TERMUX_API_APT_GITHUB_ISSUES_REPO_URL = "$TERMUX_API_APT_GITHUB_REPO_URL/issues"

    /*
     * Termux miscellaneous urls.
     */

    @JvmField val TERMUX_SITE = "$TERMUX_APP_NAME Site"
    @JvmField val TERMUX_SITE_URL = "https://termux.dev"
    @JvmField val TERMUX_WIKI = "$TERMUX_APP_NAME Wiki"
    @JvmField val TERMUX_WIKI_URL = "https://wiki.termux.com"
    @JvmField val TERMUX_GITHUB_WIKI_REPO_URL = "$TERMUX_GITHUB_REPO_URL/wiki"
    @JvmField val TERMUX_PACKAGES_GITHUB_WIKI_REPO_URL = "$TERMUX_PACKAGES_GITHUB_REPO_URL/wiki"

    @JvmField val TERMUX_SUPPORT_EMAIL_URL = "support@termux.dev"
    @JvmField val TERMUX_SUPPORT_EMAIL_MAILTO_URL = "mailto:$TERMUX_SUPPORT_EMAIL_URL"

    @JvmField val TERMUX_REDDIT_SUBREDDIT = "r/termux"
    @JvmField val TERMUX_REDDIT_SUBREDDIT_URL = "https://www.reddit.com/r/termux"

    @JvmField val TERMUX_DONATE_URL = "$TERMUX_SITE_URL/donate"

    /*
     * Termux app core directory paths.
     */

    @SuppressLint("SdCardPath")
    @JvmField val TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "/data/data/$TERMUX_PACKAGE_NAME"
    @JvmField val TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR = File(TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH)

    @JvmField val TERMUX_FILES_DIR_PATH = "$TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH/files"
    @JvmField val TERMUX_FILES_DIR = File(TERMUX_FILES_DIR_PATH)

    @JvmField val TERMUX_PREFIX_DIR_PATH = "$TERMUX_FILES_DIR_PATH/usr"
    @JvmField val TERMUX_PREFIX_DIR = File(TERMUX_PREFIX_DIR_PATH)

    @JvmField val TERMUX_BIN_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/bin"
    @JvmField val TERMUX_BIN_PREFIX_DIR = File(TERMUX_BIN_PREFIX_DIR_PATH)

    @JvmField val TERMUX_ETC_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/etc"
    @JvmField val TERMUX_ETC_PREFIX_DIR = File(TERMUX_ETC_PREFIX_DIR_PATH)

    @JvmField val TERMUX_INCLUDE_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/include"
    @JvmField val TERMUX_INCLUDE_PREFIX_DIR = File(TERMUX_INCLUDE_PREFIX_DIR_PATH)

    @JvmField val TERMUX_LIB_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/lib"
    @JvmField val TERMUX_LIB_PREFIX_DIR = File(TERMUX_LIB_PREFIX_DIR_PATH)

    @JvmField val TERMUX_LIBEXEC_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/libexec"
    @JvmField val TERMUX_LIBEXEC_PREFIX_DIR = File(TERMUX_LIBEXEC_PREFIX_DIR_PATH)

    @JvmField val TERMUX_SHARE_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/share"
    @JvmField val TERMUX_SHARE_PREFIX_DIR = File(TERMUX_SHARE_PREFIX_DIR_PATH)

    @JvmField val TERMUX_TMP_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/tmp"
    @JvmField val TERMUX_TMP_PREFIX_DIR = File(TERMUX_TMP_PREFIX_DIR_PATH)

    @JvmField val TERMUX_VAR_PREFIX_DIR_PATH = "$TERMUX_PREFIX_DIR_PATH/var"
    @JvmField val TERMUX_VAR_PREFIX_DIR = File(TERMUX_VAR_PREFIX_DIR_PATH)

    @JvmField val TERMUX_STAGING_PREFIX_DIR_PATH = "$TERMUX_FILES_DIR_PATH/usr-staging"
    @JvmField val TERMUX_STAGING_PREFIX_DIR = File(TERMUX_STAGING_PREFIX_DIR_PATH)

    @JvmField val TERMUX_HOME_DIR_PATH = "$TERMUX_FILES_DIR_PATH/home"
    @JvmField val TERMUX_HOME_DIR = File(TERMUX_HOME_DIR_PATH)

    @JvmField val TERMUX_CONFIG_HOME_DIR_PATH = "$TERMUX_HOME_DIR_PATH/.config/termux"
    @JvmField val TERMUX_CONFIG_HOME_DIR = File(TERMUX_CONFIG_HOME_DIR_PATH)

    @JvmField val TERMUX_CONFIG_PREFIX_DIR_PATH = "$TERMUX_ETC_PREFIX_DIR_PATH/termux"
    @JvmField val TERMUX_CONFIG_PREFIX_DIR = File(TERMUX_CONFIG_PREFIX_DIR_PATH)

    @JvmField val TERMUX_DATA_HOME_DIR_PATH = "$TERMUX_HOME_DIR_PATH/.termux"
    @JvmField val TERMUX_DATA_HOME_DIR = File(TERMUX_DATA_HOME_DIR_PATH)

    @JvmField val TERMUX_STORAGE_HOME_DIR_PATH = "$TERMUX_HOME_DIR_PATH/storage"
    @JvmField val TERMUX_STORAGE_HOME_DIR = File(TERMUX_STORAGE_HOME_DIR_PATH)

    @JvmField val TERMUX_APPS_DIR_PATH = "$TERMUX_FILES_DIR_PATH/apps"
    @JvmField val TERMUX_APPS_DIR = File(TERMUX_APPS_DIR_PATH)

    @JvmField val TERMUX_ENV_FILE_PATH = "$TERMUX_CONFIG_PREFIX_DIR_PATH/termux.env"
    @JvmField val TERMUX_ENV_TEMP_FILE_PATH = "$TERMUX_CONFIG_PREFIX_DIR_PATH/termux.env.tmp"

    @JvmField val TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY: List<String> = listOf(
        TERMUX_TMP_PREFIX_DIR_PATH, TERMUX_ENV_TEMP_FILE_PATH, TERMUX_ENV_FILE_PATH
    )

    /*
     * Termux app and plugin preferences and properties file paths.
     */

    @JvmField val TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "${TERMUX_PACKAGE_NAME}_preferences"
    @JvmField val TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "${TERMUX_API_PACKAGE_NAME}_preferences"
    @JvmField val TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "${TERMUX_BOOT_PACKAGE_NAME}_preferences"
    @JvmField val TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "${TERMUX_FLOAT_PACKAGE_NAME}_preferences"
    @JvmField val TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "${TERMUX_STYLING_PACKAGE_NAME}_preferences"
    @JvmField val TERMUX_TASKER_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "${TERMUX_TASKER_PACKAGE_NAME}_preferences"
    @JvmField val TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "${TERMUX_WIDGET_PACKAGE_NAME}_preferences"

    @JvmField val TERMUX_PROPERTIES_PRIMARY_FILE_PATH = "$TERMUX_DATA_HOME_DIR_PATH/termux.properties"
    @JvmField val TERMUX_PROPERTIES_PRIMARY_FILE = File(TERMUX_PROPERTIES_PRIMARY_FILE_PATH)
    @JvmField val TERMUX_PROPERTIES_SECONDARY_FILE_PATH = "$TERMUX_CONFIG_HOME_DIR_PATH/termux.properties"
    @JvmField val TERMUX_PROPERTIES_SECONDARY_FILE = File(TERMUX_PROPERTIES_SECONDARY_FILE_PATH)
    @JvmField val TERMUX_PROPERTIES_FILE_PATHS_LIST: List<String> = listOf(
        TERMUX_PROPERTIES_PRIMARY_FILE_PATH, TERMUX_PROPERTIES_SECONDARY_FILE_PATH
    )

    @JvmField val TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH = "$TERMUX_DATA_HOME_DIR_PATH/termux.float.properties"
    @JvmField val TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE = File(TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH)
    @JvmField val TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH = "$TERMUX_CONFIG_HOME_DIR_PATH/termux.float.properties"
    @JvmField val TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE = File(TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH)
    @JvmField val TERMUX_FLOAT_PROPERTIES_FILE_PATHS_LIST: List<String> = listOf(
        TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH, TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH
    )

    @JvmField val TERMUX_COLOR_PROPERTIES_FILE_PATH = "$TERMUX_DATA_HOME_DIR_PATH/colors.properties"
    @JvmField val TERMUX_COLOR_PROPERTIES_FILE = File(TERMUX_COLOR_PROPERTIES_FILE_PATH)
    @JvmField val TERMUX_FONT_FILE_PATH = "$TERMUX_DATA_HOME_DIR_PATH/font.ttf"
    @JvmField val TERMUX_FONT_FILE = File(TERMUX_FONT_FILE_PATH)

    @JvmField val TERMUX_CRASH_LOG_FILE_PATH = "$TERMUX_HOME_DIR_PATH/crash_log.md"
    @JvmField val TERMUX_CRASH_LOG_BACKUP_FILE_PATH = "$TERMUX_HOME_DIR_PATH/crash_log_backup.md"

    /*
     * Termux app plugin specific paths.
     */

    @JvmField val TERMUX_BOOT_SCRIPTS_DIR_PATH = "$TERMUX_DATA_HOME_DIR_PATH/boot"
    @JvmField val TERMUX_BOOT_SCRIPTS_DIR = File(TERMUX_BOOT_SCRIPTS_DIR_PATH)

    @JvmField val TERMUX_SHORTCUT_SCRIPTS_DIR_PATH = "$TERMUX_HOME_DIR_PATH/.shortcuts"
    @JvmField val TERMUX_SHORTCUT_SCRIPTS_DIR = File(TERMUX_SHORTCUT_SCRIPTS_DIR_PATH)

    @JvmField val TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME = "tasks"
    @JvmField val TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_PATH = "$TERMUX_SHORTCUT_SCRIPTS_DIR_PATH/$TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME"
    @JvmField val TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR = File(TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_PATH)

    @JvmField val TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME = "icons"
    @JvmField val TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH = "$TERMUX_SHORTCUT_SCRIPTS_DIR_PATH/$TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME"
    @JvmField val TERMUX_SHORTCUT_SCRIPT_ICONS_DIR = File(TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH)

    @JvmField val TERMUX_TASKER_SCRIPTS_DIR_PATH = "$TERMUX_DATA_HOME_DIR_PATH/tasker"
    @JvmField val TERMUX_TASKER_SCRIPTS_DIR = File(TERMUX_TASKER_SCRIPTS_DIR_PATH)

    /*
     * Termux app and plugins notification variables.
     */

    @JvmField val TERMUX_APP_NOTIFICATION_CHANNEL_ID = "termux_notification_channel"
    @JvmField val TERMUX_APP_NOTIFICATION_CHANNEL_NAME = "$TERMUX_APP_NAME App"
    const val TERMUX_APP_NOTIFICATION_ID = 1337

    @JvmField val TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID = "termux_run_command_notification_channel"
    @JvmField val TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME = "$TERMUX_APP_NAME RunCommandService"
    const val TERMUX_RUN_COMMAND_NOTIFICATION_ID = 1338

    @JvmField val TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID = "termux_plugin_command_errors_notification_channel"
    @JvmField val TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME = "$TERMUX_APP_NAME Plugin Commands Errors"

    @JvmField val TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID = "termux_crash_reports_notification_channel"
    @JvmField val TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME = "$TERMUX_APP_NAME Crash Reports"

    @JvmField val TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID = "termux_float_notification_channel"
    @JvmField val TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_NAME = "$TERMUX_FLOAT_APP_NAME App"
    const val TERMUX_FLOAT_APP_NOTIFICATION_ID = 1339

    /*
     * Termux app and plugins miscellaneous variables.
     */

    @JvmField val PERMISSION_RUN_COMMAND = "$TERMUX_PACKAGE_NAME.permission.RUN_COMMAND"
    @JvmField val PROP_ALLOW_EXTERNAL_APPS = "allow-external-apps"
    @JvmField val PROP_DEFAULT_VALUE_ALLOW_EXTERNAL_APPS = "false"

    @JvmField val BROADCAST_TERMUX_OPENED = "$TERMUX_PACKAGE_NAME.app.OPENED"
    @JvmField val TERMUX_FILE_SHARE_URI_AUTHORITY = "$TERMUX_PACKAGE_NAME.files"

    @JvmField val COMMA_NORMAL = ","
    @JvmField val COMMA_ALTERNATIVE = "‚"

    @JvmField val TERMUX_ENV_PREFIX_ROOT = "TERMUX"

    /**
     * Termux app constants.
     */
    object TERMUX_APP {
        @JvmField val APPS_DIR_PATH = "$TERMUX_APPS_DIR_PATH/$TERMUX_PACKAGE_NAME"
        @JvmField val TERMUX_AM_SOCKET_FILE_PATH = "$APPS_DIR_PATH/termux-am/am.sock"

        @JvmField val BUILD_CONFIG_CLASS_NAME = "$TERMUX_PACKAGE_NAME.BuildConfig"
        @JvmField val FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME = "$TERMUX_PACKAGE_NAME.app.api.file.FileShareReceiverActivity"
        @JvmField val FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME = "$TERMUX_PACKAGE_NAME.app.api.file.FileViewReceiverActivity"

        @JvmField val TERMUX_ACTIVITY_NAME = "$TERMUX_PACKAGE_NAME.app.TermuxActivity"

        /**
         * Termux app core activity.
         */
        object TERMUX_ACTIVITY {
            @JvmField val EXTRA_FAILSAFE_SESSION = "$TERMUX_PACKAGE_NAME.app.failsafe_session"
            @JvmField val ACTION_NOTIFY_APP_CRASH = "$TERMUX_PACKAGE_NAME.app.notify_app_crash"
            @JvmField val ACTION_RELOAD_STYLE = "$TERMUX_PACKAGE_NAME.app.reload_style"
            @Deprecated("Deprecated in original Java code")
            @JvmField val EXTRA_RELOAD_STYLE = "$TERMUX_PACKAGE_NAME.app.reload_style"
            @JvmField val EXTRA_RECREATE_ACTIVITY = "$TERMUX_ACTIVITY_NAME.EXTRA_RECREATE_ACTIVITY"
            @JvmField val ACTION_REQUEST_PERMISSIONS = "$TERMUX_PACKAGE_NAME.app.request_storage_permissions"
        }

        @JvmField val TERMUX_SETTINGS_ACTIVITY_NAME = "$TERMUX_PACKAGE_NAME.app.activities.SettingsActivity"
        @JvmField val TERMUX_SERVICE_NAME = "$TERMUX_PACKAGE_NAME.app.TermuxService"

        /**
         * Termux app core service.
         */
        object TERMUX_SERVICE {
            @JvmField val ACTION_STOP_SERVICE = "$TERMUX_PACKAGE_NAME.service_stop"
            @JvmField val ACTION_WAKE_LOCK = "$TERMUX_PACKAGE_NAME.service_wake_lock"
            @JvmField val ACTION_WAKE_UNLOCK = "$TERMUX_PACKAGE_NAME.service_wake_unlock"
            @JvmField val ACTION_SERVICE_EXECUTE = "$TERMUX_PACKAGE_NAME.service_execute"

            @JvmField val URI_SCHEME_SERVICE_EXECUTE = "$TERMUX_PACKAGE_NAME.file"
            @JvmField val EXTRA_ARGUMENTS = "$TERMUX_PACKAGE_NAME.execute.arguments"
            @JvmField val EXTRA_STDIN = "$TERMUX_PACKAGE_NAME.execute.stdin"
            @JvmField val EXTRA_WORKDIR = "$TERMUX_PACKAGE_NAME.execute.cwd"
            @Deprecated("Use EXTRA_RUNNER instead")
            @JvmField val EXTRA_BACKGROUND = "$TERMUX_PACKAGE_NAME.execute.background"
            @JvmField val EXTRA_RUNNER = "$TERMUX_PACKAGE_NAME.execute.runner"
            @JvmField val EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL = "$TERMUX_PACKAGE_NAME.execute.background_custom_log_level"
            @JvmField val EXTRA_SESSION_ACTION = "$TERMUX_PACKAGE_NAME.execute.session_action"
            @JvmField val EXTRA_SHELL_NAME = "$TERMUX_PACKAGE_NAME.execute.shell_name"
            @JvmField val EXTRA_SHELL_CREATE_MODE = "$TERMUX_PACKAGE_NAME.execute.shell_create_mode"
            @JvmField val EXTRA_COMMAND_LABEL = "$TERMUX_PACKAGE_NAME.execute.command_label"
            @JvmField val EXTRA_COMMAND_DESCRIPTION = "$TERMUX_PACKAGE_NAME.execute.command_description"
            @JvmField val EXTRA_COMMAND_HELP = "$TERMUX_PACKAGE_NAME.execute.command_help"
            @JvmField val EXTRA_PLUGIN_API_HELP = "$TERMUX_PACKAGE_NAME.execute.plugin_api_help"
            @JvmField val EXTRA_PENDING_INTENT = "pendingIntent"
            @JvmField val EXTRA_RESULT_DIRECTORY = "$TERMUX_PACKAGE_NAME.execute.result_directory"
            @JvmField val EXTRA_RESULT_SINGLE_FILE = "$TERMUX_PACKAGE_NAME.execute.result_single_file"
            @JvmField val EXTRA_RESULT_FILE_BASENAME = "$TERMUX_PACKAGE_NAME.execute.result_file_basename"
            @JvmField val EXTRA_RESULT_FILE_OUTPUT_FORMAT = "$TERMUX_PACKAGE_NAME.execute.result_file_output_format"
            @JvmField val EXTRA_RESULT_FILE_ERROR_FORMAT = "$TERMUX_PACKAGE_NAME.execute.result_file_error_format"
            @JvmField val EXTRA_RESULT_FILES_SUFFIX = "$TERMUX_PACKAGE_NAME.execute.result_files_suffix"

            const val VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY = 0
            const val VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY = 1
            const val VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY = 2
            const val VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY = 3

            const val MIN_VALUE_EXTRA_SESSION_ACTION = VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY
            const val MAX_VALUE_EXTRA_SESSION_ACTION = VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY

            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT = "stdout"
            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"
            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE_STDERR = "stderr"
            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE_STDERR_ORIGINAL_LENGTH = "stderr_original_length"
            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE = "exitCode"
            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE_ERR = "err"
            @JvmField val EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG = "errmsg"
        }

        @JvmField val RUN_COMMAND_SERVICE_NAME = "$TERMUX_PACKAGE_NAME.app.RunCommandService"

        /**
         * Termux app run command service.
         */
        object RUN_COMMAND_SERVICE {
            @JvmField val RUN_COMMAND_API_HELP_URL = "$TERMUX_GITHUB_WIKI_REPO_URL/RUN_COMMAND-Intent"

            @JvmField val ACTION_RUN_COMMAND = "$TERMUX_PACKAGE_NAME.RUN_COMMAND"
            @JvmField val EXTRA_COMMAND_PATH = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_PATH"
            @JvmField val EXTRA_ARGUMENTS = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_ARGUMENTS"
            @JvmField val EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"
            @JvmField val EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"
            @JvmField val EXTRA_STDIN = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_STDIN"
            @JvmField val EXTRA_WORKDIR = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_WORKDIR"
            @Deprecated("Use EXTRA_RUNNER instead")
            @JvmField val EXTRA_BACKGROUND = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_BACKGROUND"
            @JvmField val EXTRA_RUNNER = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_RUNNER"
            @JvmField val EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL"
            @JvmField val EXTRA_SESSION_ACTION = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_SESSION_ACTION"
            @JvmField val EXTRA_SHELL_NAME = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_SHELL_NAME"
            @JvmField val EXTRA_SHELL_CREATE_MODE = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_SHELL_CREATE_MODE"
            @JvmField val EXTRA_COMMAND_LABEL = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_COMMAND_LABEL"
            @JvmField val EXTRA_COMMAND_DESCRIPTION = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_COMMAND_DESCRIPTION"
            @JvmField val EXTRA_COMMAND_HELP = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_COMMAND_HELP"
            @JvmField val EXTRA_PENDING_INTENT = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_PENDING_INTENT"
            @JvmField val EXTRA_RESULT_DIRECTORY = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_RESULT_DIRECTORY"
            @JvmField val EXTRA_RESULT_SINGLE_FILE = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_RESULT_SINGLE_FILE"
            @JvmField val EXTRA_RESULT_FILE_BASENAME = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_RESULT_FILE_BASENAME"
            @JvmField val EXTRA_RESULT_FILE_OUTPUT_FORMAT = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_RESULT_FILE_OUTPUT_FORMAT"
            @JvmField val EXTRA_RESULT_FILE_ERROR_FORMAT = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_RESULT_FILE_ERROR_FORMAT"
            @JvmField val EXTRA_RESULT_FILES_SUFFIX = "$TERMUX_PACKAGE_NAME.RUN_COMMAND_RESULT_FILES_SUFFIX"
        }
    }

    /**
     * Termux:API app constants.
     */
    object TERMUX_API_APP {
        @JvmField val TERMUX_API_MAIN_ACTIVITY_NAME = "$TERMUX_API_PACKAGE_NAME.activities.TermuxAPIMainActivity"
        @JvmField val TERMUX_API_LAUNCHER_ACTIVITY_NAME = "$TERMUX_API_PACKAGE_NAME.activities.TermuxAPILauncherActivity"
    }

    /**
     * Termux:Boot app constants.
     */
    object TERMUX_BOOT_APP {
        @JvmField val TERMUX_BOOT_MAIN_ACTIVITY_NAME = "$TERMUX_BOOT_PACKAGE_NAME.activities.TermuxBootMainActivity"
        @JvmField val TERMUX_BOOT_LAUNCHER_ACTIVITY_NAME = "$TERMUX_BOOT_PACKAGE_NAME.activities.TermuxBootLauncherActivity"
    }

    /**
     * Termux:Float app constants.
     */
    object TERMUX_FLOAT_APP {
        @JvmField val TERMUX_FLOAT_ACTIVITY_NAME = "$TERMUX_FLOAT_PACKAGE_NAME.TermuxFloatActivity"
        @JvmField val TERMUX_FLOAT_SERVICE_NAME = "$TERMUX_FLOAT_PACKAGE_NAME.TermuxFloatService"

        /**
         * Termux:Float app core service.
         */
        object TERMUX_FLOAT_SERVICE {
            @JvmField val ACTION_STOP_SERVICE = "$TERMUX_FLOAT_PACKAGE_NAME.ACTION_STOP_SERVICE"
            @JvmField val ACTION_SHOW = "$TERMUX_FLOAT_PACKAGE_NAME.ACTION_SHOW"
            @JvmField val ACTION_HIDE = "$TERMUX_FLOAT_PACKAGE_NAME.ACTION_HIDE"
        }
    }

    /**
     * Termux:Styling app constants.
     */
    object TERMUX_STYLING_APP {
        @JvmField val TERMUX_STYLING_ACTIVITY_NAME = "$TERMUX_STYLING_PACKAGE_NAME.TermuxStyleActivity"
        @JvmField val TERMUX_STYLING_MAIN_ACTIVITY_NAME = "$TERMUX_STYLING_PACKAGE_NAME.activities.TermuxStylingMainActivity"
        @JvmField val TERMUX_STYLING_LAUNCHER_ACTIVITY_NAME = "$TERMUX_STYLING_PACKAGE_NAME.activities.TermuxStylingLauncherActivity"
    }

    /**
     * Termux:Tasker app constants.
     */
    object TERMUX_TASKER_APP {
        @JvmField val TERMUX_TASKER_MAIN_ACTIVITY_NAME = "$TERMUX_TASKER_PACKAGE_NAME.activities.TermuxTaskerMainActivity"
        @JvmField val TERMUX_TASKER_LAUNCHER_ACTIVITY_NAME = "$TERMUX_TASKER_PACKAGE_NAME.activities.TermuxTaskerLauncherActivity"
    }

    /**
     * Termux:Widget app constants.
     */
    object TERMUX_WIDGET_APP {
        @JvmField val TERMUX_WIDGET_MAIN_ACTIVITY_NAME = "$TERMUX_WIDGET_PACKAGE_NAME.activities.TermuxWidgetMainActivity"
        @JvmField val TERMUX_WIDGET_LAUNCHER_ACTIVITY_NAME = "$TERMUX_WIDGET_PACKAGE_NAME.activities.TermuxWidgetLauncherActivity"

        @JvmField val EXTRA_TOKEN_NAME = "$TERMUX_PACKAGE_NAME.shortcut.token"

        /**
         * Termux:Widget app AppWidgetProvider class.
         */
        object TERMUX_WIDGET_PROVIDER {
            @JvmField val ACTION_WIDGET_ITEM_CLICKED = "$TERMUX_WIDGET_PACKAGE_NAME.ACTION_WIDGET_ITEM_CLICKED"
            @JvmField val ACTION_REFRESH_WIDGET = "$TERMUX_WIDGET_PACKAGE_NAME.ACTION_REFRESH_WIDGET"
            @JvmField val EXTRA_FILE_CLICKED = "$TERMUX_WIDGET_PACKAGE_NAME.EXTRA_FILE_CLICKED"
        }
    }
}
