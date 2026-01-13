package com.termux.shared.termux.shell.command.environment

import android.content.Context
import android.os.Build
import com.termux.shared.android.PackageUtils
import com.termux.shared.android.SELinuxUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.termux.TermuxBootstrap
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.shell.am.TermuxAmSocketServer

/**
 * Environment for [TermuxConstants.TERMUX_PACKAGE_NAME] app.
 */
object TermuxAppShellEnvironment {

    /** Termux app environment variables. */
    @JvmStatic
    var termuxAppEnvironment: HashMap<String, String>? = null
        private set

    /** Environment variable for the Termux app version. */
    @JvmField val ENV_TERMUX_VERSION = TermuxConstants.TERMUX_ENV_PREFIX_ROOT + "_VERSION"

    /** Environment variable prefix for the Termux app. */
    @JvmField val TERMUX_APP_ENV_PREFIX = TermuxConstants.TERMUX_ENV_PREFIX_ROOT + "_APP__"

    /** Environment variable for the Termux app version name. */
    @JvmField val ENV_TERMUX_APP__VERSION_NAME = TERMUX_APP_ENV_PREFIX + "VERSION_NAME"
    /** Environment variable for the Termux app version code. */
    @JvmField val ENV_TERMUX_APP__VERSION_CODE = TERMUX_APP_ENV_PREFIX + "VERSION_CODE"
    /** Environment variable for the Termux app package name. */
    @JvmField val ENV_TERMUX_APP__PACKAGE_NAME = TERMUX_APP_ENV_PREFIX + "PACKAGE_NAME"
    /** Environment variable for the Termux app process id. */
    @JvmField val ENV_TERMUX_APP__PID = TERMUX_APP_ENV_PREFIX + "PID"
    /** Environment variable for the Termux app uid. */
    @JvmField val ENV_TERMUX_APP__UID = TERMUX_APP_ENV_PREFIX + "UID"
    /** Environment variable for the Termux app targetSdkVersion. */
    @JvmField val ENV_TERMUX_APP__TARGET_SDK = TERMUX_APP_ENV_PREFIX + "TARGET_SDK"
    /** Environment variable for the Termux app is debuggable apk build. */
    @JvmField val ENV_TERMUX_APP__IS_DEBUGGABLE_BUILD = TERMUX_APP_ENV_PREFIX + "IS_DEBUGGABLE_BUILD"
    /** Environment variable for the Termux app [TermuxConstants] APK_RELEASE_*. */
    @JvmField val ENV_TERMUX_APP__APK_RELEASE = TERMUX_APP_ENV_PREFIX + "APK_RELEASE"
    /** Environment variable for the Termux app install path. */
    @JvmField val ENV_TERMUX_APP__APK_PATH = TERMUX_APP_ENV_PREFIX + "APK_PATH"
    /** Environment variable for the Termux app is installed on external/portable storage. */
    @JvmField val ENV_TERMUX_APP__IS_INSTALLED_ON_EXTERNAL_STORAGE = TERMUX_APP_ENV_PREFIX + "IS_INSTALLED_ON_EXTERNAL_STORAGE"

    /** Environment variable for the Termux app process selinux context. */
    @JvmField val ENV_TERMUX_APP__SE_PROCESS_CONTEXT = TERMUX_APP_ENV_PREFIX + "SE_PROCESS_CONTEXT"
    /** Environment variable for the Termux app data files selinux context. */
    @JvmField val ENV_TERMUX_APP__SE_FILE_CONTEXT = TERMUX_APP_ENV_PREFIX + "SE_FILE_CONTEXT"
    /** Environment variable for the Termux app seInfo tag. */
    @JvmField val ENV_TERMUX_APP__SE_INFO = TERMUX_APP_ENV_PREFIX + "SE_INFO"
    /** Environment variable for the Termux app user id. */
    @JvmField val ENV_TERMUX_APP__USER_ID = TERMUX_APP_ENV_PREFIX + "USER_ID"
    /** Environment variable for the Termux app profile owner. */
    @JvmField val ENV_TERMUX_APP__PROFILE_OWNER = TERMUX_APP_ENV_PREFIX + "PROFILE_OWNER"

    /** Environment variable for the Termux app [TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER]. */
    @JvmField val ENV_TERMUX_APP__PACKAGE_MANAGER = TERMUX_APP_ENV_PREFIX + "PACKAGE_MANAGER"
    /** Environment variable for the Termux app [TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT]. */
    @JvmField val ENV_TERMUX_APP__PACKAGE_VARIANT = TERMUX_APP_ENV_PREFIX + "PACKAGE_VARIANT"
    /** Environment variable for the Termux app files directory. */
    @JvmField val ENV_TERMUX_APP__FILES_DIR = TERMUX_APP_ENV_PREFIX + "FILES_DIR"

    /** Environment variable for the Termux app AM socket server enabled. */
    @JvmField val ENV_TERMUX_APP__AM_SOCKET_SERVER_ENABLED = TERMUX_APP_ENV_PREFIX + "AM_SOCKET_SERVER_ENABLED"

