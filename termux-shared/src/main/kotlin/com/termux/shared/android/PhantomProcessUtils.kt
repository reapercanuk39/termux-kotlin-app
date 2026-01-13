package com.termux.shared.android

import android.Manifest
import android.content.Context
import android.os.Build
import com.termux.shared.logger.Logger
import com.termux.shared.shell.command.environment.AndroidShellEnvironment
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell

/**
 * Utils for phantom processes added in android 12.
 *
 * https://github.com/termux/termux-app/issues/2366
 * https://issuetracker.google.com/u/1/issues/205156966#comment28
 * https://cs.android.com/android/_/android/platform/frameworks/base/+/09dcdad5
 */
object PhantomProcessUtils {

    private const val LOG_TAG = "PhantomProcessUtils"

    /**
     * If feature flag set to false, then will disable trimming of phantom process and processes using
     * excessive CPU. Flag is available on Pixel Android 12L beta 3 and Android 13.
     */
    const val FEATURE_FLAG_SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS = "settings_enable_monitor_phantom_procs"

    /**
     * Maximum number of allowed phantom processes. It is also used as the label for the currently
     * enforced ActivityManagerConstants MAX_PHANTOM_PROCESSES value in the `dumpsys activity settings` output.
     */
    const val KEY_MAX_PHANTOM_PROCESSES = "max_phantom_processes"

    /**
     * Whether or not syncs (bulk set operations) for DeviceConfig are disabled currently.
     */
    const val SETTINGS_GLOBAL_DEVICE_CONFIG_SYNC_DISABLED = "device_config_sync_disabled"

    /**
     * Get [FEATURE_FLAG_SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS] feature flag value.
     *
     * @param context The [Context] for operations.
     * @return Returns [FeatureFlagUtils.FeatureFlagValue].
     */
    @JvmStatic
    fun getFeatureFlagMonitorPhantomProcsValueString(context: Context): FeatureFlagUtils.FeatureFlagValue {
        return FeatureFlagUtils.getFeatureFlagValueString(context, FEATURE_FLAG_SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS)
    }

    /**
     * Get currently enforced ActivityManagerConstants MAX_PHANTOM_PROCESSES value, defaults to 32.
     *
     * @param context The [Context] for operations.
     * @return Returns [Integer].
     */
    @JvmStatic
    fun getActivityManagerMaxPhantomProcesses(context: Context): Int? {
        if (!PermissionUtils.checkPermissions(context, arrayOf(Manifest.permission.DUMP, Manifest.permission.PACKAGE_USAGE_STATS))) {
            return null
        }

        // Dumpsys logs the currently enforced MAX_PHANTOM_PROCESSES value and not the device config setting.
        val script = "/system/bin/dumpsys activity settings | /system/bin/grep -iE '^[\t ]+$KEY_MAX_PHANTOM_PROCESSES=[0-9]+\$' | /system/bin/cut -d = -f2"
        val executionCommand = ExecutionCommand(
            -1, "/system/bin/sh", null,
            "$script\n", "/", ExecutionCommand.Runner.APP_SHELL.getName(), true
        )
        executionCommand.commandLabel = " ActivityManager $KEY_MAX_PHANTOM_PROCESSES Command"
        executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF
        val appShell = AppShell.execute(context, executionCommand, null, AndroidShellEnvironment(), null, true)
        val stderrSet = executionCommand.resultData.stderr.toString().isNotEmpty()
        if (appShell == null || !executionCommand.isSuccessful() || executionCommand.resultData.exitCode != 0 || stderrSet) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString())
            return null
        }

        return try {
            executionCommand.resultData.stdout.toString().trim().toInt()
        } catch (e: NumberFormatException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The ${executionCommand.commandLabel} did not return a valid integer", e)
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString())
            null
        }
    }

    /**
     * Get [SETTINGS_GLOBAL_DEVICE_CONFIG_SYNC_DISABLED] settings value.
     *
     * @param context The [Context] for operations.
     * @return Returns [Integer].
     */
    @JvmStatic
    fun getSettingsGlobalDeviceConfigSyncDisabled(context: Context): Int? {
        return SettingsProviderUtils.getSettingsValue(
            context, SettingsProviderUtils.SettingNamespace.GLOBAL,
            SettingsProviderUtils.SettingType.INT, SETTINGS_GLOBAL_DEVICE_CONFIG_SYNC_DISABLED, null
        ) as? Int
    }

    /**
     * Check if running on Android 12 or higher where phantom process killing is a concern.
     *
     * @return Returns true if Android 12+.
     */
    @JvmStatic
    fun isPhantomProcessKillingRelevant(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Get a user-friendly message about phantom process issues and workarounds.
     *
     * @return Returns information message about phantom processes.
     */
    @JvmStatic
    fun getPhantomProcessInfoMessage(): String {
        if (!isPhantomProcessKillingRelevant()) {
            return "Phantom process killing is not relevant on this Android version."
        }

        return """Android 12+ may kill Termux processes after 32 total phantom processes across all apps.

Workarounds:
1. Use ADB to disable phantom process killing:
   adb shell "settings put global settings_enable_monitor_phantom_procs false"

2. Use ADB to increase max phantom processes:
   adb shell "device_config put activity_manager max_phantom_processes 2147483647"

3. Disable battery optimization for Termux in Android settings.

Note: Some workarounds require root or may reset after reboot.
See: https://github.com/termux/termux-app/issues/2366"""
    }
}
