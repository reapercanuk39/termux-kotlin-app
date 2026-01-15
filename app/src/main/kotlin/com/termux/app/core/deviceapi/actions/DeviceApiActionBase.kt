package com.termux.app.core.deviceapi.actions

import com.termux.app.core.api.DeviceApiError
import com.termux.app.core.api.Result
import com.termux.app.core.logging.TaggedLogger
import com.termux.app.core.logging.TermuxLogger

/**
 * Base class for all Device API actions.
 * Provides common functionality for permission checking, logging, and error handling.
 *
 * @param T The response type for this action
 */
abstract class DeviceApiActionBase<T>(
    protected val logger: TermuxLogger
) {
    
    /**
     * Unique identifier for this action type.
     */
    abstract val actionName: String
    
    /**
     * Human-readable description of this action.
     */
    abstract val description: String
    
    /**
     * List of Android permissions required by this action.
     * Empty list means no special permissions needed.
     */
    open val requiredPermissions: List<String> = emptyList()
    
    /**
     * Minimum Android API level required.
     * Default is API 24 (Android 7.0) which is the app minimum.
     */
    open val minApiLevel: Int = 24
    
    /**
     * Tagged logger for this action.
     */
    protected val log: TaggedLogger by lazy { 
        logger.forTag("DeviceApi.$actionName") 
    }
    
    /**
     * Execute the API action.
     * 
     * @param params Optional parameters for the action
     * @return Result containing the response data or an error
     */
    abstract suspend fun execute(params: Map<String, String> = emptyMap()): Result<T, DeviceApiError>
    
    /**
     * Check if this action is available on the current device.
     */
    open fun isAvailable(): Boolean = android.os.Build.VERSION.SDK_INT >= minApiLevel
    
    /**
     * Validate parameters before execution.
     */
    protected open fun validateParams(params: Map<String, String>): Result<Unit, DeviceApiError> {
        return Result.success(Unit)
    }
    
    /**
     * Wrap an action execution with logging and error handling.
     */
    protected suspend inline fun <R> executeWithLogging(
        crossinline block: suspend () -> R
    ): Result<R, DeviceApiError> {
        log.d("Executing action")
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - startTime
            log.d("Action completed", mapOf("durationMs" to duration))
            Result.success(result)
        } catch (e: SecurityException) {
            log.e("Permission denied", e)
            Result.error(DeviceApiError.PermissionRequired(
                permission = "unknown",
                apiAction = actionName
            ))
        } catch (e: Exception) {
            log.e("Action failed", e)
            Result.error(DeviceApiError.SystemException(
                operation = actionName,
                cause = e
            ))
        }
    }
}
