package com.termux.shared.android

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import com.termux.shared.R
import com.termux.shared.data.DataUtils
import com.termux.shared.interact.MessageDialogUtils
import com.termux.shared.logger.Logger
import com.termux.shared.reflection.ReflectionUtils
import java.security.MessageDigest

object PackageUtils {

    private const val LOG_TAG = "PackageUtils"

    /**
     * Get the [Context] for the package name with [Context.CONTEXT_RESTRICTED] flags.
     *
     * @param context The [Context] to use to get the [Context] of the [packageName].
     * @param packageName The package name whose [Context] to get.
     * @return Returns the [Context]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getContextForPackage(context: Context, packageName: String?): Context? {
        return getContextForPackage(context, packageName, Context.CONTEXT_RESTRICTED)
    }

    /**
     * Get the [Context] for the package name.
     *
     * @param context The [Context] to use to get the [Context] of the [packageName].
     * @param packageName The package name whose [Context] to get.
     * @param flags The flags for [Context] type.
     * @return Returns the [Context]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getContextForPackage(context: Context, packageName: String?, flags: Int): Context? {
        return try {
            context.createPackageContext(packageName, flags)
        } catch (e: Exception) {
            Logger.logVerbose(LOG_TAG, "Failed to get \"$packageName\" package context with flags $flags: ${e.message}")
            null
        }
    }

    /**
     * Get the [Context] for a package name.
     *
     * @param context The [Context] to use to get the [Context] of the [packageName].
     * @param packageName The package name whose [Context] to get.
     * @param exitAppOnError If `true` and failed to get package context, then a dialog will
     *                       be shown which when dismissed will exit the app.
     * @param helpUrl The help user to add to [R.string.error_get_package_context_failed_help_url_message].
     * @return Returns the [Context]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getContextForPackageOrExitApp(
        context: Context,
        packageName: String?,
        exitAppOnError: Boolean,
        helpUrl: String?
    ): Context? {
        val packageContext = getContextForPackage(context, packageName)

        if (packageContext == null && exitAppOnError) {
            var errorMessage = context.getString(R.string.error_get_package_context_failed_message, packageName)
            if (!DataUtils.isNullOrEmpty(helpUrl)) {
                errorMessage += "\n" + context.getString(R.string.error_get_package_context_failed_help_url_message, helpUrl)
            }
            Logger.logError(LOG_TAG, errorMessage)
            MessageDialogUtils.exitAppWithErrorMessage(
                context,
                context.getString(R.string.error_get_package_context_failed_title),
                errorMessage
            )
        }

        return packageContext
    }

    /**
     * Get the [PackageInfo] for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the [PackageInfo]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getPackageInfoForPackage(context: Context): PackageInfo? {
        return getPackageInfoForPackage(context, context.packageName)
    }

    /**
     * Get the [PackageInfo] for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @param flags The flags to pass to [PackageManager.getPackageInfo].
     * @return Returns the [PackageInfo]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getPackageInfoForPackage(context: Context, flags: Int): PackageInfo? {
        return getPackageInfoForPackage(context, context.packageName, flags)
    }

    /**
     * Get the [PackageInfo] for the package associated with the [packageName].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the [PackageInfo]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getPackageInfoForPackage(context: Context, packageName: String): PackageInfo? {
        return getPackageInfoForPackage(context, packageName, 0)
    }

    /**
     * Get the [PackageInfo] for the package associated with the [packageName].
     *
     * Also check [isAppInstalled] if targeting sdk `30` (android `11`) since
     * [PackageManager.NameNotFoundException] may be thrown.
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @param flags The flags to pass to [PackageManager.getPackageInfo].
     * @return Returns the [PackageInfo]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getPackageInfoForPackage(context: Context, packageName: String, flags: Int): PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(packageName, flags)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the [ApplicationInfo] for the [packageName].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the [ApplicationInfo]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getApplicationInfoForPackage(context: Context, packageName: String): ApplicationInfo? {
        return getApplicationInfoForPackage(context, packageName, 0)
    }

    /**
     * Get the [ApplicationInfo] for the [packageName].
     *
     * Also check [isAppInstalled] if targeting sdk `30` (android `11`) since
     * [PackageManager.NameNotFoundException] may be thrown.
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @param flags The flags to pass to [PackageManager.getApplicationInfo].
     * @return Returns the [ApplicationInfo]. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getApplicationInfoForPackage(context: Context, packageName: String, flags: Int): ApplicationInfo? {
        return try {
            context.packageManager.getApplicationInfo(packageName, flags)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the `privateFlags` field of the [ApplicationInfo] class.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the private flags or `null` if an exception was raised.
     */
    @JvmStatic
    fun getApplicationInfoPrivateFlagsForPackage(applicationInfo: ApplicationInfo): Int? {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            ReflectionUtils.invokeField(ApplicationInfo::class.java, "privateFlags", applicationInfo).value as? Int
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get privateFlags field value for ApplicationInfo class", e)
            null
        }
    }

    /**
     * Get the `seInfo` field of the [ApplicationInfo] class.
     *
     * String retrieved from the seinfo tag found in selinux policy. This value can be set through
     * the mac_permissions.xml policy construct. This value is used for setting an SELinux security
     * context on the process as well as its data directory.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the selinux info or `null` if an exception was raised.
     */
    @JvmStatic
    fun getApplicationInfoSeInfoForPackage(applicationInfo: ApplicationInfo): String? {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            val fieldName = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) "seinfo" else "seInfo"
            ReflectionUtils.invokeField(ApplicationInfo::class.java, fieldName, applicationInfo).value as? String
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get seInfo field value for ApplicationInfo class", e)
            null
        }
    }

    /**
     * Get the `seInfoUser` field of the [ApplicationInfo] class.
     *
     * Also check [getApplicationInfoSeInfoForPackage].
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the selinux info user or `null` if an exception was raised.
     */
    @JvmStatic
    fun getApplicationInfoSeInfoUserForPackage(applicationInfo: ApplicationInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            ReflectionUtils.invokeField(ApplicationInfo::class.java, "seInfoUser", applicationInfo).value as? String
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get seInfoUser field value for ApplicationInfo class", e)
            null
        }
    }

    /**
     * Get a static int field value from the [ApplicationInfo] class.
     *
     * @param fieldName The name of the field to get.
     * @return Returns the field value or `null` if an exception was raised.
     */
    @JvmStatic
    fun getApplicationInfoStaticIntFieldValue(fieldName: String): Int? {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            ReflectionUtils.invokeField(ApplicationInfo::class.java, fieldName, null).value as? Int
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"$fieldName\" field value for ApplicationInfo class", e)
            null
        }
    }

    /**
     * Check if the app associated with the [applicationInfo] has a specific flag set.
     *
     * @param flagToCheckName The name of the field for the flag to check.
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns `true` if app has flag is set, otherwise `false`. This will be
     * `null` if an exception is raised.
     */
    @JvmStatic
    fun isApplicationInfoPrivateFlagSetForPackage(flagToCheckName: String, applicationInfo: ApplicationInfo): Boolean? {
        val privateFlags = getApplicationInfoPrivateFlagsForPackage(applicationInfo) ?: return null
        val flagToCheck = getApplicationInfoStaticIntFieldValue(flagToCheckName) ?: return null
        return (0 != (privateFlags and flagToCheck))
    }

    /**
     * Get the app name for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the `android:name` attribute.
     */
    @JvmStatic
    fun getAppNameForPackage(context: Context): String {
        return getAppNameForPackage(context, context.applicationInfo)
    }

    /**
     * Get the app name for the package associated with the [applicationInfo].
     *
     * @param context The [Context] for operations.
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the `android:name` attribute.
     */
    @JvmStatic
    fun getAppNameForPackage(context: Context, applicationInfo: ApplicationInfo): String {
        return applicationInfo.loadLabel(context.packageManager).toString()
    }

    /**
     * Get the package name for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the package name.
     */
    @JvmStatic
    fun getPackageNameForPackage(context: Context): String {
        return getPackageNameForPackage(context.applicationInfo)
    }

    /**
     * Get the package name for the package associated with the [applicationInfo].
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the package name.
     */
    @JvmStatic
    fun getPackageNameForPackage(applicationInfo: ApplicationInfo): String {
        return applicationInfo.packageName
    }

    /**
     * Get the uid for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the uid.
     */
    @JvmStatic
    fun getUidForPackage(context: Context): Int {
        return getUidForPackage(context.applicationInfo)
    }

    /**
     * Get the uid for the package associated with the [applicationInfo].
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the uid.
     */
    @JvmStatic
    fun getUidForPackage(applicationInfo: ApplicationInfo): Int {
        return applicationInfo.uid
    }

    /**
     * Get the `targetSdkVersion` for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the `targetSdkVersion`.
     */
    @JvmStatic
    fun getTargetSDKForPackage(context: Context): Int {
        return getTargetSDKForPackage(context.applicationInfo)
    }

    /**
     * Get the `targetSdkVersion` for the package associated with the [applicationInfo].
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the `targetSdkVersion`.
     */
    @JvmStatic
    fun getTargetSDKForPackage(applicationInfo: ApplicationInfo): Int {
        return applicationInfo.targetSdkVersion
    }

    /**
     * Get the base apk path for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the base apk path.
     */
    @JvmStatic
    fun getBaseAPKPathForPackage(context: Context): String {
        return getBaseAPKPathForPackage(context.applicationInfo)
    }

    /**
     * Get the base apk path for the package associated with the [applicationInfo].
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the base apk path.
     */
    @JvmStatic
    fun getBaseAPKPathForPackage(applicationInfo: ApplicationInfo): String {
        return applicationInfo.publicSourceDir
    }

    /**
     * Check if the app associated with the [context] has [ApplicationInfo.FLAG_DEBUGGABLE] set.
     *
     * @param context The [Context] for the package.
     * @return Returns `true` if app is debuggable, otherwise `false`.
     */
    @JvmStatic
    fun isAppForPackageADebuggableBuild(context: Context): Boolean {
        return isAppForPackageADebuggableBuild(context.applicationInfo)
    }

    /**
     * Check if the app associated with the [applicationInfo] has [ApplicationInfo.FLAG_DEBUGGABLE] set.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns `true` if app is debuggable, otherwise `false`.
     */
    @JvmStatic
    fun isAppForPackageADebuggableBuild(applicationInfo: ApplicationInfo): Boolean {
        return (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE))
    }

    /**
     * Check if the app associated with the [context] has [ApplicationInfo.FLAG_EXTERNAL_STORAGE] set.
     *
     * @param context The [Context] for the package.
     * @return Returns `true` if app is installed on external storage, otherwise `false`.
     */
    @JvmStatic
    fun isAppInstalledOnExternalStorage(context: Context): Boolean {
        return isAppInstalledOnExternalStorage(context.applicationInfo)
    }

    /**
     * Check if the app associated with the [applicationInfo] has [ApplicationInfo.FLAG_EXTERNAL_STORAGE] set.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns `true` if app is installed on external storage, otherwise `false`.
     */
    @JvmStatic
    fun isAppInstalledOnExternalStorage(applicationInfo: ApplicationInfo): Boolean {
        return (0 != (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE))
    }

    /**
     * Check if the app associated with the [context] has
     * ApplicationInfo.PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE (requestLegacyExternalStorage)
     * set to `true` in app manifest.
     *
     * @param context The [Context] for the package.
     * @return Returns `true` if app has requested legacy external storage, otherwise
     * `false`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun hasRequestedLegacyExternalStorage(context: Context): Boolean? {
        return hasRequestedLegacyExternalStorage(context.applicationInfo)
    }

    /**
     * Check if the app associated with the [applicationInfo] has
     * ApplicationInfo.PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE (requestLegacyExternalStorage)
     * set to `true` in app manifest.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns `true` if app has requested legacy external storage, otherwise
     * `false`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun hasRequestedLegacyExternalStorage(applicationInfo: ApplicationInfo): Boolean? {
        return isApplicationInfoPrivateFlagSetForPackage("PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE", applicationInfo)
    }

    /**
     * Get the `versionCode` for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the `versionCode`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getVersionCodeForPackage(context: Context): Int? {
        return getVersionCodeForPackage(context, context.packageName)
    }

    /**
     * Get the `versionCode` for the [packageName].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the `versionCode`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getVersionCodeForPackage(context: Context, packageName: String): Int? {
        return getVersionCodeForPackage(getPackageInfoForPackage(context, packageName))
    }

    /**
     * Get the `versionCode` for the [packageInfo].
     *
     * @param packageInfo The [PackageInfo] for the package.
     * @return Returns the `versionCode`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getVersionCodeForPackage(packageInfo: PackageInfo?): Int? {
        return packageInfo?.versionCode
    }

    /**
     * Get the `versionName` for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the `versionName`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getVersionNameForPackage(context: Context): String? {
        return getVersionNameForPackage(context, context.packageName)
    }

    /**
     * Get the `versionName` for the [packageName].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the `versionName`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getVersionNameForPackage(context: Context, packageName: String): String? {
        return getVersionNameForPackage(getPackageInfoForPackage(context, packageName))
    }

    /**
     * Get the `versionName` for the [packageInfo].
     *
     * @param packageInfo The [PackageInfo] for the package.
     * @return Returns the `versionName`. This will be `null` if [packageInfo] is `null`.
     */
    @JvmStatic
    fun getVersionNameForPackage(packageInfo: PackageInfo?): String? {
        return packageInfo?.versionName
    }

    /**
     * Get the `SHA-256 digest` of signing certificate for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the `SHA-256 digest`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    fun getSigningCertificateSHA256DigestForPackage(context: Context): String? {
        return getSigningCertificateSHA256DigestForPackage(context, context.packageName)
    }

    /**
     * Get the `SHA-256 digest` of signing certificate for the [packageName].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the `SHA-256 digest`. This will be `null` if an exception is raised.
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun getSigningCertificateSHA256DigestForPackage(context: Context, packageName: String): String? {
        return try {
            val packageInfo = getPackageInfoForPackage(context, packageName, PackageManager.GET_SIGNATURES)
                ?: return null
            val signatures = packageInfo.signatures ?: return null
            DataUtils.bytesToHex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the serial number for the user for the package associated with the [context].
     *
     * @param context The [Context] for the package.
     * @return Returns the serial number. This will be `null` if failed to get it.
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun getUserIdForPackage(context: Context): Long? {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return null
        return userManager.getSerialNumberForUser(UserHandle.getUserHandleForUid(getUidForPackage(context)))
    }

    /**
     * Check if the current user is the primary user. This is done by checking if the serial
     * number for the current user equals 0.
     *
     * @param context The [Context] for operations.
     * @return Returns `true` if the current user is the primary user, otherwise `false`.
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun isCurrentUserThePrimaryUser(context: Context): Boolean {
        val userId = getUserIdForPackage(context)
        return userId != null && userId == 0L
    }

    /**
     * Get the profile owner package name for the current user.
     *
     * @param context The [Context] for operations.
     * @return Returns the profile owner package name. This will be `null` if failed to get it
     * or no profile owner for the current user.
     */
    @JvmStatic
    fun getProfileOwnerPackageNameForUser(context: Context): String? {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return null
        val activeAdmins = devicePolicyManager.activeAdmins ?: return null
        for (admin in activeAdmins) {
            val packageName = admin.packageName
            if (devicePolicyManager.isProfileOwnerApp(packageName)) {
                return packageName
            }
        }
        return null
    }

    /**
     * Get the process id of the main app process of a package. This will work for sharedUserId.
     * Note that some apps have multiple processes for the app like with `android:process=":background"`
     * attribute in AndroidManifest.xml.
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the process.
     * @return Returns the process if found and running, otherwise `null`.
     */
    @JvmStatic
    fun getPackagePID(context: Context, packageName: String): String? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null
        val processInfos = activityManager.runningAppProcesses ?: return null
        for (processInfo in processInfos) {
            if (processInfo.processName == packageName) {
                return processInfo.pid.toString()
            }
        }
        return null
    }

    /**
     * Check if app is installed and enabled. This can be used by external apps that don't
     * share `sharedUserId` with the an app.
     *
     * If your third-party app is targeting sdk `30` (android `11`), then it needs to add package
     * name to the `queries` element or request `QUERY_ALL_PACKAGES` permission in its
     * `AndroidManifest.xml`. Otherwise it will get `PackageSetting{...... package_name/......} BLOCKED`
     * errors in `logcat` and [PackageManager.NameNotFoundException] may be thrown.
     *
     * @param context The context for operations.
     * @param appName The name of the app.
     * @param packageName The package name of the package.
     * @return Returns `errmsg` if [packageName] is not installed or disabled, otherwise `null`.
     */
    @JvmStatic
    fun isAppInstalled(context: Context, appName: String?, packageName: String?): String? {
        val applicationInfo = getApplicationInfoForPackage(context, packageName!!)
        val isAppEnabled = applicationInfo != null && applicationInfo.enabled

        // If app is not installed or is disabled
        return if (!isAppEnabled) {
            context.getString(R.string.error_app_not_installed_or_disabled_warning, appName, packageName)
        } else {
            null
        }
    }

    /**
     * Wrapper for [setComponentState] with [alwaysShowToast] `true`.
     */
    @JvmStatic
    fun setComponentState(
        context: Context,
        packageName: String,
        className: String,
        newState: Boolean,
        toastString: String?,
        showErrorMessage: Boolean
    ): String? {
        return setComponentState(context, packageName, className, newState, toastString, showErrorMessage, true)
    }

    /**
     * Enable or disable a [ComponentName] with a call to
     * [PackageManager.setComponentEnabledSetting].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the component.
     * @param className The class name of the component.
     * @param newState If component should be enabled or disabled.
     * @param toastString If this is not `null` or empty, then a toast before setting state.
     * @param alwaysShowToast If toast should always be shown even if current state matches new state.
     * @param showErrorMessage If an error message toast should be shown.
     * @return Returns the errmsg if failed to set state, otherwise `null`.
     */
    @JvmStatic
    fun setComponentState(
        context: Context,
        packageName: String,
        className: String,
        newState: Boolean,
        toastString: String?,
        alwaysShowToast: Boolean,
        showErrorMessage: Boolean
    ): String? {
        var toast = toastString
        try {
            val packageManager = context.packageManager
            if (packageManager != null) {
                if (toast != null && alwaysShowToast) {
                    Logger.showToast(context, toast, true)
                    toast = null
                }

                val currentlyDisabled = isComponentDisabled(context, packageName, className, false)
                    ?: throw UnsupportedOperationException("Failed to find if component currently disabled")

                val setState: Boolean? = when {
                    newState && currentlyDisabled -> true
                    !newState && !currentlyDisabled -> false
                    else -> null
                }

                if (setState == null) return null

                if (toast != null) Logger.showToast(context, toast, true)
                val componentName = ComponentName(packageName, className)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    if (setState) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            return null
        } catch (e: Exception) {
            val errmsg = context.getString(
                if (newState) R.string.error_enable_component_failed else R.string.error_disable_component_failed,
                packageName, className
            ) + ": " + e.message
            if (showErrorMessage) {
                Logger.showToast(context, errmsg, true)
            }
            return errmsg
        }
    }

    /**
     * Check if state of a [ComponentName] is [PackageManager.COMPONENT_ENABLED_STATE_DISABLED]
     * with a call to [PackageManager.getComponentEnabledSetting].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the component.
     * @param className The class name of the component.
     * @param logErrorMessage If an error message should be logged.
     * @return Returns `true` if disabled, `false` if not and `null` if failed to get the state.
     */
    @JvmStatic
    fun isComponentDisabled(
        context: Context,
        packageName: String,
        className: String,
        logErrorMessage: Boolean
    ): Boolean? {
        try {
            val packageManager = context.packageManager
            if (packageManager != null) {
                val componentName = ComponentName(packageName, className)
                return packageManager.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        } catch (e: Exception) {
            if (logErrorMessage) {
                Logger.logStackTraceWithMessage(
                    LOG_TAG,
                    context.getString(R.string.error_get_component_state_failed, packageName, className),
                    e
                )
            }
        }
        return null
    }

    /**
     * Check if an Activity [ComponentName] can be called by calling
     * [PackageManager.queryIntentActivities].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the component.
     * @param className The class name of the component.
     * @param flags The flags to filter results.
     * @return Returns `true` if it exists, otherwise `false`.
     */
    @JvmStatic
    fun doesActivityComponentExist(
        context: Context,
        packageName: String,
        className: String,
        flags: Int
    ): Boolean {
        try {
            val packageManager = context.packageManager
            if (packageManager != null) {
                val intent = Intent()
                intent.setClassName(packageName, className)
                return packageManager.queryIntentActivities(intent, flags).size > 0
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }
}
