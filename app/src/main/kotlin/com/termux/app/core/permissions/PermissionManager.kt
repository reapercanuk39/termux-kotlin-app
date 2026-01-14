package com.termux.app.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.termux.app.core.api.PermissionError
import com.termux.app.core.api.PermissionEvent
import com.termux.app.core.api.PermissionType
import com.termux.app.core.api.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Unified permission manager for handling all Termux permission requests.
 * Uses modern Activity Result API with coroutine support.
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val _permissionEvents = MutableSharedFlow<PermissionEvent>(extraBufferCapacity = 10)
    
    /**
     * Flow of permission events for observing permission changes.
     */
    val permissionEvents: Flow<PermissionEvent> = _permissionEvents.asSharedFlow()
    
    /**
     * Check if a permission is granted.
     */
    fun isGranted(permission: PermissionType): Boolean = when (permission) {
        PermissionType.STORAGE -> checkStoragePermission()
        PermissionType.NOTIFICATIONS -> checkNotificationPermission()
        PermissionType.BATTERY_OPTIMIZATION -> checkBatteryOptimization()
        PermissionType.DISPLAY_OVERLAY -> checkOverlayPermission()
        PermissionType.EXTERNAL_STORAGE_MANAGE -> checkManageStoragePermission()
    }
    
    /**
     * Check all required permissions.
     */
    fun checkAllPermissions(): Map<PermissionType, Boolean> = PermissionType.entries.associateWith { isGranted(it) }
    
    /**
     * Get list of missing permissions.
     */
    fun getMissingPermissions(): List<PermissionType> = PermissionType.entries.filter { !isGranted(it) }
    
    // Storage permission check
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check MANAGE_EXTERNAL_STORAGE or scoped storage
            Environment.isExternalStorageManager() ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Notification permission check (Android 13+)
    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }
    }
    
    // Battery optimization exemption check
    private fun checkBatteryOptimization(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    
    // Overlay permission check
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    // MANAGE_EXTERNAL_STORAGE permission check (Android 11+)
    private fun checkManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
    
    /**
     * Create a permission requester bound to an activity's lifecycle.
     * Must be called before the activity is started (e.g., in onCreate before super.onCreate).
     */
    fun createRequester(activity: AppCompatActivity): PermissionRequester {
        return PermissionRequester(activity, this)
    }
    
    internal fun emitEvent(event: PermissionEvent) {
        _permissionEvents.tryEmit(event)
    }
}

/**
 * Activity-scoped permission requester.
 * Handles permission requests using the Activity Result API.
 */
class PermissionRequester(
    private val activity: AppCompatActivity,
    private val manager: PermissionManager
) : DefaultLifecycleObserver {
    
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var multiplePermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var settingsLauncher: ActivityResultLauncher<Intent>? = null
    
    private var pendingPermission: PermissionType? = null
    private var resultChannel: Channel<Result<Unit, PermissionError>>? = null
    
    init {
        activity.lifecycle.addObserver(this)
        registerLaunchers()
    }
    
    private fun registerLaunchers() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            handlePermissionResult(granted)
        }
        
        multiplePermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            handlePermissionResult(allGranted)
        }
        
        settingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Check permission again after returning from settings
            val permission = pendingPermission ?: return@registerForActivityResult
            val granted = manager.isGranted(permission)
            handlePermissionResult(granted)
        }
    }
    
    private fun handlePermissionResult(granted: Boolean) {
        val permission = pendingPermission ?: return
        val channel = resultChannel ?: return
        
        if (granted) {
            manager.emitEvent(PermissionEvent.Granted(permission))
            channel.trySend(Result.success(Unit))
        } else {
            val isPermanent = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                getManifestPermission(permission) ?: ""
            )
            manager.emitEvent(PermissionEvent.Denied(permission, isPermanent))
            
            val error = if (isPermanent) {
                PermissionError.DeniedPermanently(permission.name)
            } else {
                PermissionError.Denied(permission.name)
            }
            channel.trySend(Result.error(error))
        }
        
        pendingPermission = null
        resultChannel = null
    }
    
    /**
     * Request a permission. Returns a Result indicating success or the specific error.
     */
    suspend fun request(permission: PermissionType): Result<Unit, PermissionError> {
        // Check if already granted
        if (manager.isGranted(permission)) {
            return Result.success(Unit)
        }
        
        return suspendCancellableCoroutine { continuation ->
            val channel = Channel<Result<Unit, PermissionError>>(1)
            resultChannel = channel
            pendingPermission = permission
            
            manager.emitEvent(PermissionEvent.Requested(permission))
            
            when (permission) {
                PermissionType.STORAGE -> requestStoragePermission()
                PermissionType.NOTIFICATIONS -> requestNotificationPermission()
                PermissionType.BATTERY_OPTIMIZATION -> requestBatteryOptimization()
                PermissionType.DISPLAY_OVERLAY -> requestOverlayPermission()
                PermissionType.EXTERNAL_STORAGE_MANAGE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        requestManageStoragePermission()
                    } else {
                        // Not required on older versions
                        handlePermissionResult(true)
                    }
                }
            }
            
            continuation.invokeOnCancellation {
                pendingPermission = null
                resultChannel = null
            }
            
            // This is a simplified approach - in production you'd use a proper coroutine-based callback
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    val result = channel.tryReceive().getOrNull()
                    if (result != null) {
                        activity.lifecycle.removeObserver(this)
                        continuation.resume(result)
                    }
                }
            })
        }
    }
    
    /**
     * Request multiple permissions at once.
     */
    suspend fun requestMultiple(permissions: List<PermissionType>): Map<PermissionType, Result<Unit, PermissionError>> {
        return permissions.associateWith { request(it) }
    }
    
    /**
     * Open app settings for the user to manually grant permissions.
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ needs MANAGE_EXTERNAL_STORAGE via settings
            requestManageStoragePermission()
        } else {
            permissionLauncher?.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Not required on older versions, complete immediately
            handlePermissionResult(true)
        }
    }
    
    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            settingsLauncher?.launch(intent)
        } else {
            handlePermissionResult(true)
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            settingsLauncher?.launch(intent)
        } else {
            handlePermissionResult(true)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestManageStoragePermission() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        settingsLauncher?.launch(intent)
    }
    
    private fun getManifestPermission(permission: PermissionType): String? = when (permission) {
        PermissionType.STORAGE -> Manifest.permission.WRITE_EXTERNAL_STORAGE
        PermissionType.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null
        PermissionType.BATTERY_OPTIMIZATION -> null // Handled via settings
        PermissionType.DISPLAY_OVERLAY -> null // Handled via settings
        PermissionType.EXTERNAL_STORAGE_MANAGE -> null // Handled via settings
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        permissionLauncher = null
        multiplePermissionLauncher = null
        settingsLauncher = null
        pendingPermission = null
        resultChannel = null
    }
}
