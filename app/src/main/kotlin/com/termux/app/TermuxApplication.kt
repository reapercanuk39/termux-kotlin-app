package com.termux.app

import android.app.Application
import android.content.Context
import com.termux.BuildConfig
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxBootstrap
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.am.TermuxAmSocketServer
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.theme.TermuxThemeUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TermuxApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val context = applicationContext

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this)

        // Set log config for the app
        setLogConfig(context)

        Logger.logDebug("Starting Application")

        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT)

        // Init app wide SharedProperties loaded from termux.properties
        val properties = TermuxAppSharedProperties.init(context)

        // Init app wide shell manager
        val shellManager = TermuxShellManager.init(context)

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode())

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        var error: Error? = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true)
        val isTermuxFilesDirectoryAccessible = error == null
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible")

            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true)
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n$error")
                return
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context)
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n$error")
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this)

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this)
            
            // Start the autonomous agent service in the background
            startAgentService(context)
        }
    }
    
    private fun startAgentService(context: Context) {
        try {
            // Only start if bootstrap is complete
            val prefixDir = java.io.File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
            if (prefixDir.exists() && java.io.File(prefixDir, "bin/sh").exists()) {
                Logger.logInfo(LOG_TAG, "Starting autonomous agent service")
                AgentService.startAgentService(context)
            } else {
                Logger.logDebug(LOG_TAG, "Bootstrap not complete, agent service will start after bootstrap")
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to start agent service: ${e.message}")
        }
    }

    companion object {
        private const val LOG_TAG = "TermuxApplication"

        @JvmStatic
        fun setLogConfig(context: Context) {
            Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME)

            // Load the log level from shared preferences and set it to the Logger.CURRENT_LOG_LEVEL
            val preferences = TermuxAppSharedPreferences.build(context) ?: return
            preferences.setLogLevel(null, preferences.getLogLevel())
        }
    }
}
