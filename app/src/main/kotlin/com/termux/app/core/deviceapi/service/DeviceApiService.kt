package com.termux.app.core.deviceapi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.termux.app.TermuxActivity
import com.termux.app.core.api.Result
import com.termux.app.core.deviceapi.DeviceApiError
import com.termux.app.core.deviceapi.actions.BatteryAction
import com.termux.app.core.deviceapi.models.BatteryInfo
import com.termux.app.core.deviceapi.models.DeviceApiAction
import com.termux.app.core.deviceapi.models.DeviceApiMessage
import com.termux.app.core.logging.TaggedLogger
import com.termux.app.core.logging.TermuxLogger
import com.termux.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Background service for Device API operations.
 * 
 * Provides:
 * - Execution of device API actions
 * - Streaming sensor/location data
 * - Event-based communication with terminal sessions
 * - Foreground service for long-running operations
 * 
 * Usage from terminal commands:
 * ```kotlin
 * val result = deviceApiService.executeAction(DeviceApiAction.BATTERY_STATUS)
 * ```
 */
@AndroidEntryPoint
class DeviceApiService : Service() {
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "termux_device_api"
        private const val NOTIFICATION_ID = 1337
        private const val ACTION_STOP_SERVICE = "com.termux.STOP_DEVICE_API_SERVICE"
        
        fun createIntent(context: Context): Intent {
            return Intent(context, DeviceApiService::class.java)
        }
        
        fun startService(context: Context) {
            val intent = createIntent(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            context.stopService(createIntent(context))
        }
    }
    
    @Inject lateinit var logger: TermuxLogger
    @Inject lateinit var batteryAction: BatteryAction
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    
    private val log: TaggedLogger by lazy { logger.forTag("DeviceApiService") }
    
    private val json = Json { 
        prettyPrint = true 
        encodeDefaults = true
    }
    
    // Event flow for communicating with terminal sessions
    private val _events = MutableSharedFlow<DeviceApiMessage>(extraBufferCapacity = 50)
    val events: SharedFlow<DeviceApiMessage> = _events.asSharedFlow()
    
    // Active streaming jobs
    private val activeStreams = mutableMapOf<String, Job>()
    
    // Service binder for local binding
    private val binder = DeviceApiBinder()
    
    inner class DeviceApiBinder : Binder() {
        fun getService(): DeviceApiService = this@DeviceApiService
    }
    
    override fun onCreate() {
        super.onCreate()
        log.i("DeviceApiService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                log.i("Stopping service via notification action")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        log.i("DeviceApiService started")
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        log.d("Service bound")
        return binder
    }
    
    override fun onDestroy() {
        log.i("DeviceApiService destroyed")
        cancelAllStreams()
        super.onDestroy()
    }
    
    // ========== API Execution ==========
    
    /**
     * Execute a device API action by name.
     * 
     * @param action The action to execute
     * @param params Optional parameters
     * @return Result containing response data or error
     */
    suspend fun executeAction(
        action: DeviceApiAction,
        params: Map<String, String> = emptyMap()
    ): Result<String, DeviceApiError> {
        val requestId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        log.d("Executing action", mapOf("action" to action.actionName, "requestId" to requestId))
        
        // Emit request event
        _events.tryEmit(DeviceApiMessage.ApiRequest(
            id = requestId,
            action = action.actionName,
            parameters = params
        ))
        
        return when (action) {
            DeviceApiAction.BATTERY_STATUS -> {
                executeBatteryAction(requestId, params, startTime)
            }
            else -> {
                val error = DeviceApiError.FeatureNotAvailable(action.actionName)
                emitError(requestId, action.actionName, error)
                Result.error(error)
            }
        }
    }
    
    private suspend fun executeBatteryAction(
        requestId: String,
        params: Map<String, String>,
        startTime: Long
    ): Result<String, DeviceApiError> {
        return when (val result = batteryAction.execute(params)) {
            is Result.Success -> {
                val data = json.encodeToString(result.data)
                val duration = System.currentTimeMillis() - startTime
                
                _events.tryEmit(DeviceApiMessage.ApiResponse(
                    id = UUID.randomUUID().toString(),
                    requestId = requestId,
                    action = DeviceApiAction.BATTERY_STATUS.actionName,
                    data = data,
                    executionTimeMs = duration
                ))
                
                Result.success(data)
            }
            is Result.Error -> {
                emitError(requestId, DeviceApiAction.BATTERY_STATUS.actionName, result.error)
                Result.error(result.error)
            }
            is Result.Loading -> {
                Result.error(DeviceApiError.SystemException(
                    operation = "battery",
                    cause = IllegalStateException("Unexpected loading state")
                ))
            }
        }
    }
    
    /**
     * Get battery status directly (convenience method).
     */
    suspend fun getBatteryStatus(): Result<BatteryInfo, DeviceApiError> {
        return batteryAction.execute()
    }
    
    // ========== Streaming ==========
    
    /**
     * Start a streaming operation (e.g., sensor updates).
     * 
     * @param action The streaming action
     * @param params Stream parameters
     * @return Stream ID for managing the stream
     */
    fun startStream(
        action: DeviceApiAction,
        params: Map<String, String> = emptyMap()
    ): Result<String, DeviceApiError> {
        val streamId = UUID.randomUUID().toString()
        
        log.d("Starting stream", mapOf("action" to action.actionName, "streamId" to streamId))
        
        // TODO: Implement streaming for sensors, location, etc.
        return Result.error(DeviceApiError.FeatureNotAvailable("Streaming for ${action.actionName}"))
    }
    
    /**
     * Stop a streaming operation.
     */
    fun stopStream(streamId: String) {
        activeStreams[streamId]?.let { job ->
            log.d("Stopping stream", mapOf("streamId" to streamId))
            job.cancel()
            activeStreams.remove(streamId)
            
            _events.tryEmit(DeviceApiMessage.StreamEnded(
                id = UUID.randomUUID().toString(),
                streamId = streamId,
                action = "unknown",
                reason = "stopped"
            ))
        }
    }
    
    /**
     * Cancel all active streams.
     */
    private fun cancelAllStreams() {
        log.d("Cancelling all streams", mapOf("count" to activeStreams.size))
        activeStreams.values.forEach { it.cancel() }
        activeStreams.clear()
    }
    
    // ========== Helpers ==========
    
    private fun emitError(requestId: String, action: String, error: DeviceApiError) {
        _events.tryEmit(DeviceApiMessage.ApiError(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            action = action,
            errorCode = error.code,
            errorMessage = error.message
        ))
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Device API Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running device API operations"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, TermuxActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, DeviceApiService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Termux Device API")
            .setContentText("Device API service running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
