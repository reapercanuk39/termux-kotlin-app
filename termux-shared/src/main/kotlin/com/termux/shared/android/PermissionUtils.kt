package com.termux.shared.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.common.base.Joiner
import com.termux.kotlin.shared.R
import com.termux.shared.activity.ActivityUtils
import com.termux.shared.errors.Error
import com.termux.shared.errors.FunctionErrno
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger

object PermissionUtils {

    const val REQUEST_GRANT_STORAGE_PERMISSION = 1000
    const val REQUEST_DISABLE_BATTERY_OPTIMIZATIONS = 2000
    const val REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION = 2001
    const val REQUEST_POST_NOTIFICATIONS_PERMISSION = 2002

    private const val LOG_TAG = "PermissionUtils"

    /**
     * Check if app has notification permission (required for Android 13+).
     */
    @JvmStatic
    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for Android < 13
        }
    }

    /**
     * Request notification permission for Android 13+.
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    fun requestNotificationPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkNotificationPermission(activity)) {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS_PERMISSION
                )
                return true
            }
        }
        return false
    }

    /**
     * Check if app has been granted the required permission.
     */
    @JvmStatic
    fun checkPermission(context: Context, permission: String): Boolean {
        return checkPermissions(context, arrayOf(permission))
    }

    /**
     * Check if app has been granted the required permissions.
     */
    @JvmStatic
    fun checkPermissions(context: Context, permissions: Array<String>): Boolean {
        // checkSelfPermission may return true for permissions not even requested
        val permissionsNotRequested = getPermissionsNotRequested(context, permissions)
        if (permissionsNotRequested.isNotEmpty()) {
            Logger.logError(
                LOG_TAG,
                context.getString(
                    R.string.error_attempted_to_check_for_permissions_not_requested,
                    Joiner.on(", ").join(permissionsNotRequested)
                )
            )
            return false
        }

        for (permission in permissions) {
            val result = ContextCompat.checkSelfPermission(context, permission)
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        return true
    }

    /**
     * Request user to grant required permissions to the app.
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun requestPermission(context: Context, permission: String, requestCode: Int): Boolean {
        return requestPermissions(context, arrayOf(permission), requestCode)
    }

    /**
     * Request user to grant required permissions to the app.
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun requestPermissions(context: Context, permissions: Array<String>, requestCode: Int): Boolean {
        val permissionsNotRequested = getPermissionsNotRequested(context, permissions)
        if (permissionsNotRequested.isNotEmpty()) {
            Logger.logErrorAndShowToast(
                context, LOG_TAG,
                context.getString(
                    R.string.error_attempted_to_ask_for_permissions_not_requested,
                    Joiner.on(", ").join(permissionsNotRequested)
                )
            )
            return false
        }

        for (permission in permissions) {
            val result = ContextCompat.checkSelfPermission(context, permission)
            // If at least one permission not granted
            if (result != PackageManager.PERMISSION_GRANTED) {
                Logger.logInfo(LOG_TAG, "Requesting Permissions: ${permissions.contentToString()}")

                try {
                    when (context) {
                        is AppCompatActivity -> context.requestPermissions(permissions, requestCode)
                        is Activity -> context.requestPermissions(permissions, requestCode)
                        else -> {
                            Error.logErrorAndShowToast(
                                context, LOG_TAG,
                                FunctionErrno.ERRNO_PARAMETER_NOT_INSTANCE_OF.getError(
                                    "context", "requestPermissions", "Activity or AppCompatActivity"
                                )
                            )
                            return false
                        }
                    }
                } catch (e: Exception) {
                    val errmsg = context.getString(
                        R.string.error_failed_to_request_permissions,
                        requestCode,
                        permissions.contentToString()
                    )
                    Logger.logStackTraceWithMessage(LOG_TAG, errmsg, e)
                    Logger.showToast(context, "$errmsg\n${e.message}", true)
                    return false
                }

                break
            }
        }

        return true
    }

    /**
     * Check if app has requested the required permission in the manifest.
     */
    @JvmStatic
    fun isPermissionRequested(context: Context, permission: String): Boolean {
        return getPermissionsNotRequested(context, arrayOf(permission)).isEmpty()
    }

    /**
     * Check if app has requested the required permissions or not in the manifest.
     */
    @JvmStatic
    fun getPermissionsNotRequested(context: Context, permissions: Array<String>): List<String> {
        val permissionsNotRequested = permissions.toMutableList()

        val packageInfo = PackageUtils.getPackageInfoForPackage(context, PackageManager.GET_PERMISSIONS)
            ?: return permissionsNotRequested

        // If no permissions are requested, then nothing to check
        val requestedPermissions = packageInfo.requestedPermissions
        if (requestedPermissions == null || requestedPermissions.isEmpty())
            return permissionsNotRequested

        val requestedPermissionsList = requestedPermissions.toList()
        for (permission in permissions) {
            if (requestedPermissionsList.contains(permission)) {
                permissionsNotRequested.remove(permission)
            }
        }

        return permissionsNotRequested
    }

    /**
     * If path is under primary external storage directory and storage permission is missing,
     * then legacy or manage external storage permission will be requested from the user.
     */
    @JvmStatic
    @SuppressLint("SdCardPath")
    fun checkAndRequestLegacyOrManageExternalStoragePermissionIfPathOnPrimaryExternalStorage(
        context: Context,
        filePath: String?,
        requestCode: Int,
        showErrorMessage: Boolean
    ): Boolean {
        // If path is under primary external storage directory, then check for missing permissions.
        if (!FileUtils.isPathInDirPaths(
                filePath,
                listOf(Environment.getExternalStorageDirectory().absolutePath, "/sdcard"),
                true
            )
        )
            return true

        return checkAndRequestLegacyOrManageExternalStoragePermission(context, requestCode, showErrorMessage)
    }

    /**
     * Check if legacy or manage external storage permissions has been granted.
     */
    @JvmStatic
    fun checkAndRequestLegacyOrManageExternalStoragePermission(
        context: Context,
        requestCode: Int,
        showErrorMessage: Boolean
    ): Boolean {
        val requestLegacyStoragePermission = isLegacyExternalStoragePossible(context)
        val checkIfHasRequestedLegacyExternalStorage = checkIfHasRequestedLegacyExternalStorage(context)

        if (requestLegacyStoragePermission && checkIfHasRequestedLegacyExternalStorage) {
            // Check if requestLegacyExternalStorage is set to true in app manifest
            if (!hasRequestedLegacyExternalStorage(context, showErrorMessage))
                return false
        }

        if (checkStoragePermission(context, requestLegacyStoragePermission)) {
            return true
        }

        val errmsg = context.getString(R.string.msg_storage_permission_not_granted)
        Logger.logError(LOG_TAG, errmsg)
        if (showErrorMessage)
            Logger.showToast(context, errmsg, false)

        if (requestCode < 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return false

        if (requestLegacyStoragePermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestLegacyStorageExternalPermission(context, requestCode)
        } else {
            requestManageStorageExternalPermission(context, requestCode)
        }

        return false
    }

    /**
     * Check if app has been granted storage permission.
     */
    @JvmStatic
    fun checkStoragePermission(context: Context, checkLegacyStoragePermission: Boolean): Boolean {
        return if (checkLegacyStoragePermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            checkPermissions(
                context,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else {
            Environment.isExternalStorageManager()
        }
    }

    /**
     * Request user to grant READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE permissions.
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun requestLegacyStorageExternalPermission(context: Context, requestCode: Int): Boolean {
        Logger.logInfo(LOG_TAG, "Requesting legacy external storage permission")
        return requestPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE, requestCode)
    }

    /** Wrapper for [requestManageStorageExternalPermission]. */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.R)
    fun requestManageStorageExternalPermission(context: Context): Error? {
        return requestManageStorageExternalPermission(context, -1)
    }

    /**
     * Request user to grant MANAGE_EXTERNAL_STORAGE permission to the app.
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.R)
    fun requestManageStorageExternalPermission(context: Context, requestCode: Int): Error? {
        Logger.logInfo(LOG_TAG, "Requesting manage external storage permission")

        var intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.addCategory("android.intent.category.DEFAULT")
        intent.data = Uri.parse("package:${context.packageName}")

        // Flag must not be passed for activity contexts
        if (context !is Activity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val error: Error? = if (requestCode >= 0)
            ActivityUtils.startActivityForResult(context, requestCode, intent, true, false)
        else
            ActivityUtils.startActivity(context, intent, true, false)

        // Use fallback if matching Activity did not exist
        if (error != null) {
            intent = Intent()
            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            return if (requestCode >= 0)
                ActivityUtils.startActivityForResult(context, requestCode, intent)
            else
                ActivityUtils.startActivity(context, intent)
        }

        return null
    }

    /**
     * If app is targeting targetSdkVersion 30 (android 11) and running on sdk 30 or higher,
     * then requestLegacyExternalStorage attribute is ignored.
     */
    @JvmStatic
    fun isLegacyExternalStoragePossible(context: Context): Boolean {
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            PackageUtils.getTargetSDKForPackage(context) >= Build.VERSION_CODES.R)
    }

    /**
     * Return whether it should be checked if app has set requestLegacyExternalStorage attribute to true.
     */
    @JvmStatic
    fun checkIfHasRequestedLegacyExternalStorage(context: Context): Boolean {
        val targetSdkVersion = PackageUtils.getTargetSDKForPackage(context)

        return when {
            targetSdkVersion >= Build.VERSION_CODES.R -> Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
            targetSdkVersion == Build.VERSION_CODES.Q -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            else -> false
        }
    }

    /**
     * Check if requestLegacyExternalStorage is set in manifest.
     */
    @JvmStatic
    fun hasRequestedLegacyExternalStorage(context: Context, showErrorMessage: Boolean): Boolean {
        val hasRequestedLegacyExternalStorage = PackageUtils.hasRequestedLegacyExternalStorage(context)
        if (hasRequestedLegacyExternalStorage != null && !hasRequestedLegacyExternalStorage) {
            val errmsg = context.getString(
                R.string.error_has_not_requested_legacy_external_storage,
                context.packageName,
                PackageUtils.getTargetSDKForPackage(context),
                Build.VERSION.SDK_INT
            )
            Logger.logError(LOG_TAG, errmsg)
            if (showErrorMessage)
                Logger.showToast(context, errmsg, true)
            return false
        }

        return true
    }

    /**
     * Check if SYSTEM_ALERT_WINDOW permission has been granted.
     */
    @JvmStatic
    fun checkDisplayOverOtherAppsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(context)
        else
            true
    }

    /** Wrapper for [requestDisplayOverOtherAppsPermission]. */
    @JvmStatic
    fun requestDisplayOverOtherAppsPermission(context: Context): Error? {
        return requestDisplayOverOtherAppsPermission(context, -1)
    }

    /**
     * Request user to grant SYSTEM_ALERT_WINDOW permission to the app.
     */
    @JvmStatic
    fun requestDisplayOverOtherAppsPermission(context: Context, requestCode: Int): Error? {
        Logger.logInfo(LOG_TAG, "Requesting display over apps permission")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null

        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = Uri.parse("package:${context.packageName}")

        // Flag must not be passed for activity contexts
        if (context !is Activity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return if (requestCode >= 0)
            ActivityUtils.startActivityForResult(context, requestCode, intent)
        else
            ActivityUtils.startActivity(context, intent)
    }

    /**
     * Check if running on sdk 29 or higher and SYSTEM_ALERT_WINDOW permission has been granted or not.
     */
    @JvmStatic
    fun validateDisplayOverOtherAppsPermissionForPostAndroid10(context: Context, logResults: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true

        return if (!checkDisplayOverOtherAppsPermission(context)) {
            if (logResults)
                Logger.logWarn(LOG_TAG, "${context.packageName} does not have Display over other apps (SYSTEM_ALERT_WINDOW) permission")
            false
        } else {
            if (logResults)
                Logger.logDebug(LOG_TAG, "${context.packageName} already has Display over other apps (SYSTEM_ALERT_WINDOW) permission")
            true
        }
    }

    /**
     * Check if REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission has been granted.
     */
    @JvmStatic
    fun checkIfBatteryOptimizationsDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /** Wrapper for [requestDisableBatteryOptimizations]. */
    @JvmStatic
    fun requestDisableBatteryOptimizations(context: Context): Error? {
        return requestDisableBatteryOptimizations(context, -1)
    }

    /**
     * Request user to grant REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission to the app.
     */
    @JvmStatic
    @SuppressLint("BatteryLife")
    fun requestDisableBatteryOptimizations(context: Context, requestCode: Int): Error? {
        Logger.logInfo(LOG_TAG, "Requesting to disable battery optimizations")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${context.packageName}")

        // Flag must not be passed for activity contexts
        if (context !is Activity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return if (requestCode >= 0)
            ActivityUtils.startActivityForResult(context, requestCode, intent)
        else
            ActivityUtils.startActivity(context, intent)
    }
}
