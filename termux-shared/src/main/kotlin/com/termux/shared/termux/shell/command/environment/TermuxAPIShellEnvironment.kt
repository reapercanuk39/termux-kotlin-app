package com.termux.shared.termux.shell.command.environment

import android.content.Context
import com.termux.shared.android.PackageUtils
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.termux.TermuxConstants

/**
 * Environment for [TermuxConstants.TERMUX_API_PACKAGE_NAME] app.
 * Note: Since v2.0.5, Termux:API is built-in to the main app.
 */
object TermuxAPIShellEnvironment {

    /** Environment variable prefix for the Termux:API app. */
    @JvmField val TERMUX_API_APP_ENV_PREFIX = TermuxConstants.TERMUX_ENV_PREFIX_ROOT + "_API_APP__"

    /** Environment variable for the Termux:API app version. */
    @JvmField val ENV_TERMUX_API_APP__VERSION_NAME = TERMUX_API_APP_ENV_PREFIX + "VERSION_NAME"

    /** Get shell environment for Termux:API app. */
    @JvmStatic
    fun getEnvironment(currentPackageContext: Context): HashMap<String, String>? {
        // Termux:API is built-in since v2.0.5 - use current app's package info
        // First check for external package, then fall back to main app
        var packageName = TermuxConstants.TERMUX_API_PACKAGE_NAME
        var packageInfo = PackageUtils.getPackageInfoForPackage(currentPackageContext, packageName)
        
        if (packageInfo == null) {
            // API is built-in, use main app's package info
            packageName = TermuxConstants.TERMUX_PACKAGE_NAME
            packageInfo = PackageUtils.getPackageInfoForPackage(currentPackageContext, packageName) ?: return null
        }

        val environment = HashMap<String, String>()
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_API_APP__VERSION_NAME, PackageUtils.getVersionNameForPackage(packageInfo))

        return environment
    }
}