    /** Get shell environment for Termux app. */
    @JvmStatic
    fun getEnvironment(currentPackageContext: Context): HashMap<String, String>? {
        setTermuxAppEnvironment(currentPackageContext)
        return termuxAppEnvironment
    }

    /** Set Termux app environment variables in [termuxAppEnvironment]. */
    @JvmStatic
    @Synchronized
    fun setTermuxAppEnvironment(currentPackageContext: Context) {
        val isTermuxApp = TermuxConstants.TERMUX_PACKAGE_NAME == currentPackageContext.packageName

        // If current package context is of termux app and its environment is already set, then no need to set again
        if (termuxAppEnvironment != null && isTermuxApp) return

        termuxAppEnvironment = null

        val packageName = TermuxConstants.TERMUX_PACKAGE_NAME
        val packageInfo = PackageUtils.getPackageInfoForPackage(currentPackageContext, packageName) ?: return
        val applicationInfo = PackageUtils.getApplicationInfoForPackage(currentPackageContext, packageName)
        if (applicationInfo == null || !applicationInfo.enabled) return

        val environment = HashMap<String, String>()

        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_VERSION, PackageUtils.getVersionNameForPackage(packageInfo))
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__VERSION_NAME, PackageUtils.getVersionNameForPackage(packageInfo))
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__VERSION_CODE, PackageUtils.getVersionCodeForPackage(packageInfo).toString())

        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__PACKAGE_NAME, packageName)
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__PID, TermuxUtils.getTermuxAppPID(currentPackageContext))
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__UID, PackageUtils.getUidForPackage(applicationInfo).toString())
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__TARGET_SDK, PackageUtils.getTargetSDKForPackage(applicationInfo).toString())
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__IS_DEBUGGABLE_BUILD, PackageUtils.isAppForPackageADebuggableBuild(applicationInfo))
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__APK_PATH, PackageUtils.getBaseAPKPathForPackage(applicationInfo))
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__IS_INSTALLED_ON_EXTERNAL_STORAGE, PackageUtils.isAppInstalledOnExternalStorage(applicationInfo))

        putTermuxAPKSignature(currentPackageContext, environment)

        val termuxPackageContext = TermuxUtils.getTermuxPackageContext(currentPackageContext)
        if (termuxPackageContext != null) {
            TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER?.let {
                environment[ENV_TERMUX_APP__PACKAGE_MANAGER] = it.getName()
            }
            TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT?.let {
                environment[ENV_TERMUX_APP__PACKAGE_VARIANT] = it.getName()
            }

            ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__AM_SOCKET_SERVER_ENABLED,
                TermuxAmSocketServer.getTermuxAppAMSocketServerEnabled(currentPackageContext))

            val filesDirPath = currentPackageContext.filesDir.absolutePath
            ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__FILES_DIR, filesDirPath)

            ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__SE_PROCESS_CONTEXT, SELinuxUtils.getContext())
            ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__SE_FILE_CONTEXT, SELinuxUtils.getFileContext(filesDirPath))

            val seInfoUser = PackageUtils.getApplicationInfoSeInfoUserForPackage(applicationInfo)
            ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__SE_INFO, PackageUtils.getApplicationInfoSeInfoForPackage(applicationInfo) +
                (if (DataUtils.isNullOrEmpty(seInfoUser)) "" else seInfoUser))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__USER_ID, PackageUtils.getUserIdForPackage(currentPackageContext).toString())
            ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__PROFILE_OWNER, PackageUtils.getProfileOwnerPackageNameForUser(currentPackageContext))
        }

        termuxAppEnvironment = environment
    }

    /** Put [ENV_TERMUX_APP__APK_RELEASE] in [environment]. */
    @JvmStatic
    fun putTermuxAPKSignature(currentPackageContext: Context, environment: HashMap<String, String>) {
        val signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(
            currentPackageContext, TermuxConstants.TERMUX_PACKAGE_NAME
        )
        if (signingCertificateSHA256Digest != null) {
            ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__APK_RELEASE,
                TermuxUtils.getAPKRelease(signingCertificateSHA256Digest).replace(Regex("[^a-zA-Z]"), "_").uppercase())
        }
    }

    /** Update [ENV_TERMUX_APP__AM_SOCKET_SERVER_ENABLED] value in [environment]. */
    @JvmStatic
    @Synchronized
    fun updateTermuxAppAMSocketServerEnabled(currentPackageContext: Context) {
        val env = termuxAppEnvironment ?: return
        env.remove(ENV_TERMUX_APP__AM_SOCKET_SERVER_ENABLED)
        ShellEnvironmentUtils.putToEnvIfSet(env, ENV_TERMUX_APP__AM_SOCKET_SERVER_ENABLED,
            TermuxAmSocketServer.getTermuxAppAMSocketServerEnabled(currentPackageContext))
    }
}
